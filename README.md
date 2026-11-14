# Droidy

AirDrop over AWDL for the Redmi K20 Pro / Mi 9T Pro (`raphael`, WCN3990),
built as a Jetpack Compose Android app with a Rust radio/AirDrop helper.

## What it does

- Discovers nearby AirDrop peers over AWDL.
- Sends files and text to a selected peer.
- Receives AirDrop offers with a foreground dialog or a background
  Accept/Decline notification.
- Tracks sent and received files separately.
- Runs the radio session in a foreground service while backgrounded.
- Provides an ADB control/log interface for debugging.

The K20's internal WCN3990 radio is the target. External USB Wi-Fi is out of
scope.

## Production layout

```text
android-app/       Android app and Rust helper source
kernel/            Evolution-X kernel_xiaomi_raphael fork (git submodule)
third_party/owly/  Droidy Owly fork with K20 radio fixes (git submodule)
scripts/           build-app.sh and droidy-adb.sh
docs/              setup and ADB debugging instructions
```

Radio bring-up, monitor/injection experiments, captures, and historical tools
live on the investigation branches:

```text
investigation/exclusive
investigation/shared
experiments/investigation-tools
```

They are intentionally not part of the production tree.

## Build

Prerequisites:

- JDK 21
- Android SDK platform 36
- Rust and the `aarch64-unknown-linux-musl` target
- `git`

```bash
git clone https://github.com/RenzVillegas24/droidy.git
cd droidy
git submodule update --init third_party/owly
scripts/build-app.sh debug
```

The script builds the Rust helper from `third_party/owly`, copies it into
`android-app/assets/`, and builds the APK. The kernel submodule is not needed
to build the APK.

Release build:

```bash
scripts/build-app.sh release
```

The release APK is unsigned. To sign it with a private keystore:

```bash
scripts/sign-release.sh
```

See [docs/RELEASE.md](docs/RELEASE.md) for keystore creation and signing.
See [docs/SETUP.md](docs/SETUP.md) for device setup and
[docs/raphael/ADB-DEBUG.md](docs/raphael/ADB-DEBUG.md) for the ADB controls.

## Kernel

- Upstream base:
  [Evolution-X-Devices/kernel_xiaomi_raphael](https://github.com/Evolution-X-Devices/kernel_xiaomi_raphael)
- Droidy fork:
  [RenzVillegas24/kernel_xiaomi_raphael](https://github.com/RenzVillegas24/kernel_xiaomi_raphael),
  branch `owly-k20`
- Submodule: `kernel/`

```bash
git submodule update --init kernel
```

The monitor/injection boot-image workflow is kept on the investigation
branches. The app only switches the already-flashed kernel between normal
Wi-Fi and AirDrop mode.

## Credits

- [Owly](https://github.com/t4t5/owly) — AWDL/AirDrop Rust stack.
- [Evolution-X-Devices/kernel_xiaomi_raphael](https://github.com/Evolution-X-Devices/kernel_xiaomi_raphael)
  — K20 Pro kernel base.
- [ex18a/qcacld-monitor-injection](https://github.com/ex18a/qcacld-monitor-injection)
  — WCN3990 monitor/injection design.
- [seemoo-lab/opendrop](https://github.com/seemoo-lab/opendrop) and
  [seemoo-lab/owl](https://github.com/seemoo-lab/owl) — AirDrop/AWDL protocol
  references.
- [Jetpack Compose](https://developer.android.com/jetpack/compose) and
  [Material 3](https://m3.material.io/) — Android UI.
