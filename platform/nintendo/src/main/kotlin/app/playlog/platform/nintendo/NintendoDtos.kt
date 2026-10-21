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
    // No default value: TrackerJson does not encode defaults, and without grant_type Nintendo answers
    // 400 "request doesn't match schema".
    @SerialName("grant_type") val grantType: String,
) {
    companion object {
        const val SESSION_TOKEN_GRANT = "urn:ietf:params:oauth:grant-type:jwt-bearer-session-token"
    }
}

@Serializable
internal data class AccessTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("id_token") val idToken: String? = null,
)

@Serializable
internal data class NintendoUserDto(
    val id: String,
    val nickname: String,
    val mii: NintendoMiiDto? = null,
    /** Account country, ISO alpha-2 (e.g. "PL"); the eShop region. */
    val country: String? = null,
)

@Serializable
internal data class NintendoMiiDto(
    val imageOrigin: String? = null,
    val imageUriTemplate: String? = null,
)

@Serializable
internal data class OwnedDevicesResponse(val ownedDevices: List<OwnedDeviceDto> = emptyList())

@Serializable
internal data class OwnedDeviceDto(val deviceId: String, val label: String? = null, val device: DeviceDto? = null)

@Serializable
internal data class DeviceDto(
    /** `P00` = Switch, `P01` = Switch 2 (assumed by analogy; unknown values show no device name). */
    val platformGeneration: String? = null,
)

@Serializable
internal data class DailySummariesResponse(val dailySummaries: List<DailySummaryDto> = emptyList())

@Serializable
internal data class DailySummaryDto(
    /** `yyyy-MM-dd`, console-local. */
    val date: String,
    /** `ACHIEVED` (final) or `CALCULATING` (today, still growing). */
    val result: String? = null,
    val players: List<DailyPlayerDto> = emptyList(),
)

@Serializable
internal data class DailyPlayerDto(val playingTime: Long = 0, val playedGames: List<PlayedGameDto> = emptyList())

@Serializable
internal data class PlayedGameDto(val meta: AppMetaDto, val playingTime: Long = 0)

@Serializable
internal data class AppMetaDto(val applicationId: String, val title: String? = null, val imageUri: ImageUriDto? = null)

@Serializable
internal data class ImageUriDto(val small: String? = null, val medium: String? = null, val large: String? = null, val extraLarge: String? = null)

/** One title's accumulated play, minutes per console-local date (summed over devices and players). */
@Serializable
internal data class NintendoTitleHistory(
    val title: String,
    val imageUrl: String? = null,
    val device: String? = null,
    val minutesByDate: Map<String, Long> = emptyMap(),
)
