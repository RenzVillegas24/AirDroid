# Internal-radio AWDL on Xiaomi 14 Civi (`chenfeng`, QCA6750)

Status: **not working yet**. This document records every path tried, the exact
driver/firmware blockers, and the remaining ways to make the internal radio
usable for Owly/AirDrop.

> **Correction (user clarification):** AirDrop was **not opened** during the
> tests described below. Any statement that an AirDrop-open ACK test was
> performed is unverified. The monitor did see iPhone AWDL action frames.
> Later controlled AirDrop-open runs are recorded in sections 24-26
> (monitor baseline, P2P TX/ACK, and time slice).

## 1. Goal and constraints

- Target: Xiaomi 14 Civi / Civi 4 Pro, `chenfeng`, SM8635, EvolutionX 12.3
  (Android 17, LineageOS-based), KernelSU root.
- Radio: internal Qualcomm **QCN6750 / WCN6750** (`qca_cld3_qca6750`).
- **No external USB Wi-Fi adapter.**
- Owly needs a monitor interface that can transmit raw 802.11 frames, and the
  QCA6750 firmware must ACK unicast AWDL frames sent to an iPhone.

## 2. Hardware/software facts

- Kernel: `6.1.138-android14-11-g0c3d559bcd85-ab14529422`.
- Stock vendor module: `/vendor_dlkm/lib/modules/qca_cld3_qca6750.ko`
  - banner `v5.2.1.91G`
  - vermagic `6.1.68-android14-11-maybe-dirty SMP preempt mod_unload modversions aarch64`
  - coresize `11112448`
  - 490 `__versions` CRCs
- Stock monitor RX works: `con_mode=4`, `wlan0` type `803`
  (`IEEE80211_RADIOTAP`); `tcpdump` captured beacons, ACKs, RTS/CTS, BA.
- Kernel module signing is not forced; unsigned modules with vermagic 6.1.57
  and 6.1.68 both load.
- AVB/verity: `ro.boot.veritymode=enforcing`, `ro.boot.flash.locked=1`,
  `ro.boot.verifiedbootstate=green`; `/vendor_dlkm` is read-only. Do not
  modify the partition. Early bind-mount is the supported route.
- `/metadata` is writable and available before `vendor.modprobe`.
- KernelSU v3.3.0 supports module `initrc/` injection, but **not** preinit
  (`ksud module preinit` unrecognized).
- The early initrc service runs at ~1.2 s; `vendor.modprobe` starts at ~1.4 s;
  qca loads at ~2.5 s. This is enough to bind-mount a replacement qca module
  before the stock module is loaded.

## 3. Driver builds

### 3.1 5.2.1.86B (chenfeng source)

- Source: MiCode `vendor_qcom_opensource_wlan`, branch `chenfeng-u-oss`.
- Built with the 6.1.57 chenfeng kernel headers and stock 492 symbol CRCs.
- Monitor TX patch adds a branch in `__hdd_hard_start_xmit` that strips the
  radiotap header and calls `ucfg_dp_start_xmit(skb, adapter->deflink->vdev)`.
- `PANIC_ON_BUG` was initially enabled; rebuilt with `PANIC_ON_BUG=n` and
  `WLAN_WARN_ON_ASSERT=n`.
- Result: loads cleanly via early bind (`coresize=10678272`), but the phone
  resets shortly after boot even with Wi-Fi disabled and no `rmmod`.

### 3.2 5.2.1.91G (flourite source)

- Source: MiCode `vendor_qcom_opensource_wlan`, branch `flourite-v-oss`
  (`QWLAN_VERSIONSTR "5.2.1.91G"`; exact stock version).
- Applied the monitor-TX hunk, disabled the WALT CPU-affinity Kbuild block,
  added the `hwid.h` stub include.
- Built/stripped:
  - `/home/renepi/Codes/qca_cld3_qca6750.91g.stripped.ko`
  - banner `5.2.1.91G; cld:; cmn:; dev:qca6750`
  - vermagic stock 6.1.68 string
  - 463 undefined symbols, all covered by `vendor-symvers.txt`
  - coresize `10702848`
- **This is the first stable patched-driver build.**
  - Normal mode: stayed up >3 minutes.
  - Monitor RX: `con_mode=4` worked; `wlan0` type 803; 45 lines of
    beacons/ACKs/RTS/CTS/BA captured on channel 36.
  - Monitor mode can still reset during vdev start/teardown; the patched
    monitor TX path resets on the first frame.

## 4. Loading path

### 4.1 KernelSU post-fs-data bind mount

- `post-fs-data.sh` runs after init has already loaded the stock qca module.
  The bind mount succeeds but does not replace the running driver.
- Direct `rmmod qca_cld3_qca6750` after boot is dangerous; early swap via a
  post-fs-data hook loaded the patched driver but the 86B version reset.

### 4.2 Early initrc bind mount (works)

- Module `/data/adb/modules/owly-qca6750/initrc/owly-qca-early.rc`:
  - service `owly-qca-early`
  - `before vendor.modprobe`
  - `seclabel u:r:ksu:s0`
  - `on early-init: start owly-qca-early`
- Script `/metadata/owly/early-mount.sh`:
  - one-shot flag `/metadata/owly/armed`
  - `chcon u:object_r:vendor_file:s0 /metadata/owly/qca_cld3_qca6750.ko`
  - `mount --bind` over `/vendor_dlkm/lib/modules/qca_cld3_qca6750.ko`
  - logs to `/metadata/owly/early.log`
- SELinux detail: the metadata file is `metadata_file`; `vendor_modprobe`
  cannot read it. Relabel to `vendor_file` before the bind mount.
