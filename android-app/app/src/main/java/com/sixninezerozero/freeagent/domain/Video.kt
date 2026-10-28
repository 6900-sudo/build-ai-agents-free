package com.sixninezerozero.freeagent.domain

import org.json.JSONArray
import org.json.JSONObject

/** Voices the phone app offers. Ids are Kokoro voice names, or "none" for captions only. */
enum class VideoVoice(val id: String, val label: String) {
    UK_MALE("bm_george", "UK male"),
    UK_FEMALE("bf_emma", "UK female"),
    US_FEMALE("af_heart", "US female"),
    NONE("none", "No voice (captions only)"),
}

enum class VideoMood(val id: String, val label: String) {
    CALM("calm", "Calm"),
    UPBEAT("upbeat", "Upbeat"),
    NONE("none", "No music"),
}

/** What fills the screen behind the captions. Real footage comes from Pexels. */
enum class VideoBackground(val id: String, val label: String, val needsPexels: Boolean) {
    ANIMATED("animated", "Animated", false),
    PHOTOS("photos", "Real photos", true),
    CLIPS("clips", "Real video clips", true),
}

data class VideoRequest(
    val script: String,
    val voice: VideoVoice = VideoVoice.UK_MALE,
    val mood: VideoMood = VideoMood.CALM,
    val quality: String = "1080",
    val background: VideoBackground = VideoBackground.ANIMATED,
    val pexelsKey: String = "",
    /** Only for tests: a fake Pexels API address. */
    val stockBase: String? = null,
) {
    fun toJson(queries: List<String>? = null): String = JSONObject()
        .put("script", script)
        .put("voiceId", voice.id)
        .put("mood", mood.id)
        .put("quality", quality)
        .put("background", if (background.needsPexels && pexelsKey.isBlank()) "animated" else background.id)
        .put("pexelsKey", pexelsKey)
        .apply { if (queries != null) put("queries", JSONArray(queries)) }
        .apply { if (stockBase != null) put("stockBase", stockBase) }
        .toString()
}

/** Asking the AI for stock-footage search words, one per scene. */
object VideoQueries {
    const val SYSTEM_PROMPT =
        "You choose stock footage for short vertical videos. For each numbered scene, give one " +
            "Pexels search of 1 to 3 plain words describing something visible on screen (objects, " +
            "places, people doing things). Avoid abstract words, brand names and text. Reply with " +
            "only a JSON array of strings, one per scene, in order."

    fun prompt(scenes: List<String>): String =
        scenes.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")

    /** The search words, or null when the reply isn't a JSON array with one entry per scene. */
    fun parse(reply: String, sceneCount: Int): List<String>? {
        val start = reply.indexOf('[')
        val end = reply.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        val array = runCatching { JSONArray(reply.substring(start, end + 1)) }.getOrNull() ?: return null
        if (array.length() != sceneCount) return null
        val queries = (0 until array.length()).map {
            array.optString(it).replace(Regex("""[^\p{L}\p{N} '-]"""), " ").replace(Regex("""\s+"""), " ").trim().take(40)
        }
        return if (queries.all { it.isNotEmpty() }) queries else null
    }
}

/** Progress from the video engine. fraction is null when the step has no measurable size. */
data class VideoProgress(val stage: String, val fraction: Double?) {
    val label: String
        get() = when (stage) {
            "voice-download" -> "Downloading the free voice (first time only, about 90 MB)…"
            "voice" -> "Making the voiceover…"
            "music" -> "Adding music…"
            "video" -> "Rendering the video…"
            "convert" -> "Converting to MP4…"
            "plan" -> "Choosing footage for each scene…"
            "stock" -> "Downloading stock footage…"
            "save" -> "Saving…"
            else -> "Getting ready…"
        }
}

object VideoScript {
    const val MAX_WORDS = 220
    const val MIN_WORDS = 3

    private val lineNoise = listOf(
        Regex("""^\s*(\*\*)?\s*(title|script|voice-?over|vo|narrator|caption|scene \d+|hook|cta|call to action)\s*(\*\*)?\s*[:\-–]\s*""", RegexOption.IGNORE_CASE),
        Regex("""^\s*[>*\-•]+\s+"""),
    )
    private val stageDirection = Regex("""^\s*[\[(].*[\])]\s*$""")
    // Only clear chat lines are dropped, so a script line that starts "Great..." is kept.
    private val chatter = Regex(
        """^\s*(?:(?:here(?:'s| is)|sure|okay|ok|of course|certainly)\b.*(?:script|video|:)\W*""" +
            """|(?:tap|press)\b.*make video.*|(?:hope this helps|let me know|feel free)\b.*)$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Turns an assistant reply into words to be spoken. It removes markdown, emoji, stage
     * directions in brackets and chatty lines such as "Here's your script:" or "Tap Make video".
     */
    fun fromReply(reply: String): String {
        val lines = reply.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { stageDirection.matches(it) || chatter.matches(it) || it.startsWith("```") }
            .map { line ->
                var cleaned = line
                lineNoise.forEach { cleaned = cleaned.replace(it, "") }
                cleaned
                    .replace(Regex("""^#+\s*"""), "")
                    .replace(Regex("""[*_`~]"""), "")
                    .replace(Regex("""\[(.*?)]\(.*?\)"""), "$1")
                    .replace(Regex("""\s*[\[(](pause|beat|music|sfx|cut to|b-?roll)[^\])]*[\])]\s*""", RegexOption.IGNORE_CASE), " ")
                    .replace(emoji, "")
                    .replace(Regex("""^"(.*)"$"""), "$1")
                    .trim()
            }
            .filter { it.isNotEmpty() }
        return lines.joinToString("\n\n").trim()
    }

    fun wordCount(script: String): Int = script.split(Regex("""\s+""")).count { it.any(Char::isLetterOrDigit) }

    /** Null when the script can be made into a video, otherwise what is wrong. */
    fun problem(script: String): String? {
        val words = wordCount(script)
        return when {
            words < MIN_WORDS -> "Write a few words for the video first."
            words > MAX_WORDS -> "That's $words words. Keep it under $MAX_WORDS (about 90 seconds) for a short video."
            else -> null
        }
    }

    private val emoji = Regex("""[\x{1F000}-\x{1FAFF}\x{2600}-\x{27BF}\x{FE0F}\x{200D}\x{2B00}-\x{2BFF}]""")
}

/** A video that has been made and saved where the user can find it. */
data class VideoOutcome(
    val uri: String,
    val displayPath: String,
    val durationSeconds: Double,
    val makeSeconds: Double,
    val warnings: List<String>,
)

interface VideoService {
    /**
     * Makes and saves the video. For stock footage, [searchesFor] gets the scene texts and may
     * return one search per scene (null lets the engine choose its own keywords).
     */
    suspend fun makeAndSave(
        request: VideoRequest,
        searchesFor: suspend (List<String>) -> List<String>? = { null },
        onProgress: (VideoProgress) -> Unit,
    ): VideoOutcome
}

data class VideoUiState(
    val script: String = "",
    val voice: VideoVoice = VideoVoice.UK_MALE,
    val mood: VideoMood = VideoMood.CALM,
    val background: VideoBackground = VideoBackground.ANIMATED,
    val isMaking: Boolean = false,
    val progress: VideoProgress? = null,
    val error: String? = null,
    val result: VideoOutcome? = null,
)
