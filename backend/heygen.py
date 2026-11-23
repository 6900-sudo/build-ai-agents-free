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

def create_video(script: str, title: str) -> str:
    body = {
        "type": "avatar",
        "avatar_id": os.environ["HEYGEN_AVATAR_ID"],
        "voice_id": os.environ["HEYGEN_VOICE_ID"],
        "script": script,
        "title": title,
        "aspect_ratio": "9:16",
        "resolution": "720p",
    }
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
