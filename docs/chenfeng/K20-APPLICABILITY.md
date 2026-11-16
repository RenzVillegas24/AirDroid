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

## Branch contents

This branch contains documentation only. Civi build artifacts and raw
captures remain in the local ignored workspace, not in the production tree.
