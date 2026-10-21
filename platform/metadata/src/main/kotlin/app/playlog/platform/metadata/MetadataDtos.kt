package app.playlog.platform.metadata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class MetadataException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
internal data class HltbSearchResponse(val data: List<HltbGameDto> = emptyList())

/** Times are in seconds; 0 means "no submissions". */
@Serializable
internal data class HltbGameDto(
    @SerialName("game_id") val id: Long,
    @SerialName("game_name") val name: String,
    @SerialName("game_alias") val alias: String? = null,
    @SerialName("comp_main") val main: Long = 0,
    @SerialName("comp_plus") val mainExtra: Long = 0,
    @SerialName("comp_100") val completionist: Long = 0,
    @SerialName("review_score") val reviewScore: Int = 0,
    @SerialName("release_world") val releaseYear: Int = 0,
    @SerialName("profile_dev") val developer: String? = null,
)

/** `store.steampowered.com/api/appdetails` is keyed by appid: `{"1245620": {"success": true, "data": {…}}}`. */
@Serializable
internal data class AppDetailsEnvelope(val success: Boolean = false, val data: AppDetailsDto? = null)

@Serializable
internal data class AppDetailsDto(
    val name: String? = null,
    val developers: List<String> = emptyList(),
    val metacritic: MetacriticDto? = null,
    @SerialName("release_date") val releaseDate: ReleaseDateDto? = null,
    val genres: List<GenreDto> = emptyList(),
)

@Serializable
internal data class MetacriticDto(val score: Int = 0, val url: String? = null)

@Serializable
internal data class ReleaseDateDto(val date: String? = null)

@Serializable
internal data class GenreDto(val description: String)

@Serializable
internal data class AppReviewsResponse(
    val success: Int = 0,
    @SerialName("query_summary") val summary: ReviewSummaryDto? = null,
)

@Serializable
internal data class ReviewSummaryDto(
    @SerialName("review_score_desc") val description: String? = null,
    @SerialName("total_positive") val positive: Int = 0,
    @SerialName("total_reviews") val total: Int = 0,
)
