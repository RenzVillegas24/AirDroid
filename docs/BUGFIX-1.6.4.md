# 1.6.4 bug fix

App-only release. No kernel or boot-image change: 1.6.3 already required the
AirDrop-capable kernel, and this release only fixes the app.

## User-facing fixes

- **A fresh install could never find an iPhone.** Discovery stayed on whatever
  channel the driver came up on, so `awdl0` received nothing on 149, 44 or 6.
- **The iPhone showed a UUID or a generic "iPhone".** The AirDrop name was only
  read by an investigation helper that was never shipped.
- **Phones kept booting into Transfer mode.** The one-shot reboot flag was never
  consumed, so every reboot re-entered Transfer mode.
- **A reboot could enter Transfer mode minutes later**, when the app was opened,
  because the boot switch waited on a slow `su`.
- **An old helper could be staged after a reinstall.** The asset copy ran after
  a root command and was skipped when root was not granted yet.
- **Stale investigation tools were used silently.** The app depended on files
  left in `/data/local/tmp/owly-app/` by the investigation branches.
- **A phone that turned AirDrop off stayed listed as ready.** A learned name was
  treated as proof of readiness, so the peer kept showing as a send target
  after its service was withdrawn.

No name, MAC, channel or peer table is hardcoded. Names come from the AirDrop
announcement, channels from the configured channel, MACs from the interfaces.

## Root causes and fixes

### 1. Monitor channel was never tuned

`set_radio_channel()` only executed
`/data/local/tmp/owly-app/owly-nl80211-set-channel`. That binary came from
`scripts/nl80211-set-channel.c` on the investigation branches and was never an
APK asset, so on a clean install the function returned silently. The monitor
kept the driver's default channel: injection worked and TX counters rose, but
RX stayed at zero.

Fix: tune in the helper through nl80211 (`SET_WIPHY` on `wlan0`, the same
`cfg80211_set_monitor_channel` path as the C tool) and log the result:

```text
RADIO_CHANNEL channel=149 freq=5745 via=nl80211
```

A failure is now visible (`SET_FAILED`), never silent.

### 2. Display names came from a dev-only sniffer

The helper spawned `owly-monitor-full` and scraped `dnm=` from its output. That
binary was only present on the development phone. Without it the app showed the
peer's ARPA host name, which iOS sets to a UUID, and `Peer.displayName()` then
fell back to "iPhone".

Fix, in the protocol layer:

- `owly-awdl`: parse the Service Response TLV (type 2) and its DNS-SD TXT
  record for `dnm=`, with a bounds-checked fallback scan; data frames are
  scanned too, since iOS repeats the string there. `Peer.name` prefers that
  name and falls back to ARPA.
- `owly-airdrop`: publish our own `dnm=<configured name>` in the mDNS TXT and
  derive the SRV host label from that name instead of the hardcoded
  `airdroid-k20`.

### 3. Staging depended on root at the wrong moment

`prepare()` started with a root `pkill`. When `su` was not yet granted (fresh
install, boot), the exception aborted the method before the assets were copied,
so an older helper already in `/data/local/tmp/owly-app/` stayed in place.

Fix: copy the APK assets to the app's private directory first, run the kill as
a best effort afterwards, and make the staging `cp` fail the step
(`|| exit 1`). Staging now also prunes anything the APK does not ship, keeping
only `owly-k20-helper`, `owly-mode.sh`, `owly-watchdog.sh` and the helper's own
`state/`.

### 4. Boot flag was permanent, and could fire late

`/data/adb/owly-transfer-mode` was written by `reboot-enter` and never removed.
Every `BOOT_COMPLETED` started the boot enter, and when `su` was slow the switch
completed whenever it unblocked, typically when the app was opened.

Fix:

- The flag is one-shot: `BootReceiver` deletes it as soon as it is consumed.
- The flag check retries 5 × 2 s, for a `su` that is not ready at `BOOT_COMPLETED`.
- A boot enter expires 120 s after boot (`BOOT_ENTER_WINDOW_MS`); a stale
  request can no longer change modes later.
- Leaving Transfer mode clears any pending boot flag.

`Reboot into Transfer mode` still means: enter once on the next boot, then
behave normally.

### 5. Readiness was derived from the name

The nearby list marked a peer ready when it had a name (`name.isNotBlank() ||
seen`). That was written before names were parsed reliably; once `dnm=` arrived,
every named AWDL peer looked AirDrop-ready forever. `LOST` also removed the MAC
from nothing, so `seen` stayed true, and a 10 s "recently found" guard dropped
the only `LOST` the browser would ever emit.

Fix: readiness is a state machine over the service events, and the name is
purely a label.

- `FOUND` adds the MAC to `seen`; `LOST` removes it.
- `LOST` schedules the downgrade after the same 1.2 s debounce `FOUND` uses;
  a `FOUND` inside that window cancels it, so radio hopping cannot flicker.
- `PEER` updates take the state from `lost` / `seen`, and keep the previous
  state when neither changed; `AWDL name:` only updates the name.
- Pending jobs are cancelled on `PEER_GONE`, scan reset and transfer recovery.

A peer that leaves AWDL entirely is still removed by `PEER_GONE`; a peer that
only withdraws AirDrop stays listed with `airdrop=NOT_READY`.

### 6. Investigation boot hooks (device state, not app code)

The development phone had KernelSU boot scripts from November that wrote
`con_mode=4` unconditionally:

```text
/data/adb/post-fs-data.d/owly-mode-boot.sh
/data/adb/service.d/owly-mode-boot.sh
/data/adb/service.d/owly-boot-test.sh
/data/adb/owly-airdrop-mode
```

They bypassed the app entirely and made the radio come up in monitor mode with
Wi-Fi unavailable. They were removed from the development phone. No such hook
is installed or removed by the app, and the cleanup command used for them is
not part of this release.

## Verification

Both phones, same build, AirDrop open on the iPhone:

- `RADIO_CHANNEL channel=149 freq=5745 via=nl80211`
- peers resolve as `RenziPhone` (iPhone) and `AirDroid K20` (the other phone)
- `/data/local/tmp/owly-app/` contains only the three assets and `state/`
- after a normal reboot: `boot: Transfer-mode flag present=false`, Wi-Fi on,
  no helper process, no monitor mode
- after `Reboot into Transfer mode`: one boot enter, flag consumed, next reboot
  back to Wi-Fi

## Files changed

- `android-app/helper/src/main.rs`
- `android-app/app/src/main/java/io/moonbase/airdroid/AirDroidController.kt`
- `android-app/app/src/main/java/io/moonbase/airdroid/BootReceiver.kt`
- `android-app/app/src/main/java/io/moonbase/airdroid/AirDroidService.kt`
- `android-app/app/src/main/java/io/moonbase/airdroid/ControlReceiver.kt`
- `android-app/app/src/main/java/io/moonbase/airdroid/MainActivity.kt` (preview fixture name)
- `android-app/app/build.gradle.kts` (1.6.4, versionCode 13)
- `third_party/owly/crates/owly-awdl/src/tlv.rs`
- `third_party/owly/crates/owly-awdl/src/node.rs`
- `third_party/owly/crates/owly-awdl/src/peer.rs`
- `third_party/owly/crates/owly-airdrop/src/proto/mdns.rs`
- `scripts/airdroid-adb.sh`

The Owly changes are local to the pinned submodule; they need a fork commit and
a submodule bump before the release tag.
