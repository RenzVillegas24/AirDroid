# K20 Pro (raphael, SM8150/WCN3990) injection results

Date: 2026-11-13

## Device and source

- Device: Xiaomi Mi 9T Pro / Redmi K20 Pro, `raphael` (SM8150, WCN3990)
- Serial: `<redacted-serial>`
- Bootloader: unlocked (`fastboot getvar unlocked` -> `yes`)
- Root: KernelSU-Next legacy v3.4.0, root shell via Termux
- Exact kernel source:
  - Repo: `https://github.com/Evolution-X-Devices/kernel_xiaomi_raphael`
  - Commit: `2a67a9737583b2e6158cd8ef2dc026439bd80e4e`
  - Version: `4.14.357-openela-perf`
- Build config: pulled from the running K20 (`/proc/config.gz`); saved as
  `workspaces/raphael/config/k20-kernel.config`.

## Monitor mode

Monitor mode works with:

```text
wlan.con_mode=4            # boot command line
/sys/module/wlan/parameters/con_mode = 4
/sys/class/net/wlan0/type = 803
```

Putting `wlan.con_mode=4` in the boot image command line is stable. Live
switching via sysfs is unreliable on this firmware; one live switch caused a
firmware reset/reboot. After boot, `ip link set wlan0 up` and
`nl80211-set-channel wlan0 2437` are enough.

The K20's stock driver has no `monitor_mode_channel` sysfs. Use
`scripts/nl80211-set-channel.c` (static aarch64 binary).

## Injection patch

Ported the working WCN3990 hidden-STA-vdev design from
`ex18a/qcacld-monitor-injection` onto the exact K20 source.

- `drivers/staging/qcacld-3.0/core/wma/src/wma_frame_inject.c`
- `drivers/staging/qcacld-3.0/core/wma/inc/wma_frame_inject.h`
- `Kbuild`: add `wma_frame_inject.o`
- `wlan_hdd_main.c`:
  - monitor `net_device_ops` gains `ndo_start_xmit`
  - first TX creates a hidden STA adapter (`wlan_inj0`) through
    `hdd_open_adapter()` + `hdd_start_adapter()`
  - injected radiotap-stripped frames go through
    `wlan_mgmt_txrx_mgmt_frame_tx()` using the hidden STA vdev
  - hidden adapter is revived after SSR
  - monitor open brings up carrier/TX queues
- Build-only include-path fixes were needed in this source tree for the
  camera trace header and IPA header.

