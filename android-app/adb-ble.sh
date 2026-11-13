#!/usr/bin/env bash
set -euo pipefail
SERIAL="${ADB_SERIAL:?set ADB_SERIAL}"
adb -s "$SERIAL" shell \
  "am broadcast -a io.moonbase.droidy.DEBUG -n io.moonbase.droidy/.DebugReceiver --es token droidy-local-debug --ez ble true --ei arm ${1:-0}" \
  >/dev/null
