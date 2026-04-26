#!/bin/bash
set -euo pipefail

PROJECT_ROOT="$(pwd)"
ANDROID_DIR="$PROJECT_ROOT/android"
APP_ID="com.photovault.app"
MAIN_ACTIVITY=".MainActivity"

if [ ! -d "$ANDROID_DIR" ]; then
  exit 0
fi

if ! command -v adb >/dev/null 2>&1; then
  exit 0
fi

if ! adb get-state >/dev/null 2>&1; then
  exit 0
fi

cd "$ANDROID_DIR"
./gradlew :app:installDevDebug >/dev/null
adb shell am start -n "${APP_ID}/${MAIN_ACTIVITY}" >/dev/null

