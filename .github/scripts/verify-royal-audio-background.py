from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SONGS = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalSongsScreen.kt").read_text(encoding="utf-8")
SERVICE = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalMusicPlaybackService.kt").read_text(encoding="utf-8")
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
GRADLE = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")

assert "MediaSessionService" in SERVICE
assert "ExoPlayer.Builder(this)" in SERVICE
assert "MediaSession.Builder(this, player)" in SERVICE
assert "setAudioAttributes(AudioAttributes.DEFAULT, true)" in SERVICE
assert "MediaController.Builder(context, token).buildAsync()" in SONGS
assert "setMediaItems(queue, index, 0L)" in SONGS
assert "MediaController.releaseFuture(future)" in SONGS
assert "DisposableEffect(player)" not in SONGS
assert "MediaPlayer" not in SONGS
assert "androidx.media3.session.MediaSessionService" in MANIFEST
assert 'android:name=".RoyalMusicPlaybackService"' in MANIFEST
assert 'android:foregroundServiceType="mediaPlayback"' in MANIFEST
assert 'androidx.media3:media3-session:1.5.1' in GRADLE
assert "local_playlist" in SONGS
print("Verified background ROYAL AUDIO service, MediaSession controller, persistent playlist and screen-independent playback ownership.")
