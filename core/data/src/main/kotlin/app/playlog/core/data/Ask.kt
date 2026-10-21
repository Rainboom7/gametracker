package app.playlog.core.data

import app.playlog.core.common.runSuspendCatching
import app.playlog.core.model.AskAnswer
import app.playlog.core.model.AskIntent
import app.playlog.core.model.DealPick
import app.playlog.core.model.DealsState
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Platform
import app.playlog.core.model.PlayFeature
import app.playlog.core.model.TitleKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration

/** A store search by tags and play modes, in one region. */
data class FinderQuery(
    val country: String,
    val tags: List<String> = emptyList(),
    val features: Set<PlayFeature> = emptySet(),
    /** Plain title search when no tags or features were understood. */
    val text: String = "",
    val free: Boolean = false,
    val onSale: Boolean = false,
    val limit: Int = 50,
)

/** Finds store games for a request. Throws when the store cannot be reached. */
interface GameFinder {
    /** Most relevant first. */
    suspend fun find(query: FinderQuery): List<DealPick>

    /** Strongest user tags of the store game that best matches [title]; empty when unknown. */
    suspend fun tagsOf(title: String): List<String>
}

/** Answers "во что поиграть" requests from the library and the Steam store. */
class AskEngine(
    private val finder: GameFinder,
    private val library: LibraryRepository,
    private val deals: DealsRepository,
    private val stores: StoreDealsRepository,
    private val log: (String) -> Unit = {},
) {
    suspend fun ask(text: String): AskAnswer {
        val intent = AskParser.parse(text)
        // An unknown title only loses the "like X" part; the rest of the request still runs.
        val similarTags = intent.similarTo
            ?.let { title -> runSuspendCatching { finder.tagsOf(title) }.getOrNull() }
            .orEmpty()
            .take(SIMILAR_TAGS)
        val entries = library.observeLibrary().first()
        if (!intent.understood && intent.leftover.isBlank()) return AskAnswer(intent, emptyList(), emptyList())

        val country = stores.region(Platform.STEAM).country
        val query = storeQuery(intent, similarTags, country)
        log("Ask '$text': tags=${intent.tags} features=${intent.features} like=${intent.similarTo} -> $query")
        val found = if (query.tags.isEmpty() && query.features.isEmpty() && query.text.isBlank()) {
            emptyList()
        } else {
            runSuspendCatching { finder.find(query) }
                .onFailure { log("Ask store search failed: ${it.javaClass.simpleName}: ${it.message}") }
                .getOrThrow()
        }
        val taste = taste()
        return answer(intent, similarTags, entries, found, taste)
            .also { log("Ask '$text': ${found.size} found, ${it.owned.size} owned, ${it.store.size} to buy") }
    }

    private suspend fun taste(): Map<String, Double> = withTimeoutOrNull(TASTE_WAIT_MILLIS) {
        (deals.observe().first { it !is DealsState.Loading } as? DealsState.Ready)?.taste
    }.orEmpty()

    companion object {
        private const val SIMILAR_TAGS = 3
        private const val STORE_TAGS = 3
        private const val TASTE_WAIT_MILLIS = 1_500L
        private const val RESULTS = 30
        private val UNPLAYED: Duration = Duration.ofHours(1)

        /**
         * Steam ANDs every tag, so only the strongest few go into the search; play modes travel as
         * categories, which are exact. With nothing understood, the leftover words become a title search.
         */
        fun storeQuery(intent: AskIntent, similarTags: List<String>, country: String): FinderQuery {
            val tags = (intent.tags + similarTags).distinct().take(STORE_TAGS)
            val features = intent.features
            val text = if (tags.isEmpty() && features.isEmpty() && intent.similarTo == null) intent.leftover else ""
            return FinderQuery(
                country = country,
                tags = tags,
                features = features,
                text = text,
                free = intent.free,
                onSale = intent.onSale,
                limit = if (intent.ownedOnly) OWNED_SEARCH_LIMIT else STORE_SEARCH_LIMIT,
            )
        }

        private const val OWNED_SEARCH_LIMIT = 100
        private const val STORE_SEARCH_LIMIT = 50

        /** Splits store hits into "you already have" and "you could buy", ranked by taste and reviews. */
        fun answer(
            intent: AskIntent,
            similarTags: List<String>,
            entries: List<LibraryEntry>,
            found: List<DealPick>,
            taste: Map<String, Double>,
        ): AskAnswer {
            val bySteamId = entries.flatMap { e ->
                e.games.filter { it.id.platform == Platform.STEAM }.map { it.id.externalId to e }
            }.toMap()
            val byKey = entries.associateBy { it.key }
            fun ownedEntry(p: DealPick): LibraryEntry? =
                (if (p.platform == Platform.STEAM) bySteamId[p.storeId] else null) ?: byKey[TitleKey.of(p.title)]

            val similarKey = intent.similarTo?.let(TitleKey::of)
            val owned = found.mapNotNull(::ownedEntry)
                .distinctBy { it.key }
                .filter { it.key != similarKey }
                .filter { intent.platforms.isEmpty() || it.platforms.any(intent.platforms::contains) }
                .filter { !intent.unplayed || it.totalPlaytime < UNPLAYED }
            val store = if (intent.ownedOnly) {
                emptyList()
            } else {
                found.filter { ownedEntry(it) == null && TitleKey.of(it.title) != similarKey }
                    .let { rank(it, taste) }
                    .take(RESULTS)
            }
            return AskAnswer(intent, owned, store, similarTags)
        }

        /** Store order still counts most (it is relevance); taste and reviews reorder near ties. */
        fun rank(found: List<DealPick>, taste: Map<String, Double>): List<DealPick> {
            val n = found.size.coerceAtLeast(1)
            val tasteKeys = taste.keys.associateBy { it.lowercase() }
            return found.mapIndexed { i, p ->
                val match = TasteMatch.score(taste, p.tags)
                val shared = p.tags.mapNotNull { tasteKeys[it.lowercase()] }.sortedByDescending { taste[it] }.take(SIMILAR_TAGS)
                val score = RELEVANCE * (1.0 - i.toDouble() / n) + TASTE * match + REVIEWS * ((p.reviewPercent ?: 0) / 100.0)
                p.copy(match = match, sharedTags = shared) to score
            }.sortedByDescending { it.second }.map { it.first }
        }

        private const val RELEVANCE = 0.5
        private const val TASTE = 0.3
        private const val REVIEWS = 0.2
    }
}
