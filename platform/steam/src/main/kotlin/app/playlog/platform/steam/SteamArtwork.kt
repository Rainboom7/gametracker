package app.playlog.platform.steam

import app.playlog.core.model.GameArtwork

/** Steam CDN artwork. Library assets exist for nearly every app; the UI falls back to `header.jpg` if one 404s. */
object SteamArtwork {
    private const val STORE_ASSETS = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps"
    private const val COMMUNITY_ICONS = "https://media.steampowered.com/steamcommunity/public/images/apps"

    fun cover(appId: Long) = "$STORE_ASSETS/$appId/library_600x900_2x.jpg"
    fun hero(appId: Long) = "$STORE_ASSETS/$appId/library_hero.jpg"
    fun header(appId: Long) = "$STORE_ASSETS/$appId/header.jpg"
    fun icon(appId: Long, hash: String?) = hash?.takeIf { it.isNotBlank() }?.let { "$COMMUNITY_ICONS/$appId/$it.jpg" }

    fun forApp(appId: Long, iconHash: String?) = GameArtwork(
        coverUrl = cover(appId),
        heroUrl = hero(appId),
        iconUrl = icon(appId, iconHash),
        bannerUrl = header(appId),
    )
}
