#!/usr/bin/env bash
# Automated screen navigation and high-fidelity screenshot capture for Blocktime.
#
# Programmatically drives Blocktime through all core screens using Android
# broadcasts, captures pixel-perfect composited screenshots, and pulls them
# to the host filesystem.
#
# Usage:
#   tools/capture_all_screens.sh [output_directory]
#
# Requirements:
#   - adb on PATH (or ANDROID_HOME set)
#   - An online Android emulator or device with Blocktime installed.
set -euo pipefail

PACKAGE="com.silas270.blocktime"
MAIN_ACTIVITY="$PACKAGE/.CesiumGameActivity"
OUT_DIR="${1:-./screenshots}"
CACHE_DIR="/data/data/$PACKAGE/cache"

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"

# Ensure adb is accessible
if ! command -v adb &>/dev/null; then
    if [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
        export PATH="$ANDROID_HOME/platform-tools:$PATH"
    elif [ -x "$HOME/android-sdk/platform-tools/adb" ]; then
        export PATH="$HOME/android-sdk/platform-tools:$PATH"
    else
        echo "Error: adb not found in PATH or standard Android SDK directories." >&2
        exit 1
    fi
fi

# Verify device is connected
if ! adb get-state &>/dev/null; then
    echo "Error: No adb device or emulator found." >&2
    exit 1
fi

echo "=========================================================="
echo "Blocktime Automated Screen Tour & Capture"
echo "Target directory: $OUT_DIR"
echo "Device: $(adb get-serialno)"
echo "=========================================================="

# Ensure app is running and brought to front
echo "==> Starting $MAIN_ACTIVITY..."
adb shell am start -n "$MAIN_ACTIVITY"

echo "==> Waiting for activity to be focused and ready..."
local_focused=0
for _ in $(seq 1 40); do
    if adb shell dumpsys window | grep -q "mCurrentFocus.*CesiumGameActivity"; then
        local_focused=1
        break
    fi
    sleep 1
done
if [ "$local_focused" -eq 1 ]; then
    echo "==> CesiumGameActivity is focused and ready."
else
    echo "WARNING: CesiumGameActivity focus not confirmed, continuing anyway..."
fi
sleep 2

capture_screen() {
    local index="$1"
    local name="$2"
    local route="$3"
    local wait_s="${4:-2}"

    local remote_file="$CACHE_DIR/${index}_${name}.png"
    local local_file="$OUT_DIR/${index}_${name}.png"

    echo "----------------------------------------------------------"
    echo "==> [$index/10] Navigating to: $name ($route)"
    adb shell am broadcast -a "$PACKAGE.CONTROL" --es navigate "$route" > /dev/null
    sleep "$wait_s"

    echo "==> Capturing screenshot..."
    adb shell rm -f "$remote_file" "${remote_file}.done"
    adb shell am broadcast -a "$PACKAGE.CAPTURE_SCREEN" --es path "$remote_file" > /dev/null

    local done_found=0
    for _ in $(seq 1 30); do
        if adb shell "[ -f '${remote_file}.done' ]" 2>/dev/null; then
            done_found=1
            break
        fi
        sleep 0.5
    done

    if [ "$done_found" -ne 1 ]; then
        echo "WARNING: Sentinel file not observed within timeout, attempting pull anyway..." >&2
    fi

    echo "==> Pulling screenshot to $local_file..."
    adb pull "$remote_file" "$local_file" > /dev/null

    if [ -s "$local_file" ]; then
        local size
        size=$(stat -c%s "$local_file" 2>/dev/null || stat -f%z "$local_file")
        echo "==> Captured ${index}_${name}.png ($size bytes)"
    else
        echo "ERROR: Failed to capture or pull ${index}_${name}.png" >&2
        return 1
    fi
}

capture_screen "01" "onboarding" "onboarding" 2
capture_screen "02" "hub" "hub" 2
capture_screen "03" "flight_search" "flight_search" 2
capture_screen "04" "check_in" "check_in/MUC/FF-549/STR/45/STORY" 2
capture_screen "05" "in_flight" "in_flight/MUC/FF-549/STR/45/STORY" 3
capture_screen "06" "arrival_celebration" "arrival_celebration/FF-549/STR/45/CO-PILOT/STORY" 5
capture_screen "07" "challenge_outcome" "challenge_outcome" 2
capture_screen "08" "challenges" "challenges" 2
capture_screen "09" "account" "account" 2
capture_screen "10" "settings" "settings" 2

echo "=========================================================="
echo "Capture Tour Complete!"
echo "Files in $OUT_DIR:"
ls -lh "$OUT_DIR"
echo "=========================================================="
