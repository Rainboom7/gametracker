package app.playlog.core.model

import java.time.Instant

data class Screenshot(
    val id: String,
    /** Null for a manual import the user has not assigned to a game yet. */
    val gameId: GameId?,
    val platform: Platform,
    /** https URL for synced captures, `content://` URI for manual imports. */
    val fullUrl: String,
    val thumbnailUrl: String? = null,
    val takenAt: Instant? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** True for video captures (PS5 cloud gallery); the app shows a play badge. */
    val isVideo: Boolean = false,
    val origin: ScreenshotOrigin = ScreenshotOrigin.SYNCED,
)

enum class ScreenshotOrigin {
    /** Pulled from the platform during sync. */
    SYNCED,

    /** Picked by the user (photo picker / SAF), e.g. Switch captures copied to the phone. */
    IMPORTED,
}