- This path loaded the 91G module before `vendor.modprobe`; the service log
  showed the patched coresize and md5.

## 5. Root command and logging tooling

- `scripts/adb-root.sh` runs commands without Termux via a rootd initrc
  service:
  - host writes `/data/local/tmp/owly-root2-cmd.sh`
  - touches `owly-root2-go`
  - rootd executes it and writes stdout/exit code
- `/metadata/owly/rootd2.sh` is started by `initrc/owly-rootd.rc` at
  `post-fs-data` and at `sys.boot_completed=1`.
- `owly-dmesg-log` runs from early-init and writes per-boot:
  - `/metadata/owly/dmesg-qca-<boot_id>.log`
  - `/metadata/owly/dmesg-tail-<boot_id>.log`
  - shell-readable copies in `/data/local/tmp`
- This captured the 91G monitor-mode reset timeline (no panic/oops; silent
  reset after monitor vdev start).
- These are temporary development helpers; remove when done.

## 6. TX paths tested

| Path | Result | Evidence |
|---|---|---|
| Stock monitor RX | works | tcpdump captured real frames |
| 91G early-bind normal mode | stable | >3 min uptime, no reset |
| 91G monitor mode (`con_mode=4`) | RX works; vdev start/teardown can reset | `iw` type monitor; tcpdump 45 lines |
| Patched monitor TX (`ucfg_dp_start_xmit`) | resets phone | dmesg ends after monitor vdev start / first socket; no panic |
| Station mgmt TX, probe/public action | accepted; no ACK status | `NL80211_CMD_FRAME` command accepted, `FRAME_TX_STATUS` timeout |
| Station mgmt TX, Apple AWDL vendor action | rejected or no status | qcacld validation/peer path |
| Station off-channel mgmt TX | `EINVAL` | 2437 MHz while associated/up |
| Monitor-interface mgmt TX | `EOPNOTSUPP` | cfg80211 rejects `NL80211_CMD_FRAME` on monitor |
| P2P-GO mgmt TX, public action | accepted; no ACK status | `p2p0` on channel 6; no TX status |
| P2P-GO mgmt TX, Apple vendor action | `EINVAL` | no P2P peer for iPhone AWDL MAC |

### 6.1 Why P2P vendor action returns EINVAL

`components/p2p/core/src/wlan_p2p_off_chan_tx.c`:

- `p2p_mgmt_tx()` looks up a peer for the frame's DA (iPhone AWDL MAC), then
  for the local SA. The iPhone is not a P2P peer, so no peer exists.
- It only falls back to the vdev's own/self peer for NAN or
  `tx_ctx->rand_mac_tx`; otherwise it returns `QDF_STATUS_E_INVAL`.
- The Apple AWDL vendor action (`0x7f`, OUI `00:17:f2`) does not
  automatically take the NAN/rand_mac path, so it hits the no-peer error.
- A 26-byte public action is accepted, but the P2P TX confirmation callback
  is only wired to P2P peers; no `FRAME_TX_STATUS`/ACK is reported.

### 6.2 Interface-combination limits

`iw phy phy0 info` valid combinations include monitor<=2, but **not**
managed+monitor. So we cannot capture ACK on a monitor interface while the
station/P2P interface transmits. The monitor interface is exclusive.

## 7. Real iPhone AWDL capture

With an iPhone AirDrop sheet open, stock monitor RX captured AWDL PSF frames:

- iPhone AWDL address: `<redacted-mac>`
- AWDL BSSID: `<redacted-mac>`
- Channels: **6** (2437 MHz) and **149** (5745 MHz).
- Channel-6 frame: 351 bytes; channel-149 frame: 945 bytes carrying AirDrop
  service TLVs.
- Body starts `7f 00 17 f2 08 10 00 ...`: Apple vendor action, AWDL type 8,
  version 0x10, subtype 0 (PSF).

We built a channel-6 **P2P GO** (`p2p0`, `DIRECT-ow`, frequency 2437) so we
could transmit on the AWDL social channel without monitor mode or off-channel
TX. The group formed, `p2p0` was `type P2P-GO` on channel 6.

## 8. Remaining methods, ranked

### A. Patched 91G qca with P2P self-peer fallback (current next step)

Patch `p2p_mgmt_tx()` to fall back to the vdev's own peer whenever no peer is
found, not only for NAN/rand_mac_tx. Then:

- load the 91G module via early bind,
- create a P2P GO on channel 6,
- send the captured AWDL PSF (unicast to iPhone and broadcast) from `p2p0`,
- register action frames; wait for the iPhone's PSF/MIF response or ACK.

This is the least invasive way to bypass the `no valid peer` rejection without
touching the monitor TX crash path. It may still fail if the firmware refuses
to transmit an Apple vendor action or if the iPhone filters frames from an
unknown AWDL address.

**Latest result:** the peer-fallback 91G module builds and loads, but its
**station interface does not start** on this ROM:

```text
Wi-Fi is disabled
CMD_STA_START_FAILURE
CMD_RECOVERY_RESTART_WIFI ... WifiNative Failure
```

The qca firmware becomes ready and the driver loads, but Android cannot bring
up the station/P2P interface with this build. The peer fallback itself is not
the cause. The stock-profile (`pineapple_gki_qca6750`) build with FTM disabled
produces the same failure, so the profile/FTM was not the root cause.

The qca dmesg showed a PSOC idle-shutdown timer firing at ~17.2 s, and
`hdd_psoc_idle_timer_start` uses `hdd_ctx->config->iface_change_wait_time`
from the INI key `gInterfaceChangeWait`. An early bind-mount of a modified
`WCNSS_qcom_cfg.ini` with `gInterfaceChangeWait=0` was tested. The bind
succeeded (`config_bind_rc=0`, file visible in the vendor path), but station
start **still failed**:

