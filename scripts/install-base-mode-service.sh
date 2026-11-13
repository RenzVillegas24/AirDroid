#!/system/bin/sh
# Install the early-boot AirDrop-mode flag service on a KernelSU device.
# Run as root. The service is a no-op unless /data/adb/owly-airdrop-mode exists.
set -eu

DIR=/data/adb/post-fs-data.d
SERVICE="$DIR/owly-mode-boot.sh"
LOG=/data/local/tmp/owly-mode-boot.log

mkdir -p "$DIR"
cat > "$SERVICE" <<'EOS'
#!/system/bin/sh
FLAG=/data/adb/owly-airdrop-mode
CON_MODE=/sys/module/wlan/parameters/con_mode
LOG=/data/local/tmp/owly-mode-boot.log

[ -e "$FLAG" ] || exit 0

# Wait for the WLAN driver to expose con_mode, then enter monitor mode
# before Android's Wi-Fi framework starts.
for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
    [ -e "$CON_MODE" ] && break
    sleep 0.5
done

if [ ! -e "$CON_MODE" ]; then
    echo "con_mode sysfs missing" >> "$LOG"
    exit 1
fi

echo 4 > "$CON_MODE" 2>> "$LOG"
for _ in 1 2 3 4 5 6 7 8 9 10; do
    [ "$(cat /sys/class/net/wlan0/type 2>/dev/null)" = "803" ] && break
    sleep 1
done
ip link set wlan0 up 2>/dev/null
echo "boot mode=$(cat "$CON_MODE" 2>/dev/null) type=$(cat /sys/class/net/wlan0/type 2>/dev/null)" >> "$LOG"
EOS
chmod 755 "$SERVICE"
echo "installed $SERVICE"
