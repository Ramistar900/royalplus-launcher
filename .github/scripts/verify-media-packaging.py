#!/usr/bin/env python3
"""Verify APK media boundaries and retained motion assets."""
from __future__ import annotations

import argparse
import json
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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk-dir", required=True, type=Path)
    parser.add_argument("--weather-pack", required=True, type=Path)
    args = parser.parse_args()

    apks = sorted(args.apk_dir.rglob("*.apk"))
    if not apks:
        raise SystemExit(f"No APK files found under {args.apk_dir}")

    for apk in apks:
        with zipfile.ZipFile(apk) as archive:
            names = set(archive.namelist())
            if not any(name.startswith("assets/weather_videos/") for name in names):
                pass
            else:
                raise SystemExit(f"Optional weather videos leaked into base APK: {apk}")
            fireplace = "assets/fireplace_burning_fire_loop.mp4"
            if fireplace not in names:
                raise SystemExit(f"Fireplace loop missing from base APK: {apk}")
            archive.testzip()
        print(f"Verified media in {apk}: fireplace retained; optional weather videos excluded.")

    with zipfile.ZipFile(args.weather_pack) as archive:
        bad_entry = archive.testzip()
        if bad_entry:
            raise SystemExit(f"Weather-video pack ZIP is corrupt at {bad_entry}")
        manifest = json.loads(archive.read("manifest.json").decode("utf-8"))
        videos = manifest.get("videos") or []
        names = {item.get("file") for item in videos}
        if names != EXPECTED:
            raise SystemExit(
                f"Weather-video pack does not contain the expected 13 clips: "
                f"missing={sorted(EXPECTED - names)}, extra={sorted(names - EXPECTED)}"
            )
        archive_names = set(archive.namelist())
        if not EXPECTED.issubset(archive_names):
            raise SystemExit("Weather-video ZIP is missing one or more MP4 payloads.")
        if any(int(item.get("pack_video", {}).get("frames", 0)) <= 1 for item in videos):
            raise SystemExit("A weather clip lost motion frames during optimization.")
        print("Verified optional weather pack: all 13 clips and manifest are present.")

    fireplace_source = Path("app/src/main/java/com/royalplus/launcher/RoyalFireplaceBackground.kt")
    fireplace_code = fireplace_source.read_text(encoding="utf-8")
    if 'assets.openFd("fireplace_burning_fire_loop.mp4")' not in fireplace_code or "player.isLooping = true" not in fireplace_code:
        raise SystemExit("Fireplace looping playback code changed or is missing.")

    motion_source = Path("app/src/main/java/com/royalplus/launcher/RoyalWeatherAtmosphereOverlay.kt")
    motion_code = motion_source.read_text(encoding="utf-8")
    if "rememberInfiniteTransition" not in motion_code or "motionEnabled" not in motion_code:
        raise SystemExit("Existing animated weather atmosphere is missing or disabled.")

    print("Verified fireplace looping playback and existing weather-motion animation code.")


if __name__ == "__main__":
    main()
