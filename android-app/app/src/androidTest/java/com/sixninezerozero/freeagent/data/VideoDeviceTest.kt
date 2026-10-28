package com.sixninezerozero.freeagent.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixninezerozero.freeagent.MainActivity
import com.sixninezerozero.freeagent.domain.VideoBackground
import com.sixninezerozero.freeagent.domain.VideoMood
import com.sixninezerozero.freeagent.domain.VideoRequest
import com.sixninezerozero.freeagent.domain.VideoVoice
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Makes real MP4s on the device with the bundled video engine, saves them to the gallery and
 * checks the files. CI pulls the saved videos (Movies/Free AI Agent) for a human to watch.
 */
@RunWith(AndroidJUnit4::class)
class VideoDeviceTest {
    // The activity gives the engine's WebView a window to render in, as in the real app.
    @get:Rule
    val activity = ActivityScenarioRule(MainActivity::class.java)

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun makesCaptionOnlyMp4() = makeAndCheck(VideoVoice.NONE, timeoutMinutes = 8)

    @Test
    fun makesMp4WithFreeVoice() = makeAndCheck(VideoVoice.UK_MALE, timeoutMinutes = 15)

    /**
     * Stock clips from a fake Pexels API that CI runs on the test machine (tools/fake-pexels.mjs),
     * reached from the emulator at 10.0.2.2. Real Pexels needs the user's own key.
     */
    @Test
    fun makesMp4WithStockClips() {
        val searches = mutableListOf<List<String>>()
        makeAndCheck(
            VideoVoice.NONE,
            timeoutMinutes = 10,
            background = VideoBackground.CLIPS,
            searchesFor = { scenes -> List(scenes.size) { i -> if (i % 2 == 0) "radiator" else "living room" }.also { searches += scenes } },
        )
        assertEquals("The script should be split into one scene per sentence", 3, searches.single().size)
    }

    private fun makeAndCheck(
        voice: VideoVoice,
        timeoutMinutes: Long,
        background: VideoBackground = VideoBackground.ANIMATED,
        searchesFor: suspend (List<String>) -> List<String>? = { null },
    ) = runBlocking {
        val request = VideoRequest(
            script = SCRIPT,
            voice = voice,
            mood = VideoMood.CALM,
            quality = "720",
            background = background,
            pexelsKey = if (background == VideoBackground.ANIMATED) "" else "test-key",
            stockBase = if (background == VideoBackground.ANIMATED) null else "http://10.0.2.2:8787",
        )
        val label = "${voice.id}/${background.id}"
        val outcome = withTimeout(timeoutMinutes * 60_000) {
            PhoneVideoService(context).makeAndSave(request, searchesFor) { Log.i(TAG, "$label: ${it.stage} ${it.fraction}") }
        }

        val extractor = MediaExtractor()
        extractor.setDataSource(context, outcome.uri.toUri(), null)
        val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
        extractor.release()
        val mimes = formats.map { it.getString(MediaFormat.KEY_MIME).orEmpty() }
        val videoSeconds = formats.first { it.getString(MediaFormat.KEY_MIME) == "video/avc" }
            .getLong(MediaFormat.KEY_DURATION) / 1_000_000.0

        Log.i(
            TAG,
            "RESULT video=$label path=${outcome.displayPath} tracks=$mimes " +
                "duration=${"%.1f".format(videoSeconds)}s made_in=${outcome.makeSeconds}s warnings=${outcome.warnings}",
        )
        assertTrue("No H.264 video track: $mimes", "video/avc" in mimes)
        assertTrue("No AAC audio track: $mimes", mimes.any { it == "audio/mp4a-latm" })
        assertTrue("Video is only $videoSeconds s long", videoSeconds in 6.0..30.0)
        if (background != VideoBackground.ANIMATED) {
            assertTrue("Stock footage fell back: ${outcome.warnings}", outcome.warnings.none { "animated" in it })
        }
    }

    private companion object {
        const val TAG = "VideoTest"
        const val SCRIPT =
            "Radiators cold at the top but warm at the bottom? That usually means trapped air. " +
                "Bleeding them takes a few minutes and can make your whole home feel warmer."
    }
}
