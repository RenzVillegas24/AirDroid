# Shared-mode experiments

Tools used to probe whether the WCN3990 could run a monitor beside the
associated station, or receive AWDL on the station itself.

| File | Purpose |
|---|---|
| `nl80211-add-monitor.c` | Create a monitor interface on a wiphy. |
| `nl80211-send-awdl.c` | Send an AWDL action frame from a file; supports on-channel and off-channel TX. |
| `nl80211-connect.c` | Direct-BSSID connect helper for an open AP. |
| `nl80211-scan.c` | Targeted nl80211 scan helper. |

The shared conclusion is in `docs/raphael/INVESTIGATION-SHARED.md` on the
`investigation/shared-local` branch. The firmware blocks monitor + associated
STA, packet capture, and STA AWDL action frames.
