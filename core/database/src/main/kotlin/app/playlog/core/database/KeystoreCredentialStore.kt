package app.playlog.core.database

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.playlog.core.model.Platform
import app.playlog.platform.api.CredentialStore
import kotlinx.coroutines.flow.first
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.credentialsDataStore: DataStore<Preferences> by preferencesDataStore(name = "credentials")

/**
 * Platform secrets (Steam key, PSN refresh token, Nintendo session token) encrypted with an AES-GCM
 * key that never leaves the Android Keystore, stored in a DataStore file excluded from backups.
 */
class KeystoreCredentialStore(context: Context) : CredentialStore {
    private val dataStore = context.applicationContext.credentialsDataStore

    override suspend fun read(platform: Platform): String? {
        val stored = dataStore.data.first()[key(platform)] ?: return null
        return try {
            decrypt(stored)
        } catch (_: GeneralSecurityException) {
            // Key invalidated (device restore, lock screen reset): drop the secret, the user relinks.
            write(platform, null)
            null
        } catch (_: IllegalArgumentException) {
            write(platform, null)
            null
        }
    }

    override suspend fun write(platform: Platform, value: String?) {
        dataStore.edit { prefs ->
            if (value == null) prefs.remove(key(platform)) else prefs[key(platform)] = encrypt(value)
        }
    }

    private fun key(platform: Platform) = stringPreferencesKey("secret_${platform.name.lowercase()}")

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES))
        return String(cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES), Charsets.UTF_8)
    }

    @Synchronized
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KeySizeBits)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "playlog_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

private const val KeySizeBits = 256
