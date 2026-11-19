# Setup

This is the production setup for the K20 Pro AirDrop app. The monitor/injection
bring-up history and boot-image tooling live on the investigation branches.

## 1. Clone and submodules

```bash
git clone https://github.com/RenzVillegas24/CrossPair.git
cd airdroid
git submodule update --init third_party/owly
```

Only `third_party/owly` is needed to build the APK. The kernel submodule is
needed only if you are rebuilding the kernel/boot image:

```bash
git submodule update --init kernel
```

## 2. Build the APK

```bash
scripts/build-app.sh debug
```

or:

```bash
scripts/build-app.sh release
```

The script builds the Rust helper and copies it into `android-app/assets/`.
Do not add the helper binary to Git.

On an ARM64 Linux host, pass the local qemu `aapt2` explicitly:

```bash
AAPT2_OVERRIDE="$HOME/Android/Sdk/aapt2-qemu/aapt2" scripts/build-app.sh debug
```

On x86_64 hosts, the normal Android Gradle Plugin `aapt2` is used. No absolute
path is committed in `gradle.properties`.

## 3. Install

```bash
adb install -r android-app/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.moonbase.airdroid/.MainActivity
```

Grant KernelSU root when the first radio action asks for it.

## 4. Kernel requirements

The running kernel must support:

- `wlan.con_mode=4` monitor mode
- the hidden injection STA (`wlan_inj0`)
- the monitor TX path used by Owly

The CrossPair kernel fork is pinned in `kernel/`:

```text
RenzVillegas24/kernel_xiaomi_raphael
branch: CrossPair/main
```

### Kernel build status

Kernel building is **not automated** in the production tree.
`scripts/build-app.sh` builds only the Rust helper and the APK. It does not
build `Image.gz`, pack a boot image, or flash anything.

The pinned `kernel/` submodule provides the exact kernel source. Building a
bootable K20 image still requires manual work:

1. an aarch64 kernel toolchain compatible with the 4.14 tree;
2. the K20 kernel config;
3. unpacking the stock boot image to obtain its ramdisk and DTB;
4. building `arch/arm64/boot/Image.gz`;
5. packing the image with `mkbootimg`;
6. flashing it manually.

The boot-image pack/flash scripts and the historical config/artifacts are kept
on the investigation branches (`investigation/exclusive`,
`investigation/shared`, `experiments/investigation-tools`). They are not part
of the production build and are not run by CI.

### Release signing

`scripts/build-app.sh release` builds an **unsigned** release APK. Sign it with
a private keystore:

```bash
scripts/sign-release.sh
```

See [RELEASE.md](RELEASE.md) for keystore creation, signing, and verification.

## 5. Radio mode

In the app:

```text
Settings -> Radio mode -> Enter Transfer mode
```

The live switch disables Wi-Fi, sets `wlan.con_mode=4`, and waits for the
monitor/hidden-STA interfaces. To restore normal Wi-Fi:

```text
Settings -> Radio mode -> Exit Transfer mode
```

## 6. ADB control and logs

```bash
scripts/crosspair-adb.sh status
scripts/crosspair-adb.sh log
scripts/crosspair-adb.sh follow
```

All app/helper output is mirrored to logcat:

```bash
adb logcat -d -s AirDroidLog:D '*:S'
```

See [ADB-DEBUG.md](raphael/ADB-DEBUG.md) for the full command set.

## Privacy

The app and docs do not hardcode MAC addresses, BSSIDs, SSIDs, serials, IPs,
or tokens. Device values are read at runtime and only appear in local debug
output.
