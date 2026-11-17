# Civi stock-driver TX findings

Status: **investigation only**. No Civi boot image, vendor module, or
persistent driver state was modified by these tests.

Device: Xiaomi Civi 4 Pro / Xiaomi 14 Civi (`chenfeng`)

```text
kernel:  6.1.138-android14-11-g0c3d559bcd85-ab14529422
driver:  qca_cld3_qca6750
version: 5.2.1.91G
firmware: signed stock WPSS
```

The tests used the stock qca module and a small helper module built against
the exact 6.1.138 kernel headers. The helper resolves the driver's existing
`wlan_hdd_mgmt_tx()` with a kprobe and calls it with the monitor/AP vdev's
existing wireless_dev. It does **not** create a hidden vdev and does **not**
issue raw WMI commands.

## Results

### 1. cfg80211 action TX to an associated peer works

A raw action frame sent through `NL80211_CMD_FRAME` to the currently
associated AP was accepted by the driver and the AP returned an 802.11 ACK:

```text
mgmt TX status: acked
```

This confirms that the stock driver can transmit a management action frame
when the destination is an existing peer.

### 2. cfg80211 rejects monitor-interface management TX

`NL80211_CMD_FRAME` on the radiotap monitor interface fails in cfg80211 with
`EOPNOTSUPP` before reaching qca. The kernel's `nl80211_tx_mgmt()` switch does
not allow `NL80211_IFTYPE_MONITOR`.

Using the helper to bypass that check did reach qca, but did not put a frame
on air.

### 3. STA off-channel/broadcast action frames need a peer

With the stock STA interface:

- a broadcast probe request was accepted;
- a broadcast action frame was rejected with `ENOTCONN`;
- an action frame to an unassociated destination was rejected;
- an action frame to the associated AP was accepted and ACKed.

qca's management path looks up a peer by destination, then by transmitter.
Broadcast action frames have no destination peer, and the source self-peer is
not enough for this path.

### 4. Monitor self-peer management TX is accepted but not radiated

The helper was loaded while `wlan0` was a radiotap monitor:

```text
type=803
con_mode=4
```

The module resolved:

```text
wlan_hdd_mgmt_tx [qca_cld3_qca6750]
```

and submitted a 40-byte AWDL vendor action frame:

```text
mgmt_tx ret=0 len=40 freq=2437
mgmt_tx ret=0 len=40 freq=5745
```

No crash and no reboot occurred. However:

- no 802.11 ACK was captured for a unicast action frame;
- a strong channel-149 AP at approximately -32 dBm did not answer a broadcast
  probe request;
- an iPhone with AirDrop open was transmitting AWDL action frames on
  channel 149, but did not ACK the injected unicast action or return a probe
  response.

This is consistent with the monitor vdev being RX-only in the firmware: qca
accepts the host request and returns success, but the frame is dropped before
radiating.

### 5. AP-vdev path is constrained by Android concurrency

Android soft AP started as `wlan1` while the STA remained connected. Although
the request asked for channel 149, the driver/framework forced the AP to the
STA channel (channel 36, 5180 MHz) because of STA + AP concurrency.

The helper was loaded against `wlan1` and qca again returned `ret=0` for
management frames, but no probe response or action frame was observed. A
standalone AP start did not succeed cleanly and left the Wi-Fi framework in a
disabled state; a reboot restored stock Wi-Fi.

This test does not prove that an AP vdev can never transmit management frames,
but it rules out the straightforward Android soft-AP route as a usable AWDL TX
path on this firmware.

## What is ruled out

Do not repeat these as TX candidates on the stock Civi driver:

1. cfg80211 `NL80211_CMD_FRAME` on a monitor interface.
2. The monitor vdev self-peer + `wlan_hdd_mgmt_tx()` route.
3. Broadcast action frames through the associated STA vdev.
4. Android soft-AP concurrency on the STA channel as an AWDL transmitter.

## What remains

The K20 design still requires a real hidden STA vdev created through the
driver's own lifecycle/object management. That cannot be validated with a
rebuilt qca module until the exact Civi vendor build environment is available.

The remaining paths are:

1. Obtain the exact Xiaomi/Qualcomm vendor kernel and qca build environment
   for `OS3.0.307.0.WNJCNXM` and port the hidden-STA design in-tree.
2. If the vendor environment is unavailable, keep the K20 as the production
   AirDrop target and leave Civi as an investigation-only device.
3. Manual hostapd AP-only experimentation remains possible, but is a separate
   low-confidence path and should not be treated as a solution.

External USB Wi-Fi is not part of this investigation.

## Restore state

Every test ended with the stock driver and stock Wi-Fi restored:

```text
con_mode=0
wlan0 managed
Wi-Fi enabled
no helper module
no soft AP
```

Civi backups include the current boot, init_boot, vendor_boot, dtbo, vbmeta,
and the current-slot vendor_dlkm and system_dlkm module partitions.
`super` and userdata were intentionally not backed up.
