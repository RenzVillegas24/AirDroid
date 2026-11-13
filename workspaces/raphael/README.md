# K20 Pro workspace

Device: Mi 9T Pro / Redmi K20 Pro (`raphael`), SM8150 / WCN3990.

Status: monitor RX + hidden-STA-vdev injection verified. Full AirDrop userspace
is the remaining work.

## Layout

- `kernel/` — exact Evolution-X `kernel_xiaomi_raphael` source at
  `2a67a9737583`; contains the injection port and source-only cleanup. The
  monitor-open atomic-context fix is present but not yet rebuilt.
  - `.config` — exact K20 config retained
- `config/` — `k20-kernel.config` and the original `/proc/config.gz`
- `boot-images/` — `boot-current.img` (stock/KernelSU) and
  `boot-owly-monitor.img` (injection + `wlan.con_mode=4`), plus unpacked
  `boot-unpack/`
- `boot-artifacts/` — unpacked kernel/DTB/ramdisk for repacking
- `captures/` — AWDL captures, injection logs, screenshots, and K20 evidence
- `logs/` — host/kernel build logs and patch attempts
- `scripts/` — boot dump/pack scripts and one-shot service installer
- `references/` — ex18a and SM8150 injection reference patches
- `tools/` — reserved for K20-specific helpers

## Key captures

- `captures/iphone-awdl.txt` — 86 frames; 69 iPhone AWDL action frames
- `captures/awdl-response2.txt` — 20 K20-written PSFs captured
- `captures/awdl-mif-resp.txt` — 10 K20 MIFs plus new `aa:19` peer
- `captures/k20-inject-now.txt` — `wlan_inj0` creation and probe TX
- `captures/cap-probe.txt` — five injected probes captured
- `captures/owly-radio-*.png` — app screenshots

See `docs/raphael/README.md` and `docs/raphael/K20-INJECTION.md`.
