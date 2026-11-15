#!/system/bin/sh
# Switch the K20 between normal Wi-Fi and the base monitor+hidden-STA mode.
# Usage: owly-mode status|enter [owner-pid]|exit|reboot-enter|reboot-exit
#
# `enter` takes the app PID so the kernel owner watchdog is armed before
# con_mode=4 is written. A force-stop during the 4-19 s switch then still
# reverts the driver. Kernels without owly_owner_pid ignore the write.

CON_MODE=/sys/module/wlan/parameters/con_mode
OWNER_PATH=/sys/module/wlan/parameters/owly_owner_pid
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

owner_alive() {
    [ -n "$1" ] && kill -0 "$1" 2>/dev/null
}

# Arm the kernel watchdog before monitor mode is up. An absent node means an
# older kernel without the watchdog, so the write is skipped.
arm_owner() {
    [ -n "$1" ] || return 0
    if [ ! -e "$OWNER_PATH" ]; then
        log "owner: $OWNER_PATH absent, kernel watchdog unavailable"
        return 0
    fi
    if echo "$1" > "$OWNER_PATH" 2>/dev/null; then
        log "owner=$1 armed"
        return 0
    fi
    log "owner: write $1 failed"
    return 1
}

status() {
    echo "con_mode=$(cat "$CON_MODE" 2>/dev/null)"
    echo "wlan0_type=$(cat /sys/class/net/wlan0/type 2>/dev/null)"
    echo "wlan0_mac=$(cat /sys/class/net/wlan0/address 2>/dev/null)"
    if [ -e "$OWNER_PATH" ]; then
        echo "owner_pid=$(cat "$OWNER_PATH" 2>/dev/null)"
    fi
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
    owner="$1"
    log "enter_live owner=${owner:-none}"
    # Arm first: the kernel only reverts con_mode while monitor mode is up, so
    # a PID that dies before this write would leave the switch unguarded.
    if ! arm_owner "$owner"; then
        log "enter_live aborted: could not arm owner ${owner:-none}"
        return 1
    fi
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
    # The kernel drops a dead owner without reverting while con_mode is still
    # 0, so re-check once monitor mode is up instead of leaving it unguarded.
    if [ -n "$owner" ] && ! owner_alive "$owner"; then
        log "enter_live aborted: owner $owner exited during enter"
        exit_live
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
        enter_live "$2"
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
        echo "usage: $0 status|enter [owner-pid]|exit|reboot-enter|reboot-exit" >&2
        exit 2
        ;;
esac
