package app.playlog.platform.api

import app.playlog.core.model.Platform
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Secure key-value storage for platform secrets (API keys, refresh tokens). The Android
 * implementation encrypts values with the Android Keystore.
 */
interface CredentialStore {
    suspend fun read(platform: Platform): String?

    /** `null` deletes. */
    suspend fun write(platform: Platform, value: String?)
}

/** Typed JSON view over one platform's slot in a [CredentialStore]. */
class TypedCredentials<T>(
    private val store: CredentialStore,
    private val platform: Platform,
    private val serializer: KSerializer<T>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun read(): T? {
        val raw = store.read(platform) ?: return null
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: SerializationException) {
            // Format changed between app versions; force a relink rather than crash.
            null
        }
    }

    suspend fun require(): T = read() ?: throw PlatformException.NotLinked(platform)

    suspend fun write(value: T?) {
        store.write(platform, value?.let { json.encodeToString(serializer, it) })
    }
}

/** In-memory store for tests and previews. */
class InMemoryCredentialStore : CredentialStore {
    private val values = mutableMapOf<Platform, String>()

    override suspend fun read(platform: Platform): String? = synchronized(values) { values[platform] }

    override suspend fun write(platform: Platform, value: String?) {
        synchronized(values) {
            if (value == null) values.remove(platform) else values[platform] = value
        }
    }
}
