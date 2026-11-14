#!/system/bin/sh
# Restore Wi-Fi if the AirDroid app process disappears without a clean exit.
APP_PID="$1"
PID_FILE=/data/local/tmp/owly-watchdog.pid
LOG=/data/local/tmp/owly-watchdog.log
[ -n "$APP_PID" ] || exit 2
echo $$ > "$PID_FILE"
while kill -0 "$APP_PID" 2>/dev/null; do
    sleep 1
done
/data/local/tmp/owly-app/owly-mode.sh exit >>"$LOG" 2>&1
rm -f "$PID_FILE"
