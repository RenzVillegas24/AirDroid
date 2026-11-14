# Shared radio helpers

These are bring-up and diagnostic tools used while developing the K20 AWDL
radio. They are not all part of the current Android app build.

## Bundled by the app

Only these assets are installed by the current app:

- `owly-mode.sh` — switch between normal Wi-Fi and monitor/hidden-STA mode
- `owly-k20-helper` — Rust monitor/TAP/AWDL/AirDrop engine, built from
  `android-app/helper` against `third_party/owly`

## C diagnostics

These are aarch64 static tools used during radio bring-up. They are not
required for the current app build.

- `monitor-recv.c` — receive 802.11 action frames from a radiotap monitor
  interface.
- `monitor-ack.c` — capture 802.11 ACK control frames addressed to the hidden
  injection address. Pass the address at runtime; it is not hardcoded.
- `monitor-send-frame.c` — send a raw 802.11 frame through the monitor TX hook.
- `nl80211-set-channel.c` — set the monitor interface channel via nl80211.
- `nl80211-add-monitor.c` — create a monitor interface through nl80211.
- `nl80211-add-ibss.c` — create/join an IBSS interface through nl80211.
- `nl80211-send-awdl.c` — send a management/action frame off-channel through
  nl80211 and observe responses.
- `nl80211-tx.c` — send a unicast probe through nl80211 and wait for the
  management-TX ACK status.
- `inject-probe.c` — raw 802.11 probe injector through a packet socket.

Example static build:

```bash
aarch64-linux-gnu-gcc -static -O2 -o owly-monitor-send-frame monitor-send-frame.c
aarch64-linux-gnu-gcc -static -O2 -o owly-nl80211-set-channel nl80211-set-channel.c
aarch64-linux-gnu-gcc -static -O2 -o owly-inject-probe inject-probe.c
```

Legacy binaries `owly-monitor-full` and `owly-monitor-probe` were used during
earlier experiments. Their sources were not tracked, and the current app no
longer installs them; the unified Rust helper owns the monitor/TAP path.

## Shell helpers

- `droidy-adb.sh` — ADB control/log interface for the current app.
- `owly-mode.sh` — mode switch used by the app.
- `adb-root.sh`, `dmesg-log.sh` — device diagnostics.
- `awdl-capture.sh`, `awdl-pcap.sh` — AWDL capture helpers.
- `monitor-test.sh`, `nl80211-ack-test.sh`, `nl80211-monitor-ack.sh` —
  injection/ACK experiments.
- `inject-probe.py` — host-side probe injector.

Device-specific scripts live in:

- `../workspaces/chenfeng/scripts/`
- `../workspaces/raphael/scripts/`

Compiled experiment binaries live in `../workspaces/shared/bin/` (gitignored).
