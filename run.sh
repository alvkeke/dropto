#!/usr/bin/env bash

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

echo "==> Building debug APK..."
cd "$PROJECT_DIR"
./gradlew assembleDebug

if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: APK not found at $APK_PATH"
    exit 1
fi

echo ""
echo "==> APK built successfully: $APK_PATH"
echo ""

# Check adb connectivity
DEVICES=$(adb devices | grep -w "device" | grep -v "List")
if [ -z "$DEVICES" ]; then
    echo "ERROR: No adb device connected."
    echo "Make sure USB debugging is enabled and the device is connected."
    exit 1
fi

echo "==> Installing APK to device..."
adb install -r "$APK_PATH"


echo "==> Launching app on device..."
adb shell am start -n cn.alvkeke.dropto.debug/cn.alvkeke.dropto.ui.activity.MainActivity

echo ""
echo "==> Done! App installed and launched."
