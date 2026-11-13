# Droidy

Experimental Android AirDrop / AWDL work for two Qualcomm phones.

| Phone | Codename | Radio | Status |
|---|---|---|---|
| Xiaomi 14 Civi / Civi 4 Pro | `chenfeng` | QCA6750 / WCN6750 | blocked by signed WPSS firmware and monitor-TX behavior |
| Redmi K20 Pro / Mi 9T Pro | `raphael` | WCN3990 / SM8150 | **monitor RX + hidden-STA-vdev injection working** |

The current injection testbed is the **K20 Pro**. Its stock kernel was replaced
with a patched `4.14.357-openela` kernel; monitor RX, frame TX, and AWDL
PSF/MIF injection have been verified. Full AirDrop protocol handling is still
the next software step.

## Layout

```text
docs/
  chenfeng/    Civi 4 Pro / QCA6750 findings
  raphael/     K20 Pro / WCN3990 findings

workspaces/
  chenfeng/    captures, artifacts, logs, source for the Civi
  raphael/     kernel source, configs, boot images, captures, scripts
  shared/      shared tools and the Owly reference checkout

android-app/   Kotlin "Droidy" test APK (Material 3 Expressive UI)
scripts/       general monitor/AWDL helpers
```

## Start here

- [K20 injection findings](docs/raphael/K20-INJECTION.md)
- [Civi radio methods](docs/chenfeng/INTERNAL-RADIO-METHODS.md)
- [Android app](android-app/README.md)

## K20 Pro quick facts

- Exact source: `Evolution-X-Devices/kernel_xiaomi_raphael`, commit
  `2a67a9737583b2e6158cd8ef2dc026439bd80e4e`
- Kernel: `4.14.357-openela-perf`
- Monitor: boot with `wlan.con_mode=4`; `wlan0` type 803
- Injection: hidden STA `wlan_inj0` via the ported `ex18a` design
- Verified: five probe frames injected and captured on channel 6; AWDL PSF/MIF
  sent; a new iPhone AWDL peer appeared after the MIF run
- Boot images: `workspaces/raphael/boot-images/`
- One-time `scheduling while atomic` warning on first TX has a source fix;
  rebuild is pending

## Android APK

`android-app/out/owly-radio.apk` is a signed Kotlin app targeting Android
16 / API 36. It installs the bundled aarch64 radio tools, checks root/radio,
sends probe and AWDL MIF frames, captures AWDL, and includes an AirDrop
transfer card.

The transfer card invokes a static Rust helper built from the local Owly
reference. The helper starts Owly's monitor/TAP backend, browses
`_airdrop._tcp`, and uses the AirDrop sender. The send path is ready for an
end-to-end iPhone test once the fixed kernel is flashed.

Build and install:

```bash
cd android-app
./build.sh
adb install -r out/owly-radio.apk
```

## Device state / safety

- Civi 4 Pro: stock driver, Wi-Fi working, `con_mode=0`, no test modules.
- K20 Pro: stock boot image restored, Wi-Fi working; injection boot image saved
  but not currently flashed.
- AirDrop should be opened only for a specific live-iPhone test, and closed
  immediately afterward.

## Credits and references

Droidy would not exist without the open-source AirDrop/AWDL work below. Owly
is the primary reference and the source of the Rust radio, AWDL and AirDrop
stack used by the helper.

### Primary reference

