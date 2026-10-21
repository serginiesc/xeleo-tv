#!/usr/bin/env bash
# Builds XELEO TV and installs it on the TV over Wi-Fi as the home screen, screensaver and helper service.
# Needs: JDK 17, the Android SDK (platform-tools) and USB debugging on in the TV's Developer options.
#
# Usage: ./install.sh 192.168.1.50
set -euo pipefail
cd "$(dirname "$0")"
TV="${1:?usage: ./install.sh <TV IP>}"
export MSYS_NO_PATHCONV=1   # Git Bash on Windows: don't rewrite /data/... paths

SDK="${ANDROID_HOME:-${LOCALAPPDATA:-$HOME}/Android/Sdk}"
ADB="$SDK/platform-tools/adb"
[ -x "$ADB" ] || ADB="$SDK/platform-tools/adb.exe"
[ -f local.properties ] || echo "sdk.dir=$(cd "$SDK" && { pwd -W 2>/dev/null || pwd; })" > local.properties

./gradlew assembleRelease -q --console=plain
APK=app/build/outputs/apk/release/app-release.apk

"$ADB" connect "$TV:5555" >/dev/null
echo "If the TV asks \"Allow USB debugging?\", accept it."
"$ADB" -s "$TV:5555" wait-for-device
A=("$ADB" -s "$TV:5555" shell)

# Android 14 waits for an on-screen confirmation with "adb install"; pm install from the shell does not.
"$ADB" -s "$TV:5555" push "$APK" /data/local/tmp/xeleo.apk >/dev/null
"${A[@]}" pm install -r /data/local/tmp/xeleo.apk
"${A[@]}" rm -f /data/local/tmp/xeleo.apk

"${A[@]}" pm grant com.xeleo.tv android.permission.ACCESS_FINE_LOCATION
"${A[@]}" cmd notification allow_listener com.xeleo.tv/com.xeleo.tv.MediaListener
"${A[@]}" settings put secure enabled_accessibility_services com.xeleo.tv/.XeleoService
"${A[@]}" settings put secure accessibility_enabled 1
"${A[@]}" settings put secure screensaver_components com.xeleo.tv/com.xeleo.tv.SceneryDream
"${A[@]}" cmd package set-home-activity --user 0 com.xeleo.tv/.HomeActivity
"${A[@]}" input keyevent KEYCODE_HOME
echo "XELEO TV installed. Home is now: $("${A[@]}" cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1)"
