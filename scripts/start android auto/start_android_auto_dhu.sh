#!/bin/bash
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
export LD_LIBRARY_PATH="/snap/android-studio/current/plugins/android-ndk/resources/lldb/lib64:${SDK_DIR}/emulator/lib64:${LD_LIBRARY_PATH:-}"

# Find the connected physical phone (skip any running emulators)
PHONE=$(adb devices | grep -v "emulator" | grep -w "device" | awk '{print $1}' | head -n 1)
if [ -n "$PHONE" ]; then
    echo "Forwarding port 5277 to phone ($PHONE)..."
    adb -s "$PHONE" forward tcp:5277 tcp:5277
else
    echo "No physical phone found by serial, attempting adb -d..."
    adb -d forward tcp:5277 tcp:5277
fi

DHU_BIN="${SDK_DIR}/extras/google/auto/desktop-head-unit"
DHU_CONF="${SDK_DIR}/extras/google/auto/config/default.ini"

if [ -f "$DHU_BIN" ]; then
    echo "Connecting to Android Auto Head Unit Server on your phone..."
    "$DHU_BIN" -c "$DHU_CONF"
else
    echo "Error: Desktop Head Unit binary not found at: $DHU_BIN"
    exit 1
fi