```text
12:16:23.5  CMD_STA_START_FAILURE
12:16:23.5  CMD_RECOVERY_RESTART_WIFI ... WifiNative Failure
12:16:26.8  CMD_STA_START_FAILURE again
17.3s       hdd_psoc_idle_timeout_callback
```

The station failure happens **before** the PSOC idle timeout, so the idle race
was not the cause. The source-built 91G driver is effectively monitor-only on
this ROM: it loads and receives, but its station datapath does not come up.
The stock-profile/FTM/config variations did not change this.

Until a driver build can start the station interface, the peer-fallback/P2P
test cannot run. Remaining paths are now:

1. **Binary-patch or kprobe-hook the stock 91G module** (keeps the working
   station/P2P path) to bypass the P2P peer lookup for AWDL frames.
2. Build a custom kernel / qca module from the exact stock vendor config (not
   just the public profile), hoping the station datapath matches.
3. Stop and accept the internal-radio path is not viable on this ROM.

### B. Patch the station mgmt-TX path / off-channel logic

- Use the station vdev to send the AWDL action while connected on channel 36.
- Off-channel currently returns `EINVAL`; may require patching
  `wlan_cfg80211_mgmt_tx`/policy-manager guards or sending on a channel-6 AP.
- Needs an AP on channel 6 (router change, iPhone hotspot, or phone AP).

### C. AP mode / hostapd

Create an AP on channel 6 and use `NL80211_CMD_FRAME`/hostapd action TX. The
driver’s AP mgmt path may have the same peer validation, and Android lacks a
simple softap CLI on this ROM. Untested.

### D. QCA vendor/testmode/FTM raw TX

Research QCA vendor subcommands and FTM interface for a raw management-frame
TX path that bypasses cfg80211 qcacld action validation. No evidence yet that
the firmware supports arbitrary monitor-style injection.

### E. Binary-patch the stock 91G module

The source build is easier to iterate; use binary patching only if the exact
source build can’t be made to work. The peer fallback is a small code change;
source build is preferred.

### F. Custom kernel

A custom kernel does not by itself solve the two real blockers:

1. qcacld host action-frame validation/peer lookup;
2. whether the QCA6750 firmware ACKs injected monitor frames.

It may be needed later to embed a patched qca driver or pstore/WMI tracing,
but it is not the first fix.

### G. External USB adapter

Technically the most likely success, but ruled out by the user. Keep as a
fallback only.

## 9. Current phone state

- Stock qca driver loaded, Wi-Fi connected; phone stable.
- Patched 91G module and early-bind scripts remain in `/metadata/owly` and the
  KernelSU module, but the module is **dormant** unless
  `/metadata/owly/armed` is present.
- iPhone AirDrop can be closed; no further test can run until the next patched
  driver boot.

## 10. Artifacts

- `scripts/build-qca.sh` — build 91G/86B patched module.
- `scripts/finalize-qca.sh` — strip, vermagic, symbol check.
- `scripts/package-ksu-module.sh` — package KernelSU module.
- `scripts/early-mount.sh` — early bind/chcon service.
- `scripts/owly-qca-early.rc` — initrc early service.
- `scripts/adb-root.sh` — root commands over ADB.
- `scripts/awdl-capture.sh` — monitor AWDL capture.
- `scripts/nl80211-tx.c` — mgmt TX/ACK test.
- `scripts/nl80211-send-awdl.c` — send a captured frame on/off channel.
- `scripts/inject-probe.c` — raw monitor probe injector.
- `docs/INVESTIGATION-LOG.md` — chronological evidence.
- `docs/RECOVERY.md` — bootloop recovery.

## 11. Stock driver + runtime kprobe: current viable method

This is the first path proven to make the QCA6750 firmware transmit an AWDL
frame **without replacing the stock driver**:

1. Create a stock P2P-GO on a channel the iPhone AWDL is using, e.g. `p2p0`
   on channel 6.
2. Generate the AWDL PSF with:
   - `DA` = iPhone AWDL MAC,
   - `SA` = `p2p0` MAC,
   - `BSSID` = `p2p0` MAC (required by cfg80211 for a non-public action on
     a P2P-GO; using the iPhone AWDL BSSID is rejected with `EINVAL` before
     the driver).
3. Load `peer-hook/owly_peer_hook.ko`, which:
   - kretprobes `p2p_get_frame_info` and changes the parsed `action_type` so
     the P2P SAP/GO validation does not drop the Apple vendor action;
   - kprobes `wlan_objmgr_get_peer` and redirects the iPhone AWDL MAC lookup
     to the P2P self-peer, so the P2P TX path accepts the frame.
4. Arm the hook with target/self MACs and send with
   `ONCHANNEL=1 owly-nl80211-send-awdl p2p0 <frame> <freq>`.
5. Watch the firmware TX confirmation (`wlan_p2p_action_tx_cnf_callback`):
   - `status=0` = ACKed,
   - `status=3` = `WMI_MGMT_TX_COMP_TYPE_COMPLETE_NO_ACK` (transmitted,
     receiver did not ACK).

Proven result: the 351-byte PSF reaches `p2p_execute_tx_action_frame` ->
`wlan_mgmt_txrx_mgmt_frame_tx` -> firmware. With the iPhone AirDrop sheet
open and AWDL active on channel 6, a 100-frame burst produced firmware
confirmations with `status=0`
(`WMI_MGMT_TX_COMP_TYPE_COMPLETE_OK`) for cookies 256, 257, 267, and 268:

