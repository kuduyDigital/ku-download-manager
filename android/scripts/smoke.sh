#!/usr/bin/env sh
# Runs the device tests on a connected emulator and keeps its log (logcat.txt).
# A crash anywhere in the app fails the tests; the lines that matter are printed.
set -u
adb logcat -c
adb logcat -v time > logcat.txt 2>&1 &
logpid=$!
(cd android && gradle --no-daemon connectedDebugAndroidTest)
status=$?
sleep 2
kill "$logpid" 2>/dev/null
echo "──── device log (crashes, test steps) ────"
grep -E "KU-SMOKE|FATAL EXCEPTION|AndroidRuntime|has died|RenderProcessGone|render process|Fatal signal|ANR in" logcat.txt | tail -n 300 || true
exit $status
