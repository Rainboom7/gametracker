package app.playlog.platform.playstation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PsnTokenResponse(
    @SerialName("access_token") val accessToken: String,
    /** Seconds. */
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("refresh_token_expires_in") val refreshTokenExpiresIn: Long? = null,
)

@Serializable
internal data class PsnProfileDto(
    val onlineId: String,
    val avatars: List<PsnAvatarDto> = emptyList(),
)

@Serializable
internal data class PsnAvatarDto(val size: String? = null, val url: String)

/** `us-prof.np.community.playstation.net/userProfile/v1/users/me/profile2`. */
@Serializable
internal data class PsnLegacyProfileResponse(val profile: PsnLegacyProfile)

@Serializable
internal data class PsnLegacyProfile(
    val onlineId: String,
    val accountId: String? = null,
    val avatarUrls: List<PsnLegacyAvatar> = emptyList(),
    /** Base64 of "<onlineId>@<server>.<region>", e.g. "user@a6.tr"; the store region. */
    val npId: String? = null,
) {
    fun toProfile() = PsnProfileDto(onlineId, avatarUrls.map { PsnAvatarDto(it.size, it.avatarUrl) })
}

@Serializable
internal data class PsnLegacyAvatar(val size: String? = null, val avatarUrl: String)

@Serializable
internal data class PsnTitlesPage(
    val titles: List<PsnTitleDto> = emptyList(),
    val nextOffset: Int? = null,
    val totalItemCount: Int? = null,
)

@Serializable
internal data class PsnTitleDto(
    val titleId: String,
    val name: String,
    val localizedName: String? = null,
    val imageUrl: String? = null,
    val localizedImageUrl: String? = null,
    /** `ps4_game`, `ps5_native_game`, `pspc_game`, `unknown`. */
    val category: String? = null,
    val playCount: Int? = null,
    val concept: PsnConceptDto? = null,
    val media: PsnMediaDto? = null,
    val firstPlayedDateTime: String? = null,
    val lastPlayedDateTime: String? = null,
    /** ISO-8601 duration, e.g. `PT243H18M48S`. */
    val playDuration: String? = null,
)

@Serializable
internal data class PsnConceptDto(
    val id: Long? = null,
    val name: String? = null,
    val media: PsnMediaDto? = null,
)

@Serializable
internal data class PsnMediaDto(val images: List<PsnImageDto> = emptyList())

@Serializable
internal data class PsnImageDto(
    val url: String,
    /** `MASTER`, `GAMEHUB_COVER_ART`, `PORTRAIT_BANNER`, `BACKGROUND_LAYER_ART`, `SIXTEEN_BY_NINE_BANNER`, … */
    val type: String? = null,
)

@Serializable
internal data class PsnTrophyTitlesPage(
    val trophyTitles: List<PsnTrophyTitleDto> = emptyList(),
    val nextOffset: Int? = null,
)

@Serializable
internal data class PsnTrophyTitleDto(
    val trophyTitleName: String,
    val trophyTitlePlatform: String? = null,
    /** Percent, 0..100. */
    val progress: Int? = null,
)

internal data class PsnGalleryResult(val page: PsnMediaGalleryPage, val signing: CloudFrontSigning?)

@Serializable
internal data class PsnMediaGalleryPage(
    val ugcDocument: List<PsnUgcDto> = emptyList(),
    val nextCursorMark: String? = null,
)

@Serializable
internal data class PsnUgcDto(
    val id: String,
    /** 1 = screenshot, 2 = video. */
    val ugcType: Int? = null,
    val sceTitleId: String? = null,
    val sceTitleName: String? = null,
    val uploadDate: String? = null,
    val downloadUrl: String? = null,
    val screenshotUrl: String? = null,
    val largePreviewImage: String? = null,
    val smallPreviewImage: String? = null,
    val thumbnailUrl: String? = null,
)
