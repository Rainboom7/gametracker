package app.playlog.platform.metadata

import app.playlog.core.data.MetadataStore
import app.playlog.core.model.CriticScore
import app.playlog.core.model.GameMetadata
import app.playlog.core.model.HowLongToBeat
import app.playlog.core.model.UserReviews
import app.playlog.core.network.TrackerJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/**
 * One small JSON file per title under [directory] (the app's cache dir). Metadata is
 * re-fetchable, so the OS clearing the cache only costs a lookup, and Room needs no migration.
 */
class FileMetadataStore(
    private val directory: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : MetadataStore {

    override suspend fun get(key: String): GameMetadata? = withContext(io) {
        val file = fileFor(key)
        if (!file.exists()) return@withContext null
        val text = try {
            file.readText()
        } catch (@Suppress("SwallowedException") e: IOException) {
            return@withContext null
        }
        try {
            TrackerJson.decodeFromString(StoredMetadata.serializer(), text).toModel()
        } catch (e: SerializationException) {
            // Written by an older build or truncated: drop it and look the title up again.
            file.delete()
            null
        }
    }

    override suspend fun put(metadata: GameMetadata) = withContext(io) {
        directory.mkdirs()
        val target = fileFor(metadata.key)
        val temp = File(directory, "${target.name}.tmp")
        temp.writeText(TrackerJson.encodeToString(StoredMetadata.serializer(), StoredMetadata.of(metadata)))
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        Unit
    }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(directory, "$digest.json")
    }
}

/** On-disk shape, versioned separately from the domain model. Durations in seconds, instants in epoch millis. */
@Serializable
internal data class StoredMetadata(
    val key: String,
    val fetchedAt: Long,
    val hltbId: Long? = null,
    val hltbTitle: String? = null,
    val hltbMain: Long? = null,
    val hltbMainExtra: Long? = null,
    val hltbCompletionist: Long? = null,
    val hltbUserScore: Int? = null,
    val criticScore: Int? = null,
    val criticUrl: String? = null,
    val reviewsPercent: Int? = null,
    val reviewsTotal: Int? = null,
    val reviewsSummary: String? = null,
    val developer: String? = null,
    val releaseYear: Int? = null,
    val genres: List<String> = emptyList(),
) {
    fun toModel() = GameMetadata(
        key = key,
        fetchedAt = Instant.ofEpochMilli(fetchedAt),
        howLongToBeat = hltbId?.let {
            HowLongToBeat(
                id = it,
                title = hltbTitle.orEmpty(),
                mainStory = hltbMain?.let(Duration::ofSeconds),
                mainExtra = hltbMainExtra?.let(Duration::ofSeconds),
                completionist = hltbCompletionist?.let(Duration::ofSeconds),
                userScore = hltbUserScore,
            )
        },
        criticScore = criticScore?.let { CriticScore(it, criticUrl) },
        userReviews = if (reviewsPercent != null && reviewsTotal != null) UserReviews(reviewsPercent, reviewsTotal, reviewsSummary) else null,
        developer = developer,
        releaseYear = releaseYear,
        genres = genres,
    )

    companion object {
        fun of(m: GameMetadata) = StoredMetadata(
            key = m.key,
            fetchedAt = m.fetchedAt.toEpochMilli(),
            hltbId = m.howLongToBeat?.id,
            hltbTitle = m.howLongToBeat?.title,
            hltbMain = m.howLongToBeat?.mainStory?.seconds,
            hltbMainExtra = m.howLongToBeat?.mainExtra?.seconds,
            hltbCompletionist = m.howLongToBeat?.completionist?.seconds,
            hltbUserScore = m.howLongToBeat?.userScore,
            criticScore = m.criticScore?.score,
            criticUrl = m.criticScore?.url,
            reviewsPercent = m.userReviews?.percentPositive,
            reviewsTotal = m.userReviews?.total,
            reviewsSummary = m.userReviews?.summary,
            developer = m.developer,
            releaseYear = m.releaseYear,
            genres = m.genres,
        )
    }
}
