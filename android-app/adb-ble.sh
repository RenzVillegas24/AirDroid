#!/usr/bin/env bash
set -euo pipefail
SERIAL="${ADB_SERIAL:?set ADB_SERIAL}"
TOKEN="$(adb -s "$SERIAL" shell run-as io.moonbase.airdroid cat files/debug-token 2>/dev/null | tr -d '\r')"
[ -n "$TOKEN" ] || { echo "debug token missing; launch the debug app once" >&2; exit 1; }
adb -s "$SERIAL" shell \
  "am broadcast -a io.moonbase.airdroid.DEBUG -n io.moonbase.airdroid/.DebugReceiver --es token $TOKEN --ez ble true --ei arm ${1:-0}" \
  >/dev/null
