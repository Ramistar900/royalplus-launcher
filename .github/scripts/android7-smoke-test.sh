#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.royalplus.launcher"
APK_ROOT="$GITHUB_WORKSPACE/app/build/outputs/apk"
APK="$(find "$APK_ROOT" -type f -path "*/release/*x86_64*.apk" -print -quit)"
BUILD_TYPE="minified x86_64 Release"
if [[ -z "$APK" ]]; then
  APK="$(find "$APK_ROOT" -type f -path "*/debug/*.apk" -print -quit)"
  BUILD_TYPE="Debug fallback"
fi

if [[ -z "$APK" || ! -s "$APK" ]]; then
  echo "::error::No APK found under $APK_ROOT"
  find "$GITHUB_WORKSPACE/app/build/outputs" -maxdepth 5 -type f -name "*.apk" -print || true
  exit 1
fi

AAPT="$(find "$ANDROID_HOME/build-tools" -name aapt -type f | sort -V | tail -n 1)"
test -n "$AAPT"
BADGING="$("$AAPT" dump badging "$APK")"
PACKAGE="$(printf '%s\n' "$BADGING" | sed -n "s/^package: name='\\([^']*\\)'.*/\\1/p")"
ACTIVITY="$(printf '%s\n' "$BADGING" | sed -n "s/^launchable-activity: name='\\([^']*\\)'.*/\\1/p")"
test -n "$PACKAGE"
test -n "$ACTIVITY"

echo "Installing Android 7 $BUILD_TYPE APK: $APK ($PACKAGE)"
adb install -r "$APK"

# Stop any previous instance before starting a fresh, live log capture.
adb shell am force-stop "$PACKAGE"

# Do not run 'adb logcat -c': Android 7 emulator images may deny clearing
# log buffers. Stream only new AndroidRuntime errors from this point onward.
LOG_DIR="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
LOG_FILE="$LOG_DIR/android7-runtime-errors.log"
: > "$LOG_FILE"
adb logcat -v brief -s AndroidRuntime:E > "$LOG_FILE" 2>&1 &
LOGCAT_PID=$!

cleanup_logcat() {
  kill "$LOGCAT_PID" >/dev/null 2>&1 || true
  wait "$LOGCAT_PID" >/dev/null 2>&1 || true
}
trap cleanup_logcat EXIT

sleep 1
if ! kill -0 "$LOGCAT_PID" >/dev/null 2>&1; then
  echo "::error::Could not start AndroidRuntime log capture."
  cat "$LOG_FILE" || true
  exit 1
fi

echo "Launching $ACTIVITY"
START_OUTPUT="$(adb shell am start -W -n "$PACKAGE/$ACTIVITY" 2>&1)" || {
  printf '%s\n' "$START_OUTPUT"
  echo "::error::Activity launch command failed."
  cat "$LOG_FILE" || true
  exit 1
}
printf '%s\n' "$START_OUTPUT"

sleep 8
PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
if [[ -z "${PID//[[:space:]]/}" ]]; then
  echo "::error::ROYAL+ Launcher process did not remain alive after startup."
  cat "$LOG_FILE" || true
  adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" || true
  exit 1
fi

if grep -E 'FATAL EXCEPTION|Process: com\.royalplus\.launcher' "$LOG_FILE"; then
  echo "::error::AndroidRuntime reported a launcher crash during startup."
  exit 1
fi

echo "Android 7 API 24 $BUILD_TYPE install, activity launch, process liveness, and crash-log checks passed (PID: $PID)."
