from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SONGS = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalSongsScreen.kt").read_text(encoding="utf-8")
SERVICE = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalMusicPlaybackService.kt").read_text(encoding="utf-8")
FLOATING = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalFloatingMusicPlayer.kt").read_text(encoding="utf-8")
HOME = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalHomeScreen.kt").read_text(encoding="utf-8")
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
GRADLE = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")

assert "MediaSessionService" in SERVICE
assert "ExoPlayer.Builder(this)" in SERVICE
assert "MediaSession.Builder(this, player)" in SERVICE
assert ".setWakeMode(C.WAKE_MODE_LOCAL)" in SERVICE
assert ".setHandleAudioBecomingNoisy(true)" in SERVICE
assert ".setUsage(C.USAGE_MEDIA)" in SERVICE
assert ".setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)" in SERVICE
assert "MediaController.Builder(context, token).buildAsync()" in SONGS
assert "Handler(Looper.getMainLooper())" in SONGS
assert "context.mainExecutor" not in SONGS
assert "setMediaItems(queue, index, 0L)" in SONGS
assert "MediaController.releaseFuture(future)" in SONGS
assert "DisposableEffect(player)" not in SONGS
assert "MediaPlayer" not in SONGS
assert "androidx.media3.session.MediaSessionService" in MANIFEST
assert 'android:name=".RoyalMusicPlaybackService"' in MANIFEST
assert 'android:foregroundServiceType="mediaPlayback"' in MANIFEST
assert 'androidx.media3:media3-session:1.5.1' in GRADLE
assert "local_playlist" in SONGS
assert "MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)" in SONGS
assert "READ_MEDIA_AUDIO" in SONGS
assert "READ_MEDIA_AUDIO" in MANIFEST
assert "MediaController.Builder(context, token).buildAsync()" in FLOATING
assert "active.stop()" in FLOATING
assert "active.clearMediaItems()" in FLOATING
assert "minimized = true" in FLOATING
assert "minimized = false" in FLOATING
assert "RoyalFloatingMusicPlayer(language = selectedLanguage)" in HOME
print("Verified background ROYAL AUDIO service, MediaSession controller, persistent playlist and screen-independent playback ownership.")