```text
owly_peer_hook: ACK status=0 cookie=256 len=351
owly_peer_hook: ACK status=0 cookie=257 len=351
owly_peer_hook: ACK status=0 cookie=267 len=351
owly_peer_hook: ACK status=0 cookie=268 len=351
owly_peer_hook: disarmed, hits=200
```

**This proves the QCA6750 transmitted the AWDL PSF and the iPhone ACKed it.**
The host-side validation and peer blockers are solved.

Next functional step: make the iPhone's AWDL stack accept the phone as an
AWDL peer. The current frame is ACKed at the MAC layer but uses `SA`/`BSSID`
= `p2p0`, so the iPhone may discard it above the MAC. Options are a
kprobe-time rewrite of `SA`/`BSSID` to an AWDL identity, or a proper AWDL
virtual interface. After that, implement discovery/service exchange and the
Owly daemon/UI.

## 12. AP vdev with the fixed AWDL BSSID

The stock driver supports AP mode. A real AP vdev can be created with the
fixed AWDL BSSID, avoiding the P2P-GO vdev's BSSID:

```sh
ip link set wlan0 down
iw dev wlan0 interface add ap0 type __ap
ip link set ap0 up
```

Hostapd config used:

```ini
interface=ap0
driver=nl80211
ssid=owly-test
hw_mode=g
channel=6
bssid=<redacted-mac>
ignore_broadcast_ssid=1
auth_algs=1
wpa=0
ctrl_interface=/data/vendor/wifi/hostapd/owly-ctrl
ctrl_interface_group=wifi
```

The AP came up as:

```text
addr <redacted-mac>
type AP
channel 6 (2437 MHz)
```

With the station up, AP+STA was forced to channel 36; bringing `wlan0` down
first is required to get the AP on channel 6. The AP path was tested:

- `SA` patched to `p2p0` after cfg80211: every frame returned `status=1`
  (`DISCARD`), so the AP firmware enforces the AP interface address as SA.
- `SA=BSSID=AWDL`: frames transmitted but returned `status=3`
  (`COMPLETE_NO_ACK`); the iPhone did not accept them.

## 13. Valid AWDL slave PSF and RX limits

The previous frame files were not raw captures; blind MAC replacement had
also rewritten the AWDL TLVs. The original PSF was extracted from
`owly-awdl6.pcap` and a valid slave frame was built:

- 802.11 `SA = <redacted-mac>`, `BSSID = <redacted-mac>`,
  DA = iPhone for unicast or broadcast for discovery.
- Sync params: master address = iPhone.
- Election v1/v2: top master/sync = iPhone; master metric 1280;
  self metric 1281; distance 1; self counter 0.
- Data path state: AWDL address = p2p0.

Results with AirDrop open:

- Unicast PSF: transmitted, no ACK.
- Broadcast PSF: 50/50 TX complete (`status=0` is broadcast completion,
  not an ACK); no cfg80211 RX action.
- RX probes at `p2p_process_rx_mgmt` and
  `tgt_mgmt_txrx_process_rx_frame` saw **zero** AWDL frames delivered to the
  P2P or AP vdev while listening on channel 6.
The RX-only retest was repeated immediately after reopening AirDrop:
`rx2_all=0` on P2P-GO and `rx2_all=0` on an AP vdev with BSSID
`<redacted-mac>`. This is a firmware/vdev RX-filter result, not a
cfg80211 registration issue: the probe sits at
`tgt_mgmt_txrx_process_rx_frame`, before the P2P non-P2P filter.

Monitor coexistence test:

```sh
iw dev wlan0 interface add mon0 type monitor
# while p2p0 active: command failed: Invalid argument (-22)
```

Stock monitor RX only works through `con_mode=4`, which replaces the normal
Wi-Fi vdevs. Monitor RX therefore cannot coexist with P2P TX.

## 14. Why normal-vdev AWDL RX fails, and the real remaining options

Source probes settled the RX path:

- `wma_mgmt_rx_process` scanning WMI mgmt RX buffers for the Apple OUI: **0**
- `tgt_mgmt_txrx_process_rx_frame`: **0**
- `p2p_process_rx_mgmt`: **0**
- PE `lim_process_action_frame`: **0**

The PE has a second block: vendor-specific action frames are forwarded only
when `DA == session->self_mac_addr`, so broadcast AWDL PSFs would be dropped
there too. But the WMA result shows the frames never reach the host at all:
the FullMAC firmware is filtering AWDL frames out for normal vdevs.

A kprobe rewrite of PE broadcast DA was attempted; it caused a phone reset
during the AP test. The phone recovered to stock; that unsafe probe was
removed from the module.

External research:

1. **WMI_MGMT_TX vdev-type check:** the firmware accepts management TX only
   for vdev type AP (2) or 6. Monitor vdevs are dropped. The 2026
   “Achieving Frame Injection on QCACLD-3.0” writeup creates a hidden STA
   vdev with a self-peer and remaps monitor TX vdev_id to it
   (`Loukious/android_kernel_xiaomi_sm8150`, commit
   `65c6a05ecd9b25ebf0742d39987c6a8a042227f1`).
2. **FullMAC counter-result:** `spiral009/aviumui-wlan-tools` on
   WCN7851/SM8750 reports monitor capture works but monitor TX reaches
   `ndo_start_xmit` and is **not radiated by the FullMAC firmware**.
3. **NAN** accepts only public action/auth frames to the NAN cluster, not
   Apple AWDL; not an RX workaround.
4. Monitor + P2P coexistence on chenfeng returns `EINVAL`; monitor only via
   `con_mode=4`, which removes normal vdevs.

Ranked remaining paths:

- **A. Hidden STA/AP vdev + monitor TX** — most technically complete, but
  needs a custom kernel/driver, hidden WMI vdev + self-peer, vdev_id remap,
  and may still fail on WCN6750 FullMAC.
