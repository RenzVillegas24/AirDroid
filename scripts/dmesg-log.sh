#!/system/bin/sh
# Early, per-boot dmesg snapshot for the QCA driver. Runs as a long-lived
# initrc service from early-init, before vendor.modprobe loads qca_cld3.
# Each boot writes its own files under /metadata/owly, so a crash log is not
# overwritten by the stock boot that follows.
BOOT_ID="$(cat /proc/sys/kernel/random/boot_id 2>/dev/null || date +%s)"
LOG="/metadata/owly/dmesg-qca-${BOOT_ID}.log"
TAIL="/metadata/owly/dmesg-tail-${BOOT_ID}.log"
PATTERN='qca_cld3|qca6750|wlan|icnss|cnss|watchdog|panic|BUG|Unable to handle|Oops|Call trace|soft lockup|rcu|assert|timeout|firmware|WMI|HTC|dp_|hdd_|osif_|target_if|wma_|htc_'

echo "=== owly-dmesg-log start $(date 2>/dev/null) boot_id=$BOOT_ID ==="

while true; do
    {
        echo "=== $(date 2>/dev/null) boot_id=$BOOT_ID uptime=$(cut -d' ' -f1 /proc/uptime) ==="
        dmesg 2>/dev/null | grep -i -E "$PATTERN" | tail -4000
    } > "$LOG"
    {
        echo "=== $(date 2>/dev/null) boot_id=$BOOT_ID uptime=$(cut -d' ' -f1 /proc/uptime) ==="
        dmesg 2>/dev/null | tail -400
    } > "$TAIL"
    # Also keep shell-readable copies under /data/local/tmp. /metadata files
    # are metadata_file and not readable by adbd; this lets us pull a crash log
    # even if rootd is unavailable.
    cp "$LOG" "/data/local/tmp/owly-dmesg-qca-${BOOT_ID}.log" 2>/dev/null
    cp "$TAIL" "/data/local/tmp/owly-dmesg-tail-${BOOT_ID}.log" 2>/dev/null
    chmod 0644 "/data/local/tmp/owly-dmesg-qca-${BOOT_ID}.log" \
                "/data/local/tmp/owly-dmesg-tail-${BOOT_ID}.log" 2>/dev/null
    sync
    sleep 1
done
