"""Piper TTS: free, local, MIT-licensed narration. No API key, no credits spent.
Auto-downloads the voice model from Hugging Face on first use and caches it."""
import subprocess
import urllib.request
from pathlib import Path

VOICE = "en_GB-northern_english_male-medium"  # UK male, calm -- matches faceless-studio's bm_george pick
MODELS_DIR = Path(__file__).resolve().parent / "piper_voices"


def _voice_url(voice: str) -> str:
    lang, name, quality = voice.split("-")
    lang_family = lang.split("_")[0]
    return f"https://huggingface.co/rhasspy/piper-voices/resolve/main/{lang_family}/{lang}/{name}/{quality}/{voice}"


def _ensure_voice(voice: str = VOICE) -> tuple[Path, Path]:
    MODELS_DIR.mkdir(exist_ok=True)
    model_path = MODELS_DIR / f"{voice}.onnx"
    config_path = MODELS_DIR / f"{voice}.onnx.json"
    base_url = _voice_url(voice)
    if not model_path.exists():
        urllib.request.urlretrieve(f"{base_url}.onnx", model_path)
    if not config_path.exists():
        urllib.request.urlretrieve(f"{base_url}.onnx.json", config_path)
    return model_path, config_path


def synthesize(text: str, output_path: str, voice: str = VOICE) -> str:
    """Render text to a WAV file with Piper. Returns output_path."""
    model_path, config_path = _ensure_voice(voice)
    subprocess.run(
        ["piper", "-m", str(model_path), "-c", str(config_path), "-f", output_path],
        input=text.encode("utf-8"),
        check=True,
    )
    return output_path


def duration_s(wav_path: str) -> float:
    """Measured duration of a synthesized WAV, in seconds."""
    import wave
    with wave.open(wav_path, "rb") as w:
        return w.getnframes() / w.getframerate()
