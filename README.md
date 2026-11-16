<img src="docs/assets/airdroid-icon.png" width="128" alt="AirDroid">

# AirDroid

AirDrop over AWDL for the Redmi K20 Pro / Mi 9T Pro (`raphael`, WCN3990),
built as a Jetpack Compose Android app with a Rust radio/AirDrop helper.

## What it does

- Discovers nearby AirDrop peers over AWDL.
- Sends files and text to a selected peer.
- Receives AirDrop offers with a foreground dialog or a background
  Accept/Decline notification.
- Tracks sent and received files separately, with per-file progress.
- Supports per-transfer cancellation without stopping the radio or receiver.
- Keeps the radio session in a foreground service while backgrounded.
- Provides ADB control/log commands for debugging.

The K20's internal WCN3990 radio is the target. External USB Wi-Fi is out of
scope.

## Transfer behavior

### Channel selection

Auto is the default and recommended mode:

```text
149 → 44 → 6
```

- Auto prefers 5 GHz.
- Channel 6 is used only as a fallback for 2.4 GHz-only peers.
- Auto reads the peer's advertised AWDL channel sequence and matches a
  supported social channel (`149`, `44`, or `6`) before locking.
- A fixed channel can be selected manually, but the app shows a warning
  because the iPhone may move to a different channel.
- The locked channel is shown in Settings and in the log.

A fixed channel is useful for troubleshooting. It is not the recommended
normal mode.

### Sending and receiving

- Every send passes `/Discover` before `/Ask`.
- The direct AWDL peer address is tried before a cached mDNS address.
- Cancelling a send closes the connection and keeps the session alive.
- Stopping a receive aborts only that request; the receiver keeps listening.
- Named peers stay visible while their mDNS service briefly flaps. A peer is
  removed only after the AWDL peer actually disappears.
- Per-file receive progress comes from the receiver data stream.

## Investigation results

### K20 Pro / WCN3990

- Monitor mode works with `wlan.con_mode=4`; `wlan0` reports type `803`.
- The hidden injection STA (`wlan_inj*`) is created by the kernel patch and
  its address is read at runtime. It is not hardcoded.
- Injected probe requests, AWDL PSF/MIF frames, and peer discovery were
  verified with monitor captures.
- The fixed-channel TX scheduler in the Owly fork removed the observed
  ~11.4-second transmit holes.
- A 20 MB upload completed on 5 GHz channel 149.
- Channel 6 remains available for 2.4 GHz-only operation, but a fixed 6 link
  can stall when the iPhone is actually using 5 GHz. Auto avoids that case by
  matching the peer's advertised supported channel.
- Send cancellation and receive stop were verified against an iPhone on
  5 GHz.

### Civi / `chenfeng`

- The internal QCA6750 path is blocked by signed WPSS firmware and monitor-TX
  behavior.
- The K20 Pro remains the working production target.

### Android app

- Unified Home with Sent/Received tabs and per-file transfer cards.
- Foreground incoming-request dialog and background Accept/Decline
  notification.
- Foreground service keeps the receiver/controller alive while backgrounded.
- Settings for pausing peer discovery and staying visible during transfers.
- ADB control/log interface (`scripts/airdroid-adb.sh`, `AirDroidLog`,
  `AirDroidDebug`).
- Open/Share uses the received directory directly; no cache copy.

Full bring-up logs, monitor/injection tools, captures, and boot-image
experiments are preserved on:

```text
investigation/exclusive
investigation/shared
experiments/investigation-tools
```

## Production layout

