package app.playlog.platform.steam

import app.playlog.core.model.Account
import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.Platform
import app.playlog.core.model.Screenshot
import app.playlog.platform.api.AccountLinker
import app.playlog.platform.api.CredentialStore
import app.playlog.platform.api.GamePlatformSource
import app.playlog.platform.api.LinkFieldType
import app.playlog.platform.api.LinkFlow
import app.playlog.platform.api.LinkStep
import app.playlog.platform.api.LinkStepType
import app.playlog.platform.api.PlatformException
import app.playlog.platform.api.SourceCapability
import app.playlog.platform.api.TypedCredentials
import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

@Serializable
internal data class SteamCredentials(val apiKey: String, val steamId: String, val displayName: String, val avatarUrl: String? = null)

class SteamSource internal constructor(
    private val api: SteamApi,
    credentialStore: CredentialStore,
    private val maxScreenshotPages: Int = 10,
) : GamePlatformSource {

    constructor(client: HttpClient, credentialStore: CredentialStore) : this(SteamApi(client), credentialStore)

    private val credentials = TypedCredentials(credentialStore, Platform.STEAM, SteamCredentials.serializer())

    override val platform = Platform.STEAM

    override val capabilities = setOf(
        SourceCapability.TOTAL_PLAYTIME,
        SourceCapability.RECENT_PLAYTIME,
        SourceCapability.PLAY_DATES,
        SourceCapability.SCREENSHOTS,
    )

    override suspend fun fetchLibrary(): List<Game> {
        val creds = credentials.require()
        val response = api.ownedGames(creds.apiKey, creds.steamId)
        // A private "Game details" setting returns an empty object instead of an error.
        if (response.games == null && response.gameCount == 0) {
            val summary = api.playerSummary(creds.apiKey, creds.steamId)
            if (summary != null && summary.communityvisibilitystate != PUBLIC) throw PlatformException.PrivateProfile(platform)
            return emptyList()
        }
        return response.games.orEmpty().map { it.toGame() }
    }

    override suspend fun fetchScreenshots(): List<Screenshot> {
        val creds = credentials.require()
        val result = mutableListOf<Screenshot>()
        var page = 1
        while (page <= maxScreenshotPages) {
            val response = api.screenshots(creds.apiKey, creds.steamId, page, PAGE_SIZE)
            val files = response.publishedfiledetails.orEmpty()
            files.mapNotNullTo(result) { it.toScreenshot() }
            if (files.size < PAGE_SIZE || page * PAGE_SIZE >= response.total) break
            page++
        }
        return result
    }

    override val linker: AccountLinker = object : AccountLinker {
        override suspend fun start() = LinkFlow(
            steps = listOf(
                LinkStep(LinkStepType.STEAM_OPEN_API_KEY_PAGE, "https://steamcommunity.com/dev/apikey"),
                LinkStep(LinkStepType.STEAM_MAKE_PROFILE_PUBLIC, "https://steamcommunity.com/my/edit/settings"),
            ),
            fields = listOf(LinkFieldType.STEAM_API_KEY, LinkFieldType.STEAM_PROFILE),
        )

        override suspend fun complete(flow: LinkFlow, input: Map<LinkFieldType, String>): Account {
            val key = input[LinkFieldType.STEAM_API_KEY].orEmpty().trim()
            if (!API_KEY.matches(key)) {
                throw PlatformException.InvalidInput(platform, LinkFieldType.STEAM_API_KEY, "API key must be 32 hex characters")
            }
            val profile = SteamProfileInput.parse(input[LinkFieldType.STEAM_PROFILE].orEmpty())
                ?: throw PlatformException.InvalidInput(platform, LinkFieldType.STEAM_PROFILE, "Unrecognized Steam profile")

            val steamId = try {
                when (profile) {
                    is SteamProfileInput.Id64 -> profile.steamId
                    is SteamProfileInput.Vanity -> {
                        val resolved = api.resolveVanity(key, profile.name)
                        resolved.steamid?.takeIf { resolved.success == 1 }
                            ?: throw PlatformException.InvalidInput(platform, LinkFieldType.STEAM_PROFILE, "No profile named ${profile.name}")
                    }
                }
            } catch (e: PlatformException.AuthExpired) {
                throw PlatformException.InvalidInput(platform, LinkFieldType.STEAM_API_KEY, "Steam rejected the API key")
            }
            val summary = api.playerSummary(key, steamId)
                ?: throw PlatformException.InvalidInput(platform, LinkFieldType.STEAM_PROFILE, "Profile $steamId not found")

            val creds = SteamCredentials(key, steamId, summary.personaname, summary.avatarfull)
            credentials.write(creds)
            return creds.toAccount()
        }

        override suspend fun current(): Account? = credentials.read()?.toAccount()

        override suspend fun unlink() = credentials.write(null)
    }

    private fun SteamCredentials.toAccount() =
        Account(platform = platform, userId = steamId, displayName = displayName, avatarUrl = avatarUrl)

    private fun OwnedGameDto.toGame(): Game {
        val devices = buildSet {
            if (playtimeWindows > 0) add("Windows")
            if (playtimeMac > 0) add("macOS")
            if (playtimeLinux > 0) add("Linux")
            if (playtimeDeck > 0) add("Steam Deck")
        }
        return Game(
            id = GameId(platform, appid.toString()),
            title = name ?: "App $appid",
            artwork = SteamArtwork.forApp(appid, iconHash),
            playtime = Duration.ofMinutes(playtimeForever),
            recentPlaytime = Duration.ofMinutes(playtime2Weeks ?: 0),
            lastPlayedAt = lastPlayed.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
            devices = devices,
        )
    }

    private fun PublishedFileDto.toScreenshot(): Screenshot? {
        if (fileType != null && fileType != WORKSHOP_FILE_TYPE_SCREENSHOT) return null
        val url = fileUrl ?: return null
        return Screenshot(
            id = "steam-$publishedfileid",
            gameId = GameId(platform, appid.toString()),
            platform = platform,
            fullUrl = url,
            thumbnailUrl = previewUrl,
            takenAt = timeCreated.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
            width = width,
            height = height,
        )
    }

    private companion object {
        const val PUBLIC = 3
        const val PAGE_SIZE = 100
        const val WORKSHOP_FILE_TYPE_SCREENSHOT = 5
        val API_KEY = Regex("^[0-9A-Fa-f]{32}$")
    }
}
