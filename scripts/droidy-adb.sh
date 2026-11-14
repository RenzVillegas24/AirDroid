#!/usr/bin/env bash
# ADB debug control for Droidy. All output is under logcat tags DroidyLog and
# DroidyDebug. The receiver is protected by android.permission.DUMP.
set -euo pipefail

ADB="${ADB:-adb}"
PKG="io.moonbase.droidy"
RECEIVER="$PKG/.DebugReceiver"

usage() {
  cat <<'EOF'
Usage: scripts/droidy-adb.sh <command> [args]

  status                 print controller/peer/transfer state
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
  channel <44|6|149|0>   save AWDL channel (0 = auto)
  name <device-name>     save the AirDrop display name
  enter                  enter AirDrop mode
  exit                   exit to Wi-Fi mode
  reboot-enter           reboot into AirDrop mode
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

case "$command" in
  status)
    broadcast -a "$PKG.debug.STATUS"
    "$ADB" logcat -d -s DroidyDebug:D '*:S' | tail -n 40
    ;;
  log)
    "$ADB" logcat -d -s DroidyLog:D '*:S'
    ;;
  follow)
    "$ADB" logcat -v time -s DroidyLog:D '*:S'
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
  channel)
    channel="${1:?channel needs 0, 6, 44, or 149}"
    broadcast -a "$PKG.debug.CHANNEL" --ei channel "$channel"
    ;;
  name)
    name="${1:?name needs a value}"
    broadcast -a "$PKG.debug.SET_NAME" --es name "$name"
    ;;
  enter)
    broadcast -a "$PKG.debug.ENTER_AIRDROP"
    ;;
  exit)
    broadcast -a "$PKG.debug.EXIT_AIRDROP"
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
