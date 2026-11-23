#!/usr/bin/env bash
set -Eeuo pipefail

API_LEVEL=26
SYSTEM_IMAGE="system-images;android-26;google_apis;x86_64"
AVD_NAME="royalplus-android-26"
SERIAL="emulator-5554"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
export PATH="$SDK_ROOT/platform-tools:$SDK_ROOT/emulator:$PATH"

SDKMANAGER="$(find "$SDK_ROOT/cmdline-tools" -type f -name sdkmanager -print -quit 2>/dev/null || true)"
AVDMANAGER="$(find "$SDK_ROOT/cmdline-tools" -type f -name avdmanager -print -quit 2>/dev/null || true)"
EMULATOR="$SDK_ROOT/emulator/emulator"
ADB="$SDK_ROOT/platform-tools/adb"
RUN_TMP="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
EMULATOR_LOG="$RUN_TMP/royalplus-android-${API_LEVEL}-emulator.log"
EMULATOR_PID=""

fail_with_emulator_log() {
  echo "::group::Android ${API_LEVEL} emulator startup diagnostics"
  if [[ -s "$EMULATOR_LOG" ]]; then tail -n 120 "$EMULATOR_LOG"; fi
  echo "::endgroup::"
}

cleanup() {
  set +e
  # Ask Android to shut down cleanly, but never let emulator teardown hang the CI job.
  if [[ -x "$ADB" ]]; then
    timeout --foreground --signal=TERM --kill-after=5s 15s "$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1
  fi
  if [[ -n "$EMULATOR_PID" ]] && kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
    kill -TERM "$EMULATOR_PID" >/dev/null 2>&1
    for attempt in $(seq 1 10); do
      kill -0 "$EMULATOR_PID" >/dev/null 2>&1 || break
      sleep 1
    done
    # A stuck QEMU process must not consume the remaining GitHub Actions timeout.
    if kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
      kill -KILL "$EMULATOR_PID" >/dev/null 2>&1
    fi
    wait "$EMULATOR_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

if [[ ! -x "$EMULATOR" || ! -x "$ADB" || -z "$SDKMANAGER" || -z "$AVDMANAGER" ]]; then
  echo "::error::Android SDK emulator or command-line tools were not found under $SDK_ROOT."
  exit 1
fi

mkdir -p "$ANDROID_AVD_HOME" "$RUN_TMP"
echo "Ensuring Android ${API_LEVEL} Google APIs x86_64 system image is installed."
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
timeout --foreground --signal=TERM --kill-after=10s 600s "$SDKMANAGER" --install "$SYSTEM_IMAGE"

echo "Creating a fresh Android ${API_LEVEL} emulator."
printf 'no\n' | "$AVDMANAGER" create avd --force --name "$AVD_NAME" --package "$SYSTEM_IMAGE" --device "Nexus 6"
AVD_CONFIG="$ANDROID_AVD_HOME/$AVD_NAME.avd/config.ini"
if [[ -f "$AVD_CONFIG" ]]; then
  printf '\nhw.cpu.ncore=2\nhw.ramSize=2048\nhw.gpu.enabled=yes\n' >> "$AVD_CONFIG"
fi

echo "Starting Android ${API_LEVEL} emulator."
"$EMULATOR" -avd "$AVD_NAME" -port 5554 -no-window -gpu swiftshader_indirect -no-snapshot -noaudio -no-boot-anim -no-metrics -wipe-data >"$EMULATOR_LOG" 2>&1 &
EMULATOR_PID=$!

echo "Waiting for ADB device connection."
CONNECTED=false
for attempt in $(seq 1 90); do
  DEVICE_STATE="$(timeout --foreground --signal=TERM --kill-after=3s 12s "$ADB" -s "$SERIAL" get-state 2>/dev/null || true)"
  if [[ "$DEVICE_STATE" == "device" ]]; then CONNECTED=true; break; fi
  if ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
    echo "::error::Android emulator exited before ADB became available."
    fail_with_emulator_log
    exit 1
  fi
  sleep 2
done
if [[ "$CONNECTED" != "true" ]]; then
  echo "::error::ADB did not connect to Android ${API_LEVEL} within 180 seconds."
  fail_with_emulator_log
  exit 1
fi

echo "Waiting for Android ${API_LEVEL} boot completion."
BOOTED=false
for attempt in $(seq 1 100); do
  BOOT_STATE="$(timeout --foreground --signal=TERM --kill-after=3s 15s "$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$BOOT_STATE" == "1" ]]; then BOOTED=true; break; fi
  if ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
    echo "::error::Android emulator exited during boot."
    fail_with_emulator_log
    exit 1
  fi
  sleep 3
done
if [[ "$BOOTED" != "true" ]]; then
  echo "::error::Android ${API_LEVEL} did not finish booting within 300 seconds."
  fail_with_emulator_log
  exit 1
fi

echo "Android ${API_LEVEL} emulator is ready; disabling animations and running install/start smoke test."
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  timeout --foreground --signal=TERM --kill-after=3s 15s "$ADB" -s "$SERIAL" shell settings put global "$setting" 0 || true
done
timeout --foreground --signal=TERM --kill-after=3s 15s "$ADB" -s "$SERIAL" shell input keyevent 82 || true

bash .github/scripts/android8-smoke-test.sh
