package app.playlog.platform.deals

import app.playlog.core.common.runSuspendCatching
import app.playlog.core.data.DealsSource
import app.playlog.core.data.Ownership
import app.playlog.core.data.Recommendation
import app.playlog.core.data.TasteSeed
import app.playlog.core.model.DealPick
import app.playlog.core.model.Platform
import app.playlog.core.model.TitleKey
import io.ktor.client.HttpClient
import java.util.Locale

/**
 * 1. Tags the user's most played titles (Steam appid, or a store search by name for console games).
 * 2. Collects Steam games on sale: specials searched by the user's strongest tags ([SteamTasteSpecials]),
 *    plus CheapShark and the Steam front page, minus owned ones.
 * 3. Tags the most promising general deals and ranks everything by similarity ([DealRecommender]).
 * 4. Fetches regional prices, descriptions and screenshots for the winners.
 */
class OnlineDealsSource(
    private val cheapShark: CheapSharkClient,
    private val shop: SteamShopClient,
    private val steamSpy: SteamSpyClient,
    private val tagStore: TagStore,
    private val onError: (String, Throwable) -> Unit = { _, _ -> },
    private val tasteSpecials: SteamTasteSpecials? = null,
) : DealsSource {

    constructor(client: HttpClient, tagStore: TagStore, locale: Locale = Locale.getDefault(), onError: (String, Throwable) -> Unit = { _, _ -> }) : this(
        CheapSharkClient(client),
        SteamShopClient(client, countryCode = locale.country.ifEmpty { "US" }, language = steamLanguage(locale)),
        SteamSpyClient(client),
        tagStore,
        onError,
        SteamTasteSpecials(client, SteamStoreDeals(client)),
    )

    override suspend fun recommend(seeds: List<TasteSeed>, owned: Ownership, country: String): Recommendation {
        try {
            val tagged = seeds.mapNotNull { seed ->
                val appId = seed.steamAppId ?: resolve(seed.title) ?: return@mapNotNull null
                tagsOf(appId)?.takeIf { it.isNotEmpty() }?.let { DealRecommender.TaggedSeed(seed.title, seed.weight, it) }
            }
            if (tagged.isEmpty()) throw DealsException("No tags for any played title")

            val taste = DealRecommender.profile(tagged)
            val targeted = runSuspendCatching { tasteSpecials?.candidates(taste, country).orEmpty() }
                .onFailure { onError("taste specials", it) }
                .getOrDefault(emptyList())
                .filter { (c, _) -> c.isWorthShowing(owned) }
            val targetedIds = targeted.mapTo(HashSet()) { it.first.appId }
            val general = candidates(owned, country, required = targeted.isEmpty())
                .filter { it.appId !in targetedIds }
                .sortedByDescending(::promise)
                .take(MAX_TAGGED)
                .mapNotNull { c -> tagsOf(c.appId)?.let { c to it } }
            val ranked = DealRecommender.rank(tagged, targeted + general, limit = MAX_RANKED)
            val picks = ArrayList<DealPick>(MAX_PICKS)
            for (scored in ranked) {
                if (picks.size == MAX_PICKS) break
                toPick(scored, country)?.let(picks::add)
            }
            return Recommendation(picks, taste)
        } finally {
            tagStore.flush()
        }
    }

    /** With [required] false a dead feed is tolerated: the taste search already found candidates. */
    private suspend fun candidates(owned: Ownership, country: String, required: Boolean = true): List<DealCandidate> {
        val feeds = listOf(
            runSuspendCatching { cheapShark.steamDeals(page = 0) },
            runSuspendCatching { cheapShark.steamDeals(page = 1) },
            runSuspendCatching { shop.specials(country) },
        )
        feeds.forEach { r -> r.exceptionOrNull()?.let { onError("deals feed", it) } }
        if (required && feeds.all { it.isFailure }) throw DealsException("No deals feed reachable", feeds.first().exceptionOrNull())
        return feeds.flatMap { it.getOrDefault(emptyList()) }
            .groupBy { it.appId }
            .map { (_, same) -> same.reduce(::mergeCandidates) }
            .filter { it.isWorthShowing(owned) }
    }

    private fun DealCandidate.isWorthShowing(owned: Ownership): Boolean {
        if (appId in owned.steamAppIds || TitleKey.of(title) in owned.titleKeys) return false
        val review = reviewPercent ?: MIN_REVIEW
        return discountPercent >= MIN_DISCOUNT && review >= MIN_REVIEW
    }

    private suspend fun toPick(scored: DealRecommender.Scored, country: String): DealPick? {
        val c = scored.candidate
        val details = runSuspendCatching { shop.details(c.appId, country) }
        details.exceptionOrNull()?.let { onError("details ${c.appId}", it) }
        val d = details.getOrNull()
        // Not sold in the region, or the regional sale is over: the promise is "on sale now".
        if (details.isSuccess && d?.isOnSale() != true) return null
        val price = d?.priceText ?: c.usdSalePrice ?: return null
        return DealPick(
            platform = Platform.STEAM,
            storeId = c.appId.toString(),
            title = d?.title?.ifBlank { null } ?: c.title,
            headerUrl = d?.headerUrl ?: c.headerUrl ?: steamHeader(c.appId),
            storeUrl = DealPick.steamStoreUrl(c.appId),
            screenshots = d?.screenshots.orEmpty(),
            shortDescription = d?.shortDescription,
            discountPercent = d?.discountPercent ?: c.discountPercent,
            priceText = price,
            originalPriceText = d?.originalPriceText ?: c.usdNormalPrice,
            reviewPercent = c.reviewPercent,
            becauseOf = scored.becauseOf,
            sharedTags = scored.sharedTags,
            match = scored.match,
        )
    }

    private fun SteamShopClient.ShopDetails.isOnSale() = discountPercent > 0 && priceText != null

    private suspend fun resolve(title: String): Long? {
        val key = TitleKey.of(title)
        tagStore.appId(key)?.let { return it.takeIf { id -> id != TagStore.NOT_ON_STEAM } }
        val results = runSuspendCatching { shop.search(title) }
            .onFailure { onError("search $title", it) }
            .getOrNull() ?: return null
        // Only an exact normalized match: "Zelda" must not become some Zelda-like indie.
        val id = results.firstOrNull { (_, name) -> TitleKey.of(name) == key }?.first ?: TagStore.NOT_ON_STEAM
        tagStore.putAppId(key, id)
        return id.takeIf { it != TagStore.NOT_ON_STEAM }
    }

    private suspend fun tagsOf(appId: Long): Map<String, Int>? {
        tagStore.tags(appId)?.let { return it }
        return runSuspendCatching { steamSpy.tags(appId) }
            .onSuccess { tagStore.putTags(appId, it) }
            .onFailure { onError("tags $appId", it) }
            .getOrNull()
    }

    internal companion object {
        const val MAX_TAGGED = 36
        const val MAX_RANKED = 20
        const val MAX_PICKS = 12
        const val MIN_DISCOUNT = 20
        const val MIN_REVIEW = 70
        private const val DEFAULT_REVIEW = 75.0
        private const val DEFAULT_DEAL_RATING = 5.0
        private const val DEAL_RATING_FACTOR = 5.0
        private const val DISCOUNT_FACTOR = 0.2
        private const val REVIEW_FACTOR = 0.5

        /** Cheap pre-ranking before spending SteamSpy requests: well-reviewed, good deals first. */
        fun promise(c: DealCandidate): Double =
            (c.reviewPercent?.toDouble() ?: DEFAULT_REVIEW) * REVIEW_FACTOR +
                (c.dealRating ?: DEFAULT_DEAL_RATING) * DEAL_RATING_FACTOR +
                c.discountPercent * DISCOUNT_FACTOR

        fun mergeCandidates(a: DealCandidate, b: DealCandidate) = DealCandidate(
            appId = a.appId,
            title = a.title.ifBlank { b.title },
            discountPercent = maxOf(a.discountPercent, b.discountPercent),
            headerUrl = a.headerUrl ?: b.headerUrl,
            reviewPercent = a.reviewPercent ?: b.reviewPercent,
            dealRating = a.dealRating ?: b.dealRating,
            usdSalePrice = a.usdSalePrice ?: b.usdSalePrice,
            usdNormalPrice = a.usdNormalPrice ?: b.usdNormalPrice,
        )

        fun steamLanguage(locale: Locale): String = when (locale.language) {
            "ru" -> "russian"
            "uk" -> "ukrainian"
            "de" -> "german"
            "fr" -> "french"
            "es" -> "spanish"
            "pl" -> "polish"
            else -> "english"
        }
    }
}
