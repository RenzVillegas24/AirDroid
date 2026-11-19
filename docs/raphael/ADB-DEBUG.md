# ADB debug control

The release app exposes a local debug receiver while the controller/service is
running. It is protected by `android.permission.DUMP`, which the adb shell user
holds; normal apps cannot call it.

All app and helper output is mirrored to logcat under `AirDroidLog`, with
millisecond timestamps. Controller state is under `AirDroidDebug`.

## Commands

```bash
scripts/crosspair-adb.sh status
scripts/crosspair-adb.sh log
scripts/crosspair-adb.sh follow
```

`status` prints scanning/receiver state, peers, active transfers, and the
current incoming request. `log` dumps the timestamped buffer; `follow` streams
it live.

```bash
scripts/crosspair-adb.sh scan
scripts/crosspair-adb.sh stop
scripts/crosspair-adb.sh accept
scripts/crosspair-adb.sh decline
scripts/crosspair-adb.sh history
```

```bash
scripts/crosspair-adb.sh send /sdcard/Download/example.bin <redacted-mac>
scripts/crosspair-adb.sh send-text "hello" <redacted-mac>
scripts/crosspair-adb.sh cancel <transfer-id>
scripts/crosspair-adb.sh pin <redacted-mac>
scripts/crosspair-adb.sh delete /sdcard/Download/CrossPair/example.jpg
```

The peer MAC is optional when a peer is already selected. It is read at
runtime; do not commit real device identifiers.

```bash
scripts/crosspair-adb.sh channel 6
scripts/crosspair-adb.sh name "CrossPair K20"
scripts/crosspair-adb.sh enter
scripts/crosspair-adb.sh exit
scripts/crosspair-adb.sh reboot-enter
scripts/crosspair-adb.sh reboot-exit
```

`channel` saves the fixed monitor channel (`0` = auto) and drops the live
session so the next operation recreates it. `name` saves the AirDrop display
name.

## Direct broadcasts

Every action can also be sent directly:

```bash
adb shell am broadcast -n io.moonbase.airdroid/.ControlReceiver \
  -a io.moonbase.airdroid.debug.STATUS
adb shell am broadcast -n io.moonbase.airdroid/.ControlReceiver \
  -a io.moonbase.airdroid.debug.SEND --es path /sdcard/Download/example.bin
adb logcat -d -s AirDroidLog:D '*:S'
```

## Notes

- The receiver only works while the app process/controller is alive. The
  foreground service keeps it alive in the background during Transfer mode.
- Debug broadcasts are a local development surface; remove them before a
  public release if the threat model changes.
