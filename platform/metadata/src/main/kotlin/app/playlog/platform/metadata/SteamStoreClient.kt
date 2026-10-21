package app.playlog.platform.metadata

import app.playlog.core.model.CriticScore
import app.playlog.core.model.UserReviews
import app.playlog.core.network.TrackerJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Public, keyless Steam store endpoints: app details (with the Metacritic score) and review summary. */
class SteamStoreClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://store.steampowered.com",
) {
    data class StoreFacts(
        val criticScore: CriticScore?,
        val developer: String?,
        val releaseYear: Int?,
        val genres: List<String>,
    )

    suspend fun details(appId: Long): StoreFacts? {
        val response = client.get("$baseUrl/api/appdetails") {
            parameter("appids", appId)
            parameter("l", "english")
            parameter("cc", "us")
        }
        if (!response.status.isSuccess()) throw MetadataException("Steam store HTTP ${response.status.value}")
        val envelopes = TrackerJson.decodeFromString(MapSerializer(String.serializer(), AppDetailsEnvelope.serializer()), response.bodyAsText())
        val data = envelopes[appId.toString()]?.takeIf { it.success }?.data ?: return null
        return StoreFacts(
            criticScore = data.metacritic?.takeIf { it.score > 0 }?.let { CriticScore(it.score, it.url) },
            developer = data.developers.firstOrNull(),
            releaseYear = data.releaseDate?.date?.let { YEAR.find(it)?.value?.toInt() },
            genres = data.genres.map { it.description },
        )
    }

    suspend fun reviews(appId: Long): UserReviews? {
        val response = client.get("$baseUrl/appreviews/$appId") {
            parameter("json", 1)
            parameter("language", "all")
            parameter("purchase_type", "all")
            parameter("num_per_page", 0)
        }
        if (!response.status.isSuccess()) throw MetadataException("Steam reviews HTTP ${response.status.value}")
        val summary = TrackerJson.decodeFromString(AppReviewsResponse.serializer(), response.bodyAsText()).summary
        if (summary == null || summary.total <= 0) return null
        return UserReviews(
            percentPositive = Math.round(summary.positive * PERCENT / summary.total.toFloat()),
            total = summary.total,
            summary = summary.description,
        )
    }

    private companion object {
        val YEAR = Regex("(19|20)\\d{2}")
        const val PERCENT = 100f
    }
}
