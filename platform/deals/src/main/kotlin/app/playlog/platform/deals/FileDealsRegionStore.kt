package app.playlog.platform.deals

import app.playlog.core.data.DealsRegionStore
import app.playlog.core.model.Platform
import app.playlog.core.network.TrackerJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** Manual store regions in one small JSON file (app files dir, not cache: it is a user choice). */
class FileDealsRegionStore(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : DealsRegionStore {
    private val lock = Mutex()
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    override suspend fun get(platform: Platform): String? = lock.withLock { read()[platform.name] }

    override suspend fun set(platform: Platform, country: String?) = lock.withLock {
        val values = read().toMutableMap()
        if (country == null) values.remove(platform.name) else values[platform.name] = country
        withContext(io) {
            file.parentFile?.mkdirs()
            file.writeText(TrackerJson.encodeToString(serializer, values))
        }
    }

    private suspend fun read(): Map<String, String> = withContext(io) {
        if (!file.exists()) return@withContext emptyMap()
        runCatching { TrackerJson.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyMap())
    }
}
