package app.playlog.platform.metadata

import app.playlog.core.common.AppClock
import app.playlog.core.common.runSuspendCatching
import app.playlog.core.data.GameMetadataSource
import app.playlog.core.data.MetadataQuery
import app.playlog.core.model.GameMetadata
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Queries HowLongToBeat (every title) and the Steam store (titles owned on Steam) in parallel.
 * One service failing still yields a record from the others; all of them failing throws, so the
 * repository does not cache an outage as "nothing known".
 */
class OnlineMetadataSource(
    private val hltb: HowLongToBeatClient,
    private val steamStore: SteamStoreClient,
    private val clock: AppClock,
) : GameMetadataSource {

    constructor(client: HttpClient, clock: AppClock) : this(HowLongToBeatClient(client), SteamStoreClient(client), clock)

    override suspend fun fetch(query: MetadataQuery): GameMetadata = coroutineScope {
        val hltbResult = async { attempt { hltb.find(query.title) } }
        val details = query.steamAppId?.let { id -> async { attempt { steamStore.details(id) } } }
        val reviews = query.steamAppId?.let { id -> async { attempt { steamStore.reviews(id) } } }

        val results = listOfNotNull(hltbResult.await(), details?.await(), reviews?.await())
        if (results.all { it.isFailure }) throw results.firstNotNullOf { it.exceptionOrNull() }

        val match = hltbResult.await().getOrNull()
        val facts = details?.await()?.getOrNull()
        GameMetadata(
            key = query.key,
            fetchedAt = clock.now(),
            howLongToBeat = match?.game,
            criticScore = facts?.criticScore,
            userReviews = reviews?.await()?.getOrNull(),
            developer = facts?.developer ?: match?.developer,
            releaseYear = facts?.releaseYear ?: match?.releaseYear,
            genres = facts?.genres.orEmpty(),
        )
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = runSuspendCatching { block() }
}
