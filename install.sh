#!/usr/bin/env bash
# Builds XELEO TV and installs it on the TV over Wi-Fi as the home screen, screensaver and helper service.
# Needs: the Android SDK's platform-tools (adb); to build it, also JDK 17. On the TV, debugging on in Developer
# options: "USB debugging" (onn: adb on port 5555) or "Wireless debugging" (Chromecast: pair once with a code).
#
# Usage: ./install.sh <TV IP[:port]> [options]        (or an adb serial such as emulator-5554)
#   --pair <IP:port> <code>   pair Wireless debugging first (the IP:port and code on its "pair with code" screen);
#                             then give the TV as the IP:port on the Wireless debugging screen itself
#   --apk <file>              install this APK (from GitHub Releases) instead of building one
#   --replace-google-home     also turn off Google TV's home screen and its setup assistant, so the Home button
#                             opens XELEO (on Google TV it doesn't otherwise)
#   --restore-google-home     only turn Google TV's home screen and setup assistant back on, and stop
# Examples:
#   ./install.sh 192.168.1.50 --replace-google-home
#   ./install.sh 192.168.1.60:37253 --pair 192.168.1.60:46101 189303 --apk XELEO-TV.apk --replace-google-home
set -euo pipefail
cd "$(dirname "$0")"
TV="${1:?usage: ./install.sh <TV IP[:port]> [--pair IP:port code] [--apk file] [--replace-google-home]}"
shift
PAIR="" CODE="" APK="" REPLACE=0 RESTORE=0
while [ $# -gt 0 ]; do
  case "$1" in
    --pair) PAIR="${2:?--pair needs IP:port and code}"; CODE="${3:?--pair needs IP:port and code}"; shift 3 ;;
    --apk) APK="${2:?--apk needs a file}"; shift 2 ;;
    --replace-google-home) REPLACE=1; shift ;;
    --restore-google-home) RESTORE=1; shift ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done
case "$TV" in *:*|emulator-*) DEV="$TV" ;; *) DEV="$TV:5555" ;; esac
export MSYS_NO_PATHCONV=1   # Git Bash on Windows: don't rewrite /data/... paths
GOOGLE_HOME=com.google.android.apps.tv.launcherx
GOOGLE_SETUP=com.google.android.tungsten.setupwraith

SDK="${ANDROID_HOME:-${LOCALAPPDATA:-$HOME}/Android/Sdk}"
ADB="$SDK/platform-tools/adb"
[ -x "$ADB" ] || ADB="$SDK/platform-tools/adb.exe"
[ -f local.properties ] || echo "sdk.dir=$(cd "$SDK" && { pwd -W 2>/dev/null || pwd; })" > local.properties

if [ -n "$PAIR" ]; then
  "$ADB" pair "$PAIR" "$CODE"
fi
case "$DEV" in emulator-*) ;; *) "$ADB" connect "$DEV" >/dev/null ;; esac
echo "If the TV asks \"Allow USB debugging?\", accept it."
"$ADB" -s "$DEV" wait-for-device
A=("$ADB" -s "$DEV" shell)

if [ "$RESTORE" = 1 ]; then
  "${A[@]}" "pm enable $GOOGLE_HOME; pm enable $GOOGLE_SETUP" >/dev/null
  "${A[@]}" input keyevent KEYCODE_HOME
  echo "Google TV's home screen and setup assistant are back on."
  exit 0
fi

if [ -z "$APK" ]; then
  ./gradlew assembleRelease -q --console=plain
  APK=app/build/outputs/apk/release/app-release.apk
fi
[ -f "$APK" ] || { echo "No APK at $APK"; exit 1; }
command -v cygpath >/dev/null && APK=$(cygpath -w "$APK")   # Git Bash: adb.exe wants a Windows path

PKG=com.xeleo.tv
# This TV's settings, kept next to this script (not in git); by IP only, as Wireless debugging's port changes.
NAME="${DEV%%:*}"
BACKUP="respaldo-tv-${NAME//./_}.json"
OLD="respaldo-tv-${NAME//./_}_5555.json"   # its name before, with the port
[ -f "$BACKUP" ] || [ ! -f "$OLD" ] || mv "$OLD" "$BACKUP"
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
  "${A[@]}" rm -f "$REMOTE"   # pushed by adb it belongs to the shell; the app writes its own next time
fi

"${A[@]}" pm grant com.xeleo.tv android.permission.ACCESS_FINE_LOCATION
"${A[@]}" cmd notification allow_listener com.xeleo.tv/com.xeleo.tv.MediaListener
"${A[@]}" settings put secure enabled_accessibility_services com.xeleo.tv/.XeleoService
"${A[@]}" settings put secure accessibility_enabled 1
"${A[@]}" settings put secure screensaver_components com.xeleo.tv/com.xeleo.tv.SceneryDream
if [ "$REPLACE" = 1 ]; then
  # In one shell command on the TV: with only Google's home screen off, its setup assistant takes the Home
  # button (a black screen) and Wireless debugging can drop before the rest gets there.
  "${A[@]}" "pm disable-user --user 0 $GOOGLE_HOME; pm disable-user --user 0 $GOOGLE_SETUP; cmd role add-role-holder android.app.role.HOME com.xeleo.tv; cmd package set-home-activity --user 0 com.xeleo.tv/.HomeActivity" >/dev/null 2>&1 || true
fi
"${A[@]}" cmd package set-home-activity --user 0 com.xeleo.tv/.HomeActivity
"${A[@]}" cmd role add-role-holder android.app.role.HOME com.xeleo.tv >/dev/null 2>&1 || true
# Let XELEO install its own updates from GitHub (Android still asks for the OK on screen each time).
"${A[@]}" appops set com.xeleo.tv REQUEST_INSTALL_PACKAGES allow >/dev/null 2>&1 || true
"${A[@]}" input keyevent KEYCODE_HOME
HOME_NOW=$("${A[@]}" cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1)
echo "XELEO TV installed. Home is now: $HOME_NOW"
case "$HOME_NOW" in
  com.xeleo.tv/*) ;;
  *) echo "The Home button still opens another app: run again with --replace-google-home." ;;
esac
