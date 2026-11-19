#!/system/bin/sh
# Restore Wi-Fi if the CrossPair app process disappears without a clean exit.
APP_PID="$1"
PID_FILE=@PID@
LOG=@LOG@
MODE=@MODE@
CON_MODE=/sys/module/wlan/parameters/con_mode

log() {
    echo "$(date 2>/dev/null) $*" >> "$LOG"
}

# Field 22 of /proc/<pid>/stat (starttime) catches PID reuse while polling.
start_time() {
    [ -r "/proc/$1/stat" ] || return 1
    sed 's/^.*) //' "/proc/$1/stat" 2>/dev/null | cut -d' ' -f20
}

[ -n "$APP_PID" ] || exit 2
echo $$ > "$PID_FILE"
APP_START=$(start_time "$APP_PID")
while kill -0 "$APP_PID" 2>/dev/null; do
    if [ -n "$APP_START" ]; then
        now=$(start_time "$APP_PID")
        if [ "$now" != "$APP_START" ]; then
            log "app $APP_PID pid reused (starttime ${APP_START} -> ${now:-gone})"
            break
        fi
    fi
    sleep 1
done
log "app $APP_PID exited"
# The kernel owner watchdog reverts con_mode on its own, and a second
# owly-mode.sh exit would add another Wi-Fi disable/enable blip. Only make
# sure Wi-Fi is switched back on: svc wifi enable is a no-op when it already is.
if [ "$(cat "$CON_MODE" 2>/dev/null)" = "0" ]; then
    log "kernel already restored con_mode=0; skipping Wi-Fi cycle"
    svc wifi enable
else
    log "restoring Wi-Fi via $MODE exit"
    "$MODE" exit >>"$LOG" 2>&1
fi
rm -f "$PID_FILE"
