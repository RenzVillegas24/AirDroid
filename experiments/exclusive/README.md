# Exclusive-mode experiments

Tools used to test Owly-style exclusive cells on the WCN3990.

| File | Purpose |
|---|---|
| `nl80211-join-ibss.c` | Join an existing IBSS interface to a cell. |
| `nl80211-rx-action.c` | Register for Apple/public action frames on an interface and print RX frames. |
| `host-p2p-send.c` | Host-side P2P-device AWDL sender used as a local test peer. |
| `helper-main.rs.patch` | Test-only helper patch that reads `owlyap*` instead of `wlan_inj*`. |

The exclusive conclusion is in `docs/raphael/INVESTIGATION-EXCLUSIVE.md` on
the `investigation/exclusive-local` branch. IBSS join resets the device; the
hidden AP starts but monitor RX stops while it is active. Owly requires
monitor + cell, so there is no Owly-compatible exclusive path on this
firmware.
