# K20 changes on Civi 4 Pro (`chenfeng`)

Status: **investigation only**. No Civi kernel or boot image is produced on
this branch.

## Summary

The K20 Pro and Civi 4 Pro share the app/helper/AirDrop layers, but **not the
kernel radio layer**.

| Layer | Reusable on Civi? | Notes |
|---|---|---|
| Compose UI | Yes | Device name and copy are configurable |
| Controller/settings/transfer cards | Yes | No K20-specific APIs |
| Rust helper command/event protocol | Mostly | Device constants, interface names, channel helper and root paths need checking |
| Owly AWDL/AirDrop userspace | Mostly | Same protocol; radio backend assumptions must match |
| K20 kernel monitor/injection patches | **No, not directly** | Different kernel, driver and firmware |
| K20 boot image / pack scripts | **No** | Different device, DTB/DTBO and boot layout |

## Why the K20 kernel changes do not apply directly

The K20 Pro uses:

```text
WCN3990
qcacld family
Linux 4.14
con_mode=4
hidden injection STA (wlan_inj*)
K20 monitor TX patch
```

The Civi 4 Pro uses:

```text
QCA6750 / WCN6750
qcacld-3.0
Linux 6.1 GKI
monitor mode via con_mode=4
different monitor vdev and WMI TX path
signed WPSS firmware
```

The Civi driver already has monitor RX support. The missing piece is a working
monitor TX path that the firmware accepts. The K20 patch is written against a
different driver API and cannot be applied as-is.

## What the Civi research already showed

The local Civi workspace contains monitor captures, QCA module builds,
monitor-TX patches and crash/dmesg evidence. The important findings are:

- `con_mode=4` monitor mode starts and `wlan0` becomes `type monitor`.
- Monitor RX works; AWDL and infrastructure frames were captured.
- The Civi source needs its own monitor TX patch
  (`chenfeng-monitor-tx.patch`) and/or hidden-STA module.
- Monitor TX attempts did not produce a usable AWDL transmit path.
- The WPSS firmware is signed and cannot be replaced with a modified image.
- The Civi qcacld path has its own wiphy/monitor initialization failures and
  firmware capability gaps; these are independent of the K20 fixes.

## What can be reused

These userspace changes are portable in principle:

- peer parsing and availability states
- Auto channel matching from the peer's AWDL sequence
- `/Discover` gate and `/Ask` retry policy
- send cancellation and receive abort
- per-file progress and transfer cards
- mDNS/BLE discovery policy
- ADB control/log protocol

They still depend on the Civi kernel exposing a usable monitor TX path.

## What must be done on Civi

1. Build the Civi `qcacld-3.0` module from the matching source tree.
2. Apply or reimplement the Civi monitor-TX patch against that exact tree.
3. Verify raw 802.11 TX from the monitor interface.
4. Verify the hidden STA/WMI TX path if the monitor vdev cannot transmit.
5. Confirm the firmware accepts management/action frames and does not reset
   the WPSS.
6. Only then try Owly AWDL frames.
7. After AWDL TX/RX works, reuse the helper/app changes and add Civi-specific
   constants:
   - monitor interface name
   - hidden STA name
   - channel setter
   - root helper paths
   - receive directory

## Current recommendation

Do **not** port the K20 kernel patch to Civi. Treat Civi as a separate radio
bring-up:

```text
Civi kernel monitor RX (works)
→ Civi monitor TX / hidden STA (unresolved)
→ AWDL frames (blocked by TX)
→ AirDrop (not started)
```

The K20 Pro remains the production target.

## Internet research

The public qcacld-3.0 work points to two different injection strategies.

### Direct monitor-vdev TX

The older approach adds `ndo_start_xmit` to the monitor netdev and passes raw
radiotap frames into the driver. It works on some older qcacld-2.0/3.0 devices,
but on newer FullMAC firmware the monitor vdev often does not radiate TX at
all. The host sees a successful transmit, while nothing appears on air.

