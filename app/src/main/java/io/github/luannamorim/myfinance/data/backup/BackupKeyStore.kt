package io.github.luannamorim.myfinance.data.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.luannamorim.myfinance.domain.backup.BackupHeader
import io.github.luannamorim.myfinance.domain.backup.KeyDerivation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Protects the derived backup key at rest. */
interface KeyWrapper {
    fun wrap(plain: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray): ByteArray
}

/**
 * AES-256-GCM key in the Android Keystore (non-exportable). No user authentication and no
 * "unlocked device" requirement: the backup worker must run with the phone locked.
 */
class AndroidKeystoreKeyWrapper @Inject constructor() : KeyWrapper {
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, wrapped, 0, IV_SIZE))
        return cipher.doFinal(wrapped, IV_SIZE, wrapped.size - IV_SIZE)
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "myfinance_backup_wrap"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

/** Derived backup key plus the parameters needed to write a header. */
class StoredKey(val key: ByteArray, val salt: ByteArray, val iterations: Int) {
    val keyCheck: ByteArray get() = KeyDerivation.keyCheck(key)
}

enum class KeyState { NONE, READY, LOCKED }

/**
 * Holds the password-derived backup key (never the password). The key is stored wrapped by the
 * Keystore; if the Keystore key is lost (reinstall, cleared data) the state becomes LOCKED and the
 * user re-enters the password, which is checked against the saved key-check value.
 */
@Singleton
class BackupKeyStore @Inject constructor(
    @Named("backup_key") private val dataStore: DataStore<Preferences>,
    private val wrapper: KeyWrapper,
) {
    private object Keys {
        val salt = stringPreferencesKey("salt")
        val iterations = intPreferencesKey("iterations")
        val keyCheck = stringPreferencesKey("kcv")
        val wrapped = stringPreferencesKey("wrapped")
    }

    private val random = SecureRandom()
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    val hasPassword: Flow<Boolean> = dataStore.data.map { it[Keys.salt] != null }

    suspend fun state(): KeyState {
        val prefs = dataStore.data.first()
        if (prefs[Keys.salt] == null) return KeyState.NONE
        return if (key() != null) KeyState.READY else KeyState.LOCKED
    }

    /** Derives and stores a new key (new random salt). Slow by design: runs off the main thread. */
    suspend fun setPassword(password: CharArray, iterations: Int = KeyDerivation.DEFAULT_ITERATIONS): StoredKey {
        val salt = ByteArray(BackupHeader.SALT_SIZE).also { random.nextBytes(it) }
        val key = withContext(Dispatchers.Default) { KeyDerivation.derive(password, salt, iterations) }
        val stored = StoredKey(key, salt, iterations)
        adopt(stored)
        return stored
    }

    /** Stores an already-derived key (e.g. the one that unlocked a restored backup). */
    suspend fun adopt(stored: StoredKey) {
        val wrapped = withContext(Dispatchers.Default) { wrapper.wrap(stored.key) }
        dataStore.edit {
            it[Keys.salt] = b64.encodeToString(stored.salt)
            it[Keys.iterations] = stored.iterations
            it[Keys.keyCheck] = b64.encodeToString(stored.keyCheck)
            it[Keys.wrapped] = b64.encodeToString(wrapped)
        }
    }

    /** The usable key, or null when no password is set or the wrapped key cannot be opened. */
    suspend fun key(): StoredKey? {
        val prefs = dataStore.data.first()
        val salt = prefs[Keys.salt] ?: return null
        val iterations = prefs[Keys.iterations] ?: return null
        val wrapped = prefs[Keys.wrapped] ?: return null
        val kcv = prefs[Keys.keyCheck] ?: return null
        val key = runCatching { wrapper.unwrap(unb64.decode(wrapped)) }.getOrNull() ?: return null
        if (!MessageDigest.isEqual(KeyDerivation.keyCheck(key), unb64.decode(kcv))) return null
        return StoredKey(key, unb64.decode(salt), iterations)
    }

    /** Re-derives with the saved salt; on match, re-wraps the key. */
    suspend fun unlock(password: CharArray): Boolean {
        val prefs = dataStore.data.first()
        val salt = prefs[Keys.salt]?.let { unb64.decode(it) } ?: return false
        val iterations = prefs[Keys.iterations] ?: return false
        val kcv = prefs[Keys.keyCheck]?.let { unb64.decode(it) } ?: return false
        val key = withContext(Dispatchers.Default) { KeyDerivation.derive(password, salt, iterations) }
        if (!MessageDigest.isEqual(KeyDerivation.keyCheck(key), kcv)) return false
        adopt(StoredKey(key, salt, iterations))
        return true
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
