package com.sixninezerozero.freeagent.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTest {
    @Test
    fun `chatty lines, markdown, labels, emoji and stage directions are removed`() {
        val reply = """
            Sure! Here's a script for your video:

            **Title:** Bleed Your Radiators 🔧
            [Upbeat music]
            Radiators cold at the top? That usually means *trapped air*.
            (pause)
            Bleeding them takes a few minutes. 💪

            Tap Make video below this message to create the MP4.
        """.trimIndent()

        val script = VideoScript.fromReply(reply)

        assertEquals(
            "Bleed Your Radiators\n\nRadiators cold at the top? That usually means trapped air.\n\nBleeding them takes a few minutes.",
            script,
        )
    }

    @Test
    fun `ordinary sentences that start like chat are kept`() {
        val script = VideoScript.fromReply("Great gutters save money.\nSure enough, leaves block them every autumn.")
        assertTrue(script.contains("Great gutters save money."))
        assertTrue(script.contains("Sure enough, leaves block them every autumn."))
    }

    @Test
    fun `numbered list items keep their numbers for the listicle style`() {
        val script = VideoScript.fromReply("# 3 Signs Of A Leak\n1. Your bill jumps.\n2. Musty smells.")
        assertEquals("3 Signs Of A Leak\n\n1. Your bill jumps.\n\n2. Musty smells.", script)
    }

    @Test
    fun `word limits are explained`() {
        assertNotNull(VideoScript.problem(""))
        assertNull(VideoScript.problem("Bleed your radiators before winter arrives."))
        val long = List(VideoScript.MAX_WORDS + 5) { "word" }.joinToString(" ")
        assertTrue(VideoScript.problem(long)!!.contains("under"))
    }

    @Test
    fun `request is sent to the engine as JSON`() {
        val json = JSONObject(VideoRequest("Hello there friends", VideoVoice.NONE, VideoMood.UPBEAT, "720").toJson())
        assertEquals("Hello there friends", json.getString("script"))
        assertEquals("none", json.getString("voiceId"))
        assertEquals("upbeat", json.getString("mood"))
        assertEquals("720", json.getString("quality"))
    }

    @Test
    fun `agent is told it can make videos, after the user's own instruction`() {
        val prompt = AiCoordinator.withAppAbilities("Be brief.")
        assertTrue(prompt.startsWith("Be brief."))
        assertTrue(prompt.contains("MP4"))
        assertFalse(VideoScript.fromReply("Tap Make video below this message to create the MP4.").contains("Tap"))
    }

    @Test
    fun `stock background settings reach the engine, and fall back without a Pexels key`() {
        val withKey = JSONObject(
            VideoRequest("Hello there friends", background = VideoBackground.CLIPS, pexelsKey = "abc")
                .toJson(listOf("radiator", "living room")),
        )
        assertEquals("clips", withKey.getString("background"))
        assertEquals("abc", withKey.getString("pexelsKey"))
        assertEquals("living room", withKey.getJSONArray("queries").getString(1))
        assertFalse(withKey.has("stockBase"))

        val noKey = JSONObject(VideoRequest("Hello there friends", background = VideoBackground.PHOTOS).toJson())
        assertEquals("animated", noKey.getString("background"))
    }

    @Test
    fun `AI search words are parsed defensively`() {
        assertEquals(listOf("radiator valve", "cosy living room"), VideoQueries.parse("[\"radiator valve\", \"cosy living room\"]", 2))
        assertEquals(
            listOf("boiler", "plumber at work"),
            VideoQueries.parse("Sure! Here you go:\n```json\n[\"boiler\", \"plumber at work!\"]\n```", 2),
        )
        assertNull(VideoQueries.parse("[\"only one\"]", 2))
        assertNull(VideoQueries.parse("no json here", 1))
        assertNull(VideoQueries.parse("[\"ok\", \"\"]", 2))
        assertEquals("1. First\n2. Second", VideoQueries.prompt(listOf("First", "Second")))
    }

    @Test
    fun `Pexels keys are cleaned and other services' keys refused`() {
        val key = "Ab12Cd34Ef56Gh78Ij90Kl12Mn34Op56Qr78St90Uv12Wx34Yz56Ab12"
        assertNull(ApiKeyValidator.errorForPexels(""))
        assertNull(ApiKeyValidator.errorForPexels("  \u200B$key\n"))
        assertEquals(key, ApiKeyValidator.normalise("Pexels key: $key"))
        assertTrue(ApiKeyValidator.errorForPexels("gsk_" + "A1b2C3d4".repeat(6) + "Z9y8")!!.contains("Groq"))
        assertTrue(ApiKeyValidator.errorForPexels("short")!!.contains("short"))
    }
}