- **B. Stock-module monitor TX via runtime `ndo_start_xmit` hook** — easier,
  but the firmware vdev-type check likely drops monitor TX unless combined
  with A.
- **C. Firmware patch (`wlanmdsp.mbn`)** — allow monitor TX / AWDL RX;
  highest risk, requires firmware load control.
- **D. Time-sliced AP/P2P TX + monitor RX** — only for discovery/proof, not
  practical data transfer.
- **E. External USB adapter** — ruled out by the user.
- **F. Stop at the MAC-level TX/ACK proof.**

Artifacts: `awdl_iphone_raw.bin`, `awdl_slave_cfg_unicast.bin`,
`awdl_slave_cfg_broadcast.bin`; hostapd config under
`/data/vendor/wifi/hostapd/owly.conf`. The hook module's PE write probe has
been removed; the phone is clean on stock Wi-Fi.

## 15. Monitor TX is working on the stock driver

`monitor-tx/owly_monitor_tx.ko` replaces the monitor netdev's
`ndo_start_xmit` at runtime, strips radiotap, and calls the stock driver's
`ucfg_dp_start_xmit(skb, monitor_vdev)`. It validates the real stock layout
before hooking:

```text
hdd_adapter magic = 0x71636136 ("qca6")
adapter->dev at +32 == netdev
adapter->deflink at +50704 -> adapter
deflink->vdev at +32 != NULL
```

Test: `con_mode=4`, channel 6, `owly-inject-probe wlan0 6 3`.

Result: **3 probe requests produced 34 probe responses**, and the phone
stayed up. Raw 802.11 TX+RX works on the internal QCA6750 with the stock
driver. This invalidates the earlier conclusion that FullMAC monitor TX was
impossible on this device.

The path to full AWDL is now:

1. Enter stock monitor mode (`con_mode=4`), channel 6.
2. Load/enable `owly_monitor_tx`.
3. Transmit a valid AWDL PSF with `BSSID=<redacted-mac>`, a valid AWDL
   self address, and master/sync/election TLVs pointing at the iPhone.
4. Capture the iPhone's AWDL response in monitor RX.
5. Port the Owly AWDL sync/election/channel-sequence loop onto this raw
   monitor TX/RX backend, then AirDrop service discovery and data path.
6. Restore `con_mode=0` and Wi-Fi when done.

Artifacts: `monitor-tx/monitor_tx.c`, `monitor-tx/owly_monitor_tx.ko`,
`owly-inject-probe`.

## 16. Current best architecture and remaining gap

Proven on the stock driver:

- Monitor RX works (`con_mode=4`).
- Monitor TX works for **probe requests** (3 requests -> 34 responses).
- Monitor TX does **not** radiate **action frames** (no ACK for AWDL PSF or
  a control action frame).
- P2P action TX works; a live 355-byte valid AWDL PSF with the real AWDL
  BSSID got **19 ACKs** from the iPhone.
- P2P/AP RX never sees AWDL; the firmware filters it before WMA.
- P2P + monitor coexistence is rejected (`EINVAL`).
- Time-sliced P2P TX -> monitor RX did not catch a peer-specific iPhone
  response.

Missing piece: **concurrent valid AWDL action TX and monitor RX**. The
monitor vdev has no AP/STA self-peer, so action TX does not radiate; P2P has
a peer but its RX filter is blind to AWDL.

Highest-value next engineering:

1. Create a **hidden STA/AP vdev with a self-peer** in the firmware while
   keeping monitor mode, then route AWDL action frames through WMI mgmt TX
   using that hidden vdev (the Medium/SM8150 hidden-vdev approach).
2. Implement it as a custom qcacld module/kernel build for chenfeng, because
   the stock module has no host-visible hidden-vdev API.
3. Keep the proven P2P live-PSF path as the TX fallback and monitor RX as the
   capture path for protocol development.

Artifacts: `awdl_iphone_live.bin` (355-byte live PSF),
`awdl_live_slave_*` (valid slave variants), `monitor-tx/*`,
`owly-monitor-send-frame`, `owly-inject-probe`.

## 17. Hidden-vdev source port: built, but source monitor mode resets

A minimal hidden-vdev TX path was added to the 91G source:

- firmware-only STA vdev create/start/self-peer;
- monitor-xmit WMI management send through the hidden vdev;
- preparation from the monitor-channel sysfs context.

The module built and finalized cleanly. The early-bind boot loaded it
(`coresize=10698752`), but bringing `wlan0` up in monitor mode reset the
phone before hidden-vdev creation. The one-shot bind was consumed and the
phone returned to stock. This is a source-driver monitor-start stability
problem, not a hidden-vdev rejection.

Remaining hidden-vdev approach: implement it against the **stock** driver
via runtime kprobe-resolved WMI APIs and structs, keeping the stable stock
monitor RX. This is a substantial kernel module: resolve
`wmi_unified_vdev_create_send`, `vdev_start`, `peer_create`,
`wmi_mgmt_unified_cmd_send`, and the WMA/WMI handles; construct the hidden
vdev/peer and WMI mgmt parameters; pre-create from process context; send from
the stock monitor xmit hook.

Current artifacts: `wma_hidden_tx.c/h` in `.flourite-wlan`,
`owly-qca6750-hidden-tx.zip`, dormant; stock Wi-Fi restored, no modules
loaded.

## 18. Stock-runtime hidden STA vdev: probe TX works, action TX filtered

`monitor-tx/hidden_tx.c` (`owly_hidden_tx.ko`) implements the hidden vdev
against the stock driver:

