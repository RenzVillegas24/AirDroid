#!/system/bin/sh
# Capture AWDL discovery frames from an iPhone/iPad on the likely AWDL
# channels. Stock driver monitor RX; no patched TX involved.
OUT=/data/local/tmp/owly-awdl-scan.txt
export PATH=/data/data/com.termux/files/usr/bin:$PATH
export LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib
exec > "$OUT" 2>&1

echo "=== id ==="
id
echo "=== disable wifi ==="
cmd wifi set-wifi-enabled disabled 2>&1
svc wifi disable 2>&1
sleep 2

echo "=== monitor mode ==="
echo 4 > /sys/module/qca_cld3_qca6750/parameters/con_mode
cat /sys/module/qca_cld3_qca6750/parameters/con_mode
ip link set wlan0 down 2>&1
ip link set wlan0 up 2>&1
sleep 1
iw dev wlan0 info

for CH in 6 44 149; do
    echo "=== channel $CH ==="
    echo "$CH 0" > /sys/class/net/wlan0/monitor_mode_channel
    echo "set_channel_rc=$?"
    sleep 1
    timeout 10 tcpdump -i wlan0 -nn -e > "/data/local/tmp/owly-awdl-ch${CH}.txt" 2>&1
    echo "tcpdump_rc=$?"
    chmod 0644 "/data/local/tmp/owly-awdl-ch${CH}.txt" 2>/dev/null
    echo "lines=$(wc -l < /data/local/tmp/owly-awdl-ch${CH}.txt 2>/dev/null)"
done

echo "=== restore ==="
echo 0 > /sys/module/qca_cld3_qca6750/parameters/con_mode
ip link set wlan0 down 2>&1
ip link set wlan0 up 2>&1
chmod 0644 "$OUT" 2>/dev/null
echo "=== done ==="
