# AirDroid K20

Android AirDrop/AWDL app for the Redmi K20 Pro (`raphael`, WCN3990).

- Kotlin + Jetpack Compose, Material 3
- Compile/target SDK 36, min SDK 23
- Rust helper using the Owly radio/AWDL/AirDrop stack
- Internal WCN3990 radio only; external USB Wi-Fi is out of scope

## Build

Prerequisites:

- JDK 21
- Android SDK platform 36
- Rust and the `aarch64-unknown-linux-musl` target

From the repository root:

```bash
git submodule update --init third_party/owly
scripts/build-app.sh debug
```

The script builds the helper from `third_party/owly`, copies it into
`android-app/assets/`, and builds the APK. The kernel submodule is not needed
to build the APK.

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release:

```bash
scripts/build-app.sh release
```

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.moonbase.airdroid/.MainActivity
```

Grant KernelSU root when the first radio action asks for it.

The running kernel must support `wlan.con_mode=4` and the hidden injection STA.
The kernel fork and boot-image/injection workflow are documented in the
repository root and on the investigation branches.

## Use

1. Open **Settings → Radio mode → Enter Transfer mode**.
2. Open AirDrop on the iPhone when a live test is intended.
3. Select a nearby peer to send a file or text.
4. Incoming offers show a foreground dialog or a background notification.
5. **Settings → Radio mode → Exit Transfer mode** restores Wi-Fi.

## ADB debugging

```bash
scripts/airdroid-adb.sh status
scripts/airdroid-adb.sh log
scripts/airdroid-adb.sh follow
```

All app/helper output is mirrored to logcat tag `AirDroidLog`.

See `docs/raphael/ADB-DEBUG.md` for the full command set.

## Layout

```text
app/            Android app module
helper/         Rust helper source
assets/         generated helper asset (gitignored)
src/main/assets/owly-mode.sh
```

The helper owns monitor/TAP, AWDL, mDNS, TLS, and AirDrop transfers. Kotlin
owns UI state, transfer cards, notifications, pinning, file actions, and
channel policy.
