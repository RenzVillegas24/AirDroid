# AGENTS.md

## Project context

Owly/AirDrop port for the Redmi K20 Pro / Mi 9T Pro (`raphael`, WCN3990). The
production tree is the app, helper, pinned kernel/Owly submodules, build script,
and setup/ADB docs. The internal radio is the target; external USB Wi-Fi is
ruled out.

Production branches must stay builder-only. Monitor/injection bring-up work,
captures, legacy C tools, and boot-image experiments live on:

```text
investigation/exclusive
investigation/shared
experiments/investigation-tools
```

Do not move investigation code into `main` unless it is required by the
production app or build.

## Repository layout

```text
android-app/       Kotlin app + Rust helper
kernel/            pinned Evolution-X kernel fork submodule
third_party/owly/  pinned Owly fork submodule
scripts/           build-app.sh, airdroid-adb.sh
docs/              SETUP.md, ADB-DEBUG.md, BASE-IMPROVEMENT.md
```

The helper is built from `third_party/owly`; the kernel submodule is not needed
to build the APK.

## Build rules

```bash
git submodule update --init third_party/owly
scripts/build-app.sh debug
scripts/build-app.sh release
```

- `build-app.sh` builds the helper, copies it into `android-app/assets/`, and
  builds the APK.
- Do not commit helper binaries or `android-app/assets/`.
- On ARM64 hosts, pass `AAPT2_OVERRIDE="$HOME/Android/Sdk/aapt2-qemu/aapt2"`.
- Release APKs are unsigned by default; sign with `scripts/sign-release.sh`
  and a private keystore. Never commit keystores or passwords.
- Kernel building, boot-image packing, and flashing are **not automated**.
  Keep that workflow on the investigation branches.
- The kernel source is pinned in `kernel/`; do not add boot images or flashing
  automation to production.

## ADB debugging controls

Use the app control receiver, not screenshots, for routine debugging.

```bash
scripts/airdroid-adb.sh status
scripts/airdroid-adb.sh history
scripts/airdroid-adb.sh log
scripts/airdroid-adb.sh follow
```

Control commands:

```bash
scripts/airdroid-adb.sh scan
scripts/airdroid-adb.sh stop
scripts/airdroid-adb.sh accept
scripts/airdroid-adb.sh decline
scripts/airdroid-adb.sh send /sdcard/Download/file.bin <redacted-mac>
scripts/airdroid-adb.sh send-text "hello" <redacted-mac>
scripts/airdroid-adb.sh cancel <transfer-id>
scripts/airdroid-adb.sh pin <redacted-mac>
scripts/airdroid-adb.sh delete /sdcard/Download/AirDroid/file
scripts/airdroid-adb.sh channel 6
scripts/airdroid-adb.sh name "AirDroid K20"
scripts/airdroid-adb.sh enter
scripts/airdroid-adb.sh exit
```

Implementation details:

- Receiver: `io.moonbase.airdroid/.ControlReceiver`
- Protection: `android.permission.DUMP` (adb shell has it)
- It only works while the app process/controller is alive; the foreground
  service keeps it alive in AirDrop mode.
- Logs are mirrored to logcat:

```bash
adb logcat -d -s AirDroidLog:D '*:S'
adb logcat -d -s AirDroidDebug:D '*:S'
```

- `AirDroidLog` contains timestamped app/helper output.
- `AirDroidDebug` contains status, peers, active transfers, history, and errors.
- Use `scripts/airdroid-adb.sh log` or `follow` instead of screencap whenever
  the information is available in logs.
- Do not print real peer MACs in final user-facing summaries. Use
  `<redacted-mac>` in docs and commits.

## Testing workflow

1. Build and install the app.
2. Ask the user to open AirDrop only when a live iPhone test is ready.
3. Enter AirDrop mode from Settings.
4. Use `scripts/airdroid-adb.sh status` to confirm peers/transfers.
5. Use `scripts/airdroid-adb.sh log` for timestamped progress.
6. For speed issues, compare progress timestamps and byte counts; identify
   gaps before changing code.
7. After the test, exit AirDrop mode and restore Wi-Fi unless the user is
   waiting for another test.
8. Tell the user when AirDrop can be closed.

## AirDrop testing rule

**Always ask the user to open AirDrop on the iPhone when an AWDL ACK,
response, peer-discovery, or other live-iPhone test is needed.**

- Do not assume AirDrop is open, even if it was open in a previous test.
- Do not silently start an iPhone-dependent test; ask explicitly first.
- Specify the exact setting needed, e.g. Control Center → AirDrop →
  **Everyone for 10 Minutes**.
- Do not ask the user to keep AirDrop open during builds or unrelated work.
  Ask again only when the test is ready to run.
- Tell the user when the test is finished and they can close AirDrop.
- Treat any captured iPhone AWDL frames as RX-only evidence unless AirDrop
  was explicitly confirmed open for that specific run.

## Privacy/hardcoding rule

Never commit MAC addresses, BSSIDs, SSIDs, serial numbers, IP addresses,
tokens, or other device-specific identifiers. Use placeholders such as
`<redacted-mac>` and `<redacted-bssid>` in docs. Read device values at runtime;
do not hardcode them in app or helper code.

## Safety/restore rule

After monitor/P2P/driver experiments, restore the stock driver and working
Wi-Fi (`con_mode=0`, no test modules), unless the user is actively waiting
for the next test.

## Debugging notes

- Prefer ADB control and logcat over UI screenshots.
- For transfer stalls, inspect timestamped `SEND_FILE_PROGRESS` /
  `RECEIVE_PROGRESS` lines and compute gaps/rates.
- `OWLY_TX_NO_ACK=1` is set by the app session; do not silently change it.
- The fixed-channel TX behavior lives in the pinned Owly fork. Do not
  reintroduce local patches or an ignored reference checkout.
- Kernel/boot-image failures belong on the investigation branches; do not
  block production app work on kernel automation.
