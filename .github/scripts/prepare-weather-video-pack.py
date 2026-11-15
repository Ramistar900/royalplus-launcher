#!/usr/bin/env python3
"""Build a validated optional weather-video pack and keep it out of the base APK.

The repository originals are never modified: this runs only in the CI checkout.
"""
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import zipfile
from pathlib import Path

EXPECTED = {
    "weather_clear_night.mp4",
    "weather_clear_sunny.mp4",
    "weather_cloudy.mp4",
    "weather_fog.mp4",
    "weather_light_rain.mp4",
    "weather_light_snow.mp4",
    "weather_partly_cloudy_day.mp4",
    "weather_partly_cloudy_night.mp4",
    "weather_rain.mp4",
    "weather_snow_day.mp4",
    "weather_snow_night.mp4",
    "weather_thunderstorm.mp4",
    "weather_wind.mp4",
}


def run(command: list[str], *, capture: bool = False) -> str:
    result = subprocess.run(
        command,
        check=True,
        text=True,
        stdout=subprocess.PIPE if capture else subprocess.DEVNULL,
        stderr=subprocess.PIPE if capture else None,
    )
    return result.stdout if capture else ""


def probe(path: Path) -> dict:
    raw = run(
        [
            "ffprobe", "-v", "error", "-count_frames", "-select_streams", "v:0",
            "-show_entries",
            "stream=codec_name,width,height,avg_frame_rate,r_frame_rate,duration,nb_read_frames:format=duration",
            "-of", "json", str(path),
        ],
        capture=True,
    )
    payload = json.loads(raw)
    streams = payload.get("streams") or []
    if not streams:
        raise RuntimeError(f"No video stream found: {path.name}")
    stream = streams[0]
    duration = stream.get("duration") or (payload.get("format") or {}).get("duration") or 0
    frames = stream.get("nb_read_frames") or stream.get("nb_frames") or 0
    info = {
        "codec": stream.get("codec_name"),
        "width": int(stream.get("width") or 0),
        "height": int(stream.get("height") or 0),
        "duration_seconds": float(duration),
        "frames": int(frames) if str(frames).isdigit() else 0,
        "frame_rate": stream.get("avg_frame_rate") or stream.get("r_frame_rate"),
    }
    if info["width"] <= 0 or info["height"] <= 0 or info["duration_seconds"] <= 0 or info["frames"] <= 1:
        raise RuntimeError(f"Invalid or non-moving video: {path.name}: {info}")
    return info


def transcode(source: Path, target: Path) -> bool:
    temporary = target.with_suffix(".tmp.mp4")
    command = [
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
        "-i", str(source),
        "-map", "0:v:0", "-map", "0:a?",
        "-vf",
        "scale=w='min(1280,iw)':h='min(720,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2,fps=24",
        "-c:v", "libx264", "-preset", "slow", "-crf", "26",
        "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "96k",
        "-movflags", "+faststart", str(temporary),
    ]
    try:
        run(command)
        if not temporary.is_file() or temporary.stat().st_size == 0:
            return False
        source_info = probe(source)
        output_info = probe(temporary)
        # Verify the full output decodes and retains the moving video stream.
        run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-i", str(temporary), "-f", "null", "-"])
        tolerance = max(0.5, source_info["duration_seconds"] * 0.02)
        if abs(output_info["duration_seconds"] - source_info["duration_seconds"]) > tolerance:
            return False
        if output_info["frames"] <= 1:
            return False
        if temporary.stat().st_size >= source.stat().st_size:
            return False
        temporary.replace(target)
        return True
    except (subprocess.CalledProcessError, RuntimeError, ValueError, json.JSONDecodeError):
        return False
    finally:
        temporary.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--exclude-from-apk", action="store_true")
    args = parser.parse_args()

    asset_dir = Path("app/src/main/assets/weather_videos")
    sources = sorted(asset_dir.glob("*.mp4"))
    found = {path.name for path in sources}
    missing = EXPECTED - found
    if missing:
        raise SystemExit(f"Weather-video pack is incomplete; missing: {sorted(missing)}")
    if not sources:
        raise SystemExit("No weather MP4 files found.")

    stage = args.output.parent / "royalplus-weather-video-pack-stage"
    if stage.exists():
        shutil.rmtree(stage)
    stage.mkdir(parents=True)
    manifest = {
        "pack": "ROYAL+ Weather Motion Videos",
        "format_version": 1,
        "source_path": "app/src/main/assets/weather_videos",
        "notes": "Optional media pack. Original videos remain in the Git repository.",
        "videos": [],
    }
    original_total = 0
    packed_total = 0

    for source in sources:
        original_size = source.stat().st_size
        original_total += original_size
        source_info = probe(source)
        target = stage / source.name
        optimized = transcode(source, target)
        if not optimized:
            shutil.copy2(source, target)
        output_info = probe(target)
        packed_size = target.stat().st_size
        packed_total += packed_size
        manifest["videos"].append({
            "file": source.name,
            "source_bytes": original_size,
            "pack_bytes": packed_size,
            "optimized": optimized,
            "source_video": source_info,
            "pack_video": output_info,
        })

    manifest["totals"] = {
        "source_bytes": original_total,
        "pack_bytes": packed_total,
        "saved_bytes": original_total - packed_total,
    }
    (stage / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for item in sorted(stage.iterdir()):
            archive.write(item, arcname=item.name)
    with zipfile.ZipFile(args.output, "r") as archive:
        bad_entry = archive.testzip()
        if bad_entry:
            raise SystemExit(f"Weather-video ZIP verification failed at {bad_entry}")

    if args.output.stat().st_size <= 0:
        raise SystemExit("Weather-video ZIP is empty.")

    # This removal affects only the temporary CI checkout, not the Git repository.
    # The validated videos and manifest are already preserved in the optional ZIP.
    if args.exclude_from_apk:
        shutil.rmtree(asset_dir)

    summary = (
        f"Weather video pack: {len(sources)} clips; "
        f"source {original_total / 1_000_000:.2f} MB; "
        f"optimized media {packed_total / 1_000_000:.2f} MB; "
        f"ZIP {args.output.stat().st_size / 1_000_000:.2f} MB; "
        f"media saving {(original_total-packed_total) / 1_000_000:.2f} MB. "
        "Every clip decoded successfully and retained multiple frames."
    )
    print(summary)
    summary_path = Path(__import__("os").environ.get("GITHUB_STEP_SUMMARY", ""))
    if str(summary_path) not in ("", "."):
        with summary_path.open("a", encoding="utf-8") as handle:
            handle.write(f"\n### Optional weather-video pack\n\n{summary}\n")
            handle.write("\n| Video | Source MB | Pack MB | Compressed |\n|---|---:|---:|---|\n")
            for item in manifest["videos"]:
                handle.write(
                    f"| {item['file']} | {item['source_bytes']/1_000_000:.2f} | "
                    f"{item['pack_bytes']/1_000_000:.2f} | "
                    f"{'yes' if item['optimized'] else 'original retained'} |\n"
                )
            handle.write("\nOriginal MP4 files remain unchanged in the Git repository.\n")


if __name__ == "__main__":
    main()