Relevant references:

- [kimocoder/qualcomm_android_monitor_mode](https://github.com/kimocoder/qualcomm_android_monitor_mode)
- [QCACLD-2.0 injection commit](https://gitlab.com/Codeaurora/platform_vendor_qcom-opensource_wlan_qcacld-2.0/-/commit/3d3ad7877d1b4d097b4302a4293a1cbb2478e7bf)

### Hidden STA / self-peer bypass

The newer approach creates an invisible Station vdev and a self-peer inside
the firmware, then rewrites the monitor frame's vdev id to the hidden STA id
before handing it to WMI. The firmware's management-TX check then sees an
authorized vdev instead of the isolated monitor vdev.

References:

- [Achieving Frame Injection on QCACLD-3.0](https://medium.com/h7w/they-said-packet-injection-on-qcacld-3-0-was-impossible-i-proved-them-wrong-588fa55ee702)
- [Loukious packet-injection patchset](https://github.com/Loukious/android_kernel_xiaomi_sm8150/commit/65c6a05ecd9b25ebf0742d39987c6a8a042227f1)
- [Loukious 6.6 GKI WLAN branch](https://github.com/Loukious/konoha-kernel-gki/commits/nethunter-ksunext-upstream/)

The Civi workspace already contains a hidden-STA prototype:

```text
source/monitor-tx/hidden_tx.c
source/monitor-tx/monitor_tx.c
source/patches/chenfeng-monitor-tx.patch
```

This is the right direction for a QCA6750 FullMAC device. The remaining
question is whether this firmware accepts the hidden STA/WMI path.

### Firmware limitation

The Civi WPSS firmware is signed. A modified WPSS image cannot be flashed
without breaking the signature chain. Any solution must stay on the host
driver side and work with the stock firmware.

A similar WCN7851/FullMAC result is documented in
[spiral009/aviumui-wlan-tools](https://github.com/spiral009/aviumui-wlan-tools):
monitor capture works, the kernel TX path works, but the firmware does not
radiate monitor-vdev TX. The hidden STA bypass is the next test.

## ADB inspection

The connected Civi 4 Pro reports:

```text
model:          24069RA21C
device:         chenfeng
kernel:         6.1.138-android14-11
driver:         qca_cld3_qca6750
module:         /vendor/lib/modules/qca_cld3_qca6750.ko
con_mode:       0
```

The module parameter is:

```text
/sys/module/qca_cld3_qca6750/parameters/con_mode
```

The running module reports:

```text
vermagic: 6.1.68-android14-11-maybe-dirty
```

while the running kernel is `6.1.138`. That mismatch is important for any
replacement module build. The replacement must match the running kernel's
vermagic/ABI, not just the source branch version.

`/vendor` and `/vendor_dlkm` are read-only, so a replacement module needs a
KernelSU overlay or a flashed vendor_dlkm image, followed by a reboot.

## Implementation sequence

1. Obtain the Civi kernel and WLAN source matching the running build.
   - Kernel: [MiCode/Xiaomi_Kernel_OpenSource](https://github.com/MiCode/Xiaomi_Kernel_OpenSource), branch `chenfeng-u-oss`
   - WLAN: [MiCode/vendor_qcom_opensource_wlan](https://github.com/MiCode/vendor_qcom_opensource_wlan), branch `chenfeng-u-oss`
2. Build `qca_cld3_qca6750.ko` against the exact running kernel/ABI and confirm
   `vermagic` matches.
3. Start from the hidden-STA prototype and integrate:
   - hidden STA vdev create/start
   - self-peer create
   - monitor TX vdev-id swap
   - raw frame/radiotap handling
   - firmware TX completion handling
4. Deploy through a KernelSU module overlay, keeping the stock module and boot
   image as rollback.
5. Test monitor RX and a raw management-frame TX with a second receiver before
   involving Owly.
6. If raw TX appears on air, verify AWDL PSF/MIF and then AirDrop.
7. Only then adapt the helper constants:
   - monitor interface
   - hidden STA name
   - channel setter
   - root paths
   - receive directory

## Safe 6.1.138 module test

A fresh `owly_hidden_tx.ko` was built against the ACK 6.1.138 tree with the
device config and exact vermagic:

```text
6.1.138-android14-11-g0c3d559bcd85-ab14529422
```

The build used the device `/proc/config.gz` and `/sys/kernel/kheaders.tar.xz`.
The module was kept in `/data/local/tmp`; nothing was written to `/vendor` or
a KernelSU overlay.

Result with normal Wi-Fi (`con_mode=0`):

```text
owly_hidden_tx: wma=... wmi=... qdf_ctx=...
owly_hidden_tx: wlan0 is not radiotap monitor (type=1)
```

The module loaded, resolved its symbols, and failed cleanly at the monitor
check. This confirms the module ABI/vermagic and kprobe symbol lookup are
usable on the running kernel.

Result with monitor mode (`con_mode=4`, channel 149) and the hidden-STA
module loaded:

- the device disconnected immediately
- it rebooted
- no pstore entry was available
- the module was not persisted
- Wi-Fi was restored after reboot

That means the current hidden-STA prototype is **not safe on 6.1.138**. The
hand-built WMI command buffers and offsets were made for the older 6.1.57
prototype. They must not be reused blindly.

## Next safe implementation step

Do not load the raw-offset module again. Move the hidden-STA logic into the
matching qcacld source and use the driver's real structures:

```text
struct vdev_create_params
struct vdev_start_params
struct peer_create_params
struct mgmt_tx_params
```

Build the resulting `qca_cld3_qca6750.ko` against the exact 6.1.138 ABI, then
test in stages:

1. load with normal Wi-Fi and confirm clean failure
2. monitor RX only
3. hidden STA creation without TX
4. one raw management TX
5. AWDL frames
6. AirDrop

The test module and build tree remain in the ignored local workspace. No
device changes were persisted.

## In-tree hidden-STA implementation

The raw-offset kprobe prototype is retired. A new in-tree path was added to
the Civi qcacld source:

- `wlan_hdd_owly_hidden.c` creates a firmware-only STA vdev on the monitor
  channel using `vdev_create_params`, `vdev_start_params`,
  `peer_create_params`, and `vdev_up_params`.
- The monitor netdev now has `.ndo_start_xmit`; it strips the radiotap header
  and sends the frame through `wmi_mgmt_unified_cmd_send()` on the hidden
  vdev.
- The hidden vdev is created after the monitor channel is set and destroyed
  when monitor mode is disabled.
- The hidden MAC is derived from the monitor interface MAC with the local bit
  set; no peer MAC is hardcoded.

The full `qca_cld3_qca6750.ko` was built against the ACK 6.1.138 tree and the
device config with the exact vermagic. Build compatibility needed vendor
`cnss`/`hwid` headers and `-D__ANDROID_COMMON_KERNEL__`; the stripped module
is about 16 MB.

A direct `rmmod`/`insmod` swap of the built module was tested. The module
loaded, but the driver probe emitted a `wiphy_new_nm` warning and failed to
load `WCNSS_qcom_cfg.ini` (`-13`), so Wi-Fi did not start. The stock module was
restored immediately and Wi-Fi is connected again. Direct swapping is not a
valid test path for this driver; the next test must use the KernelSU
boot-time overlay so `firmware_mnt` and the vendor module context are
available.

Remaining work:

1. verify the patched module initializes at boot
2. verify monitor RX
3. verify hidden STA creation without TX
4. send one raw management frame
5. test AWDL PSF/MIF and AirDrop

The test module and build tree remain in the ignored local workspace. No
device changes were persisted.

## Branch contents

This branch contains documentation only. Civi build artifacts and raw
captures remain in the local ignored workspace, not in the production tree.
