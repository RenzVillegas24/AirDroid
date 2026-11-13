# AGENTS.md

## Project context

Owly/AirDrop port for Xiaomi 14 Civi (`chenfeng`, QCA6750). Work is documented
in `docs/INVESTIGATION-LOG.md` and `docs/INTERNAL-RADIO-METHODS.md`. The
internal radio is the target; external USB Wi-Fi is ruled out.

## AirDrop testing rule

**Always ask the user to open AirDrop on the iPhone when an AWDL ACK,
response, peer-discovery, or other live-iPhone test is needed.**

- Do not assume AirDrop is open, even if it was open in a previous test.
- Do not silently start an iPhone-dependent test; ask explicitly first.
- Specify the exact setting needed, e.g. Control Center → AirDrop →
  **Everyone for 10 Minutes**.
- Do not ask the user to keep AirDrop open during builds or unrelated work.
  Ask again only when the test is ready to run.
- Tell the user when the test is finished and they can close AirDrop.
- Treat any captured iPhone AWDL frames as RX-only evidence unless AirDrop
  was explicitly confirmed open for that specific run.

## Safety/restore rule

After monitor/P2P/driver experiments, restore the stock driver and working
Wi-Fi (`con_mode=0`, no test modules), unless the user is actively waiting
for the next test.
