# K20 exclusive-mode investigation

Status: **not implemented as a true IBSS/AP cell**. Current working path is
fixed monitor + hidden STA, which already ACKs the iPhone well enough for
AirDrop transfers.

## What Owly exclusive mode requires

From `crates/owly-radio/src/injection/setup.rs::exclusive`:

- Wi-Fi station is taken down.
- An ad-hoc **IBSS cell on AWDL BSSID `<redacted-mac>`** with the AWDL MAC,
  or a hidden AP on cards with no ad-hoc mode.
- The cell gives the firmware an address to ACK and a BSSID filter that passes
  AWDL.
- A monitor is added on one social channel (44/149/6); AWDL uses
  `Presence::Fixed`.

## K20 blockers

- `CONFIG_QCA_IBSS_SUPPORT` is not enabled in the current `.config`.
- The hidden injection vdev is opened as `QDF_STA_MODE`, not IBSS.
- The reference patch warns that AP vdevs trigger a firmware beacon-TX-offload
  crash, so a hidden AP is not the safe route.
- Android has no normal `iw`/`wpa_supplicant` management for the hidden vdev.
- The current hidden STA is unassociated but still ACKs; this is why fixed mode
  works without a real IBSS cell.

## Test plan if pursued

1. Enable `CONFIG_QCA_IBSS_SUPPORT` and rebuild the kernel.
2. Create an IBSS vdev with MAC `<redacted-mac>`, BSSID
   `<redacted-mac>`, channel 44.
3. Join the cell via nl80211/wpa_supplicant (no hostapd).
4. Keep the monitor fixed on 44 and AWDL on `Presence::Fixed`.
5. Compare ACK/retry behavior and iPhone throughput with the current hidden-STA
   fixed path.

## Recommendation

Keep the working fixed + hidden-STA path until IBSS is proven. Exclusive is the
more plausible of the two Owly modes for this driver, but it needs a kernel
config/feature experiment.

## Source findings (2026-11-13)

- `CONFIG_QCA_IBSS_SUPPORT` is **not present** in the K20 `.config`.
  `drivers/staging/qcacld-3.0/Kbuild` only adds `-DQCA_IBSS_SUPPORT` under
  that symbol, so the `join_ibss`/leave-IBSS cfg80211 paths are currently
  compiled out.
- `hdd_open_adapter()` has no `case QDF_IBSS_MODE`; it opens monitor/STA/AP/P2P
  adapters. IBSS is reached through `hdd_change_iface()`:
  `new_mode == QDF_IBSS_MODE` stops the adapter, sets start-adapter ops, and
  sets `roam_profile->BSSType = eCSR_BSS_TYPE_START_IBSS`.
- `__wlan_hdd_cfg80211_join_ibss()` checks
  `policy_mgr_allow_concurrency(..., PM_IBSS_MODE, freq, HW_MODE_20_MHZ)`.
  Monitor + IBSS concurrency must pass that check on the K20.
- The project already has `scripts/nl80211-add-ibss.c`. It currently uses
  `NL80211_CMD_NEW_INTERFACE` with `NL80211_IFTYPE_ADHOC`; that hits the
  missing `hdd_open_adapter` IBSS case. It should instead set
  `NL80211_IFTYPE_ADHOC` on the existing `wlan_inj0` STA netdev and then
  `NL80211_CMD_JOIN_IBSS`.
- The hidden STA `wlan_inj0` is opened as `QDF_STA_MODE`. It exists before
  IBSS work and is the natural adapter to convert.
- Hidden AP is not the route: the reference driver notes say AP vdevs trigger
  a firmware beacon-TX-offload crash. Use IBSS, not hostapd.
- Build test needed:
  `KCFLAGS=-Wno-error -DQCA_IBSS_SUPPORT` (or add the symbol to Kbuild) to
  compile the cfg80211 IBSS ops.

## Revised next experiment

1. Build with `-DQCA_IBSS_SUPPORT` (or enable the Kbuild define).
2. Open the monitor and hidden STA as now.
3. Change `wlan_inj0` to `NL80211_IFTYPE_ADHOC` via `SET_INTERFACE`.
4. Join BSSID `<redacted-mac>` on channel 44 with SSID `owlyibss0`.
5. Keep `Presence::Fixed` and test against the Arch receiver first.
6. If IBSS is rejected by policy-manager concurrency, exclusive mode is not
   viable in the current global-monitor boot and Shared becomes the alternative.

## External / qcacld findings (2026-11-13)

- External WCN3990 research confirms monitor mode and management injection via
  a hidden STA vdev. It does not claim a monitor + IBSS combination.
- `wlan_hdd_add_virtual_intf()` has an explicit global-monitor guard:
  `cds_get_conparam() == QDF_GLOBAL_MONITOR_MODE` returns
  `"Concurrency not allowed with standalone monitor mode"`. A userspace
  `NEW_INTERFACE`/`iw add type ibss` therefore cannot work in this boot.
- `wlan_hdd_cfg80211_change_iface()` rejects changes when
  `wlan_hdd_check_mon_concurrency()` sees packet-capture monitor + STA
  concurrency (`policy_mgr_get_concurrency_mode() == QDF_STA_MASK |
  QDF_MONITOR_MASK`). The hidden STA + monitor is exactly that combination, so
  changing the hidden STA to IBSS through cfg80211 is likely rejected too.
- `hdd_open_adapter()` has no `QDF_IBSS_MODE` case. IBSS is reached through
  `hdd_change_adapter_mode()`, which stops/deinits/starts the adapter and
  assumes an existing station adapter.
- `__wlan_hdd_cfg80211_join_ibss()` is compiled only under
  `QCA_IBSS_SUPPORT`; otherwise it is a dummy returning 0. The K20 `.config`
  has no `CONFIG_QCA_IBSS_SUPPORT`; external QCACLD Kbuild normally sets it
  to 1 for non-mobile-router builds.
- Changing the hidden injection STA to IBSS would also invalidate the cached
  injection vdev id (`g_hdd_inject_tx_vdev_id`) after
  `hdd_change_adapter_mode()` restarts it.
- The project's `scripts/nl80211-add-ibss.c` uses NEW_INTERFACE, which hits
  the global-monitor guard; it is not usable as-is.

## Revised exclusive plan

True exclusive is a **kernel experiment**, not a userspace switch:

1. Add `CONFIG_QCA_IBSS_SUPPORT` to the kernel build.
2. Add a QDF_IBSS path to `hdd_open_adapter()` or create a second hidden IBSS
   adapter directly in the injection patch, before cfg80211.
3. Set `roam_profile->BSSType = eCSR_BSS_TYPE_START_IBSS` and join
   BSSID `<redacted-mac>` on channel 44.
4. Keep the hidden STA used by `wma_inject` separate from the IBSS ACK cell;
   update the cached injection vdev id if it changes.
5. Verify `policy_mgr_allow_concurrency(PM_IBSS_MODE, 5220, 20MHz)` and the
   monitor + IBSS combination.
6. No hostapd/AP vdev: the reference notes a firmware beacon-TX-offload crash.

Exclusive remains viable only if the driver accepts IBSS in the current
monitor boot. Shared is more likely because it needs no IBSS and no
`QCA_IBSS_SUPPORT`.
