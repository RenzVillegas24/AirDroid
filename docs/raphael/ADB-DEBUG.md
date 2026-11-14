# ADB debug control

The release app exposes a local debug receiver while the controller/service is
running. It is protected by `android.permission.DUMP`, which the adb shell user
holds; normal apps cannot call it.

All app and helper output is mirrored to logcat under `DroidyLog`, with
millisecond timestamps. Controller state is under `DroidyDebug`.

## Commands

```bash
scripts/droidy-adb.sh status
scripts/droidy-adb.sh log
scripts/droidy-adb.sh follow
```

`status` prints scanning/receiver state, peers, active transfers, and the
current incoming request. `log` dumps the timestamped buffer; `follow` streams
it live.

```bash
scripts/droidy-adb.sh scan
scripts/droidy-adb.sh stop
scripts/droidy-adb.sh accept
scripts/droidy-adb.sh decline
scripts/droidy-adb.sh history
```

```bash
scripts/droidy-adb.sh send /sdcard/Download/example.bin <redacted-mac>
scripts/droidy-adb.sh send-text "hello" <redacted-mac>
scripts/droidy-adb.sh cancel <transfer-id>
scripts/droidy-adb.sh pin <redacted-mac>
scripts/droidy-adb.sh delete /sdcard/Download/Droidy/example.jpg
```

The peer MAC is optional when a peer is already selected. It is read at
runtime; do not commit real device identifiers.

```bash
scripts/droidy-adb.sh channel 6
scripts/droidy-adb.sh name "Droidy K20"
scripts/droidy-adb.sh enter
scripts/droidy-adb.sh exit
scripts/droidy-adb.sh reboot-enter
scripts/droidy-adb.sh reboot-exit
```

`channel` saves the fixed monitor channel (`0` = auto) and drops the live
session so the next operation recreates it. `name` saves the AirDrop display
name.

## Direct broadcasts

Every action can also be sent directly:

```bash
adb shell am broadcast -n io.moonbase.droidy/.ControlReceiver \
  -a io.moonbase.droidy.debug.STATUS
adb shell am broadcast -n io.moonbase.droidy/.ControlReceiver \
  -a io.moonbase.droidy.debug.SEND --es path /sdcard/Download/example.bin
adb logcat -d -s DroidyLog:D '*:S'
```

## Notes

- The receiver only works while the app process/controller is alive. The
  foreground service keeps it alive in the background during AirDrop mode.
- Debug broadcasts are a local development surface; remove them before a
  public release if the threat model changes.
