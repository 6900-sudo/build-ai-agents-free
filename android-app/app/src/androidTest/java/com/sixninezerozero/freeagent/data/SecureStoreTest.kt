package com.sixninezerozero.freeagent.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixninezerozero.freeagent.domain.AppSettings
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a real Android device or emulator, so it uses the real Android Keystore.
 * The unit tests on a computer cannot cover this.
 */
@RunWith(AndroidJUnit4::class)
class SecureStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefsName = "free_ai_agent_secure_store"

    @Before
    @After
    fun wipe() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("free_ai_agent_private_store", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun savesAndReadsBackAnEncryptedValue() {
        val store = SecureStore(context)
        store.putString("groq_api_key", "gsk_example_key_1234567890")

        assertEquals("gsk_example_key_1234567890", store.getString("groq_api_key"))
    }

    @Test
    fun storedValueIsNotPlainText() {
        SecureStore(context).putString("groq_api_key", "gsk_example_key_1234567890")

        val raw = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getString("groq_api_key", null)!!
        assertFalse("Key was stored without encryption.", raw.contains("gsk_example_key"))
    }

    @Test
    fun aNewStoreInstanceCanReadEarlierValues() {
        SecureStore(context).putString("system_prompt", "Be helpful.")

        assertEquals("Be helpful.", SecureStore(context).getString("system_prompt"))
    }

    @Test
    fun unreadableValueIsRemoved() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
            .putString("groq_api_key", "not-a-valid-payload").commit()

        val store = SecureStore(context)
        assertNull(store.getString("groq_api_key"))
        assertNull(
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).getString("groq_api_key", null),
        )
    }

    @Test
    fun settingsSaveAndLoadThroughTheRepository() = runTest {
        val repository = AppRepository(SecureStore(context))
        val settings = AppSettings(
            groqApiKey = "gsk_example_key_1234567890",
            geminiApiKey = "AIzaExampleKey1234567890",
            fallbackEnabled = false,
            systemPrompt = "Answer briefly.",
        )

        repository.saveSettings(settings)

        assertEquals(settings, repository.loadSettings())
    }

    /** A phone whose Keystore fails: the key is still saved (in private storage) and read back. */
    @Test
    fun brokenKeystoreStillSavesAndLoadsSettings() = runTest {
        val broken = object : ValueCipher {
            override fun encrypt(plainText: String): String = throw java.security.KeyStoreException("Failed to generate key")
            override fun decrypt(payload: String): String = throw java.security.KeyStoreException("Failed to read key")
            override fun reset() = Unit
        }
        val store = SecureStore(
            PrefsFile(context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)),
            PrefsFile(context.getSharedPreferences("free_ai_agent_private_store", Context.MODE_PRIVATE)),
            broken,
        )
        val repository = AppRepository(store)
        val settings = AppSettings(groqApiKey = "gsk_example_key_1234567890", systemPrompt = "Answer briefly.")

        repository.saveSettings(settings)

        assertEquals(settings, repository.loadSettings())
        assertEquals("KeyStoreException: Failed to generate key", repository.keystoreProblem())
    }
}
