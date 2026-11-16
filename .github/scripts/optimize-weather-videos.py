#!/usr/bin/env python3
"""Compress bundled weather MP4s for Android while preserving every clip and its duration."""
from pathlib import Path
import json
import subprocess
import tempfile

ROOT = Path("app/src/main/assets/weather_videos")
if not ROOT.is_dir():
    print("No weather_videos directory; nothing to optimize.")
    raise SystemExit(0)

def probe(path: Path):
    raw = subprocess.check_output([
        "ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(path)
    ], text=True)
    data = json.loads(raw)
    videos = [s for s in data.get("streams", []) if s.get("codec_type") == "video"]
    if not videos:
        raise RuntimeError(f"No video stream: {path}")
    stream = videos[0]
    return {
        "codec": stream.get("codec_name"),
        "width": int(stream.get("width", 0)),
        "height": int(stream.get("height", 0)),
        "duration": float(data.get("format", {}).get("duration", 0) or 0),
        "audio": any(s.get("codec_type") == "audio" for s in data.get("streams", [])),
    }

files = sorted(ROOT.glob("*.mp4"))
if len(files) != 13:
    raise SystemExit(f"Expected 13 weather videos, found {len(files)}")

before_total = sum(p.stat().st_size for p in files)
saved_total = 0
for source in files:
    original = probe(source)
    with tempfile.NamedTemporaryFile(prefix="royal-weather-", suffix=".mp4", dir="/tmp", delete=False) as tmp:
        target = Path(tmp.name)
    try:
        subprocess.run([
            "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
            "-i", str(source), "-map", "0:v:0", "-an",
            "-vf", "scale=w='min(1280,iw)':h='min(720,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2:flags=lanczos,fps=20",
            "-c:v", "libx264", "-preset", "slow", "-crf", "27",
            "-profile:v", "main", "-level", "3.1", "-pix_fmt", "yuv420p",
            "-movflags", "+faststart", str(target)
        ], check=True)
        optimized = probe(target)
        if abs(optimized["duration"] - original["duration"]) > max(0.15, original["duration"] * 0.01):
            raise RuntimeError(f"Duration changed unexpectedly for {source.name}")
        if optimized["audio"]:
            raise RuntimeError(f"Audio stream remains in optimized file: {source.name}")
        if optimized["codec"] != "h264" or optimized["width"] > 1280 or optimized["height"] > 720:
            raise RuntimeError(f"Unexpected output format for {source.name}: {optimized}")
        if target.stat().st_size < source.stat().st_size or original["audio"]:
            old_size = source.stat().st_size
            target.replace(source)
            saved_total += old_size - source.stat().st_size
            action = "optimized"
        else:
            action = "original kept (already smaller)"
        final = probe(source)
        if final["audio"]:
            raise RuntimeError(f"Audio stream not removed from {source.name}")
        print(f"{source.name}: {action}; {old_size if action == 'optimized' else source.stat().st_size} -> {source.stat().st_size} bytes; {final['width']}x{final['height']}; {final['duration']:.2f}s")
    finally:
        target.unlink(missing_ok=True)

after_total = sum(p.stat().st_size for p in files)
print(f"Weather video bundle: {before_total / 1048576:.2f} MiB -> {after_total / 1048576:.2f} MiB; saved {(before_total-after_total) / 1048576:.2f} MiB.")
