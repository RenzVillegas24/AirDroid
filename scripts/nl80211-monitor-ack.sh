#!/system/bin/sh
# Stock-driver test: monitor RX on wlan0, send a probe request through
# nl80211 mgmt TX, and capture the AP's ACK/probe response.
OUT=/data/local/tmp/owly-nl80211-monitor-ack.txt
BSSID="${BSSID:?set BSSID}"
SSID="${SSID:?set SSID}"
CAP=/data/local/tmp/owly-nl80211-monitor-cap.txt
export PATH=/data/data/com.termux/files/usr/bin:$PATH
export LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib
exec > "$OUT" 2>&1

echo "=== id ==="
id
echo "=== disable wifi framework ==="
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
echo "36 0" > /sys/class/net/wlan0/monitor_mode_channel
echo "channel_rc=$?"
sleep 1
iw dev wlan0 info

echo "=== start tcpdump text capture ==="
rm -f "$CAP"
timeout 8 tcpdump -i wlan0 -nn -e -c 100 > "$CAP" 2>&1 &
TCPDUMP_PID=$!
sleep 1

echo "=== unicast probe to AP ==="
/data/local/tmp/owly-nl80211-tx wlan0 "$BSSID" 5180 "$SSID"
echo "unicast_rc=$?"
sleep 1

echo "=== broadcast probe ==="
/data/local/tmp/owly-nl80211-tx wlan0 "$BSSID" 5180 ''
echo "broadcast_rc=$?"
sleep 2

echo "=== stop capture ==="
kill "$TCPDUMP_PID" 2>/dev/null
wait "$TCPDUMP_PID" 2>/dev/null
chmod 0644 "$CAP" 2>/dev/null
echo "=== capture ==="
cat "$CAP" 2>&1

echo "=== restore ==="
echo 0 > /sys/module/qca_cld3_qca6750/parameters/con_mode
ip link set wlan0 down 2>&1
ip link set wlan0 up 2>&1
chmod 0644 "$OUT" 2>/dev/null
echo "=== done ==="
