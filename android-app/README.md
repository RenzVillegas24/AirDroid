# Droidy K20

AirDrop over AWDL for the Xiaomi 14 / K20 Pro family (`raphael`, WCN3990),
built as a **Jetpack Compose + Material 3** Android app with a standalone Rust
radio/AirDrop helper.

- **UI:** Jetpack Compose, Material 3 (`androidx.compose.material3`)
- **Icons:** Material Symbols Rounded vector drawables
- **Kotlin:** 2.1.20
- **Compile / target SDK:** 36
- **Min SDK:** 23
- **Build:** Android Gradle Plugin 8.7.3, Gradle 8.9

## What it does

- discovers AirDrop peers over AWDL and shows them as selectable people cards;
- wakes AirDrop with the Apple Continuity BLE advertisement;
- sends a picked file, or `owly-test.txt`, to the selected peer;
- runs an AirDrop receiver on port `8771` and lists received files;
- keeps a live session log;
- persists the AWDL channel, BLE arm, and device display name;
- extracts and runs the bundled aarch64 helper through KernelSU root.

Transfer path:

```text
Compose UI
  -> Kotlin file picker
  -> /data/local/tmp/owly-app/owly-k20-helper
  -> Owly Native monitor/TAP backend
  -> AWDL -> mDNS -> AirDrop /Discover, /Ask, /Upload
```

The helper deliberately omits Owly's daemon, D-Bus, CLI and Omarchy layer. Only
the K20's internal WCN3990 radio is used; external USB Wi-Fi is out of scope.

The base boot image starts in normal Wi-Fi mode (no `wlan.con_mode=4`).
Use **System → Radio mode → Enter AirDrop mode** when a session is ready.
The app can also reboot into the mode through its `BOOT_COMPLETED` receiver if
the live switch fails.

```text
../workspaces/raphael/boot-images/boot-owly-base-mission.img
```

The older always-monitor image remains a fallback:

```text
../workspaces/raphael/boot-images/boot-owly-monitor-fixed.img
```

The app does not flash boot images.

## Build

Prerequisites:

- JDK 21
- Android SDK platform 36
- Build Tools 35.0.1
- Rust + `aarch64-unknown-linux-musl` target for the helper

The repo pins the Owly helper source as `third_party/owly`. The kernel
submodule is not needed to build the APK.

```bash
# From the repository root.
git submodule update --init third_party/owly

# Builds the helper, copies it into android-app/assets, then builds the APK.
scripts/build-app.sh debug
```

For a release build:

```bash
scripts/build-app.sh release
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/
```

The old `./build.sh` wrapper remains available for a debug-only Gradle build,
but it does not build the Rust helper.

### ARM64 Linux host note

Google's Maven `aapt2` artifact is x86_64. On this ARM64 host, Gradle is
configured to run the official x86_64 `aapt2` through `qemu-user` with a small
x86_64 sysroot. The override is in `gradle.properties`:

```properties
android.aapt2FromMavenOverride=/home/renepi/Android/Sdk/aapt2-qemu/aapt2
```

If the SDK or sysroot moves, update that path. The rest of the Compose
toolchain runs natively on the JVM.

## Kernel / boot detection

The app reads the running kernel release with `Os.uname()` and checks it
against known K20 builds:

- monitor-enabled: `4.14.357-openela-perf-g7a7b4d1b6808` or
  `4.14.357-openela-perf-g2a67a9737583-dirty`
- stock: `4.14.357-openela-perf-g2a67a9737583`

Stock boot is shown as **STOCK KERNEL** in the app bar and on the Send/Receive
pages. Scan, Send, and Start Receiver stay disabled until the monitor boot
image is active.

For an unknown or rebuilt kernel, **Verify with root** reads `/proc/cmdline`
and `/sys/module/wlan/parameters/con_mode`; `wlan.con_mode=4` confirms the
monitor boot. The app never flashes a boot image.

## ADB root debug bridge

The K20's KernelSU grant is scoped to the app process, so `adb shell su` does
not work. `DebugReceiver` runs commands through the app's root context and
publishes the output to `/data/local/tmp/droidy-debug.txt`.

```bash
cd android-app
printf '%s' 'id; uname -a; cat /sys/module/wlan/parameters/con_mode' \
  | ./adb-debug.sh
```

The receiver is included only in debug builds. It generates a random
per-install token under the app's private files directory; `adb-debug.sh` and
`adb-ble.sh` read that token with `run-as`. Set `ADB_SERIAL` explicitly before
using them.

Useful radio checks:

```bash
printf '%s' 'ip -d link show wlan0; ip -d link show wlan_inj0' | ./adb-debug.sh
printf '%s' 'dmesg | grep -iE "Duplicate MAC|wma_inject|monitor vdev" | tail -30' | ./adb-debug.sh
printf '%s' '/data/local/tmp/owly-app/owly-k20-helper scan 12 44' | ./adb-debug.sh
```

The working monitor image for the current K20 state is
`workspaces/raphael/boot-images/boot-owly-monitor-fixed-prev.img`
(`4.14.357-openela-perf-g2a67a9737583-dirty`). It creates
`wlan_inj0` with `<redacted-mac>`; the newer fixed image hit a duplicate-MAC
bug in the hidden vSTA path on this boot.

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.moonbase.droidy/.MainActivity
```

Grant KernelSU root when the first radio action asks for it. The app installs
the helper and monitor tools under `/data/local/tmp/owly-app`.

## Project layout

```text
android-app/
  app/src/main/java/io/moonbase/droidy/   Compose UI + controller
  app/src/main/res/drawable/               Material Symbols vectors
  helper/                                  Rust AWDL/AirDrop helper
  assets/                                  helper + monitor binaries (local)
  build.sh                                 Gradle build wrapper
  build-legacy.sh                          old manual APK prototype build
```

The old View/Kotlin prototype remains in `src/` for reference and is no longer
used by the Gradle build.

## Safety

- Do not run live AirDrop tests without confirming AirDrop is open on the
  iPhone.
- Do not repeatedly switch `con_mode`; it can reset the WCN3990 firmware.
- Use the boot-time monitor image for injection tests and restore the stock
  boot image when finished.