- kprobe-resolved WMI functions;
- WMA/WMI handle via `__cds_get_context`;
- hidden STA vdev + self-peer + target peer (iPhone AWDL MAC);
- monitor xmit hooked to a workqueue that sends via WMI management TX;
- monitor RX remains active.

Verified:

- vdev create/start/self-peer/target-peer all return 0;
- probe request injection through the hidden vdev gets a real probe response;
- monitor RX and hidden TX coexist;
- no reset.

Not verified / still blocked:

- AWDL action frames through the hidden vdev are not captured and get no ACK;
- the iPhone was active (70 AWDL frames captured) during the final test;
- VDEV_UP causes a firmware assert, so it is skipped.

The firmware therefore accepts hidden-STA probe TX but still filters AWDL
action frames. Remaining ideas: hidden AP vdev, alternate WMI tx_type/peer
association, or a real peer/BSS setup for action TX. The proven P2P live-PSF
path (19 ACKs) remains the strongest TX result, but it still lacks a
concurrent RX path.

Artifacts: `monitor-tx/hidden_tx.c`, `monitor-tx/owly_hidden_tx.ko`,
`monitor-tx/monitor_tx.c`, `awdl_live_slave_*`.

## 19. Hidden STA vdev action isolation: negative on WCN6750

Final isolation matrix on the stock driver:

- hidden STA vdev create/start/self-peer/target peer: all return 0;
- probe request TX: works (real probe response);
- AWDL action with BSSID AWDL: no on-air frame, no ACK;
- action with BSSID p2p0: no on-air frame, no ACK;
- iPhone active during an earlier run in the same setup (70 AWDL PSFs);
- VDEV_UP: firmware assert;
- source-driver hidden vdev: monitor start reset.

The WCN6750 firmware accepts hidden-STA probe TX but still filters action
frames. The SM8150 hidden-vdev workaround does not transfer to this FullMAC
firmware.

Cheap tests exhausted. Remaining: hidden AP vdev/alternate WMI TX parameters
(risk), firmware patch, custom kernel/driver, external adapter, or stop at
the proof.

## 20. Isolation matrix exhausted

Stock-runtime results:

- hidden STA vdev create/start/self-peer/target-peer: success;
- probe request TX: success;
- AWDL action TX, BSSID AWDL or p2p0: no frame/ACK;
- OCB hidden vdev: firmware reset;
- VDEV_UP: firmware assert;
- legacy `cdp_mgmt_send_ext`: no action frame;
- source-driver hidden vdev: monitor startup reset.

The WCN6750 firmware accepts hidden-STA probe TX but filters action frames.
The next phase must be a custom driver built against the exact running GKI
6.1.138 ABI, and if that still fails, a firmware patch (`wlanmdsp.mbn`) of
the management-TX vdev/peer/action path.

Phone restored: stock driver, Wi-Fi connected, no test modules.

## 21. Custom source driver: correct ABI is 6.1.68, monitor still resets

The stock vendor `cfg80211.ko` is a 6.1.68 build. Building qca against
6.1.138 GKI headers gives an incompatible `cfg80211_ops`/`wiphy` layout:
wiphy registration fails with `-22`. Building against the 6.1.68 GKI tag
fixes HDD: `wlan0`, `p2p0`, and `wifi-aware0` are created.

Monitor mode starts (`con_mode=4`, `wlan0` type 803), but the source driver
resets the phone after about 100 seconds. Preparing the hidden vdev and
sending a frame during that window also triggers a reset before results can
be captured. The reset is not explained by the kernel ABI now; it is in the
source monitor/hidden-vdev path or firmware watchdog handling.

The custom source-driver path is not usable for AWDL until that reset is
fixed. The stock driver remains the only working HDD/VDEV path, with hidden
probe TX but firmware-filtered action TX. Remaining serious paths: diagnose
the source monitor reset, or patch firmware `wlanmdsp.mbn` action filtering.
Phone restored to stock.

## 22. Stock hidden vdev matrix exhausted

Hidden firmware-only vdevs of type STA, AP, IBSS, and AP/P2P-GO (subtype 3)
all create, start, and add the self/target peer successfully. The host WMI
management send also returns success for every type.

With AirDrop open and the monitor on channel 6, the monitor received 116-119
live iPhone AWDL action frames per run (355-byte and 945-byte PSFs), but no
frame from the hidden SA (`<redacted-mac>`) and no ACK addressed to it.
The vdev type/subtype does not change the outcome.

Conclusion: WCN6750 firmware filters/discards action TX from the hidden WMI
injection vdev independently of vdev type. The native P2P interface can
still radiate action/PSF frames (the earlier 19 ACKs), but the hidden WMI
path cannot. A monitor + native P2P coexistence path or a firmware patch is
required; the vdev-type matrix is no longer worth repeating.

Phone restored to stock. New capture helpers: `scripts/monitor-recv.c`,
`scripts/monitor-ack.c`.

## 23. Monitor concurrency: config parsed, policy and firmware still block

A reversible early-bind overlay set `monitor_mode_concurrency=1` in
`WCNSS_qcom_cfg.ini`. Runtime tracing confirmed the driver read it:
`wlan_mlme_is_sta_mon_conc_supported` returned 1. But adding a monitor
interface still returned `EINVAL`; the stock add-interface path reported
“Adding monitor interface not supported,” and the source policy
`policy_mgr_check_mon_concurrency` explicitly rejects P2P GO/client with a
monitor. The config enables station scan + monitor only, not P2P + monitor.

The last hidden-vdev variant was also closed: an AP/P2P-GO hidden vdev
started successfully, but `wmi_unified_vdev_up_send` reset the phone, just
like hidden STA VDEV_UP. Stock recovered.

