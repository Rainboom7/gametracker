package app.playlog.platform.nintendo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SessionTokenResponse(
    @SerialName("session_token") val sessionToken: String,
)

@Serializable
internal data class AccessTokenRequest(
    @SerialName("client_id") val clientId: String,
    @SerialName("session_token") val sessionToken: String,
    @SerialName("grant_type") val grantType: String = "urn:ietf:params:oauth:grant-type:jwt-bearer-session-token",
)

@Serializable
internal data class AccessTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
internal data class NintendoUserDto(
    val id: String,
    val nickname: String,
    val mii: NintendoMiiDto? = null,
)

@Serializable
internal data class NintendoMiiDto(
    val imageOrigin: String? = null,
    val imageUriTemplate: String? = null,
)

@Serializable
internal data class PlayHistoriesResponse(
    val playHistories: List<PlayHistoryDto> = emptyList(),
    val recentPlayHistories: List<RecentDayDto> = emptyList(),
    val lastUpdatedAt: String? = null,
)

@Serializable
internal data class PlayHistoryDto(
    val titleId: String,
    val titleName: String,
    /** `HAC` = Switch, `BEE` = Switch 2. */
    val deviceType: String? = null,
    val imageUrl: String? = null,
    val firstPlayedAt: String? = null,
    val lastPlayedAt: String? = null,
    val totalPlayedDays: Int? = null,
    val totalPlayedMinutes: Long = 0,
)

@Serializable
internal data class RecentDayDto(
    /** `yyyy-MM-dd`. */
    val playedDate: String,
    val dailyTotalPlayedMinutes: Long = 0,
    val dailyPlayHistories: List<DailyTitleDto> = emptyList(),
)

@Serializable
internal data class DailyTitleDto(
    val titleId: String,
    val titleName: String? = null,
    val imageUrl: String? = null,
    val totalPlayedMinutes: Long = 0,
)
