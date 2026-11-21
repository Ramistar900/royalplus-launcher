from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/royalplus/launcher"
HOME = (JAVA / "RoyalHomeScreen.kt").read_text(encoding="utf-8")
SPORTS = (JAVA / "RoyalSportsScreen.kt").read_text(encoding="utf-8")
SINJAR = (JAVA / "SinjarTvScreen.kt").read_text(encoding="utf-8")
ACTIVITY = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
CAPTION_NSD = (JAVA / "RoyalCaptionNsd.kt").read_text(encoding="utf-8")
RECEIVER = (JAVA / "RoyalCaptionReceiverService.kt").read_text(encoding="utf-8")

# Keep settings accessible from the top bar, not as a destination in the side navigation.
assert "StatusIcon(Icons.Rounded.Settings" in HOME
assert "labels[9], Icons.Rounded.Settings" not in HOME
assert "manageAppsGear = true" in HOME
assert "إدارة التطبيقات" in HOME and "التطبيقات" in HOME

# The sports navigation icon is a television branded ROYAL SPORTS.
assert "sportsTv = true" in HOME
assert '"ROYAL SPORTS"' in HOME
assert "Icons.Rounded.Tv" in HOME
# Open the upcoming fixtures tab first; live matches are often empty outside match times.
assert "mutableStateOf(RoyalSportsTab.UPCOMING)" in SPORTS

# Sinjar TV supports touch swipes between channels and keeps the remote key path.
assert "awaitEachGesture" in SINJAR
assert "pointerInput(selectedChannel?.streamUrl, visibleChannels, channelMenuVisible)" in SINJAR
assert "horizontalSwipe" in SINJAR and "verticalSwipe" in SINJAR
assert "onPreviewKeyEvent" in SINJAR
assert "DirectionUp" in SINJAR and "DirectionDown" in SINJAR

# Restore selectable SD-to-4K stream quality and default adaptive Auto mode.
assert 'AUTO("تلقائي", Int.MAX_VALUE, Int.MAX_VALUE)' in SINJAR
assert 'SD("SD", 854, 480)' in SINJAR
assert 'HD("HD", 1280, 720)' in SINJAR
assert 'FULL_HD("Full HD", 1920, 1080)' in SINJAR
assert 'UHD_4K("4K", 3840, 2160)' in SINJAR
assert ".setMaxVideoSize(quality.maxWidth, quality.maxHeight)" in SINJAR
assert "mutableStateOf(SinjarVideoQuality.AUTO)" in SINJAR

# The TV advertises its receiver; the phone discovers it and enters only the TV PIN.
assert "RoyalCaptionNsdDiscovery" in ACTIVITY
assert "manager.discoverServices" in CAPTION_NSD
assert "manager.resolveService" in CAPTION_NSD
assert "RoyalCaptionNsdRegistration.register" in RECEIVER
assert "أدخل عنوان IP" not in ACTIVITY
assert "اكتب عنوان IP" not in ACTIVITY

print("Verified ROYAL+ 1116 repair guards: navigation, branded icons, upcoming sports, Sinjar touch zapping, SD-to-4K Auto quality, and automatic translation pairing.")