Host-only native P2P TX + monitor RX is not achievable through configuration
or the tested hidden-vdev paths. The remaining options are a patched/custom
driver that changes both the concurrency policy and VDEV_UP behavior, or a
firmware patch; both are high-risk. Phone restored: stock driver, Wi-Fi
connected, `con_mode=0`, overlay removed, no test modules.

## 24. Controlled AirDrop-open monitor baseline

With AirDrop explicitly opened, stock monitor mode on channel 6 for 15
seconds captured 147 iPhone AWDL action frames: 132 at 355 bytes and 15 at
945 bytes, SA `<redacted-mac>`, BSSID `<redacted-mac>`. This confirms
the RX baseline but is not a TX/ACK test.

Earlier hidden-vdev no-ACK runs remain inconclusive because AirDrop was not
confirmed open. Capture: `capture/airdrop-control-cap.txt`.

## 25. Controlled AirDrop-open P2P TX/ACK

With AirDrop confirmed open, `p2p0` was brought up on channel 6 (Wi-Fi
framework disabled to avoid the 5 GHz concurrency choice). The peer hook
redirected the iPhone AWDL address to the p2p0 self-peer and patched the
BSSID to `<redacted-mac>`.

A 100-frame burst of the live 355-byte PSF reached the full native P2P path
and produced:

```text
3 initial noack status=3
5+ ACK status=0 (logging capped at 5)
peer-hook hits=200
```

This is a confirmed AirDrop-open MAC-level TX/ACK. It does not prove AWDL
peer acceptance or action response: P2P RX does not deliver AWDL frames, and
monitor RX cannot coexist with P2P. Capture:
`capture/airdrop-open-p2p-ack.txt`.

## 26. Confirmed AirDrop-open time slice: TX works, RX response missed

P2P burst (20 live 355-byte PSFs) with AirDrop open:

```text
ACK status=0: 11
noack status=3: 9
peer hits=40
```

Immediate switch to stock monitor mode for 12 seconds captured 118 iPhone
AWDL action frames, but **zero** frames with the injected P2P SA or any
response to it. The switch latency loses the AWDL response window.

Time slicing proves TX/ACK but cannot provide the RX half of AWDL. Host
policy forbids simultaneous monitor + P2P; hidden VDEV_UP resets; source
monitor resets. Capture: `capture/airdrop-timeslice-cap.txt`.

## 27. Firmware images are secure-boot authenticated

The QCA6750 firmware is the WPSS bundle:

```text
/vendor/firmware_mnt/image/qca6750/wpss.mdt
/vendor/firmware_mnt/image/qca6750/wpss.b00 ... wpss.b*
```

`wpss.mdt` is a 32-bit Qualcomm DSP6 ELF with 13 segments; `bdwlan.*` is
board data. The phone reports:

```text
ro.boot.secureboot=1
ro.secureboot.lockstate=locked
ro.boot.verifiedbootstate=green
ro.boot.flash.locked=1
```

A modified WPSS image cannot load without a secure-boot/signature bypass.
The firmware-patch path (action TX filter, VDEV_UP, monitor/P2P concurrency)
is therefore not viable through normal image replacement. Only a custom
driver/host-side change remains, and that cannot override firmware behavior;
full internal-radio AWDL is blocked by secure firmware.

## 28. Custom driver: coexistence achieved, firmware monitor-up still resets

A custom 6.1.68 source driver (`.owly-custom-wlan`) was built with:

- SAP/P2P/NAN monitor-concurrency rejection removed from policy manager;
- `wlan_mlme_is_sta_mon_conc_supported()` forced true;
- the monitor-add tool sending nested
  `NL80211_ATTR_MNTR_FLAGS` / `OTHER_BSS`.

The source driver brought `p2p0` up on channel 6 and created `mon0` while
`p2p0` remained up (`mon0` type 803). Host-level P2P + monitor coexistence is
therefore possible.

But `ip link set mon0 up` immediately resets the phone. The reset is in the
firmware monitor VDEV_UP path, not host policy. Stock recovered. The
remaining custom-driver experiment would be an RX path that avoids firmware
VDEV_UP; firmware patching is blocked by secure boot.

Patch: `patches/owly-custom-p2pmon.diff`; module:
`qca_custom_p2pmon2.ko`.

## 29. Custom driver inverse ordering also resets

The reverse ordering was tested on the custom source driver: standalone
monitor mode first (`con_mode=4`, channel 6), then `p2p0` hostapd. Starting
`p2p0` resets the phone.

Both orderings fail:

- P2P first, dynamic monitor up -> reset;
- monitor first, P2P start -> reset.

Host concurrency policy is solved by the custom driver, but firmware cannot
support monitor + P2P in either direction. The custom-driver coexistence
option is exhausted.

A no-VDEV_UP custom build confirmed why: skipping `hdd_start_adapter()` avoids
the reset, but `hdd_set_mon_rx_cb()` fails with "failed to get vdev"; the
monitor stays down and receives nothing. The monitor VDEV_UP is required for
RX.

## 30. Owly research and the last untested idea

Owly has three radio modes: shared (station + monitor), exclusive (IBSS/AP +
monitor), and active-monitor. Its packaged quirks table has no QCA/WCN6750
entry. On this phone, shared and active-monitor are blocked by monitor TX/RX
firmware behavior; packet capture still depends on the monitor vdev.

The only untested composition is **native IBSS + monitor** for Owly's
exclusive mode. Native P2P TX worked where hidden P2P did not, so native IBSS
might allow action TX; whether the firmware permits monitor + IBSS without
resetting is unknown. The test is:

1. Create a native IBSS interface on channel 6.
2. Add `mon0` with `OTHER_BSS` while IBSS is up.
3. Check monitor survival and IBSS action radiation.

