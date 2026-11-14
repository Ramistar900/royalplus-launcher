#!/usr/bin/env python3
"""Fetch only the pinned original static ROYAL Design V20 sports SVGs."""
from pathlib import Path
from urllib.request import Request, urlopen
import re
import shutil
import time

ROOT = Path(__file__).resolve().parents[2]
ASSET_ROOT = ROOT / "app/src/main/assets/sports_v20"
SOURCE_COMMIT = "f784e3a1b9bdf350cd30b37f18a86efba023cc7a"
SOURCE_ROOT = (
    "https://raw.githubusercontent.com/Ramistar900/royal-design/"
    + SOURCE_COMMIT + "/preview/royal-design/v20"
)
ICONS = ["today_matches","live_scores","match_results","fixtures","standings","football","basketball","tennis","volleyball","cricket","trophy","teams","players","stadium","goal","whistle","yellow_card","red_card","medal","statistics","bracket","transfer","sports_news","replay","stopwatch"]

def fetch(url: str) -> bytes:
    last_error = None
    for attempt in range(3):
        try:
            request = Request(url, headers={"User-Agent": "ROYAL-Launcher-V20-Static-Assets"})
            with urlopen(request, timeout=45) as response:
                payload = response.read()
            if not payload:
                raise RuntimeError(f"Empty response from {url}")
            return payload
        except Exception as error:
            last_error = error
            if attempt < 2:
                time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"Could not fetch pinned V20 artwork: {url}") from last_error

def main() -> None:
    animated_root = ASSET_ROOT / "animated"
    if animated_root.exists():
        shutil.rmtree(animated_root)
    target = ASSET_ROOT / "static"
    target.mkdir(parents=True, exist_ok=True)
    for icon in ICONS:
        url = f"{SOURCE_ROOT}/sports/static/{icon}.svg"
        raw = fetch(url)
        text = raw.decode("utf-8")
        if "<svg" not in text or "</svg>" not in text:
            raise RuntimeError(f"Invalid SVG source: {url}")
        has_external_reference = re.search(r"""(?:xlink:)?href\s*=\s*['"]https?://""", text, re.IGNORECASE)
        if "<script" in text.lower() or has_external_reference:
            raise RuntimeError(f"External or executable SVG content is forbidden: {url}")
        if "<animate" in text:
            raise RuntimeError(f"Static V20 source unexpectedly contains animation: {icon}")
        (target / f"{icon}.svg").write_text(text, encoding="utf-8")
    manifest_url = f"{SOURCE_ROOT}/manifest.json"
    (ASSET_ROOT / "manifest.json").write_bytes(fetch(manifest_url))
    actual = sum(1 for _ in target.glob("*.svg"))
    if actual != 25 or animated_root.exists():
        raise RuntimeError(f"Expected only 25 static V20 icons, got {actual}; animated folder present={animated_root.exists()}")
    print(f"Prepared {actual} static V20 sports icons from {SOURCE_COMMIT}; animated icons are intentionally excluded.")

if __name__ == "__main__":
    main()