- **[Owly](https://github.com/t4t5/owly)** — the AWDL/AirDrop Rust stack,
  radio abstraction, sender/receiver protocol, mDNS, and the reference used
  under `workspaces/shared/reference/owly-ref`.
- [owly-airdrop / owly-radio / owly-awdl](https://github.com/t4t5/owly) —
  the core crates used by the K20 helper.
- [Omdrop Owl](https://github.com/t4t5/omdrop-owl) — the companion
  AWDL/monitor tooling that informed the K20 bring-up.

### Kernel and radio bring-up

- [Evolution-X-Devices/kernel_xiaomi_raphael](https://github.com/Evolution-X-Devices/kernel_xiaomi_raphael)
  — exact K20 Pro kernel source used for the monitor/injection work.
- [ex18a/qcacld-monitor-injection](https://github.com/ex18a/qcacld-monitor-injection)
  — WCN3990 monitor-mode injection design and hidden-vSTA approach.
- [MiCode/vendor_qcom_opensource_wlan](https://github.com/MiCode/vendor_qcom_opensource_wlan)
  — Qualcomm WLAN driver source reference.
- [MiCode/Xiaomi_Kernel_OpenSource](https://github.com/MiCode/Xiaomi_Kernel_OpenSource)
  — Xiaomi kernel source for the Civi / chenfeng research.
- [arter97-mirror/caf_qca-wifi-host-cmn](https://github.com/arter97-mirror/caf_qca-wifi-host-cmn)
  and [EssentialOpenSource/qca-wifi-host-cmn](https://github.com/EssentialOpenSource/qca-wifi-host-cmn)
  — CAF WLAN host driver references.
- [kimocoder/qualcomm_android_monitor_mode](https://github.com/kimocoder/qualcomm_android_monitor_mode),
  [nowsecure/airspy](https://github.com/nowsecure/airspy), and
  [saparKGZ/qca6490-mon-tx](https://github.com/saparKGZ/qca6490-mon-tx)
  — Qualcomm monitor-mode and TX research.

### AirDrop / AWDL research

- [seemoo-lab/opendrop](https://github.com/seemoo-lab/opendrop) — open AirDrop
  protocol implementation and protocol documentation.
- [seemoo-lab/owl](https://github.com/seemoo-lab/owl) and
  [seemoo-lab/privatedrop](https://github.com/seemoo-lab/privatedrop) —
  AirDrop/AWDL research implementations.
- [seemoo-lab/wireshark-awdl](https://github.com/seemoo-lab/wireshark-awdl) —
  AWDL frame analysis.
- [alyx/ath12k-wcn7850-awdl](https://github.com/alyx/ath12k-wcn7850-awdl) and
  [jedbillyb/airdrop-mt7921](https://github.com/jedbillyb/airdrop-mt7921) —
  Linux Wi-Fi AWDL implementations that informed the monitor/ACK work.
- [xmtggh/android-awdl](https://github.com/xmtggh/android-awdl),
  [BenItBuhner/AirDrop-for-Everything](https://github.com/BenItBuhner/AirDrop-for-Everything),
  and [spiral009/aviumui-wlan-tools](https://github.com/spiral009/aviumui-wlan-tools)
  — Android AWDL and WLAN tooling references.
- [DanielNappa/ring-around-the-mosey](https://github.com/DanielNappa/ring-around-the-mosey),
  [thelok1s/mosey-extended](https://github.com/thelok1s/mosey-extended),
  [swift-lee/moseyPLK-public](https://github.com/swift-lee/moseyPLK-public), and
  [leofungwai/oneplus13-mosey-research](https://github.com/leofungwai/oneplus13-mosey-research)
  — Mosey/AirDrop research that shaped the feature comparison.
- [brentkearney/omdrop-awdl](https://github.com/brentkearney/omdrop-awdl) and
  [netmojo-systems/omdrop-awdl](https://github.com/netmojo-systems/omdrop-awdl)
  — additional AWDL tooling references.
- [adineo-cmd/librephone-blob-research](https://github.com/adineo-cmd/librephone-blob-research)
  — firmware/blob research context.

### Android UI

- [Material 3](https://m3.material.io/) and
  [Material 3 toolbars](https://m3.material.io/components/toolbars/overview) —
  floating toolbar, expressive surfaces and navigation.
- [Material 3 loading indicator](https://m3.material.io/components/loading-indicator/overview)
  — transfer and indeterminate progress treatment.
- [Google Material Design Icons](https://github.com/google/material-design-icons)
  — Material Symbols Rounded vectors used by the app.
- [Jetpack Compose](https://developer.android.com/jetpack/compose) and
  [Compose Material 3](https://developer.android.com/jetpack/androidx/releases/compose-material3)
  — app UI runtime and components.
- [Android Open Source Project](https://source.android.com/) — platform,
  notification and Live Updates APIs.

Additional radio links and experiment notes are collected in
[docs/chenfeng/INTERNAL-RADIO-METHODS.md](docs/chenfeng/INTERNAL-RADIO-METHODS.md).
