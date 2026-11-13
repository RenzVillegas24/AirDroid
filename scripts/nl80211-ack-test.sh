#!/system/bin/sh
# Send a management frame through nl80211 on the stock/patched station while
# capturing the same channel on a concurrent monitor interface. If the AP ACKs
# the frame or replies with a probe response, the firmware TX/ACK path works.
OUT=/data/local/tmp/owly-nl80211-ack.txt
BSSID="${BSSID:?set BSSID}"
SSID="${SSID:?set SSID}"
PCAP=/data/local/tmp/owly-nl80211-ack.pcap
LOG=/data/local/tmp/owly-nl80211-ack-tcpdump.txt
export PATH=/data/data/com.termux/files/usr/bin:$PATH
export LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib
exec > "$OUT" 2>&1

echo "=== id ==="
id
echo "=== add monitor interface ==="
iw dev mon0 del 2>/dev/null
iw phy phy0 interface add mon0 type monitor
echo "add_rc=$?"
ip link set mon0 up
echo "up_rc=$?"
iw dev mon0 set channel 36
echo "channel_rc=$?"
iw dev mon0 info

echo "=== start capture ==="
rm -f "$PCAP" "$LOG"
timeout 8 tcpdump -i mon0 -nn -e -w "$PCAP" > "$LOG" 2>&1 &
TCPDUMP_PID=$!
sleep 1

echo "=== nl80211 mgmt TX ==="
/data/local/tmp/owly-nl80211-tx wlan0 "$BSSID" 5180 "$SSID"
echo "tx_rc=$?"
sleep 3

echo "=== stop capture ==="
kill "$TCPDUMP_PID" 2>/dev/null
wait "$TCPDUMP_PID" 2>/dev/null
chmod 0644 "$PCAP" "$LOG" 2>/dev/null
ls -l "$PCAP" "$LOG" 2>&1
echo "=== tcpdump log ==="
cat "$LOG" 2>&1

echo "=== cleanup monitor ==="
ip link set mon0 down 2>/dev/null
iw dev mon0 del 2>/dev/null
chmod 0644 "$OUT" 2>/dev/null
echo "=== done ==="
