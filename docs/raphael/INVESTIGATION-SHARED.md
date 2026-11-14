# K20 shared-mode investigation

Status: **closed (Nov 14, 2026)** — no end-to-end shared path on the WCN3990
firmware. Monitor/STA concurrency, packet capture, P2P-device TX, and STA
management frames were all tested; the firmware blocks the required
coexistence.

## What Owly shared mode requires

- Wi-Fi station remains associated and passing traffic.
- The AP must be on a social channel (6, 44, or 149).
- A monitor interface exists on the station channel.
- AWDL uses `Presence::Share` on the AP channel.
- The associated station ACKs the iPhone's unicast AWDL frames.

## K20 current state

- Stock boot with normal Wi-Fi connected on **5220 MHz / channel 44**.
- Stock boot runs `con_mode=0`; Wi-Fi station works normally.
- The current patched monitor image runs global `con_mode=4`, which removes
  the normal STA and is therefore not shared mode.
- The existing hidden injection STA is only present in the patched monitor
  boot and is not managed by Android Wi-Fi.

## Driver discovery

`wlan_hdd_add_monitor_check()` in
`drivers/staging/qcacld-3.0/core/hdd/src/wlan_hdd_main.c` implements the
packet-capture concurrency model:

- It accepts a monitor interface only when exactly one STA is open.
- It rejects SAP and multiple monitor interfaces.
- It opens the monitor through the normal adapter path.
- `wlan_hdd_add_virtual_intf()` calls this check before its global-monitor
  rejection path.

This is compiled under `WLAN_FEATURE_PKT_CAPTURE`. The current K20 `.config`
does not enable it.

## Proposed shared-mode path

1. Enable `WLAN_FEATURE_PKT_CAPTURE` in a test kernel.
2. Boot with `con_mode=0` and normal Wi-Fi associated on channel 44.
3. Create a packet-capture monitor interface with the nl80211
   `NEW_INTERFACE`/`MONITOR` request while the STA is associated.
4. Confirm the monitor receives AWDL frames and the STA remains associated.
5. Point the AWDL helper at the monitor and use `Presence::Share`.
6. Verify the STA ACKs the iPhone's unicast frames.
7. Test against the Arch receiver before any iPhone use.

## Blockers / checks

- `wlan_hdd_add_monitor_check()` requires packet-capture support in the
  driver/firmware.
- The monitor interface must be created through cfg80211/nl80211; the K20
  image has no `iw` binary, so a small nl80211 helper is needed.
- Policy-manager concurrency must allow STA + monitor on channel 44.
- The current hidden-STA injection patch is independent; shared mode should
  not depend on the global-monitor boot.

## Next experiment

Build the kernel with:

```text
CONFIG_WLAN_FEATURE_PKT_CAPTURE=y
```

Then verify that `wlan_hdd_add_monitor_check` is compiled in, and add a
minimal `NL80211_CMD_NEW_INTERFACE` monitor helper for testing. No AirDrop
test until the STA+monitor receive path is confirmed.

## Build result

A test kernel built successfully with:

```text
CONFIG_WLAN_FEATURE_PKT_CAPTURE=y
```

The resulting image contains `wlan_hdd_add_monitor_check` and the packet-capture
objects. A no-global-monitor boot image was packaged at:

```text
workspaces/raphael/boot-images/boot-owly-shared-pktcapture.img
```

A small nl80211 helper was added for creating the monitor interface:

```text
scripts/nl80211-add-monitor.c
```

## Important TX caveat

`wlan_hdd_add_monitor_check()` creates the packet-capture monitor through
`hdd_open_adapter(QDF_MONITOR_MODE)`. That path uses the packet-capture netdev
ops, which are receive-oriented. The existing raw-frame injection hook
(`hdd_mon_hard_start_xmit`) is attached to the global-monitor netdev path.

So the next shared-mode test has two stages:

1. Verify STA + packet-capture monitor RX while Wi-Fi stays associated on 44.
2. If RX works, add the hidden-STA TX hook (or equivalent) to the
   packet-capture monitor path for AWDL injection.

No flash or live test has been performed for this image.

## Packet-capture test result

Test image:

```text
boot-owly-shared-pktcapture.img
```

Result:

- Normal Wi-Fi stayed associated on **5220 MHz / channel 44**.
- `NL80211_CMD_NEW_INTERFACE` with `NL80211_IFTYPE_MONITOR` succeeded.
- `mon0` appeared with MAC `<redacted-mac>`.
- `mon0` remained `NO-CARRIER` and link type `EN10MB` (Ethernet).
- `owly-nl80211-set-channel mon0 5220` returned `-EOPNOTSUPP`.
- `tcpdump -i mon0` captured **zero frames**, including during ping traffic.
- No AWDL management frames were available on this interface.

