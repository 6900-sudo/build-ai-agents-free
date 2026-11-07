package com.sixninezerozero.freeagent.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val MASTER_KEY_ALIAS = "free_ai_agent_master_key_v1"
private const val PREFERENCES_NAME = "free_ai_agent_secure_store"
private const val FALLBACK_PREFERENCES_NAME = "free_ai_agent_private_store"
private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
private const val IV_LENGTH_BYTES = 12
private const val PROBLEM_KEY = "__keystore_problem"

/** Encrypts and decrypts stored values. */
interface ValueCipher {
    fun encrypt(plainText: String): String
    fun decrypt(payload: String): String

    /** Throws away the key, so the next encrypt makes a fresh one. */
    fun reset()
}

/** A small private key-value file (SharedPreferences in the app). Writes return false on failure. */
interface KeyValueFile {
    fun getString(key: String): String?
    fun putString(key: String, value: String): Boolean
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean): Boolean
    fun keys(): Set<String>
    fun remove(key: String): Boolean
    fun clear(): Boolean
}

/**
 * Stores the app's keys and chat history.
 *
 * Values are encrypted with a key held in Android Keystore. Some phones have a Keystore that
 * fails (vendor bugs, keys lost after a lock-screen change). Rather than refusing to save, the
 * value then goes into a second file in the app's private storage, which other apps can't read and
 * which is never backed up, and [keystoreProblem] says why. A later save that works with the
 * Keystore moves the value back.
 */
class SecureStore(
    private val secure: KeyValueFile,
    private val fallback: KeyValueFile,
    private val cipher: ValueCipher,
) {
    constructor(context: Context) : this(
        PrefsFile(context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)),
        PrefsFile(context.applicationContext.getSharedPreferences(FALLBACK_PREFERENCES_NAME, Context.MODE_PRIVATE)),
        KeystoreCipher(),
    )

    fun putString(key: String, value: String) {
        val encrypted = encryptOrNull(value)
        if (encrypted != null) {
            check(secure.putString(key, encrypted)) { "Could not write the app's private storage." }
            fallback.remove(key)
            if (fallback.keys().none { it != PROBLEM_KEY }) fallback.remove(PROBLEM_KEY)
            return
        }
        check(fallback.putString(key, value)) {
            "Could not write the app's private storage either (${keystoreProblem()})."
        }
        secure.remove(key)
    }

    fun getString(key: String): String? {
        secure.getString(key)?.let { encrypted ->
            runCatching { cipher.decrypt(encrypted) }
                .onSuccess { return it }
                .onFailure { secure.remove(key) } // the Keystore key was lost; the value can't be read
        }
        return fallback.getString(key)
    }

    fun putBoolean(key: String, value: Boolean) {
        check(secure.putBoolean(key, value)) { "Could not save app settings." }
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean = secure.getBoolean(key, defaultValue)

    fun remove(key: String) {
        check(secure.remove(key) && fallback.remove(key)) { "Could not remove app data." }
    }

    fun clear() {
        check(secure.clear() && fallback.clear()) { "Could not erase app data." }
    }

    /**
     * Why the Keystore couldn't be used, e.g. "KeyStoreException: Failed to generate key", while
     * any value is stored without it; otherwise null.
     */
    fun keystoreProblem(): String? =
        if (fallback.keys().any { it != PROBLEM_KEY }) fallback.getString(PROBLEM_KEY) ?: "unknown" else null

    private fun encryptOrNull(value: String): String? {
        try {
            return cipher.encrypt(value)
        } catch (_: Exception) {
            // Some phones lose or corrupt the Keystore key. Make a fresh key once and try again.
            // Values saved with the old key can no longer be read; getString() removes those.
            cipher.reset()
        }
        return try {
            cipher.encrypt(value)
        } catch (error: Exception) {
            val problem = "${error::class.simpleName}: ${error.message ?: "no details"}".take(300)
            fallback.putString(PROBLEM_KEY, problem)
            null
        }
    }
}

// commit() is deliberate: every call runs on Dispatchers.IO and reports durable-write failures.
@SuppressLint("UseKtx", "ApplySharedPref")
class PrefsFile(private val prefs: SharedPreferences) : KeyValueFile {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).commit()
    override fun getBoolean(key: String, defaultValue: Boolean) = prefs.getBoolean(key, defaultValue)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).commit()
    override fun keys(): Set<String> = prefs.all.keys
    override fun remove(key: String) = !prefs.contains(key) || prefs.edit().remove(key).commit()
    override fun clear() = prefs.edit().clear().commit()
}

/** AES-GCM with a key that never leaves Android Keystore. */
class KeystoreCipher : ValueCipher {
    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        // The Keystore key requires randomised encryption, so Android must pick the IV.
        // Passing our own IV is refused ("Caller-provided IV not permitted").
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        check(iv != null && iv.size == IV_LENGTH_BYTES) { "Unexpected IV from Keystore." }
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return "${iv.toBase64()}.${ciphertext.toBase64()}"
    }

    override fun decrypt(payload: String): String {
        val pieces = payload.split('.', limit = 2)
        require(pieces.size == 2) { "Encrypted value has an invalid format." }
        val iv = pieces[0].fromBase64()
        require(iv.size == IV_LENGTH_BYTES) { "Encrypted value has an invalid IV." }
        val ciphertext = pieces[1].fromBase64()

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    override fun reset() {
        runCatching {
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }.deleteEntry(MASTER_KEY_ALIAS)
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
}
