"""HeyGen: create avatar video, poll to completion, return public video_url.
Uses the v3 API (/v3/videos) -- v1/v2 passed their Oct 31 2026 sunset and now
fail intermittently per HeyGen's own docs."""
import os, json, time, urllib.request

API = "https://api.heygen.com"

def _req(method, path, body=None):
    req = urllib.request.Request(
        API + path,
        data=json.dumps(body).encode() if body else None,
        headers={"x-api-key": os.environ["HEYGEN_API_KEY"], "Content-Type": "application/json"},
        method=method,
    )
    return json.loads(urllib.request.urlopen(req, timeout=60).read())

def upload_audio_asset(path: str) -> str:
    """Upload a local audio file (e.g. Piper narration) to HeyGen's asset store.
    Returns an asset_id usable as create_video(audio_asset_id=...)."""
    import requests
    with open(path, "rb") as f:
        resp = requests.post(
            f"{API}/v3/assets",
            headers={"x-api-key": os.environ["HEYGEN_API_KEY"]},
            files={"file": f},
            timeout=60,
        )
    resp.raise_for_status()
    return resp.json()["data"]["asset_id"]

def create_video(script: str, title: str, audio_asset_id: str | None = None) -> str:
    body = {
        "type": "avatar",
        "avatar_id": os.environ["HEYGEN_AVATAR_ID"],
        "title": title,
        "aspect_ratio": "9:16",
        "resolution": "720p",
    }
    if audio_asset_id:
        body["audio_asset_id"] = audio_asset_id  # bypasses HeyGen's own TTS; mutually exclusive with script
    else:
        body["voice_id"] = os.environ["HEYGEN_VOICE_ID"]
        body["script"] = script
    return _req("POST", "/v3/videos", body)["data"]["video_id"]

def wait_for_video(video_id: str, timeout_s: int = 1200, interval_s: int = 20) -> str:
    """Poll until completed. Returns video_url. Raises on failure/timeout."""
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        d = _req("GET", f"/v3/videos/{video_id}")["data"]
        status = d["status"]
        if status == "completed":
            return d["video_url"]
        if status in ("failed", "cancelled"):  # "cancelled" is undocumented but HeyGen's own quick-start checks for it
            raise RuntimeError(f"HeyGen {status}: {d.get('failure_message') or d.get('failure_code')}")
        print(f"  heygen: {status}, retry in {interval_s}s")
        time.sleep(interval_s)
    raise TimeoutError(f"HeyGen video {video_id} not done in {timeout_s}s")

def download(url: str, path: str) -> str:
    urllib.request.urlretrieve(url, path)
    return path
