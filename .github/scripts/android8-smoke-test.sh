#!/usr/bin/env bash
set -euo pipefail

APK="$(find "$GITHUB_WORKSPACE/app/build/outputs/apk" -type f -path "*/debug/*.apk" -print -quit)"
if [[ -z "$APK" || ! -s "$APK" ]]; then
  echo "::error::Debug APK not found under $GITHUB_WORKSPACE/app/build/outputs/apk"
  find "$GITHUB_WORKSPACE/app/build/outputs" -maxdepth 5 -type f -name "*.apk" -print || true
  exit 1
fi

adb install -r "$APK"
adb logcat -c
adb shell am force-stop com.royalplus.launcher
adb shell am start -W -n com.royalplus.launcher/.MainActivity
sleep 5
PID="$(adb shell pidof com.royalplus.launcher | tr -d '\r' || true)"
if [[ -z "$PID" ]]; then
  echo "::error::ROYAL+ Launcher process did not remain alive after startup."
  adb logcat -d -v brief -s AndroidRuntime:E
  exit 1
fi

APP_CRASH_LOG="$(adb logcat -d -v brief -s AndroidRuntime:E | grep -B3 -A20 'Process: com.royalplus.launcher' || true)"
if [[ -n "$APP_CRASH_LOG" ]]; then
  printf '%s\n' "$APP_CRASH_LOG"
  exit 1
fi

echo "Android 8 API 26 install and launcher startup passed."