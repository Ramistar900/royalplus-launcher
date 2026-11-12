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

adb_limited() {
  local seconds="$1"
  shift
  timeout --foreground --signal=TERM --kill-after=10s "${seconds}s" adb "$@"
}

collect_diagnostics() {
  set +e
  echo "::group::Android 8 emulator diagnostics"
  echo "Connected devices:"
  adb_limited 20 devices -l
  echo "Emulator API level:"
  adb_limited 20 shell getprop ro.build.version.sdk
  echo "Recent AndroidRuntime and system errors:"
  adb_limited 30 logcat -d -t 250 -v brief AndroidRuntime:E ActivityManager:E System.err:W
  echo "Foreground activity:"
  adb_limited 20 shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | tail -n 10
  echo "::endgroup::"
  set -e
}

AAPT="$(find "$ANDROID_HOME/build-tools" -name aapt -type f | sort -V | tail -n 1)"
test -n "$AAPT"
BADGING="$("$AAPT" dump badging "$APK")"
PACKAGE="$(printf '%s\n' "$BADGING" | sed -n "s/^package: name='\\([^']*\\)'.*/\\1/p")"
ACTIVITY="$(printf '%s\n' "$BADGING" | sed -n "s/^launchable-activity: name='\\([^']*\\)'.*/\\1/p")"
test -n "$PACKAGE"
test -n "$ACTIVITY"

API_LEVEL="$(adb_limited 20 shell getprop ro.build.version.sdk | tr -d '\r')"
echo "Installing Android API $API_LEVEL $BUILD_TYPE APK: $APK ($PACKAGE)"
if ! adb_limited 120 install -r "$APK"; then
  echo "::error::APK installation failed or exceeded 120 seconds."
  collect_diagnostics
  exit 1
fi

if ! adb_limited 20 shell am force-stop "$PACKAGE"; then
  echo "::error::Could not stop the previous launcher process."
  collect_diagnostics
  exit 1
fi

LOG_DIR="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
LOG_FILE="$LOG_DIR/android8-runtime-errors.log"
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
  collect_diagnostics
  exit 1
fi

echo "Launching $ACTIVITY"
if ! START_OUTPUT="$(adb_limited 60 shell am start -W -n "$PACKAGE/$ACTIVITY" 2>&1)"; then
  printf '%s\n' "$START_OUTPUT"
  echo "::error::Activity launch failed or exceeded 60 seconds."
  cat "$LOG_FILE" || true
  collect_diagnostics
  exit 1
fi
printf '%s\n' "$START_OUTPUT"

sleep 8
PID="$(adb_limited 20 shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
if [[ -z "${PID//[[:space:]]/}" ]]; then
  echo "::error::ROYAL+ Launcher process did not remain alive after startup."
  cat "$LOG_FILE" || true
  collect_diagnostics
  exit 1
fi

if grep -E 'FATAL EXCEPTION|Process: com\\.royalplus\\.launcher' "$LOG_FILE"; then
  echo "::error::AndroidRuntime reported a launcher crash during startup."
  collect_diagnostics
  exit 1
fi

echo "Android API $API_LEVEL $BUILD_TYPE install, activity launch, process liveness, and crash-log checks passed (PID: $PID)."