The hidden adapter's first TX originally logged `BUG: scheduling while
atomic`, because adapter creation ran in the AF_PACKET xmit path. The source
has since been changed to pre-create the hidden vdev during monitor open
(process/RTNL context); that fixed source has **not yet been rebuilt**.

## Verified radio results

- Five injected probe requests were sent from `wlan0` and captured on the
  K20 monitor on channel 6.
- A captured iPhone AWDL PSF was replayed after rewriting its source and AWDL
  address to the K20; the K20 monitor captured the transmitted frames.
- A captured iPhone AWDL MIF was replayed the same way. During that run a new
  AWDL peer address, `<redacted-mac>`, appeared sending 506/329-byte AWDL
  frames on BSSID `<redacted-mac>`. It was absent from the earlier captures.

Evidence:

- `workspaces/raphael/captures/iphone-awdl.txt` — 86 frames, 69 iPhone AWDL action frames
- `workspaces/raphael/captures/awdl-response2.txt` — 20 K20-written PSFs captured
- `workspaces/raphael/captures/awdl-mif-resp.txt` — 10 K20-written MIFs and the new `aa:19` peer
- `workspaces/raphael/captures/k20-inject-now.txt` — hidden `wlan_inj0` creation + probe TX
- `workspaces/raphael/captures/cap-probe.txt` — five injected probe frames captured

## Boot images

- `workspaces/raphael/boot-images/boot-current.img` — original stock K20 boot image (KernelSU-Next)
- `workspaces/raphael/boot-images/boot-owly-monitor.img` — injection kernel + `wlan.con_mode=4`
  (the image tested above; still has the one-time atomic warning)
- `workspaces/raphael/boot-images/k20-Image-atomic-old.gz` — compressed kernel from that image
- `workspaces/raphael/config/k20-kernel.config` — exact device config

The K20 is currently restored to `boot-current.img`, with normal Wi-Fi up and
`con_mode=0`. Flash `boot-owly-monitor.img` again for injection tests.

## Next

1. Rebuild the source with the monitor-open pre-creation fix and verify no
   `scheduling while atomic`.
2. Run the Owly/AirDrop userspace stack on the K20 (or install the user's
   Android Owly app if one exists). The radio TX/RX path is no longer the
   blocker.
3. Reopen AirDrop only for the controlled iPhone discovery/transfer test.

## Cleanup performed

Removed the Civi/GKI build trees, downloaded tarballs, duplicate `.ko`
artifacts, `owly-module`, and the `/tmp/plume_stage_*` caches. The K20 source
tree was cleaned to source/config; unrelated user projects in `Codes/` were
left untouched.

## Data-path result (2026-11-13)

The userspace side now follows Owly's actual send path: fresh BLE advert,
direct AWDL peer link-local addresses, mDNS only as a secondary source, and
`/Discover` retries against port 8770. The K20 sees the iPhone
(`<redacted-mac>`) and the Arch Owly host (`<redacted-mac>`) as valid
AWDL peers.

What works:

- The K20's BLE AirDrop advert is on air and byte-correct. A host `btmon`
  capture showed `05 12 40 <tag> 00000000 03 0000000000000000 00`, connectable,
  at the 100 ms interval.
- mDNS RX works. With the Arch host running
  `owly receive --port 8770 --accept --dir /tmp/owly-host-test`, the K20 found
  its service at `<redacted-link-local>`; it also found the iPhone at
  `<redacted-link-local>`.
- AWDL peer discovery and iPhone peer RSSI work.
- `sender.discover` reaches neither receiver: every TCP/TLS attempt to both
  the Arch receiver and the iPhone timed out for 120 s. The Arch receiver
  received no connection and no file.

The current kernel injection patch is the blocker. Its monitor xmit path
(`hdd_mon_hard_start_xmit` -> `wma_inject_frame_via_vdev`) sends every frame
through `wlan_mgmt_txrx_mgmt_frame_tx`, the management-frame path. Management
injection (probes, AWDL PSF/MIF) is proven, but IPv6/TCP data frames are not
carried reliably. The K20 monitor also has no station/IBSS cell ACKing the
phone's unicast data, unlike Owly's supported shared or exclusive modes.

Next kernel work, in order:

1. Add a real 802.11 **data** TX path for frames from `awdl0`, or use an
   IBSS/hidden-AP vdev for data instead of the management TX path.
2. Provide ACK coverage for the AWDL address, for example a hidden STA/IBSS
   vdev sharing the AWDL MAC, as Owly does in shared/exclusive mode.
3. Re-run the Arch receiver test; only move back to the iPhone once a complete
   `/Discover` and `/Upload` succeeds against the host.

## Device name and TLS observation (2026-11-13)

The AWDL peer table's `name` is a UUID, not the phone's display name. The
iPhone carries `iPhone` in its AWDL Service Response TLV (`dnm=iPhone`);
the Android helper now sniffs that TLV and logs:

```text
AWDL name: <redacted-mac> = iPhone
```

The selected device is correct. In the same run, the K20 sent its TCP SYN to
`<redacted-mac>`, the iPhone replied with a TCP SYN-ACK, and the K20 sent
an ACK followed by a TLS ClientHello. The iPhone sent no TCP ACK and no TLS
ServerHello. This is an injected data-path/ACK failure after the first
handshake exchange, not a wrong-peer selection or a missing phone name.

## Hidden STA / AWDL MAC alignment (2026-11-13)

The hidden vSTA MAC is device- and boot-specific and must not be hardcoded.
The driver allocates it from the derived interface-address pool. The helper
opens the monitor first, reads the actual address from
`/sys/class/net/wlan_inj*/address`, and configures `awdl0` with that address.

The generic interface pool is not safe for this: it can return the monitor's
provisioned MAC, and `hdd_open_adapter()` correctly rejects it:

```text
Duplicate MAC addr: <redacted-mac> already exists
wma_inject: hdd_open_adapter failed
```

The corrected kernel requests a derived-pool address instead. A prior attempt
to pin the hidden vSTA to a hardcoded AWDL MAC was invalid and was reverted.

A local probe check after boot shows `wlan0` monitor type 803 and the hidden
`wlan_inj*` interface present. The helper prints `INJECT_MAC` and uses the same
address for the AWDL node. No AirDrop/iPhone transfer is attempted by this
check.

## First successful K20 AirDrop upload (2026-11-13)

K20 to the Arch Owly receiver succeeded. No iPhone was involved.

Working configuration:

- K20 monitor/AWDL channel: **44** (5220 MHz, 5 GHz)
- K20 AWDL and hidden STA MAC: **<redacted-mac>**
- K20 link-local: `<redacted-link-local>`
- Arch receiver: `owly receive --port 8770 --accept --dir /tmp/owly-host-test`
- Arch `awdl0`: `<redacted-mac>` / `<redacted-link-local>`
- Arch neighbor: `<redacted-link-local> lladdr <redacted-mac> dev awdl0`
- UFW: allow TCP 8770 in on `awdl0` (the existing rule only allowed `wlan0`)

K20 helper output:

```text
receiver answered on attempt 1 via peer: "Arch-Owly-host"
sending to "Arch-Owly-host" at [<redacted-link-local>%36]:8770
receiver accepted; uploading...
progress 152/152 bytes
DONE: Delivered
```

The AWDL peer, TCP/TLS, `/Discover`, `/Ask` and `/Upload` path now works
against a controlled receiver at 5 GHz channel 44.

## First successful K20 -> iPhone AirDrop transfer (2026-11-13)

With the Arch receiver removed and AirDrop open on the iPhone, the same K20
configuration delivered `owly-test.txt` to `iPhone`:

```text
receiver answered on attempt 1 via peer: "iPhone"
sending to "iPhone" at [<redacted-link-local>%37]:8770
receiver accepted; uploading...
progress 152/152 bytes
DONE: Delivered
```

Working configuration: 5 GHz channel 44, the driver-provided hidden-STA MAC,
iPhone `<redacted-mac>`. The earlier TLV display sniffer printed
`iPhone`; `/Discover` returned the correct `iPhone`, which was the name
used for the transfer. The helper now reads the AWDL Service Response TXT
record instead of guessing the name length.
