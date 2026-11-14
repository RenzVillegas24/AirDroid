#!/usr/bin/env bash
# ADB debug control for AirDroid. All output is under logcat tags AirDroidLog and
# AirDroidDebug. The receiver is protected by android.permission.DUMP.
set -euo pipefail

ADB="${ADB:-adb}"
PKG="io.moonbase.airdroid"
RECEIVER="$PKG/.ControlReceiver"

usage() {
  cat <<'EOF'
Usage: scripts/airdroid-adb.sh <command> [args]

  status                 print controller/peer/transfer state
  history                print received files and completed transfers
  log                    dump timestamped app + helper logs
  follow                 follow timestamped logs (Ctrl-C to stop)
  clear-log              clear the in-app log buffer
  scan                   start discovery
  stop                   stop discovery
  accept                 accept the current incoming request
  decline                decline the current incoming request
  send <path> [mac]      send a file, optionally selecting a peer MAC
  send-text <text> [mac] send clipboard-style text
  cancel <transfer-id>   cancel a transfer
  pin <mac>              toggle peer pin
  delete <path>          delete a received file and its history entry
  channel <44|6|149|0>   save AWDL channel (0 = auto)
  name <device-name>     save the AirDrop display name
  enter                  enter Transfer mode
  exit                   exit to Wi-Fi mode
  reboot-enter           reboot into Transfer mode
  reboot-exit            reboot into Wi-Fi mode
EOF
}

if [[ $# -lt 1 ]]; then
  usage
  exit 2
fi

command="$1"
shift

broadcast() {
  "$ADB" shell am broadcast -n "$RECEIVER" "$@" >/dev/null
}

current_pid() {
  "$ADB" shell pidof "$PKG" 2>/dev/null | tr -d '\r'
}

dump_current() {
  local pid
  pid="$(current_pid)"
  if [[ -n "$pid" ]]; then
    "$ADB" logcat -d --pid="$pid" -s AirDroidDebug:D AirDroidLog:D '*:S'
  else
    "$ADB" logcat -d -s AirDroidDebug:D AirDroidLog:D '*:S'
  fi
}

case "$command" in
  status)
    broadcast -a "$PKG.debug.STATUS"
    sleep 0.3
    dump_current | grep 'AirDroidDebug' | tail -n 40
    ;;
  history)
    broadcast -a "$PKG.debug.HISTORY"
    sleep 0.3
    dump_current | grep 'AirDroidDebug' | tail -n 80
    ;;
  log)
    dump_current | grep 'AirDroidLog' | tail -n 200
    ;;
  follow)
    pid="$(current_pid)"
    if [[ -n "$pid" ]]; then
      "$ADB" logcat -v time --pid="$pid" -s AirDroidLog:D '*:S'
    else
      "$ADB" logcat -v time -s AirDroidLog:D '*:S'
    fi
    ;;
  clear-log)
    broadcast -a "$PKG.debug.CLEAR_LOG"
    ;;
  scan)
    broadcast -a "$PKG.debug.START_SCAN"
    ;;
  stop)
    broadcast -a "$PKG.debug.STOP_SCAN"
    ;;
  accept)
    broadcast -a "$PKG.debug.ACCEPT"
    ;;
  decline)
    broadcast -a "$PKG.debug.DECLINE"
    ;;
  send)
    path="${1:?send needs a file path}"
    mac="${2:-}"
    args=(-a "$PKG.debug.SEND" --es path "$path")
    [[ -n "$mac" ]] && args+=(--es mac "$mac")
    broadcast "${args[@]}"
    ;;
  send-text)
    text="${1:?send-text needs text}"
    mac="${2:-}"
    args=(-a "$PKG.debug.SEND_TEXT" --es text "$text")
    [[ -n "$mac" ]] && args+=(--es mac "$mac")
    broadcast "${args[@]}"
    ;;
  cancel)
    id="${1:?cancel needs a transfer id}"
    broadcast -a "$PKG.debug.CANCEL" --es id "$id"
    ;;
  pin)
    mac="${1:?pin needs a peer MAC}"
    broadcast -a "$PKG.debug.PIN_PEER" --es mac "$mac"
    ;;
  delete)
    path="${1:?delete needs a received file path}"
    broadcast -a "$PKG.debug.DELETE_RECEIVED" --es path "$path"
    ;;
  channel)
    channel="${1:?channel needs 0, 6, 44, or 149}"
    broadcast -a "$PKG.debug.CHANNEL" --ei channel "$channel"
    ;;
  name)
    name="${1:?name needs a value}"
    broadcast -a "$PKG.debug.SET_NAME" --es name "$name"
    ;;
  enter)
    broadcast -a "$PKG.debug.ENTER_TRANSFER"
    ;;
  exit)
    broadcast -a "$PKG.debug.EXIT_TRANSFER"
    ;;
  reboot-enter)
    broadcast -a "$PKG.debug.REBOOT_ENTER"
    ;;
  reboot-exit)
    broadcast -a "$PKG.debug.REBOOT_EXIT"
    ;;
  *)
    usage
    exit 2
    ;;
esac
