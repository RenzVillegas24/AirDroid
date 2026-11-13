#!/system/bin/sh
# Capture raw AWDL frames from the iPhone on the social channel.
OUT=/data/local/tmp/owly-awdl-pcap.txt
IPHONE_MAC="${IPHONE_MAC:?set IPHONE_MAC}"
export PATH=/data/data/com.termux/files/usr/bin:$PATH
export LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib
exec > "$OUT" 2>&1

echo "=== id ==="
id
echo 4 > /sys/module/qca_cld3_qca6750/parameters/con_mode
ip link set wlan0 down 2>&1
ip link set wlan0 up 2>&1
sleep 1
iw dev wlan0 info

for CH in 6 149; do
    echo "=== channel $CH pcap ==="
    echo "$CH 0" > /sys/class/net/wlan0/monitor_mode_channel
    sleep 1
    timeout 8 tcpdump -i wlan0 -nn -e -w "/data/local/tmp/owly-awdl${CH}.pcap" \
        "wlan addr2 $IPHONE_MAC" > "/data/local/tmp/owly-awdl${CH}-tcpdump.txt" 2>&1
    echo "rc=$?"
    chmod 0644 "/data/local/tmp/owly-awdl${CH}.pcap" 2>/dev/null
    chmod 0644 "/data/local/tmp/owly-awdl${CH}-tcpdump.txt" 2>/dev/null
    ls -l "/data/local/tmp/owly-awdl${CH}.pcap" 2>&1
done

echo 0 > /sys/module/qca_cld3_qca6750/parameters/con_mode
ip link set wlan0 down 2>&1
ip link set wlan0 up 2>&1
chmod 0644 "$OUT" 2>/dev/null
echo "=== done ==="
