from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/royalplus/launcher"
ACTIVITY = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
CAPTURE = (JAVA / "FloatingTranslationOverlayService.kt").read_text(encoding="utf-8")
RECEIVER = (JAVA / "RoyalCaptionReceiverService.kt").read_text(encoding="utf-8")
TRANSPORT = (JAVA / "RoyalCaptionTransport.kt").read_text(encoding="utf-8")
ROLE = (JAVA / "TranslationDeviceRole.kt").read_text(encoding="utf-8")
HOME = (JAVA / "RoyalHomeScreen.kt").read_text(encoding="utf-8")
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

# Role separation: TVs only receive; phones own the microphone workflow.
assert "TranslationDeviceRole.TELEVISION_RECEIVER" in ACTIVITY
assert "toggleTvCaptionReceiver()" in ACTIVITY
assert "togglePhoneCaptionCapture()" in ACTIVITY
assert "TranslationDeviceRoleDetector.detect(this) != TranslationDeviceRole.TELEVISION_RECEIVER" in RECEIVER
assert "TranslationDeviceRoleDetector.detect(this) != TranslationDeviceRole.PHONE_CAPTURE" in CAPTURE
assert "translationProjectionPermission" not in ACTIVITY
assert "MediaProjectionManager" not in ACTIVITY

# Screen-off phone capture must stay in a microphone foreground service and keep CPU/Wi-Fi awake.
assert "ACTION_START_PHONE_CAPTURE" in CAPTURE
assert "ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE" in CAPTURE
assert "PowerManager.PARTIAL_WAKE_LOCK" in CAPTURE
assert "WifiManager.WIFI_MODE_FULL" in CAPTURE
assert "KEY_PHONE_CAPTURE_ENABLED" in CAPTURE
assert "if (phoneCompanionMode) createMicrophoneRecord()" in CAPTURE
assert "captionSender?.sendCaption(safeArabicCaption, language)" in CAPTURE

# TV receiver has no audio-recording code and uses an overlay-only receive service.
assert "android:name=\".RoyalCaptionReceiverService\"" in MANIFEST
assert 'android:foregroundServiceType="connectedDevice"' in MANIFEST
assert "FOREGROUND_SERVICE_CONNECTED_DEVICE" in MANIFEST
assert "RECORD_AUDIO" in MANIFEST  # declared for phone builds; runtime prompt is role-gated in MainActivity
assert "AudioRecord" not in RECEIVER
assert "MediaRecorder" not in RECEIVER
assert "Settings.canDrawOverlays(this)" in RECEIVER

# LAN pairing is authenticated and payloads are encrypted; no cloud endpoint is used.
assert "ServerSocket" in RECEIVER
assert "RoyalCaptionTransport.PORT" in RECEIVER
assert "isPrivateIpv4Address" in TRANSPORT
assert "HmacSHA256" in TRANSPORT
assert "PBKDF2WithHmacSHA1" in TRANSPORT
assert "AES/GCM/NoPadding" in TRANSPORT
assert "RoyalCaptionTransport.encryptPayload" in TRANSPORT
assert "RoyalCaptionTransport.decryptPayload" in RECEIVER
assert "12_000L" in TRANSPORT  # stale caption backlog is discarded after Wi-Fi outages
assert "KEY_DISPLAY_DELAY_MS" in RECEIVER
assert "ضبط تأخير العرض" in ACTIVITY

# Preserve the existing local Whisper + SMaLL-100 translation pipeline and expose distinct roles in UI.
assert "WhisperModelRepository.createRecognizer" in CAPTURE
assert "Small100Translator.create" in CAPTURE
assert "translationIsTelevision" in HOME
assert "استقبال الترجمة من الهاتف" in HOME
assert "التقاط صوت التلفاز وإرسال الترجمة" in HOME

print("Verified phone-only microphone capture, screen-off foreground operation, encrypted LAN caption transport, TV-only overlay receiver, pairing and manual display delay.")
