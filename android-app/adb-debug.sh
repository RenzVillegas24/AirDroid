#!/usr/bin/env bash
# Run a root command through the Droidy ADB debug receiver and print its output.
# Usage: printf '%s' 'id; uname -a' | ./adb-debug.sh
set -euo pipefail
SERIAL="${ADB_SERIAL:?set ADB_SERIAL}"
TOKEN="droidy-local-debug"
TIMEOUT="${TIMEOUT:-30}"
WAIT="${WAIT:-3}"
CMD="$(cat)"
B64="$(printf '%s' "$CMD" | base64 -w0)"
adb -s "$SERIAL" shell \
  "am broadcast -a io.github.owlyradio.DEBUG -n io.github.owlyradio/.DebugReceiver --es token $TOKEN --es b64 '$B64' --ei timeout $TIMEOUT" \
  >/dev/null
sleep "$WAIT"
adb -s "$SERIAL" shell cat /data/local/tmp/droidy-debug.txt
