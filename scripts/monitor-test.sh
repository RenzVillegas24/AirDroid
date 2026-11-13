#!/system/bin/sh
# Monitor-mode smoke test for the patched QCA6750 driver.
# Run as root after a boot where the patched module actually loaded.
# Wi-Fi is left disabled so the station path cannot crash the driver first.
OUT=/data/local/tmp/owly-monitor-test.txt
export PATH=/data/data/com.termux/files/usr/bin:$PATH
export LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib
exec > "$OUT" 2>&1

log() {
    echo "[$(date '+%H:%M:%S')] $*"
    sync
}

log "=== id ==="
id
log "=== driver ==="
lsmod | grep qca
log "coresize=$(cat /sys/module/qca_cld3_qca6750/coresize 2>&1)"
log "=== disable Wi-Fi framework ==="
cmd wifi set-wifi-enabled disabled 2>&1
svc wifi disable 2>&1
sleep 2
settings get global wifi_on 2>&1
cmd wifi status 2>&1 | head -3

log "=== enable monitor mode (con_mode=4) ==="
echo 4 > /sys/module/qca_cld3_qca6750/parameters/con_mode
cat /sys/module/qca_cld3_qca6750/parameters/con_mode
ip link set wlan0 down 2>&1
ip link set wlan0 up 2>&1
sleep 1
log "=== interface state ==="
ip -d link show wlan0 2>&1
cat /sys/class/net/wlan0/type 2>&1
iw dev wlan0 info 2>&1

log "=== set channel 36 ==="
echo "36 0" > /sys/class/net/wlan0/monitor_mode_channel 2>&1
sleep 1
iw dev wlan0 info 2>&1

log "=== tcpdump 6 s ==="
TCPDUMP=/data/data/com.termux/files/usr/bin/tcpdump
if [ -x "$TCPDUMP" ]; then
    LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib \
        timeout 6 "$TCPDUMP" -i wlan0 -nn -e -c 40 \
        > /data/local/tmp/owly-monitor.txt 2>&1
    log "tcpdump_rc=$?"
    wc -l /data/local/tmp/owly-monitor.txt
    head -40 /data/local/tmp/owly-monitor.txt
else
    log "tcpdump not found at $TCPDUMP"
fi

chmod 0644 "$OUT" /data/local/tmp/owly-monitor.txt 2>/dev/null
log "=== done ==="
