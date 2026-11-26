"""Unit tests for PR #15: Piper TTS, HeyGen asset upload, Groq availability caching.

All external calls (HTTP, subprocess, LLM clients) are mocked -- nothing here
makes a real request. Run from the repo root:

    python -m unittest backend.tests.test_pr15 -v
"""
import os
import tempfile
import unittest
import wave
from pathlib import Path
from unittest.mock import MagicMock, patch

from backend import heygen, piper_tts
from backend.agents import llm_registry


class HeygenCreateVideoTest(unittest.TestCase):
    """create_video() must stay backward compatible: script+voice_id by default,
    audio_asset_id when given (and never both -- HeyGen rejects that combination)."""

    def setUp(self):
        patcher = patch.dict(
            os.environ,
            {"HEYGEN_API_KEY": "key", "HEYGEN_AVATAR_ID": "avatar", "HEYGEN_VOICE_ID": "voice"},
        )
        patcher.start()
        self.addCleanup(patcher.stop)

    @patch("backend.heygen._req")
    def test_default_path_uses_script_and_voice(self, mock_req):
        mock_req.return_value = {"data": {"video_id": "vid1"}}

        video_id = heygen.create_video("hello world", "My Title")

        self.assertEqual(video_id, "vid1")
        method, path, body = mock_req.call_args[0]
        self.assertEqual(method, "POST")
        self.assertEqual(path, "/v3/videos")
        self.assertEqual(body["script"], "hello world")
        self.assertEqual(body["voice_id"], "voice")
        self.assertNotIn("audio_asset_id", body)

    @patch("backend.heygen._req")
    def test_audio_asset_id_omits_script_and_voice(self, mock_req):
        mock_req.return_value = {"data": {"video_id": "vid2"}}

        video_id = heygen.create_video("hello world", "My Title", audio_asset_id="asset-123")

        self.assertEqual(video_id, "vid2")
        _, _, body = mock_req.call_args[0]
        self.assertEqual(body["audio_asset_id"], "asset-123")
        self.assertNotIn("script", body)
        self.assertNotIn("voice_id", body)


class HeygenUploadAudioAssetTest(unittest.TestCase):
    def setUp(self):
        patcher = patch.dict(os.environ, {"HEYGEN_API_KEY": "key"})
        patcher.start()
        self.addCleanup(patcher.stop)

        fd, self.audio_path = tempfile.mkstemp(suffix=".wav")
        os.close(fd)
        self.addCleanup(os.remove, self.audio_path)

    @patch("requests.post")
    def test_upload_posts_multipart_and_returns_asset_id(self, mock_post):
        mock_resp = MagicMock()
        mock_resp.json.return_value = {"data": {"asset_id": "asset-xyz"}}
        mock_post.return_value = mock_resp

        asset_id = heygen.upload_audio_asset(self.audio_path)

        self.assertEqual(asset_id, "asset-xyz")
        mock_post.assert_called_once()
        args, kwargs = mock_post.call_args
        self.assertEqual(args[0], "https://api.heygen.com/v3/assets")
        self.assertEqual(kwargs["headers"], {"x-api-key": "key"})
        self.assertIn("file", kwargs["files"])
        mock_resp.raise_for_status.assert_called_once()

    @patch("requests.post")
    def test_http_error_propagates(self, mock_post):
        import requests

        mock_resp = MagicMock()
        mock_resp.raise_for_status.side_effect = requests.HTTPError("boom")
        mock_post.return_value = mock_resp

        with self.assertRaises(requests.HTTPError):
            heygen.upload_audio_asset(self.audio_path)


class PiperSynthesizeTest(unittest.TestCase):
    @patch("backend.piper_tts.subprocess.run")
    @patch("backend.piper_tts._ensure_voice")
    def test_synthesize_calls_piper_with_utf8_stdin(self, mock_ensure_voice, mock_run):
        mock_ensure_voice.return_value = (Path("/voices/v.onnx"), Path("/voices/v.onnx.json"))

        out = piper_tts.synthesize("Hello, Colin.", "/tmp/out.wav")

        self.assertEqual(out, "/tmp/out.wav")
        mock_run.assert_called_once()
        args, kwargs = mock_run.call_args
        self.assertEqual(
            args[0],
            ["piper", "-m", "/voices/v.onnx", "-c", "/voices/v.onnx.json", "-f", "/tmp/out.wav"],
        )
        self.assertEqual(kwargs["input"], "Hello, Colin.".encode("utf-8"))
        self.assertTrue(kwargs["check"])

    def test_duration_s_reads_real_wav(self):
        fd, path = tempfile.mkstemp(suffix=".wav")
        os.close(fd)
        self.addCleanup(os.remove, path)

        framerate = 22050
        nframes = framerate * 2  # exactly 2 seconds of silence
        with wave.open(path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(framerate)
            w.writeframes(b"\x00\x00" * nframes)

        self.assertAlmostEqual(piper_tts.duration_s(path), 2.0)


class GroqFallbackCachingTest(unittest.TestCase):
    """get_groq_with_fallback() must probe Groq availability once per process,
    not once per call -- that was PR #15's fix for the doubled Groq usage."""

    def setUp(self):
        self._original = llm_registry._groq_available
        llm_registry._groq_available = None
        self.addCleanup(self._restore)

    def _restore(self):
        llm_registry._groq_available = self._original

    @patch("langchain_groq.ChatGroq")
    def test_groq_success_probes_once_across_calls(self, mock_chat_groq):
        mock_instance = MagicMock()
        mock_chat_groq.return_value = mock_instance

        llm1 = llm_registry.get_groq_with_fallback()
        llm2 = llm_registry.get_groq_with_fallback()
        llm3 = llm_registry.get_groq_with_fallback()

        self.assertIs(llm1, mock_instance)
        self.assertIs(llm2, mock_instance)
        self.assertIs(llm3, mock_instance)
        mock_instance.invoke.assert_called_once_with("ping")
        # 1 probe construction + 1 per successful call = 4 total
        self.assertEqual(mock_chat_groq.call_count, 4)

    @patch("langchain_google_genai.ChatGoogleGenerativeAI")
    @patch("langchain_groq.ChatGroq")
    def test_groq_failure_falls_back_to_gemini_without_reprobing(self, mock_chat_groq, mock_gemini):
        probe_instance = MagicMock()
        probe_instance.invoke.side_effect = RuntimeError("groq down")
        mock_chat_groq.return_value = probe_instance
        gemini_instance = MagicMock()
        mock_gemini.return_value = gemini_instance

        llm1 = llm_registry.get_groq_with_fallback()
        llm2 = llm_registry.get_groq_with_fallback()

        self.assertIs(llm1, gemini_instance)
        self.assertIs(llm2, gemini_instance)
        probe_instance.invoke.assert_called_once_with("ping")
        self.assertEqual(mock_chat_groq.call_count, 1)  # only the failed probe, never retried
        self.assertEqual(mock_gemini.call_count, 2)


if __name__ == "__main__":
    unittest.main()
