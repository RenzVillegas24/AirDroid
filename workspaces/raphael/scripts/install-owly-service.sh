#!/system/bin/sh
exec > /sdcard/Download/install-service.out 2>&1
id
mkdir -p /data/adb/service.d
cat > /data/adb/service.d/owly-boot-test.sh <<'EOS'
#!/system/bin/sh
MARK=/data/local/tmp/owly-test.started
[ -e "$MARK" ] && exit 0
touch "$MARK"
OUT=/sdcard/Download/owly-boot-test.out
exec > "$OUT" 2>&1
sleep 20
echo "=== id ==="
id
echo "=== state ==="
cat /sys/module/wlan/parameters/con_mode 2>&1
cat /sys/class/net/wlan0/type 2>&1
ip -brief link | grep -E 'wlan|p2p|inj'
echo "=== up/channel ==="
ip link set wlan0 up 2>&1
sleep 1
/data/local/tmp/owly-nl80211-set-channel wlan0 2437 2>&1
echo chan_rc=$?
echo "=== inject ==="
/data/local/tmp/owly-monitor-send-frame wlan0 /data/local/tmp/probe-owly.bin 5 100 2>&1
echo send_rc=$?
sleep 2
echo "=== links ==="
ip -brief link | grep -E 'wlan|p2p|inj'
ls /sys/class/net
echo "=== dmesg ==="
dmesg | grep -i -E 'wma_inject|injection|wlan_inj|tx vdev|monitor|vdev|firmware|error|fail' | tail -120
echo "=== done ==="
EOS
chmod 755 /data/adb/service.d/owly-boot-test.sh
ls -l /data/adb/service.d/owly-boot-test.sh