Conclusion: qcacld's packet-capture monitor is a data-capture netdev, not a
radiotap monitor. It cannot replace the global monitor for AWDL RX. Shared
mode therefore needs either:

1. a true monitor vdev allowed alongside an associated STA, or
2. a different driver path that delivers AWDL management frames to the
   packet-capture interface.

The current packet-capture experiment does not provide shared-mode AWDL RX.

## Hidden-STA shared path (current lead)

Owly's `shared()` creates a monitor on the station's MAC while the station
remains associated. qcacld cannot do that literally in `con_mode=4`, but the
existing hidden `wlan_inj0` STA can be made into the associated station while
`wlan0` remains the monitor.

Two host-side bugs prevented this:

1. `wlan_inj0` was created with `hdd_open_adapter()` instead of
   `hdd_open_adapter_no_trans()`, so it had no `osif_vdev_sync`. Every
   cfg80211 scan/connect/key operation failed with `-EAGAIN`.
2. In global monitor mode, every adapter received monitor radiotap setup and
   `wlan_mon_drv_ops`, including the hidden STA. `ip link set wlan_inj0 up`
   therefore ran `hdd_mon_open`/`hdd_enable_monitor_mode()` and failed.

The test source now:

- creates the hidden STA with `hdd_open_adapter_no_trans()`;
- uses Ethernet setup and station netdev ops for the hidden STA;
- keeps monitor setup/ops only for the actual `QDF_MONITOR_MODE` adapter.

This should allow `wlan_inj0` to be brought up, scanned and associated while
`wlan0` remains the global monitor. Policy manager sees no associated
connection for the monitor, so the hidden STA connect should be allowed on
channel 44.

Remaining unknown: whether WCN3990 firmware ACKs unicast AWDL frames from a
non-AP BSSID through the associated hidden STA. That must be measured after a
successful association.

## Packet-capture finding

`WLAN_FEATURE_PKT_CAPTURE` builds and `mon0` can be created alongside an
associated STA, but it is a packet-capture Ethernet netdev:

- `NO-CARRIER`, link type `EN10MB`
- `set-channel` returns `-EOPNOTSUPP`
- zero frames captured even during ping traffic

`packet_capture_mode` defaults to 0 (capture nothing). It can be set to 3
(management + data) in the Wi-Fi INI, but the netdev is not a radiotap monitor
and still cannot replace the global monitor for AWDL RX.

## Hidden-STA scan result

After the `osif_vdev_sync` and per-session netdev-ops fixes:

- `wlan_inj0` is now `link/ether`, not radiotap.
- `ip link set wlan_inj0 up` returns 0.
- The hidden STA sends a vdev start on 5220 MHz with `dis_hw_ack: 0`.
- `NL80211_CMD_TRIGGER_SCAN` succeeds on `wlan_inj0`.
- A wildcard scan and a targeted 5220 MHz scan both complete with
  `scan found 0 bss`, even though the host AP is on channel 44.
- The monitor `wlan0` remains up.

The hidden STA is therefore a valid station at the cfg80211 level, but the
WCN3990 firmware does not provide scan results while the global monitor owns
the radio. Association cannot be reached through the current scan path.

This is the current shared-mode blocker. A future experiment could try a
direct BSSID connect with a test AP, but `csr_roam_connect()` normally requires
the BSS in the scan cache. No credentials or live test were used.

## Packet capture mode 3 test

The vendor INI is read-only on this build, so the test kernel changes the
compiled default in `cfg_pkt_capture.h` from 0 to management+data (3). This is
a test-only default; it does not change the fixed monitor path.

The next check is whether `mon0` now receives management frames while the
normal STA stays associated on channel 44.

## Packet capture mode wiring test (in progress)

`mon0` created in mission mode was actually a second STA interface: `type=1`,
`NO-CARRIER`, and zero packets. Source inspection shows why: this tree reads
`packet_capture_mode` through `ucfg_pkt_capture_get_mode()`, but never copies
it into `hdd_ctx->enable_pkt_capture_support` /
`hdd_ctx->val_pkt_capture_mode`, and WMA never sets
`wma_handle->is_pktcapture_enabled`. The host pdev mode therefore stays 0 even
when the INI/default says 3.

The test patch:

- copies the pkt-capture mode into the HDD context after `hdd_cfg_params_init()`
- lets WMA program the STA vdev and host pdev capture mode without requiring the
  unused `is_pktcapture_enabled` flag

