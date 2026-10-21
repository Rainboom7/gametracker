package app.playlog.platform.steam

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SteamEnvelope<T>(val response: T)

@Serializable
internal data class OwnedGamesResponse(
    @SerialName("game_count") val gameCount: Int = 0,
    val games: List<OwnedGameDto>? = null,
)

@Serializable
internal data class OwnedGameDto(
    val appid: Long,
    val name: String? = null,
    /** Minutes. */
    @SerialName("playtime_forever") val playtimeForever: Long = 0,
    @SerialName("playtime_2weeks") val playtime2Weeks: Long? = null,
    @SerialName("playtime_windows_forever") val playtimeWindows: Long = 0,
    @SerialName("playtime_mac_forever") val playtimeMac: Long = 0,
    @SerialName("playtime_linux_forever") val playtimeLinux: Long = 0,
    @SerialName("playtime_deck_forever") val playtimeDeck: Long = 0,
    @SerialName("img_icon_url") val iconHash: String? = null,
    /** Unix seconds, 0 when never played. */
    @SerialName("rtime_last_played") val lastPlayed: Long = 0,
)

@Serializable
internal data class AchievementsProgressResponse(
    @SerialName("achievement_progress") val progress: List<AchievementProgressDto> = emptyList(),
)

@Serializable
internal data class AchievementProgressDto(
    val appid: Long,
    val unlocked: Int = 0,
    val total: Int = 0,
    val percentage: Double = 0.0,
)

@Serializable
internal data class PlayerSummariesResponse(val players: List<PlayerSummaryDto> = emptyList())

@Serializable
internal data class PlayerSummaryDto(
    val steamid: String,
    val personaname: String,
    val avatarfull: String? = null,
    /** 1 = private, 3 = public. */
    val communityvisibilitystate: Int = 1,
    /** Profile country, set by the user; absent when hidden. */
    val loccountrycode: String? = null,
)

@Serializable
internal data class ResolveVanityResponse(
    val steamid: String? = null,
    /** 1 = match, 42 = no match. */
    val success: Int,
)

@Serializable
internal data class UserFilesResponse(
    val total: Int = 0,
    val publishedfiledetails: List<PublishedFileDto>? = null,
)

@Serializable
internal data class PublishedFileDto(
    val publishedfileid: String,
    @SerialName("consumer_appid") val appid: Long = 0,
    @SerialName("file_url") val fileUrl: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("time_created") val timeCreated: Long = 0,
    @SerialName("image_width") val width: Int? = null,
    @SerialName("image_height") val height: Int? = null,
    /** EWorkshopFileType of the item; 5 = screenshot. */
    @SerialName("file_type") val fileType: Int? = null,
)
