#!/system/bin/sh
# Switch the K20 between normal Wi-Fi and the base monitor+hidden-STA mode.
# Usage: owly-mode status|enter|exit|reboot-enter|reboot-exit

CON_MODE=/sys/module/wlan/parameters/con_mode
FLAG=/data/adb/owly-transfer-mode
LOG=/data/local/tmp/owly-mode.log

log() {
    echo "$(date 2>/dev/null) $*" >> "$LOG"
}

monitor_up() {
    [ -e /sys/class/net/wlan0 ] || return 1
    [ "$(cat /sys/class/net/wlan0/type 2>/dev/null)" = "803" ]
}

hidden_sta_present() {
    ls /sys/class/net/wlan_inj* >/dev/null 2>&1
}

status() {
    echo "con_mode=$(cat "$CON_MODE" 2>/dev/null)"
    echo "wlan0_type=$(cat /sys/class/net/wlan0/type 2>/dev/null)"
    echo "wlan0_mac=$(cat /sys/class/net/wlan0/address 2>/dev/null)"
    if hidden_sta_present; then
        for d in /sys/class/net/wlan_inj*; do
            echo "hidden_sta=$(basename "$d") mac=$(cat "$d/address" 2>/dev/null)"
        done
    else
        echo "hidden_sta=none"
    fi
    if monitor_up; then
        echo "mode=monitor"
    else
        echo "mode=station-or-off"
    fi
    echo "transfer_flag=$([ -e "$FLAG" ] && echo present || echo absent)"
}

enter_live() {
    log "enter_live"
    svc wifi disable
    sleep 2
    ip link set wlan0 down 2>/dev/null
    # This write can block or crash the WLAN subsystem on WCN3990.
    echo 4 > "$CON_MODE"
    for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
        monitor_up && break
        sleep 1
    done
    ip link set wlan0 up 2>/dev/null
    sleep 2
    if ! monitor_up; then
        log "enter_live failed: wlan0 is not monitor"
        return 1
    fi
    for _ in 1 2 3 4 5 6 7 8 9 10; do
        hidden_sta_present && break
        sleep 1
    done
    if ! hidden_sta_present; then
        log "enter_live failed: hidden STA did not appear"
        return 1
    fi
    log "enter_live ok"
    return 0
}

exit_live() {
    log "exit_live"
    svc wifi disable
    sleep 2
    ip link set wlan0 down 2>/dev/null
    echo 0 > "$CON_MODE"
    for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
        [ "$(cat "$CON_MODE" 2>/dev/null)" = "0" ] && break
        sleep 1
    done
    if [ "$(cat "$CON_MODE" 2>/dev/null)" != "0" ]; then
        log "exit_live failed: con_mode did not return to 0"
        return 1
    fi
    ip link set wlan0 up 2>/dev/null
    svc wifi enable
    log "exit_live done"
    return 0
}

case "$1" in
    status)
        status
        ;;
    enter)
        enter_live
        ;;
    exit)
        exit_live
        ;;
    reboot-enter)
        # The app's BOOT_COMPLETED receiver applies the flag. KernelSU
        # service.d scripts are not reliable on this K20 build.
        mkdir -p /data/adb
        touch "$FLAG"
        sync
        log "reboot-enter"
        reboot
        ;;
    reboot-exit)
        rm -f "$FLAG"
        sync
        log "reboot-exit"
        reboot
        ;;
    *)
        echo "usage: $0 status|enter|exit|reboot-enter|reboot-exit" >&2
        exit 2
        ;;
esac
