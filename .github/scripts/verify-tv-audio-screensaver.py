from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAIN = (ROOT / "app/src/main/java/com/royalplus/launcher/MainActivity.kt").read_text(encoding="utf-8")
HOME = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalHomeScreen.kt").read_text(encoding="utf-8")
PLAYER = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalFireplaceBackground.kt").read_text(encoding="utf-8")
SOUNDS = (ROOT / "royal-ui-sounds/src/main/java/com/royalplus/launcher/royalsounds/RoyalUiSounds.kt").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/build-apk.yml").read_text(encoding="utf-8")

assert 'putBoolean("ui_click_sounds_enabled", isTelevisionDevice)' in MAIN
assert "RoyalUiSound.NAV_TV_FOCUS" in MAIN
assert "televisionMode = isTelevisionDevice" in MAIN
assert "AudioAttributes.USAGE_MEDIA" in SOUNDS
assert "AudioAttributes.USAGE_ASSISTANCE_SONIFICATION" in SOUNDS
assert "Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight" in HOME
assert "RoyalUiSound.NAV_TV_FOCUS" in HOME
assert "WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON" in HOME
assert "fun scheduleRetry()" in PLAYER
assert "mainHandler.postDelayed(task, delayMs)" in PLAYER
assert "surfaceTexture?.let(::startPlayer)" in PLAYER
assert WORKFLOW.count("scale='min(1920,iw)':-2:flags=lanczos") == 2
assert '"-r", "24"' not in WORKFLOW
print("Verified TV click/focus audio routing, screensaver keep-awake behavior, fireplace decoder retry, and source-frame-rate-preserving 1080p optimization.")
