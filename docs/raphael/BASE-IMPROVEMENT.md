# K20 base improvement

Status: **in progress** — keep normal Wi-Fi on boot, enter the base
monitor+hidden-STA path only for an AirDrop session.

## Goal

The old base booted with `wlan.con_mode=4`, so Android Wi-Fi could never
connect. The improved base must:

- boot in mission mode (`con_mode=0`) by default;
- keep normal Wi-Fi working;
- enter the base monitor + hidden-STA path only when AirDrop is requested;
- leave no AP, IBSS, or second cell behind.

## Owly principles kept

- **Monitor is required** for AWDL RX.
- **Fixed channel** (`Presence::Fixed`), one of 44, 149 or 6.
- **`tx_noack`** behavior: unicast is sent once, without waiting for a cell
  ACK. This is Owly's default for a fixed radio.
- **AWDL address is the monitor MAC**, not the hidden STA MAC. The hidden STA
  is only a WMI TX tag.
- **TAP `awdl0`** remains the data interface.

## Owly principles not applied

- No hidden AP and no IBSS. Both break monitor RX on this firmware.
- No shared mode. It needs monitor beside an associated station, which the
  WCN3990 firmware cannot do.

## Modes

### Normal boot

- boot image has no `wlan.con_mode=4`;
- Android Wi-Fi connects normally;
- no `wlan0` monitor and no `wlan_inj*`.

### Transfer mode

Two entry paths:

1. **Live switch** (fast):
   `svc wifi disable`, `ip link set wlan0 down`, write `4` to
   `/sys/module/wlan/parameters/con_mode`, bring `wlan0` up. The reverse
   sequence returns to Wi-Fi without rebooting.
2. **Boot flag**:
   write `/data/adb/owly-transfer-mode`, reboot; the app's `BOOT_COMPLETED`
   receiver disables Wi-Fi and writes `con_mode=4`. Exit removes the flag and
   reboots. KernelSU `service.d` scripts were tested and do not run on this
   K20 build.

The live monitor-to-station teardown used to panic in
`wma_mlme_vdev_notify_down_complete` when it received a delete-BSS response
with vdev id 255. The driver now rejects out-of-range or missing vdev entries.

A second AirDrop entry also reused a stale cached injection vdev after the
first monitor teardown, so `wlan_inj0` was never recreated. The driver now
clears the cached injection vdev id when leaving monitor mode. Two consecutive
enter/exit cycles both recreate `wlan_inj0` and return to Wi-Fi without a
reboot.

## Base data path

```text
Transfer mode
  wlan0            global monitor, fixed channel 44
  wlan_inj0        hidden STA vdev, WMI TX tag only
  awdl0            TAP data interface
```

The monitor MAC is the AWDL address. The hidden STA MAC is deliberately not
used as the AWDL identity.

## Files

- `android-app/app/src/main/assets/owly-mode.sh` — `status`, `enter [owner-pid]`,
  `exit`, `reboot-enter`, `reboot-exit`.
- `android-app/app/src/main/assets/owly-watchdog.sh` — root watchdog that
  restores Wi-Fi if the app process disappears without a clean exit.
- `pack-base-mission.sh` — packs the base monitor+hidden-STA kernel without
  `wlan.con_mode=4`; kept on the investigation branches.
- `android-app/app/src/main/java/io/moonbase/crosspair/BootReceiver.kt` — checks
  the Transfer-mode flag after boot and starts `CrossPairService`, which enters
  through the normal owner/watchdog-armed path.
- `android-app/app/src/main/assets/owly-mode.sh` — packaged copy of the mode
  switcher installed by the app.
- `android-app/helper/src/main.rs` — reads the monitor MAC for the AWDL
  address.

## Acceptance checks

1. Normal boot: `cmd wifi status` shows the saved network connected.
2. `owly-mode status` reports `con_mode=0`, station mode, no monitor.
3. `owly-mode enter`: `wlan0` is `type=803`, `wlan_inj0` exists, Wi-Fi is off.
4. Helper starts and scans without a reset.
5. `owly-mode exit` (or reboot) restores Wi-Fi.
6. No `owlyap*`, IBSS, or hostapd process remains.
7. Force-stop during `owly-mode enter <pid>`: the kernel owner watchdog reverts
   `con_mode` to 0 and Wi-Fi comes back without a reboot. `owly-mode status`
   shows `owner_pid=0` and `owly-watchdog.log` reports the restore.
