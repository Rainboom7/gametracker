package app.playlog.platform.playstation

import app.playlog.core.common.AppClock
import app.playlog.core.common.SystemAppClock
import app.playlog.core.common.runSuspendCatching
import app.playlog.core.model.Account
import app.playlog.core.model.Game
import app.playlog.core.model.GameArtwork
import app.playlog.core.model.GameId
import app.playlog.core.model.Platform
import app.playlog.core.model.Screenshot
import app.playlog.core.model.TitleKey
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
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

class PlayStationSource internal constructor(
    private val api: PsnApi,
    private val auth: PsnAuth,
    private val credentials: TypedCredentials<PsnCredentials>,
    private val pageSize: Int = 200,
) : GamePlatformSource {

    override val platform = Platform.PLAYSTATION

    override val capabilities = setOf(
        SourceCapability.TOTAL_PLAYTIME,
        SourceCapability.PLAY_DATES,
        SourceCapability.SCREENSHOTS,
    )

    override suspend fun fetchLibrary(): List<Game> {
        val titles = mutableListOf<PsnTitleDto>()
        var offset = 0
        while (true) {
            val page = api.playedTitles(offset, pageSize)
            titles += page.titles
            val next = page.nextOffset ?: break
            if (next <= offset || page.titles.isEmpty()) break
            offset = next
        }
        val trophies = runSuspendCatching { trophyProgressByTitle() }.getOrDefault(emptyMap())
        return mergeByConcept(titles).map { game ->
            trophies[TitleKey.of(game.title)]?.let { game.copy(achievementProgress = it) } ?: game
        }
    }

    override suspend fun fetchScreenshots(): List<Screenshot> {
        val result = mutableListOf<Screenshot>()
        var cursor: String? = null
        repeat(MAX_GALLERY_PAGES) {
            val page = api.mediaGallery(cursor, GALLERY_PAGE_SIZE)
            page.ugcDocument.mapNotNullTo(result) { it.toScreenshot() }
            cursor = page.nextCursorMark?.takeIf { it.isNotBlank() && it != cursor } ?: return result
        }
        return result
    }

    /** Trophy progress keyed by normalized title; trophy titles carry no game titleId. Best of PS4/PS5 lists wins. */
    private suspend fun trophyProgressByTitle(): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        var offset = 0
        repeat(MAX_TROPHY_PAGES) {
            val page = api.trophyTitles(offset, TROPHY_PAGE_SIZE)
            for (t in page.trophyTitles) {
                val progress = t.progress ?: continue
                result.merge(TitleKey.of(t.trophyTitleName), progress, ::maxOf)
            }
            offset = page.nextOffset ?: return result
        }
        return result
    }

    /** PS4 and PS5 versions of one game share a concept id; show them as one entry. */
    private fun mergeByConcept(titles: List<PsnTitleDto>): List<Game> =
        titles.groupBy { t -> t.concept?.id?.let { "concept:$it" } ?: t.titleId }
            .map { (key, group) ->
                val primary = group.maxBy { it.parsedDuration() }
                Game(
                    id = GameId(platform, key),
                    title = primary.localizedName ?: primary.name,
                    artwork = artworkFor(primary),
                    playtime = group.fold(Duration.ZERO) { acc, t -> acc + t.parsedDuration() },
                    firstPlayedAt = group.mapNotNull { parseInstant(it.firstPlayedDateTime) }.minOrNull(),
                    lastPlayedAt = group.mapNotNull { parseInstant(it.lastPlayedDateTime) }.maxOrNull(),
                    playCount = group.mapNotNull { it.playCount }.takeIf { it.isNotEmpty() }?.sum(),
                    devices = group.mapNotNull { deviceFor(it.category) }.toSet(),
                )
            }

    private fun artworkFor(title: PsnTitleDto): GameArtwork {
        val images = title.concept?.media?.images.orEmpty() + title.media?.images.orEmpty()
        fun find(vararg types: String) = types.firstNotNullOfOrNull { type -> images.firstOrNull { it.type == type }?.url }
        return GameArtwork(
            coverUrl = find("PORTRAIT_BANNER", "GAMEHUB_COVER_ART") ?: title.localizedImageUrl ?: title.imageUrl,
            heroUrl = find("BACKGROUND_LAYER_ART", "SIXTEEN_BY_NINE_BANNER", "FOUR_BY_THREE_BANNER"),
            iconUrl = title.imageUrl ?: find("MASTER"),
        )
    }

    private fun PsnUgcDto.toScreenshot(): Screenshot? {
        val full = screenshotUrl ?: downloadUrl ?: largePreviewImage ?: return null
        return Screenshot(
            id = "psn-$id",
            gameId = sceTitleId?.let { GameId(platform, it) },
            platform = platform,
            fullUrl = full,
            thumbnailUrl = smallPreviewImage ?: thumbnailUrl ?: largePreviewImage,
            takenAt = parseInstant(uploadDate),
            isVideo = ugcType == UGC_VIDEO,
        )
    }

    override val linker: AccountLinker = object : AccountLinker {
        override suspend fun start() = LinkFlow(
            steps = listOf(
                // Preferred: sign in inside a WebView and grab the OAuth code from the redirect.
                LinkStep(
                    LinkStepType.PSN_SIGN_IN,
                    url = auth.authorizeUrl(),
                    captureRedirectPrefix = PsnAuth.REDIRECT_SCHEME,
                    captureField = LinkFieldType.PSN_REDIRECT_URL,
                ),
                // Fallback: paste the npsso cookie from the browser.
                LinkStep(LinkStepType.PSN_OPEN_NPSSO_PAGE, "https://ca.account.sony.com/api/v1/ssocookie"),
            ),
            fields = listOf(LinkFieldType.PSN_NPSSO),
        )

        override suspend fun complete(flow: LinkFlow, input: Map<LinkFieldType, String>): Account {
            val redirect = input[LinkFieldType.PSN_REDIRECT_URL]
            val minted = if (redirect != null) {
                val code = PsnAuth.codeFromRedirect(redirect)
                    ?: throw PlatformException.InvalidInput(platform, LinkFieldType.PSN_REDIRECT_URL, "Sign-in did not return a code")
                try {
                    auth.exchangeCode(code)
                } catch (e: PlatformException.Protocol) {
                    throw PlatformException.InvalidInput(platform, LinkFieldType.PSN_REDIRECT_URL, "Sign-in code rejected, try again")
                }
            } else {
                val npsso = parseNpsso(input[LinkFieldType.PSN_NPSSO].orEmpty())
                    ?: throw PlatformException.InvalidInput(platform, LinkFieldType.PSN_NPSSO, "npsso must be 64 characters")
                try {
                    auth.exchangeNpsso(npsso)
                } catch (e: PlatformException.InvalidInput) {
                    throw PlatformException.InvalidInput(platform, LinkFieldType.PSN_NPSSO, e.message ?: "npsso rejected")
                }
            }
            // Tokens are what matters; a missing profile only costs the display name and avatar.
            val profile = api.profile(minted.accessToken!!)
            val creds = minted.copy(
                onlineId = profile?.onlineId ?: FALLBACK_ONLINE_ID,
                avatarUrl = profile?.avatars?.maxByOrNull { avatarRank(it.size) }?.url,
            )
            credentials.write(creds)
            return creds.toAccount()!!
        }

        override suspend fun current(): Account? = credentials.read()?.toAccount()

        override suspend fun unlink() = credentials.write(null)
    }

    private fun PsnCredentials.toAccount(): Account? =
        onlineId?.let { Account(platform = platform, userId = it, displayName = it, avatarUrl = avatarUrl) }

    companion object {
        private const val FALLBACK_ONLINE_ID = "PlayStation"
        private const val UGC_VIDEO = 2
        private const val GALLERY_PAGE_SIZE = 100
        private const val MAX_GALLERY_PAGES = 10
        private const val TROPHY_PAGE_SIZE = 800
        private const val MAX_TROPHY_PAGES = 5
        private val NPSSO = Regex("^[A-Za-z0-9]{64}$")
        private val NPSSO_IN_JSON = Regex("\"npsso\"\\s*:\\s*\"([A-Za-z0-9]{64})\"")

        fun create(client: HttpClient, credentialStore: CredentialStore, clock: AppClock = SystemAppClock): PlayStationSource {
            val credentials = TypedCredentials(credentialStore, Platform.PLAYSTATION, PsnCredentials.serializer())
            val auth = PsnAuth(client, credentials, clock)
            return PlayStationSource(PsnApi(client, auth), auth, credentials)
        }

        /** Accepts the bare value or the whole `{"npsso":"…"}` JSON the ssocookie page shows. */
        internal fun parseNpsso(raw: String): String? {
            val value = raw.trim()
            if (NPSSO.matches(value)) return value
            return NPSSO_IN_JSON.find(value)?.groupValues?.get(1)
        }

        internal fun deviceFor(category: String?): String? = when (category) {
            "ps5_native_game" -> "PS5"
            "ps4_game" -> "PS4"
            "pspc_game" -> "PC"
            else -> null
        }

        private fun avatarRank(size: String?) = when (size) {
            "xl" -> 4
            "l" -> 3
            "m" -> 2
            "s" -> 1
            else -> 0
        }

        internal fun PsnTitleDto.parsedDuration(): Duration =
            playDuration?.let { runCatching { Duration.parse(it) }.getOrNull() } ?: Duration.ZERO

        internal fun parseInstant(value: String?): Instant? =
            value?.let {
                try {
                    Instant.parse(it)
                } catch (e: DateTimeParseException) {
                    null
                }
            }
    }
}
