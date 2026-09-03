#!/bin/bash
export LD_LIBRARY_PATH=/snap/android-studio/current/plugins/android-ndk/resources/lldb/lib64:/home/matt/Android/Sdk/emulator/lib64:$LD_LIBRARY_PATH
# Find the connected physical phone (skip any running emulators)
PHONE=$(adb devices | grep -v "emulator" | grep -w "device" | awk '{print $1}' | head -n 1)
if [ -n "$PHONE" ]; then
    echo "Forwarding port 5277 to phone ($PHONE)..."
    adb -s "$PHONE" forward tcp:5277 tcp:5277
else
    echo "No physical phone found by serial, attempting adb -d..."
    adb -d forward tcp:5277 tcp:5277
fi
echo "Connecting to Android Auto Head Unit Server on your phone..."
/home/matt/Android/Sdk/extras/google/auto/desktop-head-unit -c /home/matt/Android/Sdk/extras/google/auto/config/default.ini
