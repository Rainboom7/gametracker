package app.playlog.platform.deals

import app.playlog.core.common.runSuspendCatching
import app.playlog.core.data.GiveawaysSource
import app.playlog.core.model.Giveaway
import app.playlog.core.model.GiveawayStore
import app.playlog.core.model.TitleKey
import app.playlog.core.network.TrackerJson
import app.playlog.platform.deals.JsonWalk.array
import app.playlog.platform.deals.JsonWalk.obj
import app.playlog.platform.deals.JsonWalk.string
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Free-to-keep games from two keyless feeds:
 * - GamerPower (gamerpower.com/api): an aggregator of Steam, Epic, GOG, PlayStation, Xbox, itch giveaways;
 * - Epic's own `freeGamesPromotions`, which also announces next week's free games.
 * Either may fail; the other still fills the list. Duplicates (same title and store) are merged.
 */
class OnlineGiveawaysSource(
    private val client: HttpClient,
    private val gamerPowerUrl: String = "https://www.gamerpower.com",
    private val epicUrl: String = "https://store-site-backend-static-ipv4.ak.epicgames.com",
    private val country: () -> String = { "US" },
    private val log: (String) -> Unit = {},
) : GiveawaysSource {

    override suspend fun fetch(): List<Giveaway> {
        val feeds = listOf(runSuspendCatching { epic() }, runSuspendCatching { gamerPower() })
        feeds.forEach { r -> r.exceptionOrNull()?.let { log("Giveaways feed failed: ${it.message}") } }
        if (feeds.all { it.isFailure }) throw DealsException("No giveaways feed reachable", feeds.first().exceptionOrNull())
        val all = feeds.flatMap { it.getOrDefault(emptyList()) }
        log("Giveaways: ${all.size} from feeds")
        // Epic first in the list, so its richer entry (dates, upcoming) wins a duplicate.
        return all.distinctBy { TitleKey.of(it.title) + ":" + it.store }
    }

    private suspend fun gamerPower(): List<Giveaway> {
        val response = client.get("$gamerPowerUrl/api/giveaways") {
            parameter("type", "game")
            parameter("sort-by", "date")
        }
        if (!response.status.isSuccess()) throw DealsException("GamerPower HTTP ${response.status.value}")
        val items = TrackerJson.parseToJsonElement(response.bodyAsText()) as? JsonArray ?: return emptyList()
        return items.mapNotNull { e ->
            val g = e as? JsonObject ?: return@mapNotNull null
            if (g.string("status")?.equals("Active", ignoreCase = true) == false) return@mapNotNull null
            val title = g.string("title")?.let(::cleanTitle) ?: return@mapNotNull null
            val platforms = g.string("platforms").orEmpty()
            Giveaway(
                id = "gp:" + (g.string("id") ?: title),
                title = title,
                imageUrl = g.string("image") ?: g.string("thumbnail"),
                url = g.string("open_giveaway_url") ?: g.string("gamerpower_url") ?: return@mapNotNull null,
                store = storeOf(platforms),
                platformsText = platforms,
                worth = g.string("worth")?.takeUnless { it.equals("N/A", ignoreCase = true) },
                startsAt = g.string("published_date")?.let(::gamerPowerDate),
                endsAt = g.string("end_date")?.let(::gamerPowerDate),
                description = g.string("description"),
            )
        }
    }

    private suspend fun epic(): List<Giveaway> {
        val cc = country().ifBlank { "US" }
        val response = client.get("$epicUrl/freeGamesPromotions") {
            parameter("locale", "en-US")
            parameter("country", cc)
            parameter("allowCountries", cc)
        }
        if (!response.status.isSuccess()) throw DealsException("Epic HTTP ${response.status.value}")
        val root = TrackerJson.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return emptyList()
        val elements = root.obj("data")?.obj("Catalog")?.obj("searchStore")?.array("elements").orEmpty()
        return elements.mapNotNull { (it as? JsonObject)?.toEpic() }
    }

    private fun JsonObject.toEpic(): Giveaway? {
        val title = string("title") ?: return null
        val promotions = obj("promotions") ?: return null
        val now = freeWindow(promotions.array("promotionalOffers"))
        val next = freeWindow(promotions.array("upcomingPromotionalOffers"))
        val window = now ?: next ?: return null
        val slug = epicSlug() ?: return null
        val image = array("keyImages").orEmpty().mapNotNull { it as? JsonObject }
            .let { images -> EPIC_IMAGE_TYPES.firstNotNullOfOrNull { type -> images.firstOrNull { it.string("type") == type }?.string("url") } }
        return Giveaway(
            id = "epic:" + (string("id") ?: slug),
            title = title,
            imageUrl = image,
            url = "https://store.epicgames.com/p/$slug",
            store = GiveawayStore.EPIC,
            platformsText = "PC, Epic Games Store",
            worth = obj("price")?.obj("totalPrice")?.obj("fmtPrice")?.string("originalPrice")?.takeUnless { it == "0" },
            startsAt = window.first,
            endsAt = window.second,
            description = string("description"),
            upcoming = now == null,
        )
    }

    /** The first offer window with a 100% discount: `[{promotionalOffers: [{startDate, endDate, discountSetting}]}]`. */
    private fun freeWindow(groups: JsonArray?): Pair<Instant?, Instant?>? = groups.orEmpty()
        .flatMap { (it as? JsonObject)?.array("promotionalOffers").orEmpty() }
        .mapNotNull { it as? JsonObject }
        .firstOrNull { it.obj("discountSetting")?.get("discountPercentage")?.jsonPrimitive?.intOrNull == 0 }
        ?.let { offer -> offer.string("startDate")?.let(::isoInstant) to offer.string("endDate")?.let(::isoInstant) }

    private fun JsonObject.epicSlug(): String? {
        val mappings = (array("offerMappings").orEmpty() + obj("catalogNs")?.array("mappings").orEmpty()).mapNotNull { it as? JsonObject }
        val mapped = mappings.firstOrNull { it.string("pageType") == "productHome" }?.string("pageSlug")
            ?: mappings.firstNotNullOfOrNull { it.string("pageSlug") }
        return mapped ?: string("productSlug")?.substringBefore('/') ?: string("urlSlug")
    }

    internal companion object {
        private val EPIC_IMAGE_TYPES = listOf("OfferImageWide", "DieselStoreFrontWide", "featuredMedia", "Thumbnail", "OfferImageTall")
        private val GAMERPOWER_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val GIVEAWAY_SUFFIX = Regex(
            "\\s*\\((Steam|Epic Games|GOG|PS4|PS5|Xbox[^)]*|itch\\.io|Switch)\\)\\s*Giveaway\\s*$|\\s+Giveaway\\s*$",
            RegexOption.IGNORE_CASE,
        )

        /** "Hades (Epic Games) Giveaway" → "Hades". */
        fun cleanTitle(raw: String): String = raw.replace(GIVEAWAY_SUFFIX, "").trim().ifEmpty { raw.trim() }

        fun storeOf(platforms: String): GiveawayStore {
            val p = platforms.lowercase()
            return when {
                "epic" in p -> GiveawayStore.EPIC
                "steam" in p -> GiveawayStore.STEAM
                "gog" in p -> GiveawayStore.GOG
                "playstation" in p || "ps4" in p || "ps5" in p -> GiveawayStore.PLAYSTATION
                "xbox" in p -> GiveawayStore.XBOX
                "switch" in p || "nintendo" in p -> GiveawayStore.NINTENDO
                "itch" in p -> GiveawayStore.ITCH
                else -> GiveawayStore.OTHER
            }
        }

        /** GamerPower dates are UTC without a zone; "N/A" means no end date. */
        fun gamerPowerDate(text: String): Instant? =
            runCatching { LocalDateTime.parse(text, GAMERPOWER_DATE).toInstant(ZoneOffset.UTC) }.getOrNull()

        fun isoInstant(text: String): Instant? = runCatching { Instant.parse(text) }.getOrNull()
    }
}
