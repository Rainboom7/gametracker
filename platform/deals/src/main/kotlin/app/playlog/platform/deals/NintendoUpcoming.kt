package app.playlog.platform.deals

import app.playlog.core.data.UpcomingSource
import app.playlog.core.model.Platform
import app.playlog.core.model.UpcomingGame
import app.playlog.core.network.TrackerJson
import app.playlog.platform.deals.JsonWalk.array
import app.playlog.platform.deals.JsonWalk.obj
import app.playlog.platform.deals.JsonWalk.string
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Upcoming Switch games from the same regional catalogues as [NintendoStoreDeals]: the Europe Solr
 * search (`dates_released_dts` in the future) or the Americas Algolia index (`availability`
 * "Coming soon" / "Pre-order"). The eShop is flooded with low-effort releases, so only games from
 * publishers with a track record are kept ([KNOWN_PUBLISHERS]); wishlisted games join anyway.
 */
class NintendoUpcoming(
    private val client: HttpClient,
    private val europeSearchUrl: String = "https://searching.nintendo-europe.com",
    private val algoliaUrl: String = "https://${NintendoStoreDeals.ALGOLIA_APP}-dsn.algolia.net",
    private val siteUrl: String = "https://www.nintendo.com",
    private val log: (String) -> Unit = {},
) : UpcomingSource {

    override suspend fun upcoming(country: String): List<UpcomingGame> {
        val all = when (country) {
            in StoreRegions.NINTENDO_EUROPE -> europe(country, StoreRegions.NINTENDO_EUROPE.getValue(country))
            in StoreRegions.NINTENDO_AMERICA -> america(country, StoreRegions.NINTENDO_AMERICA.getValue(country))
            else -> throw DealsException("No eShop catalogue for $country")
        }
        val kept = all.filter { it.second?.let(::isKnownPublisher) == true }.map { it.first }
        log("Nintendo upcoming $country: ${all.size} listed, ${kept.size} from known publishers")
        return kept
    }

    private suspend fun europe(country: String, language: String): List<Pair<UpcomingGame, String?>> {
        val response = client.get("$europeSearchUrl/$language/select") {
            parameter("q", "*")
            parameter("fq", "type:GAME AND dates_released_dts:[NOW TO NOW+$HORIZON_DAYS" + "DAYS]")
            parameter("sort", "dates_released_dts asc")
            parameter("rows", ROWS)
            parameter("wt", "json")
        }
        if (!response.status.isSuccess()) throw DealsException("Nintendo upcoming HTTP ${response.status.value}")
        val body = TrackerJson.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: throw DealsException("Nintendo upcoming: not JSON")
        return body.obj("response")?.array("docs").orEmpty().mapNotNull { e ->
            val d = e as? JsonObject ?: return@mapNotNull null
            val title = d.string("title") ?: return@mapNotNull null
            val id = (d.array("nsuid_txt")?.firstOrNull() as? JsonPrimitive)?.content ?: d.string("fs_id") ?: return@mapNotNull null
            val image = d.string("image_url_h2x1_s") ?: d.string("image_url_sq_s") ?: d.string("image_url") ?: return@mapNotNull null
            val date = (d.array("dates_released_dts")?.firstOrNull() as? JsonPrimitive)?.content?.let(::isoDate)
            val genres = d.array("pretty_game_categories_txt").orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
            UpcomingGame(
                platform = Platform.NINTENDO,
                storeId = id,
                title = title,
                headerUrl = if (image.startsWith("//")) "https:$image" else image,
                storeUrl = NintendoStoreDeals.pageUrl(d.string("url"), title, country, siteUrl),
                date = date,
                tags = genres,
            ) to d.string("publisher")
        }
    }

    private suspend fun america(country: String, index: String): List<Pair<UpcomingGame, String?>> {
        val request = buildJsonObject {
            put("query", JsonPrimitive(""))
            put("hitsPerPage", JsonPrimitive(ROWS))
            put("filters", JsonPrimitive("availability:\"Coming soon\" OR availability:\"Pre-order\""))
        }
        val response = client.post("$algoliaUrl/1/indexes/$index/query") {
            header("X-Algolia-Application-Id", NintendoStoreDeals.ALGOLIA_APP)
            header("X-Algolia-API-Key", NintendoStoreDeals.ALGOLIA_KEY)
            contentType(ContentType.Application.Json)
            setBody(request.toString())
        }
        if (!response.status.isSuccess()) throw DealsException("Nintendo upcoming Algolia HTTP ${response.status.value}")
        val body = TrackerJson.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: throw DealsException("Nintendo upcoming: not JSON")
        return body.array("hits").orEmpty().mapNotNull { e ->
            val h = e as? JsonObject ?: return@mapNotNull null
            val title = h.string("title") ?: return@mapNotNull null
            val id = h.string("nsuid") ?: h.string("objectID") ?: return@mapNotNull null
            val image = h.string("horizontalHeaderImage") ?: h.string("productImage") ?: return@mapNotNull null
            val genres = h.array("genres").orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
            UpcomingGame(
                platform = Platform.NINTENDO,
                storeId = id,
                title = title,
                headerUrl = if (image.startsWith("http")) image else "$ASSET_BASE$image",
                storeUrl = NintendoStoreDeals.pageUrl(h.string("url"), title, country, siteUrl),
                date = h.string("releaseDate")?.let(::isoDate),
                tags = genres,
            ) to (h.string("softwarePublisher") ?: h.string("publisher"))
        }
    }

    internal companion object {
        private const val ROWS = 100
        private const val HORIZON_DAYS = 270
        private const val ASSET_BASE = "https://assets.nintendo.com/image/upload/ar_16:9,c_lpad,w_656/b_white/f_auto/q_auto/"

        /** Publishers whose Switch releases are worth a calendar slot; lowercase substrings. */
        val KNOWN_PUBLISHERS = listOf(
            "nintendo", "the pokémon company", "pokemon", "square enix", "capcom", "sega", "atlus", "bandai namco", "konami",
            "koei tecmo", "ubisoft", "electronic arts", "warner bros", "2k", "take-two", "bethesda", "microsoft", "xbox game studios",
            "activision", "devolver", "annapurna", "team17", "focus entertainment", "thq nordic", "plaion", "deep silver",
            "marvelous", "xseed", "nis america", "nippon ichi", "aksys", "spike chunsoft", "level-5", "arc system works",
            "humble games", "raw fury", "chucklefish", "dotemu", "limited run", "pqube", "playism", "playtonic", "finji",
            "the arcade crew", "curve games", "headup", "merge games", "modus games", "nacon", "microids", "kadokawa",
            "idea factory", "inti creates", "wayforward", "yacht club", "landfall", "coffee stain", "tinybuild", "akupara",
            "hooded horse", "paradox", "505 games", "skybound", "iam8bit", "untitled goose", "panic", "fireshine", "neon doctrine",
        )

        fun isKnownPublisher(publisher: String): Boolean {
            val p = publisher.lowercase()
            return KNOWN_PUBLISHERS.any { it in p }
        }

        fun isoDate(text: String): LocalDate? =
            runCatching { OffsetDateTime.parse(text).toLocalDate() }.getOrNull()
                ?: runCatching { LocalDate.parse(text.take(ISO_DAY)) }.getOrNull()

        private const val ISO_DAY = 10
    }
}
