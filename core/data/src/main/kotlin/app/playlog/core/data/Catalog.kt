package app.playlog.core.data

import app.playlog.core.common.runSuspendCatching
import app.playlog.core.model.DealPick
import app.playlog.core.model.Platform
import app.playlog.core.model.TitleKey
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/** One game found in the stores' catalogues, with its listing in each store that sells it. */
data class CatalogGame(
    val title: String,
    val titleKey: String,
    /** One listing per store, in the order the stores were asked. */
    val offers: List<DealPick>,
    /** The same title is already in the user's library. */
    val owned: Boolean,
) {
    val main: DealPick get() = offers.first()
    val platforms: List<Platform> get() = offers.map { it.platform }
}

/** Search beyond the library: every store's catalogue at once, merged into games. */
interface CatalogSearch {
    suspend fun search(query: String): List<CatalogGame>
}

class StoreCatalogSearch(
    private val stores: StoreDealsRepository,
    private val library: LibraryRepository,
    private val onError: (Platform, Throwable) -> Unit = { _, _ -> },
    private val limit: Int = DEFAULT_LIMIT,
) : CatalogSearch {
    override suspend fun search(query: String): List<CatalogGame> {
        val q = query.trim()
        if (q.length < MIN_QUERY) return emptyList()
        val perStore = coroutineScope {
            stores.platforms.map { platform ->
                async { runSuspendCatching { stores.search(platform, q) }.onFailure { onError(platform, it) }.getOrNull().orEmpty() }
            }.awaitAll()
        }
        val owned = library.observeLibrary().first().mapTo(HashSet()) { TitleKey.of(it.title) }
        return merge(perStore, owned).take(limit)
    }

    companion object {
        private const val MIN_QUERY = 2
        private const val DEFAULT_LIMIT = 20

        /**
         * Interleaves the stores' result lists rank by rank, so every store's best hit shows near the
         * top, and folds listings with the same [TitleKey] into one game.
         */
        fun merge(perStore: List<List<DealPick>>, owned: Set<String>): List<CatalogGame> {
            val games = LinkedHashMap<String, MutableList<DealPick>>()
            val depth = perStore.maxOfOrNull { it.size } ?: 0
            for (rank in 0 until depth) {
                for (list in perStore) {
                    val pick = list.getOrNull(rank) ?: continue
                    val key = TitleKey.of(pick.title).ifEmpty { continue }
                    val offers = games.getOrPut(key) { mutableListOf() }
                    if (offers.none { it.platform == pick.platform }) offers += pick
                }
            }
            // The shortest listing name is usually the clean one ("Hades II", not "Hades II (PS5)").
            return games.map { (key, offers) -> CatalogGame(offers.minBy { it.title.length }.title, key, offers, key in owned) }
        }
    }
}