Expected result after flashing: monitor creation takes the pkt-capture path,
`mon0` is radiotap (`type=803`), and the AF_PACKET capture sees frames when
traffic/scanning occurs.

## Final shared-mode blockers (Nov 13)

The wired packet-capture kernel reached the intended state:

- `hdd_ctx->val_pkt_capture_mode = 3` and `enable_pkt_capture_support = 1`
- WMA programmed the STA vdev and host pdev (`val=3`, status 0)
- `mon0` was created through the packet-capture path (`type=803`)
- the pkt-capture callback registered successfully (`ret=0 mode=3 pdev=0`)

But the firmware advertised **no packet-capture service**
(`WMI_SERVICE_PACKET_CAPTURE_SUPPORT = 0`), and the callback received zero
frames even during ping and scan traffic. This is a firmware capability
blocker, not an INI or driver-wiring issue.

The alternate shared paths also fail:

- A P2P-device vdev can be created (`p2p0`, `iftype=10`) but cannot be brought
  up (`EOPNOTSUPP`); remain-on-channel and action TX then return `ENETDOWN`.
- AWDL action TX from the associated STA is rejected by cfg80211 because the
  frame is vendor category 127 with a non-AP DA/BSSID (`ENOTCONN`).
- Registering Apple AWDL action frames with LIM/cfg80211 succeeds, but there is
  no startable shared vdev to carry them.

Conclusion: WCN3990 firmware on this build cannot provide the monitor/STA or
P2P-device shared data path Owly needs. Shared mode is blocked unless a
different firmware or a direct-BSSID test AP experiment is available.

## STA management-frame shared test

A final local test removed the monitor entirely:

- The host's P2P-device wdev sent a captured Apple AWDL action frame on the
  K20's current channel (2462 MHz); the host command returned `FRAME rc=0`.
- The K20 STA registered successfully for the AWDL match
  (`7f 00 17 f2 08`) via `NL80211_CMD_REGISTER_FRAME`.
- The K20 driver's LIM filter was patched to pass Apple AWDL action frames to
  cfg80211.
- No frame was received on `wlan0`.

AWDL action TX from the STA is also rejected by cfg80211 (`ENOTCONN`) because
the frame's DA/BSSID is not the associated AP. The firmware does not deliver
these vendor action frames to the STA vdev, so the STA-only shared path is not
viable either.

Shared mode therefore needs either different firmware with packet-capture or
monitor/STA concurrency, or the exclusive IBSS fallback.

## Direct-BSSID hidden-STA test with the 5 GHz AP (ch 44)

The existing home AP is at `<redacted-bssid>`, 5220 MHz. A temporary
`wpa_supplicant` was started on `wlan_inj0` with the network pinned to that
BSSID/frequency; the config was deleted after the test.

Result: the hidden STA cannot scan while the global monitor owns the radio,
even with `wlan0` brought down. `wpa_supplicant` therefore has no BSS entry and
never issues the association. `ap_scan=0`, `BSSID`, `ENABLE_NETWORK`, and
`RECONNECT` did not bypass it.

A driver fallback that issues `csr_roam_issue_connect()` without a BSS list
cannot work either: the roam state machine explicitly stops with
"cannot continue without BSS list" for infrastructure STA. A synthetic scan
result would be required.

No AirDrop test was performed; AirDrop was off throughout.

## Upstream research (Nov 13)

- Owly shared mode requires an interface combination of managed + monitor,
  creates `owlymon0` with the station's MAC, keeps the station associated, and
  injects through that monitor (`crates/owly-radio/src/injection/setup.rs`).
- Owly's own hardware table marks Qualcomm WCN6855 as unable to create the
  monitor interface (#45) and WCN7850 shared mode as receiving nothing (#40).
- WCN7850 PR #103 found that with an AP/station interface present, the monitor
  receives no frames from other devices; with the monitor alone it hears
  everything, but the driver drops monitor TX. This is a Qualcomm-family
  pattern, not just an Owly bug.
- `ex18a/qcacld-monitor-injection` (WCN3990) proves monitor RX and injection
  through a hidden STA vdev work, but only in global monitor mode. It does not
  provide monitor + associated-STA concurrency.
- The K20 driver only advertised a monitor-only combination. A test patch added
  managed + monitor and a mission-mode monitor start. `mon0` then appeared as
  `type=803` beside the associated STA on 5220 MHz and the STA stayed
  connected, but bringing `mon0` up reset the device. The monitor-beside-STA
  path is therefore not usable on this firmware.

The supported Owly fallback is exclusive mode: monitor alone, with an IBSS
cell or hidden AP to ACK. That matches the working WCN3990 injection design.
