package app.playlog.platform.deals

import app.playlog.core.common.runSuspendCatching
import app.playlog.core.data.UpcomingSource
import app.playlog.core.model.DealPick
import app.playlog.core.model.Platform
import app.playlog.core.model.UpcomingGame
import app.playlog.core.network.TrackerJson
import app.playlog.platform.deals.JsonWalk.string
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Steam's "Popular upcoming" search. Rows carry the release column as text; rows without one (and
 * the plain JSON search, which has no dates at all) get a day from `IStoreBrowseService/GetItems`.
 */
class SteamUpcoming(
    private val client: HttpClient,
    private val store: SteamStoreDeals,
    private val baseUrl: String = "https://store.steampowered.com",
    private val pages: Int = 3,
    private val log: (String) -> Unit = {},
) : UpcomingSource {

    override suspend fun upcoming(country: String): List<UpcomingGame> {
        val rows = (0 until pages).flatMap { page ->
            runSuspendCatching { rows(country, page) }
                .onFailure { log("Upcoming p$page failed: ${it.message}") }
                .getOrElse { if (page == 0) throw it else emptyList() }
        }.distinctBy { it.appId }
        val names = if (rows.any { it.tagIds.isNotEmpty() }) store.tagNames() else emptyMap()
        // A window like "Q1 2027" stays a window: GetItems would turn it into a made-up January 1st.
        val undated = rows.filter { it.released == null }.map { it.appId.toString() }
        val quoted = runSuspendCatching { store.quotes(country, undated) }
            .onFailure { log("Upcoming dates failed: ${it.message}") }
            .getOrNull()
            .orEmpty()
        log("Upcoming $country: ${rows.size} rows, ${undated.size} without a day")
        return rows.map { row ->
            val date = row.released?.let(::parseDate) ?: quoted[row.appId.toString()]?.releaseDate?.atZone(ZoneOffset.UTC)?.toLocalDate()
            UpcomingGame(
                platform = Platform.STEAM,
                storeId = row.appId.toString(),
                title = row.name,
                headerUrl = steamHeader(row.appId),
                storeUrl = DealPick.steamStoreUrl(row.appId),
                date = date,
                dateText = row.released.takeIf { date == null },
                tags = row.tagIds.mapNotNull(names::get),
            )
        }
    }

    private suspend fun rows(country: String, page: Int): List<SteamStoreDeals.Row> =
        runSuspendCatching { htmlRows(country, page) }
            .onFailure { log("Upcoming html p$page failed, trying json: ${it.message}") }
            .getOrNull()
            ?: jsonRows(country, page)

    private suspend fun htmlRows(country: String, page: Int): List<SteamStoreDeals.Row> {
        val response = client.get("$baseUrl/search/results/") {
            parameters(country, page)
            parameter("infinite", 1)
        }
        if (!response.status.isSuccess()) throw DealsException("Steam upcoming HTTP ${response.status.value}")
        val body = TrackerJson.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: throw DealsException("Steam upcoming: not JSON")
        val html = body.string("results_html") ?: throw DealsException("Steam upcoming: no results_html")
        return SteamStoreDeals.parseRows(html).ifEmpty { throw DealsException("Steam upcoming: no parsable rows") }
    }

    private suspend fun jsonRows(country: String, page: Int): List<SteamStoreDeals.Row> {
        val response = client.get("$baseUrl/search/results/") {
            parameters(country, page)
            parameter("json", 1)
        }
        if (!response.status.isSuccess()) throw DealsException("Steam upcoming (json) HTTP ${response.status.value}")
        return TrackerJson.decodeFromString(SteamSearchDto.serializer(), response.bodyAsText()).items.mapNotNull { item ->
            LOGO_APP.find(item.logo.orEmpty())?.groupValues?.get(1)?.toLongOrNull()?.let { SteamStoreDeals.Row(it, item.name) }
        }
    }

    private fun HttpRequestBuilder.parameters(country: String, page: Int) {
        parameter("filter", "popularcomingsoon")
        parameter("category1", SteamStoreDeals.GAMES_CATEGORY)
        parameter("start", page * SteamStoreDeals.PAGE_SIZE)
        parameter("count", SteamStoreDeals.PAGE_SIZE)
        parameter("cc", country)
        parameter("l", "english")
    }

    internal companion object {
        private val LOGO_APP = Regex("/apps/(\\d+)/")
        private val SEPT = Regex("\\bSept\\b")
        private val DAY_FORMATS = listOf("d MMM, yyyy", "MMM d, yyyy", "d MMM yyyy", "MMM d yyyy", "d MMMM, yyyy", "MMMM d, yyyy")
            .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

        /** A day from Steam's release column; null for windows like "Q1 2027", "October 2026" or "Coming soon". */
        fun parseDate(text: String?): LocalDate? {
            val clean = text?.trim()?.replace(SEPT, "Sep")?.takeIf { it.isNotEmpty() } ?: return null
            return DAY_FORMATS.firstNotNullOfOrNull { format ->
                try {
                    LocalDate.parse(clean, format)
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }
}
