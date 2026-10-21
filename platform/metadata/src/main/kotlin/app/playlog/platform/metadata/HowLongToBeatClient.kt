package app.playlog.platform.metadata

import app.playlog.core.model.HowLongToBeat
import app.playlog.core.model.TitleKey
import app.playlog.core.network.TrackerJson
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Duration

/**
 * HowLongToBeat has no public API; this speaks to the JSON endpoint its own search page uses.
 * The endpoint moves every few months, so it is discovered at runtime:
 * 1. `GET /api/search/init` hands out a token sent back as `x-auth-token` (2025 site), else
 * 2. the path is read from the site's `_app` script bundle (`"/api/<name>/".concat("<key>")…`).
 * A 403/404 from a cached endpoint triggers one rediscovery.
 */
class HowLongToBeatClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://howlongtobeat.com",
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private data class Endpoint(val path: String, val token: String?)

    data class Match(val game: HowLongToBeat, val releaseYear: Int?, val developer: String?)

    private val lock = Mutex()
    private var endpoint: Endpoint? = null

    /** Best match for [title], or null when HowLongToBeat has nothing close enough. */
    suspend fun find(title: String): Match? {
        val key = TitleKey.of(title)
        if (key.isEmpty()) return null
        val dto = bestMatch(key, search(key)) ?: return null
        return Match(dto.toModel(), dto.releaseYear.takeIf { it > 0 }, dto.developer?.takeIf(String::isNotBlank))
    }

    internal suspend fun search(terms: String): List<HltbGameDto> {
        val first = endpoint()
        val response = post(first, terms)
        if (response != null) return response
        // Stale key: rediscover once.
        lock.withLock { if (endpoint == first) endpoint = null }
        return post(endpoint(), terms) ?: throw MetadataException("HowLongToBeat search endpoint rejected the request")
    }

    /** Null on 403/404 (endpoint moved); throws on other failures. */
    private suspend fun post(endpoint: Endpoint, terms: String): List<HltbGameDto>? {
        val response = client.post("$baseUrl${endpoint.path}") {
            browserHeaders()
            endpoint.token?.let { header("x-auth-token", it) }
            contentType(ContentType.Application.Json)
            setBody(TrackerJson.encodeToString(JsonObject.serializer(), searchBody(terms)))
        }
        if (response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.Forbidden) return null
        if (!response.status.isSuccess()) throw MetadataException("HowLongToBeat HTTP ${response.status.value}")
        return TrackerJson.decodeFromString(HltbSearchResponse.serializer(), response.bodyAsText()).data
    }

    private suspend fun endpoint(): Endpoint = lock.withLock {
        endpoint ?: discover().also { endpoint = it }
    }

    private suspend fun discover(): Endpoint {
        initToken()?.let { return Endpoint(SEARCH_PATH, it) }
        val home = client.get("$baseUrl/") { browserHeaders() }
        if (!home.status.isSuccess()) throw MetadataException("HowLongToBeat home HTTP ${home.status.value}")
        val scriptPath = APP_SCRIPT.find(home.bodyAsText())?.value
            ?: throw MetadataException("HowLongToBeat: _app script not found")
        val script = client.get(if (scriptPath.startsWith("http")) scriptPath else "$baseUrl$scriptPath") { browserHeaders() }.bodyAsText()
        return Endpoint(searchPathFromScript(script) ?: SEARCH_PATH, token = null)
    }

    private suspend fun initToken(): String? {
        val response = client.get("$baseUrl$SEARCH_PATH/init?t=${nowMillis()}") { browserHeaders() }
        if (!response.status.isSuccess()) return null
        return runCatching {
            TrackerJson.parseToJsonElement(response.bodyAsText()).let { (it as JsonObject)["token"]?.jsonPrimitive?.content }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun HttpRequestBuilder.browserHeaders() {
        header(HttpHeaders.UserAgent, USER_AGENT)
        header(HttpHeaders.Referrer, "$baseUrl/")
        header(HttpHeaders.Origin, baseUrl)
    }

    internal companion object {
        const val SEARCH_PATH = "/api/search"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36"
        private val APP_SCRIPT = Regex("/_next/static/chunks/pages/_app-[\\w-]+\\.js")

        // fetch("/api/seek/".concat("abc").concat("def"), …)  or  fetch("/api/find/" + "abcdef", …)
        private val CONCAT_PATH = Regex("\"/api/(\\w+)/\"((?:\\.concat\\(\"[\\w-]*\"\\))+)")
        private val CONCAT_PART = Regex("\\.concat\\(\"([\\w-]*)\"\\)")
        private val PLUS_PATH = Regex("\"/api/(\\w+)/\"\\s*\\+\\s*\"([\\w-]+)\"")

        fun searchPathFromScript(script: String): String? {
            CONCAT_PATH.find(script)?.let { m ->
                val key = CONCAT_PART.findAll(m.groupValues[2]).joinToString("") { it.groupValues[1] }
                return "/api/${m.groupValues[1]}/$key"
            }
            return PLUS_PATH.find(script)?.let { "/api/${it.groupValues[1]}/${it.groupValues[2]}" }
        }

        fun searchBody(terms: String): JsonObject = buildJsonObject {
            put("searchType", "games")
            putJsonArray("searchTerms") { terms.split(' ').filter(String::isNotBlank).forEach { add(it) } }
            put("searchPage", 1)
            put("size", PAGE_SIZE)
            putJsonObject("searchOptions") {
                putJsonObject("games") {
                    put("userId", 0)
                    put("platform", "")
                    put("sortCategory", "popular")
                    put("rangeCategory", "main")
                    putJsonObject("rangeTime") {
                        put("min", null as Int?)
                        put("max", null as Int?)
                    }
                    putJsonObject("gameplay") {
                        put("perspective", "")
                        put("flow", "")
                        put("genre", "")
                        put("difficulty", "")
                    }
                    putJsonObject("rangeYear") {
                        put("min", "")
                        put("max", "")
                    }
                    put("modifier", "")
                }
                putJsonObject("users") { put("sortCategory", "postcount") }
                putJsonObject("lists") { put("sortCategory", "follows") }
                put("filter", "")
                put("sort", 0)
                put("randomizer", 0)
            }
            put("useCache", true)
        }

        /**
         * Exact normalized title (or alias) wins; otherwise the most popular result whose title
         * contains ours or the other way round, if the lengths are close. Results come sorted by popularity.
         */
        fun bestMatch(key: String, results: List<HltbGameDto>): HltbGameDto? {
            fun keys(g: HltbGameDto) = listOfNotNull(g.name, g.alias?.takeIf(String::isNotBlank)).map(TitleKey::of)
            results.firstOrNull { g -> key in keys(g) }?.let { return it }
            return results.firstOrNull { g ->
                keys(g).any { k ->
                    (k.startsWith(key) || key.startsWith(k)) && minOf(k.length, key.length) >= maxOf(k.length, key.length) * MIN_LENGTH_RATIO
                }
            }
        }

        private const val MIN_LENGTH_RATIO = 0.6
        private const val PAGE_SIZE = 20
    }
}

internal fun HltbGameDto.toModel() = HowLongToBeat(
    id = id,
    title = name,
    mainStory = seconds(main),
    mainExtra = seconds(mainExtra),
    completionist = seconds(completionist),
    userScore = reviewScore.takeIf { it > 0 },
)

private fun seconds(value: Long): Duration? = value.takeIf { it > 0 }?.let(Duration::ofSeconds)
