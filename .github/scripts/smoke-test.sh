#!/usr/bin/env bash
# Installs the APK, starts the app and checks it is still running after a while.
# Usage: smoke-test.sh <apk> <logcat output file>
set -u
apk="$1"
out="$2"
pkg=org.theharmen.geenregenaub

adb install -r "$apk"
adb logcat -c
adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1
sleep 20
pid=$(adb shell pidof "$pkg" | tr -d '\r')
adb logcat -d -v threadtime > "$out"

crash=$(grep -nE "FATAL EXCEPTION|Fatal signal|AndroidRuntime: |DEBUG   : |Process $pkg .* has died|ANR in $pkg" "$out" | head -n 150)
if [ -n "$crash" ] || [ -z "$pid" ]; then
  echo "::error::The app crashed or is not running after start (pid: '${pid}')."
  echo "$crash"
  grep -E "AndroidRuntime|DEBUG|libc|$pkg" "$out" | tail -n 200
  exit 1
fi
echo "App is running (pid $pid), no crash in logcat."
