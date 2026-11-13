# K20 exclusive-mode investigation

Status: **not implemented**. The current working path is global monitor +
hidden STA injection, not a true IBSS/AP cell.

## What Owly exclusive mode requires

- Wi-Fi station taken off the air.
- An ad-hoc IBSS cell on the AWDL MAC/BSSID, or a hidden AP on hardware with
  no ad-hoc support.
- The cell gives the firmware an address to ACK and a BSSID filter that passes
  AWDL.
- A monitor fixed on one social channel (44/149/6); AWDL uses
  `Presence::Fixed`.

## Current blockers

- `CONFIG_QCA_IBSS_SUPPORT` is not enabled. The Kbuild variable adds
  `-DQCA_IBSS_SUPPORT` and `lim_ibss_peer_mgmt.o`; without it `join_ibss` is
  an 8-byte stub.
- `hdd_open_adapter()` has no `QDF_IBSS_MODE` case. IBSS is normally reached
  through `hdd_change_adapter_mode()`, which stops/deinits/starts an existing
  station adapter.
- `hdd_start_adapter()` treats IBSS as already initialized, so a fresh IBSS
  adapter gets no vdev.
- In global monitor mode, `alloc_netdev_mq()` gives every adapter the monitor
  radiotap setup and `wlan_mon_drv_ops`; a hidden IBSS netdev needs
  mode-aware netdev setup/ops.
- Converting the live injection STA to IBSS destroys/recreates its vdev and
  leaves `g_hdd_inject_tx_vdev_id` stale.
- Policy manager allows IBSS on 5220 only with zero existing connections. With
  a 5 GHz STA present, PCL tables force IBSS to 2.4 GHz or invalid.
- A separate IBSS adapter with a different MAC cannot ACK unicast to the AWDL
  MAC; a second adapter with the same MAC is rejected as a duplicate.
- WCN3990 management TX from an IBSS vdev is unproven. The reference notes say
  firmware accepts management TX from AP/STA vdevs; an AP vdev is higher risk
  because of beacon-offload behavior.

## Minimal change set (not implemented)

1. Enable `CONFIG_QCA_IBSS_SUPPORT` through `WLAN_CFG_OVERRIDE` or
   `configs/default_defconfig`.
2. In `wlan_hdd_main.c`:
   - add `QDF_IBSS_MODE` to the station case list in `hdd_open_adapter()`;
   - set `wdev.iftype = NL80211_IFTYPE_ADHOC`;
   - route IBSS through `hdd_start_station_adapter()`, or call it directly for
     the hidden IBSS adapter;
   - create the hidden vdev as IBSS from `hdd_inject_get_tx_vdev_id()`, selected
     by a build/module option so the proven STA mode remains available;
   - make netdev setup/ops mode-aware so only the monitor vdev gets monitor
     radiotap ops.
3. If creating IBSS through nl80211, relax the global-monitor rejection for
   ADHOC only and preserve `QDF_IBSS_MODE` in the type mapping.
4. If converting a live adapter, refresh `g_hdd_inject_tx_vdev_id` after the
   vdev is recreated.
5. Add IBSS to `policy_mgr_clear_concurrency_mode()` to avoid leaking the
   concurrency bit.

## Experiment sequence

1. Build gate: confirm `-DQCA_IBSS_SUPPORT`, non-stub `join_ibss`, and
   `lim_ibss_peer_mgmt.o`.
2. Boot global monitor; confirm hidden IBSS vdev creation without a reset.
3. Join an IBSS cell on 5220 with the AWDL MAC and a BSSID candidate; verify
   beacons and no firmware reset.
4. Test management TX from the IBSS vdev using a captured AWDL frame.
5. Test ACK coverage and a data path against the Arch receiver.
6. Only after those gates, consider an iPhone AirDrop test.

## Fallbacks

- If join returns `-ECONNREFUSED`, check policy-manager connection state.
- If `-EOPNOTSUPP`, the IBSS build did not actually enable the feature.
- If the IBSS vdev start resets the firmware, revert to the proven hidden-STA
  image.
- If IBSS gives ACK but no management TX, exclusive is not viable with this
  vdev type; an AP or firmware/WMI change is the remaining route.
- If the cell beacons but does not ACK AWDL, try the alternate BSSID and then
  conclude the firmware IBSS RX filter rejects AWDL.

## Recommendation

Exclusive is a kernel experiment, not a userspace switch. Shared mode remains
the first focus because it avoids IBSS and `CONFIG_QCA_IBSS_SUPPORT`, but the
current packet-capture monitor path does not deliver AWDL management frames.
