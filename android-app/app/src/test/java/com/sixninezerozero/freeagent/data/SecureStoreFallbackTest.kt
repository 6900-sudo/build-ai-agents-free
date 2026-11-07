package com.sixninezerozero.freeagent.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureStoreFallbackTest {
    @Test
    fun `working Keystore stores encrypted values only in the secure file`() {
        val secure = MemoryFile()
        val fallback = MemoryFile()
        val store = SecureStore(secure, fallback, FakeCipher())

        store.putString("groq_api_key", "gsk_123")

        assertEquals("enc:gsk_123", secure.values["groq_api_key"])
        assertTrue(fallback.values.isEmpty())
        assertEquals("gsk_123", store.getString("groq_api_key"))
        assertNull(store.keystoreProblem())
    }

    @Test
    fun `broken Keystore still saves, in private storage, and says why`() {
        val cipher = FakeCipher(broken = true)
        val secure = MemoryFile()
        val fallback = MemoryFile()
        val store = SecureStore(secure, fallback, cipher)

        store.putString("groq_api_key", "gsk_123")

        assertEquals(1, cipher.resets) // it tried a fresh key first
        assertEquals("gsk_123", store.getString("groq_api_key"))
        assertNull(secure.values["groq_api_key"])
        assertEquals("KeyStoreException: Keystore is broken", store.keystoreProblem())
    }

    @Test
    fun `value moves back to the Keystore once it works again`() {
        val cipher = FakeCipher(broken = true)
        val fallback = MemoryFile()
        val store = SecureStore(MemoryFile(), fallback, cipher)
        store.putString("groq_api_key", "gsk_old")

        cipher.broken = false
        store.putString("groq_api_key", "gsk_new")

        assertEquals("gsk_new", store.getString("groq_api_key"))
        assertTrue("fallback copy should be gone", fallback.values.isEmpty())
        assertNull(store.keystoreProblem())
    }

    @Test
    fun `if nothing can be written the error explains what failed`() {
        val store = SecureStore(MemoryFile(), MemoryFile(writable = false), FakeCipher(broken = true))
        val error = assertThrows(IllegalStateException::class.java) { store.putString("k", "v") }
        assertTrue(error.message!!.contains("private storage"))
    }

    @Test
    fun `unreadable encrypted value is dropped and erase clears both files`() {
        val secure = MemoryFile().apply { values["groq_api_key"] = "garbage" }
        val fallback = MemoryFile().apply { values["gemini_api_key"] = "AIza" }
        val store = SecureStore(secure, fallback, FakeCipher())

        assertNull(store.getString("groq_api_key"))
        assertNull(secure.values["groq_api_key"])
        assertEquals("AIza", store.getString("gemini_api_key"))

        store.clear()
        assertTrue(secure.values.isEmpty() && fallback.values.isEmpty())
    }

    private class FakeCipher(var broken: Boolean = false) : ValueCipher {
        var resets = 0
        override fun encrypt(plainText: String): String {
            if (broken) throw java.security.KeyStoreException("Keystore is broken")
            return "enc:$plainText"
        }
        override fun decrypt(payload: String): String {
            require(payload.startsWith("enc:")) { "not ours" }
            return payload.removePrefix("enc:")
        }
        override fun reset() {
            resets++
        }
    }

    private class MemoryFile(private val writable: Boolean = true) : KeyValueFile {
        val values = mutableMapOf<String, Any>()
        override fun getString(key: String) = values[key] as? String
        override fun putString(key: String, value: String) = writable.also { if (it) values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) = writable.also { if (it) values[key] = value }
        override fun keys(): Set<String> = values.keys.toSet()
        override fun remove(key: String): Boolean {
            values.remove(key)
            return true
        }
        override fun clear(): Boolean {
            values.clear()
            return true
        }
    }
}
