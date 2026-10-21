package app.playlog.platform.nintendo

import app.playlog.core.common.AppClock
import app.playlog.core.common.SystemAppClock
import app.playlog.core.common.runSuspendCatching
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
import java.time.LocalDate

/**
 * Nintendo Switch / Switch 2 play activity from the Parental Controls API. It keeps only ~30 days,
 * so the per-day history is accumulated in the credential store and lifetime totals count from the
 * first sync (Nintendo offers no other working source of totals). Nintendo exposes no screenshots
 * online, so captures come in through manual import in the app instead.
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

    override suspend fun storeCountry(): String? = runSuspendCatching {
        if (credentials.read() == null) return null
        auth.me(auth.accessToken()).country?.uppercase()
    }.getOrNull()

    override suspend fun fetchLibrary(): List<Game> = refreshHistory().mapNotNull { (titleId, h) ->
        val dates = h.minutesByDate.filterValues { it > 0 }.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (dates.isEmpty()) return@mapNotNull null
        Game(
            id = GameId(platform, titleId),
            title = h.title,
            artwork = GameArtwork(coverUrl = h.imageUrl, iconUrl = h.imageUrl),
            playtime = Duration.ofMinutes(h.minutesByDate.values.sum()),
            firstPlayedAt = dates.min().atStartOfDay(clock.zone()).toInstant(),
            lastPlayedAt = dates.max().atStartOfDay(clock.zone()).toInstant(),
            devices = setOfNotNull(h.device),
        )
    }

    /** Exact daily minutes from the accumulated history; [fetchLibrary] refreshes it earlier in the same sync. */
    override suspend fun fetchDailyPlaytime(from: LocalDate): List<DailyPlaytime> =
        credentials.require().history.flatMap { (titleId, h) ->
            h.minutesByDate.mapNotNull { (day, minutes) ->
                val date = runCatching { LocalDate.parse(day) }.getOrNull()
                if (date == null || date.isBefore(from) || minutes <= 0) null
                else DailyPlaytime(GameId(platform, titleId), date, Duration.ofMinutes(minutes))
            }
        }

    private suspend fun pairedConsoles(): List<OwnedDeviceDto> {
        val devices = try {
            api.ownedDevices()
        } catch (e: PlatformException.Protocol) {
            // The API answers 404 resource_is_not_found when the account has no paired console (as pynintendoparental notes).
            if (e.message?.contains("HTTP 404") == true) throw PlatformException.Protocol(platform, NO_CONSOLE, e) else throw e
        }
        return devices.ifEmpty { throw PlatformException.Protocol(platform, NO_CONSOLE) }
    }

    /**
     * Merges the API's window into the stored history. Every date the API reports replaces the stored
     * day entirely (today's `CALCULATING` total grows); older days are kept as they were.
     */
    private suspend fun refreshHistory(): Map<String, NintendoTitleHistory> {
        val devices = pairedConsoles()
        val coveredDates = mutableSetOf<String>()
        val window = mutableMapOf<String, MutableMap<String, Long>>()
        val meta = mutableMapOf<String, NintendoTitleHistory>()
        for (device in devices) {
            val deviceName = deviceName(device.device?.platformGeneration)
            for (day in api.dailySummaries(device.deviceId)) {
                coveredDates += day.date
                for (game in day.players.flatMap { it.playedGames }) {
                    val id = game.meta.applicationId
                    window.getOrPut(id) { mutableMapOf() }.merge(day.date, game.playingTime, Long::plus)
                    meta.putIfAbsent(
                        id,
                        NintendoTitleHistory(
                            title = game.meta.title ?: id,
                            imageUrl = game.meta.imageUri?.let { it.extraLarge ?: it.large ?: it.medium ?: it.small },
                            device = deviceName,
                        ),
                    )
                }
            }
        }
        val current = credentials.require()
        val merged = (current.history.keys + window.keys).associateWith { id ->
            val stored = current.history[id]
            val days = stored?.minutesByDate.orEmpty().filterKeys { it !in coveredDates } + window[id].orEmpty()
            val fresh = meta[id]
            NintendoTitleHistory(
                title = fresh?.title ?: stored?.title ?: id,
                imageUrl = fresh?.imageUrl ?: stored?.imageUrl,
                device = fresh?.device ?: stored?.device,
                minutesByDate = days.filterValues { it > 0 },
            )
        }
        credentials.write(credentials.require().copy(history = merged))
        return merged
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
            val previous = credentials.read()?.takeIf { it.userId == user.id }
            val creds = NintendoCredentials(
                sessionToken = sessionToken,
                accessToken = access.accessToken,
                accessTokenExpiresAt = clock.now().epochSecond + access.expiresIn,
                idToken = access.idToken,
                clientId = auth.clientId,
                userId = user.id,
                nickname = user.nickname,
                avatarUrl = user.mii?.imageOrigin,
                // Relinking the same account keeps the accumulated history.
                history = previous?.history.orEmpty(),
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
        private const val NO_CONSOLE = "No console is paired with this account in the Nintendo Switch Parental Controls app"

        fun create(client: HttpClient, credentialStore: CredentialStore, clock: AppClock = SystemAppClock): NintendoSource {
            val credentials = TypedCredentials(credentialStore, Platform.NINTENDO, NintendoCredentials.serializer())
            val auth = NintendoAuth(client, credentials, clock)
            return NintendoSource(NintendoApi(client, auth, clock::zone), auth, credentials, clock)
        }

        internal fun deviceName(platformGeneration: String?) = when (platformGeneration) {
            "P00" -> "Switch"
            "P01" -> "Switch 2"
            else -> null
        }
    }
}
