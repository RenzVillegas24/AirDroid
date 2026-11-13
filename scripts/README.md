# Shared radio helpers

These scripts are shared monitor/AWDL experiments used across the two devices.

- `monitor-recv.c`, `monitor-ack.c`, `monitor-send-frame.c` — raw monitor tools
- `nl80211-*.c`, `nl80211-*.sh` — channel/interface/mgmt-frame experiments
- `inject-probe.c`, `inject-probe.py` — probe injection helpers
- `awdl-capture.sh`, `awdl-pcap.sh`, `monitor-test.sh` — capture helpers
- `adb-root.sh`, `dmesg-log.sh` — device diagnostics

Device-specific scripts live in:

- `../workspaces/chenfeng/scripts/`
- `../workspaces/raphael/scripts/`

Compiled aarch64 helpers are in `../workspaces/shared/bin/`.
