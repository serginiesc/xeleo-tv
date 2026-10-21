#!/usr/bin/env bash
# Builds XELEO TV and installs it on the TV over Wi-Fi as the home screen, screensaver and helper service.
# Needs: JDK 17, the Android SDK (platform-tools) and USB debugging on in the TV's Developer options.
#
# Usage: ./install.sh 192.168.1.50      (or an adb serial such as emulator-5554)
set -euo pipefail
cd "$(dirname "$0")"
TV="${1:?usage: ./install.sh <TV IP>}"
case "$TV" in *:*|emulator-*) DEV="$TV" ;; *) DEV="$TV:5555" ;; esac
export MSYS_NO_PATHCONV=1   # Git Bash on Windows: don't rewrite /data/... paths

SDK="${ANDROID_HOME:-${LOCALAPPDATA:-$HOME}/Android/Sdk}"
ADB="$SDK/platform-tools/adb"
[ -x "$ADB" ] || ADB="$SDK/platform-tools/adb.exe"
[ -f local.properties ] || echo "sdk.dir=$(cd "$SDK" && { pwd -W 2>/dev/null || pwd; })" > local.properties

./gradlew assembleRelease -q --console=plain
APK=app/build/outputs/apk/release/app-release.apk

case "$DEV" in emulator-*) ;; *) "$ADB" connect "$DEV" >/dev/null ;; esac
echo "If the TV asks \"Allow USB debugging?\", accept it."
"$ADB" -s "$DEV" wait-for-device
A=("$ADB" -s "$DEV" shell)

PKG=com.xeleo.tv
BACKUP=respaldo-tv.json   # the TV's settings, kept next to this script (not in git)
REMOTE="/sdcard/Android/data/$PKG/files/xeleo-respaldo.json"
installed() { "${A[@]}" pm list packages "$PKG" | grep -q "package:$PKG\$"; }

# Save the TV's settings first, so a fresh install (or one with a new signing key) gets them back.
if installed; then
  "${A[@]}" am broadcast -n "$PKG/.ControlReceiver" -a "$PKG.BACKUP" >/dev/null
  "$ADB" -s "$DEV" pull "$REMOTE" "$BACKUP" >/dev/null 2>&1 && echo "Settings saved to $BACKUP."
  FRESH=0
else
  FRESH=1
fi

# Android 14 waits for an on-screen confirmation with "adb install"; pm install from the shell does not.
"$ADB" -s "$DEV" push "$APK" /data/local/tmp/xeleo.apk >/dev/null
if ! OUT=$("${A[@]}" pm install -r /data/local/tmp/xeleo.apk 2>&1); then
  if echo "$OUT" | grep -q UPDATE_INCOMPATIBLE; then
    # Signed with another key (an older build): it has to go first; its settings come back below.
    echo "The installed XELEO TV has another signature: reinstalling."
    "${A[@]}" pm uninstall "$PKG" >/dev/null
    "${A[@]}" pm install /data/local/tmp/xeleo.apk
    FRESH=1
  else
    echo "$OUT"
    exit 1
  fi
fi
"${A[@]}" rm -f /data/local/tmp/xeleo.apk

if [ "$FRESH" = 1 ] && [ -f "$BACKUP" ]; then
  "${A[@]}" mkdir -p "$(dirname "$REMOTE")"
  "$ADB" -s "$DEV" push "$BACKUP" "$REMOTE" >/dev/null
  "${A[@]}" am broadcast -n "$PKG/.ControlReceiver" -a "$PKG.RESTORE" >/dev/null && echo "Settings restored from $BACKUP."
fi

"${A[@]}" pm grant com.xeleo.tv android.permission.ACCESS_FINE_LOCATION
"${A[@]}" cmd notification allow_listener com.xeleo.tv/com.xeleo.tv.MediaListener
"${A[@]}" settings put secure enabled_accessibility_services com.xeleo.tv/.XeleoService
"${A[@]}" settings put secure accessibility_enabled 1
"${A[@]}" settings put secure screensaver_components com.xeleo.tv/com.xeleo.tv.SceneryDream
"${A[@]}" cmd package set-home-activity --user 0 com.xeleo.tv/.HomeActivity
"${A[@]}" cmd role add-role-holder android.app.role.HOME com.xeleo.tv >/dev/null 2>&1 || true
"${A[@]}" input keyevent KEYCODE_HOME
echo "XELEO TV installed. Home is now: $("${A[@]}" cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1)"
