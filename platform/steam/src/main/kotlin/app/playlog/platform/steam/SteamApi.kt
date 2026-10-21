package app.playlog.platform.steam

import app.playlog.core.model.Platform
import app.playlog.core.network.platformCall
import app.playlog.core.network.successBody
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/** Thin wrapper over the public Steam Web API (https://api.steampowered.com), authenticated with a user API key. */
internal class SteamApi(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.steampowered.com",
) {
    suspend fun ownedGames(key: String, steamId: String): OwnedGamesResponse = call {
        client.get("$baseUrl/IPlayerService/GetOwnedGames/v1/") {
            parameter("key", key)
            parameter("steamid", steamId)
            parameter("include_appinfo", 1)
            parameter("include_played_free_games", 1)
            parameter("skip_unvetted_apps", false)
            parameter("format", "json")
        }.successBody<SteamEnvelope<OwnedGamesResponse>>(Platform.STEAM).response
    }

    suspend fun playerSummary(key: String, steamId: String): PlayerSummaryDto? = call {
        client.get("$baseUrl/ISteamUser/GetPlayerSummaries/v2/") {
            parameter("key", key)
            parameter("steamids", steamId)
        }.successBody<SteamEnvelope<PlayerSummariesResponse>>(Platform.STEAM).response.players.firstOrNull()
    }

    suspend fun resolveVanity(key: String, vanity: String): ResolveVanityResponse = call {
        client.get("$baseUrl/ISteamUser/ResolveVanityURL/v1/") {
            parameter("key", key)
            parameter("vanityurl", vanity)
        }.successBody<SteamEnvelope<ResolveVanityResponse>>(Platform.STEAM).response
    }

    /** Achievement progress for many apps in one call (IPlayerService/GetAchievementsProgress). */
    suspend fun achievementsProgress(key: String, steamId: String, appIds: List<Long>): AchievementsProgressResponse = call {
        client.get("$baseUrl/IPlayerService/GetAchievementsProgress/v1/") {
            parameter("key", key)
            parameter("steamid", steamId)
            parameter("language", "english")
            parameter("include_unvetted_apps", true)
            appIds.forEachIndexed { i, id -> parameter("appids[$i]", id) }
        }.successBody<SteamEnvelope<AchievementsProgressResponse>>(Platform.STEAM).response
    }

    /** Community screenshots uploaded by the user, newest first. */
    suspend fun screenshots(key: String, steamId: String, page: Int, perPage: Int): UserFilesResponse = call {
        client.get("$baseUrl/IPublishedFileService/GetUserFiles/v1/") {
            parameter("key", key)
            parameter("steamid", steamId)
            parameter("appid", 0)
            parameter("page", page)
            parameter("numperpage", perPage)
            // `filetype` is EPublishedFileInfoMatchingFileType (Screenshots = 4); `type` is a string
            // list mode ("myfiles") and keeps its default. See SteamDatabase/Protobufs.
            parameter("filetype", MATCHING_FILE_TYPE_SCREENSHOTS)
            parameter("sortmethod", "creationorder")
            parameter("return_metadata", true)
        }.successBody<SteamEnvelope<UserFilesResponse>>(Platform.STEAM).response
    }

    /** The user's store wishlist (IWishlistService); public wishlists only. */
    suspend fun wishlist(key: String, steamId: String): WishlistResponse = call {
        client.get("$baseUrl/IWishlistService/GetWishlist/v1/") {
            parameter("key", key)
            parameter("steamid", steamId)
        }.successBody<SteamEnvelope<WishlistResponse>>(Platform.STEAM).response
    }

    private suspend inline fun <T> call(block: () -> T): T = platformCall(Platform.STEAM, block)

    private companion object {
        /** EPublishedFileInfoMatchingFileType.Screenshots */
        const val MATCHING_FILE_TYPE_SCREENSHOTS = 4
    }
}