```text
android-app/       Android app and Rust helper source
kernel/            Evolution-X kernel_xiaomi_raphael fork (git submodule)
third_party/owly/  AirDroid Owly fork with K20 radio fixes (git submodule)
scripts/           build-app.sh and airdroid-adb.sh
docs/              setup, ADB debugging, release, and transfer notes
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
git clone https://github.com/RenzVillegas24/AirDroid.git
cd AirDroid
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
See [docs/SETUP.md](docs/SETUP.md) for device setup,
[docs/raphael/ADB-DEBUG.md](docs/raphael/ADB-DEBUG.md) for the ADB controls,
and [docs/TRANSFER-PIPELINE.md](docs/TRANSFER-PIPELINE.md) for the transfer
state machine and test matrix.

## Using on a phone

### 1. Flash the AirDroid kernel

The app needs the monitor-mode/injection kernel. A normal K20 kernel will
install the APK but cannot start the AirDrop radio.

Build or obtain the AirDroid kernel first, then flash its boot image. Keep a
backup of the stock boot image.

```bash
adb reboot bootloader
fastboot flash boot boot.img
fastboot reboot
```

The bootloader must be unlocked. If you are not rebuilding the kernel, use a
boot image built from the AirDroid kernel branch.

### 2. Install the app

```bash
adb install -r android-app/app/build/outputs/apk/release/app-release.apk
adb shell am start -n io.moonbase.airdroid/.MainActivity
```

Open the app and grant root when KernelSU asks. The app needs root to switch
the radio between normal Wi-Fi and Transfer mode.

### 3. Enter Transfer mode

In the app:

```text
Settings → Radio mode → Enter Transfer mode
```

This disables normal Wi-Fi, switches the kernel to `con_mode=4`, and starts
the monitor plus hidden injection STA. The app shows the active channel and
nearby peers.

On the iPhone:

```text
Control Center → AirDrop → Everyone for 10 Minutes
```

### 4. Send from Android

1. Wait for the iPhone to appear under **Nearby AirDrop**.
2. Tap the phone.
3. Choose a file or paste text.
4. Press **Send**.
5. Accept the transfer on the iPhone.

Use **Auto** channel selection unless you are troubleshooting. Auto prefers
5 GHz and falls back to 2.4 GHz when needed.

### 5. Receive from iPhone

1. Keep the app in Transfer mode.
2. On the iPhone, choose **AirDrop → AirDroid K20**.
3. Accept the offer on the Android phone.
4. Received files are saved under `/sdcard/Download/AirDroid`.

The transfer card has a **Stop** action for an active receive. It aborts only
that transfer; the receiver stays available.

### 6. Leave Transfer mode

```text
Settings → Radio mode → Exit Transfer mode
```

Normal Wi-Fi returns. If the app is force-stopped while in Transfer mode, the
root watchdog restores Wi-Fi; reboot if the radio is still in monitor mode.

### 7. Useful checks

```bash
scripts/airdroid-adb.sh status
scripts/airdroid-adb.sh log
scripts/airdroid-adb.sh follow
```

See [docs/raphael/ADB-DEBUG.md](docs/raphael/ADB-DEBUG.md) for the full ADB
command set.

## Kernel

- Upstream base:
  [Evolution-X-Devices/kernel_xiaomi_raphael](https://github.com/Evolution-X-Devices/kernel_xiaomi_raphael)
- AirDroid fork:
  [RenzVillegas24/kernel_xiaomi_raphael](https://github.com/RenzVillegas24/kernel_xiaomi_raphael),
  branch `AirDroid/main`
- Submodule: `kernel/`

```bash
git submodule update --init kernel
```

The monitor/injection boot-image workflow is kept on the investigation
branches. The app only switches the already-flashed kernel between normal
Wi-Fi and Transfer mode.

### Kernel build (manual)

Kernel building is **not automated** by `scripts/build-app.sh`. The pinned
submodule is the exact source, but the K20 boot image must be built and packed
manually.

Prerequisites:

- aarch64 kernel toolchain compatible with the 4.14 tree
- the K20 `raphael` defconfig from the AirDroid kernel fork
- Android SDK build-tools (`unpack_bootimg`, `mkbootimg`) or an equivalent
  boot-image packer
- an unlocked bootloader and a stock boot-image backup

Build the kernel:

```bash
git submodule update --init kernel
cd kernel

export ARCH=arm64
export SUBARCH=arm64
export CROSS_COMPILE=aarch64-linux-gnu-

# Use the exact raphael/K20 defconfig shipped in the fork.
ls arch/arm64/configs | grep -i raphael
make O=out ARCH=arm64 <raphael_defconfig>
make O=out ARCH=arm64 -j"$(nproc)"

ls -lh out/arch/arm64/boot/Image.gz
```

Replace `<raphael_defconfig>` with the K20 config name found by the `ls`
command. Do not mix GCC and Clang toolchains in one build.

Pack the boot image:

```bash
# Recommended: use the investigation branch's pack-base-mission.sh with the
# stock boot image and the freshly built Image.gz.
#
# Manual alternative:
unpack_bootimg --boot_img stock-boot.img --out boot-unpacked
# replace boot-unpacked/kernel with out/arch/arm64/boot/Image.gz
# repack with mkbootimg using the stock image's original offsets, ramdisk,
# DTB/DTBO and cmdline.
```

The exact pack/flash scripts and historical configs are kept on the
investigation branches:

```text
investigation/exclusive
investigation/shared
experiments/investigation-tools
```

They are intentionally not part of the production build.

Flash only after checking the boot-image header, ramdisk and DTB/DTBO against
the stock image:

```bash
fastboot flash boot boot.img
fastboot reboot
```

A KernelSU or Magisk module can package the app and userspace helper, but it
cannot supply the monitor-mode kernel patches. Those must be in the boot
image.

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
