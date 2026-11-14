#!/usr/bin/env bash
# Run a root command through the AirDroid ADB debug receiver and print its output.
# Usage: printf '%s' 'id; uname -a' | ./adb-debug.sh
set -euo pipefail
SERIAL="${ADB_SERIAL:?set ADB_SERIAL}"
TOKEN="$(adb -s "$SERIAL" shell run-as io.moonbase.airdroid cat files/debug-token 2>/dev/null | tr -d '\r')"
[ -n "$TOKEN" ] || { echo "debug token missing; launch the debug app once" >&2; exit 1; }
TIMEOUT="${TIMEOUT:-30}"
WAIT="${WAIT:-3}"
CMD="$(cat)"
B64="$(printf '%s' "$CMD" | base64 -w0)"
adb -s "$SERIAL" shell \
  "am broadcast -a io.moonbase.airdroid.DEBUG -n io.moonbase.airdroid/.DebugReceiver --es token $TOKEN --es b64 '$B64' --ei timeout $TIMEOUT" \
  >/dev/null
sleep "$WAIT"
adb -s "$SERIAL" shell cat /data/local/tmp/airdroid-debug.txt
