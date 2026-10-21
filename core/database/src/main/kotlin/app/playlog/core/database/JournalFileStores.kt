package app.playlog.core.database

import app.playlog.core.data.BadgeStore
import app.playlog.core.data.GameNotesStore
import app.playlog.core.model.BadgeRecord
import app.playlog.core.model.GameNote
import app.playlog.core.model.MilestoneKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * A small value kept as one JSON file and mirrored in memory. Journal data is a few kilobytes and
 * changes rarely, so a Room table would add migrations without buying anything.
 */
internal class JsonFile<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val empty: T,
    private val io: CoroutineDispatcher,
) {
    private val lock = Mutex()
    private val state = MutableStateFlow<T?>(null)

    fun observe(): Flow<T> = state.onStart { load() }.filterNotNull()

    suspend fun update(change: (T) -> T) = lock.withLock {
        val next = change(loadLocked())
        withContext(io) {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(JSON.encodeToString(serializer, next))
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }
        state.value = next
    }

    private suspend fun load() {
        if (state.value == null) lock.withLock { loadLocked() }
    }

    private suspend fun loadLocked(): T = state.value ?: withContext(io) {
        // A corrupt file loses notes rather than crashing every screen that reads them.
        if (file.exists()) runCatching { JSON.decodeFromString(serializer, file.readText()) }.getOrNull() else null
    }.let { it ?: empty }.also { state.value = it }

    private companion object {
        val JSON = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

class FileBadgeStore(file: File, io: CoroutineDispatcher = Dispatchers.IO) : BadgeStore {
    private val json = JsonFile(file, MapSerializer(String.serializer(), StoredBadge.serializer()), emptyMap(), io)

    override fun observe(): Flow<Map<MilestoneKind, BadgeRecord>> = json.observe().map { stored ->
        stored.mapNotNull { (kind, b) -> MilestoneKind.entries.find { it.name == kind }?.let { it to b.toModel() } }.toMap()
    }

    override suspend fun write(records: Map<MilestoneKind, BadgeRecord>) =
        json.update { records.entries.associate { (kind, r) -> kind.name to StoredBadge(r.level, r.unlockedAt?.toEpochMilli(), r.seenLevel) } }
}

class FileGameNotesStore(file: File, io: CoroutineDispatcher = Dispatchers.IO) : GameNotesStore {
    private val json = JsonFile(file, MapSerializer(String.serializer(), StoredNote.serializer()), emptyMap(), io)

    override fun observe(): Flow<Map<String, GameNote>> = json.observe().map { stored ->
        stored.mapValues { (_, n) -> GameNote(n.rating, n.text, Instant.ofEpochMilli(n.updatedAt)) }
    }

    override suspend fun put(key: String, note: GameNote?) = json.update { all ->
        if (note == null || note.isEmpty) all - key else all + (key to StoredNote(note.rating, note.text, note.updatedAt.toEpochMilli()))
    }
}

@Serializable
internal data class StoredBadge(val level: Int, val unlockedAt: Long? = null, val seenLevel: Int = 0) {
    fun toModel() = BadgeRecord(level, unlockedAt?.let(Instant::ofEpochMilli), seenLevel)
}

@Serializable
internal data class StoredNote(val rating: Int? = null, val text: String = "", val updatedAt: Long)
