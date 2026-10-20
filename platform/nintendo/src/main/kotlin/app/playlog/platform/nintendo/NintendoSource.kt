package app.playlog.platform.nintendo

import app.playlog.core.common.AppClock
import app.playlog.core.common.SystemAppClock
import app.playlog.core.model.Account
import app.playlog.core.model.DailyPlaytime
import app.playlog.core.model.Game
import app.playlog.core.model.GameArtwork
import app.playlog.core.model.GameId
import app.playlog.core.model.Platform
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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Nintendo Switch / Switch 2 play activity. Nintendo exposes no screenshots online, so captures
 * come in through manual import in the app instead.
 */
class NintendoSource internal constructor(
    private val api: NintendoApi,
    private val auth: NintendoAuth,
    private val credentials: TypedCredentials<NintendoCredentials>,
    private val clock: AppClock,
) : GamePlatformSource {

    override val platform = Platform.NINTENDO

    override val capabilities = setOf(
        SourceCapability.TOTAL_PLAYTIME,
        SourceCapability.DAILY_PLAYTIME,
        SourceCapability.PLAY_DATES,
    )

    override suspend fun fetchLibrary(): List<Game> =
        api.playHistories().playHistories.map { h ->
            Game(
                id = GameId(platform, h.titleId),
                title = h.titleName,
                artwork = GameArtwork(coverUrl = h.imageUrl, iconUrl = h.imageUrl),
                playtime = Duration.ofMinutes(h.totalPlayedMinutes),
                firstPlayedAt = parseInstant(h.firstPlayedAt),
                lastPlayedAt = parseInstant(h.lastPlayedAt),
                devices = setOfNotNull(deviceName(h.deviceType)),
            )
        }

    override suspend fun fetchDailyPlaytime(from: LocalDate): List<DailyPlaytime> =
        api.playHistories().recentPlayHistories.flatMap { day ->
            val date = runCatching { LocalDate.parse(day.playedDate) }.getOrNull() ?: return@flatMap emptyList()
            if (date.isBefore(from)) return@flatMap emptyList()
            day.dailyPlayHistories.filter { it.totalPlayedMinutes > 0 }.map {
                DailyPlaytime(GameId(platform, it.titleId), date, Duration.ofMinutes(it.totalPlayedMinutes))
            }
        }

    override val linker: AccountLinker = object : AccountLinker {
        override suspend fun start(): LinkFlow {
            val request = auth.createLoginRequest()
            return LinkFlow(
                steps = listOf(
                    LinkStep(
                        LinkStepType.NINTENDO_SIGN_IN,
                        url = request.url,
                        captureRedirectPrefix = auth.redirectUri,
                        captureField = LinkFieldType.NINTENDO_REDIRECT_URL,
                    ),
                    // Fallback when opened in an external browser: long-press "Select this person" and copy the link.
                    LinkStep(LinkStepType.NINTENDO_COPY_LINK),
                ),
                fields = listOf(LinkFieldType.NINTENDO_REDIRECT_URL),
                state = mapOf(STATE to request.state, VERIFIER to request.verifier),
            )
        }

        override suspend fun complete(flow: LinkFlow, input: Map<LinkFieldType, String>): Account {
            val state = flow.state[STATE]
            val verifier = flow.state[VERIFIER]
            check(state != null && verifier != null) { "LinkFlow did not come from start()" }
            val code = auth.parseRedirect(input[LinkFieldType.NINTENDO_REDIRECT_URL].orEmpty(), state)
                ?: throw PlatformException.InvalidInput(platform, LinkFieldType.NINTENDO_REDIRECT_URL, "Link does not contain session_token_code")
            val sessionToken = try {
                auth.sessionToken(code, verifier)
            } catch (e: PlatformException.Protocol) {
                throw PlatformException.InvalidInput(platform, LinkFieldType.NINTENDO_REDIRECT_URL, "Link expired, sign in again")
            }
            val access = auth.mintAccessToken(sessionToken)
            val user = auth.me(access.accessToken)
            val creds = NintendoCredentials(
                sessionToken = sessionToken,
                accessToken = access.accessToken,
                accessTokenExpiresAt = clock.now().epochSecond + access.expiresIn,
                userId = user.id,
                nickname = user.nickname,
                avatarUrl = user.mii?.imageOrigin,
            )
            credentials.write(creds)
            return creds.toAccount()
        }

        override suspend fun current(): Account? = credentials.read()?.toAccount()

        override suspend fun unlink() = credentials.write(null)
    }

    private fun NintendoCredentials.toAccount() = Account(
        platform = platform,
        userId = userId.orEmpty(),
        displayName = nickname ?: "Nintendo Account",
        avatarUrl = avatarUrl,
    )

    companion object {
        private const val STATE = "state"
        private const val VERIFIER = "verifier"

        fun create(client: HttpClient, credentialStore: CredentialStore, clock: AppClock = SystemAppClock): NintendoSource {
            val credentials = TypedCredentials(credentialStore, Platform.NINTENDO, NintendoCredentials.serializer())
            val auth = NintendoAuth(client, credentials, clock)
            return NintendoSource(NintendoApi(client, auth), auth, credentials, clock)
        }

        internal fun deviceName(type: String?) = when (type) {
            "HAC" -> "Switch"
            "BEE" -> "Switch 2"
            else -> null
        }

        /** Timestamps come with an offset (`2024-05-01T21:13:00+09:00`). */
        internal fun parseInstant(value: String?): Instant? = value?.let {
            try {
                OffsetDateTime.parse(it).toInstant()
            } catch (e: DateTimeParseException) {
                runCatching { Instant.parse(it) }.getOrNull()
            }
        }
    }
}
