package app.playlog.platform.deals

import app.playlog.core.data.GiveawaysStore
import app.playlog.core.data.StoredGiveaways
import app.playlog.core.model.Giveaway
import app.playlog.core.model.GiveawayStore
import app.playlog.core.network.TrackerJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant

/** Giveaways and the announced ids in one JSON file; losing it only means one repeated notification. */
class FileGiveawaysStore(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : GiveawaysStore {
    override suspend fun read(): StoredGiveaways? = withContext(io) {
        if (!file.exists()) return@withContext null
        runCatching { TrackerJson.decodeFromString(StoredFeed.serializer(), file.readText()) }.getOrNull()?.let { s ->
            StoredGiveaways(s.items.map { it.toModel() }, Instant.ofEpochMilli(s.fetchedAt), s.announced.toSet())
        }
    }

    override suspend fun write(value: StoredGiveaways) = withContext(io) {
        file.parentFile?.mkdirs()
        val stored = StoredFeed(value.fetchedAt.toEpochMilli(), value.items.map(StoredGiveaway::of), value.announced.toList())
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(TrackerJson.encodeToString(StoredFeed.serializer(), stored))
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
        Unit
    }
}

@Serializable
internal data class StoredFeed(val fetchedAt: Long, val items: List<StoredGiveaway>, val announced: List<String> = emptyList())

@Serializable
internal data class StoredGiveaway(
    val id: String,
    val title: String,
    val image: String? = null,
    val url: String,
    val store: GiveawayStore = GiveawayStore.OTHER,
    val platforms: String = "",
    val worth: String? = null,
    val startsAt: Long? = null,
    val endsAt: Long? = null,
    val description: String? = null,
    val upcoming: Boolean = false,
) {
    fun toModel() = Giveaway(
        id, title, image, url, store, platforms, worth,
        startsAt?.let(Instant::ofEpochMilli), endsAt?.let(Instant::ofEpochMilli), description, upcoming,
    )

    companion object {
        fun of(g: Giveaway) = StoredGiveaway(
            g.id, g.title, g.imageUrl, g.url, g.store, g.platformsText, g.worth,
            g.startsAt?.toEpochMilli(), g.endsAt?.toEpochMilli(), g.description, g.upcoming,
        )
    }
}