No AirDrop is needed for the coexistence setup. If this fails, no
Owly-compatible path remains on the QCA6750 firmware.

## 31. Native IBSS test fails: EOPNOTSUPP

The native IBSS test returned:

```text
nl80211 error=-95 (Operation not supported)
add IBSS interface failed
```

The QCA6750 driver does not expose `NL80211_IFTYPE_ADHOC` on this ROM. Owly's
exclusive IBSS mode cannot be created. The AP variant already resets when a
monitor is added; active-monitor/shared need monitor TX/RX behavior the
firmware rejects.

This is the final Owly-compatible-path negative. Phone restored to stock.

## 32. External-reference conclusion

Owly's own hardware table marks the comparable Qualcomm FullMAC devices as
broken: ath11k/WCN6855 cannot create the required monitor interface, and
ath12k/WCN7850 shared mode hears nothing from the phone. The only tested
Qualcomm entry is the older SoftMAC ath9k/AR9287.

The `medium-inject` and `aviumui-wlan-tools` work adds host-side monitor
injection framing; it does not change the firmware behavior that resets
monitor VDEV_UP or filters action TX on WCN6750. No host-side patch can
bypass secure FullMAC firmware.

Therefore the internal WCN6750 cannot provide the monitor behavior Owly
requires. A supported external radio (ath9k or a tested MediaTek USB card) is
the practical route, but external USB is currently ruled out by the project
constraints.

## 33. Mosey/Quick Share and protocol-RE references

The phone has `com.google.android.mosey` and Quick Share, but no native
`mosey_server`, no Mosey Binder service, and no `wonder` phy. Mosey's native
`wonder` path requires BCM `wondertap` or vendor FastConnect support. The
`mosey-extended` KSU module can create a **virtual** `wonder` phy, but its
README explicitly says it has no real RF; it cannot carry AWDL frames on
QCA6750. The OnePlus 13 research found the same missing backend on Qualcomm.

AirDrop protocol-RE references:

- Owly SPEC/implementation, OpenDrop, OWL, Windfall
- AirDrop Observatory, PrivateDrop, AirFuzz
- BLE sniffers, Wireshark, monitor captures, Frida/lldb, Ghidra

Windfall confirms the iPhone AirDrop browser is bound to `awdl0`; the BLE
beacon only wakes AWDL. No protocol-level change can substitute for an
AWDL-capable radio path.

## 34. QCA6490 monitor-TX reference and the WCN6750 reset

`saparKGZ/qca6490-mon-tx` is the closest public QCA implementation. It adds
monitor RX and raw TX via `cdp_tx_send_exc`, using the station vdev because
QCA firmware does not radiate from a monitor vdev.

A kprobe was used to skip `wlan_hdd_cm_issue_disconnect` during a stock
monitor add. Initial state:

```text
wlan0 connected + UP
mon0 UP
no immediate reset
```

Then Android's Wi-Fi framework repeatedly destroyed the station vdev
(`STA + MON mode is UP`). Stopping the framework reset the phone. The
station-vdev raw TX path could not be tested; STA+monitor is not stable on
WCN6750 under Android's Wi-Fi management.

Useful references but not a fix:

- `saparKGZ/qca6490-mon-tx` — monitor/raw-TX patch
- `spiral009/aviumui-wlan-tools` — WCN7851 monitor/AWDL tooling
- `ex18a/qcacld-monitor-injection` — WCN3990 injection
- `adineo-cmd/librephone-blob-research` — WCN6750 WMI/firmware decode

Phone restored to stock.

## 35. Snapdragon 8s Gen 3 product brief: platform vs device BOM

Qualcomm's Snapdragon 8s Gen 3 (SM8635) brief lists:

- Qualcomm FastConnect 7800 mobile connectivity
- Wi-Fi 7, 2x2, 320/240/160/80/40/20 MHz
- 2.4/5/6 GHz, 802.11be/ax/ac/a/b/g/n
- Bluetooth 5.4, LE Audio, dual Bluetooth antennas
- Snapdragon X70 5G

This is the **platform capability**, not necessarily the phone's actual
connectivity BOM. The running chenfeng device exposes:

```text
17110040.qcom,wcn6750
qca_cld3_qca6750
```

That is the **WCN6750 / QCA6750** Wi-Fi 6E class, not FastConnect 7800 /
WCN7850. The product brief's FastConnect 7800 listing does not override the
live device tree/driver.

The distinction matters:

- FastConnect 7800 / WCN7850 has an experimental ath12k AWDL patch
  (`alyx/ath12k-wcn7850-awdl`) and vendor Mosey support on some phones.
- The actual QCA6750 path is qcacld FullMAC and is the one blocked here.

The brief does not document AWDL, monitor concurrency, Mosey/wonder, or
firmware authentication, so it does not change the WCN6750 blocker. It is a
useful reference for the SoC's intended capabilities and for explaining why
different phones with the same SoC can have different Wi-Fi/AWDL behaviour.

## 36. Chenfeng 5.2.1.86B source

The Chinese Civi 4 Pro vendor qcacld source is:

```text
MiCode/vendor_qcom_opensource_wlan
commit 45401f2: Xiaomi wlan changes for Xiaomi Civi 4 Pro Android U
banner: 5.2.1.86B
```

It was built against the exact 6.1.68 GKI tree and finalized cleanly. The
stock ROM module is 5.2.1.91G. A direct stock→86B swap (`rmmod` then
`insmod` from `/data/local/tmp`) reset the phone during the swap. Stock
recovered.

The 86B source is the correct device vendor tree but older than the ROM's
91G module and not directly loadable this way. Early bind would need metadata
space and still faces the monitor VDEV_UP behavior.

Phone restored to stock.
