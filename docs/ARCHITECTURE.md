# Architecture

This document explains how the Android app, Rust helper, Owly stack and the
K20 kernel work together.

## Components

```text
MainActivity / Compose UI
        │
        ▼
CrossPairController + CrossPairService
        │  commands and log lines
        ▼
owly-k20-helper (Rust)
        │
        ├─ Owly AWDL node
        ├─ mDNS browse/register
        ├─ AirDrop receiver
        └─ AirDrop sender
        │
        ▼
wlan0 monitor + wlan_inj* + awdl0
        │
        ▼
K20 WCN3990 kernel
```

### App

The app owns:

- peer cards and transfer cards
- the foreground service that keeps the controller alive
- incoming-request dialog and notification
- settings for channel, discovery and visibility
- root commands that enter or exit Transfer mode

### Helper

`owly-k20-helper` is a long-lived Rust process started by the app with root.
It owns the live radio session and exposes a line-based command/log protocol.

Important commands include:

```text
session <channel> <name> <receive-dir> <port>
scan <seconds> <id>
send <id> <mac> <path>
cancel <id>
accept <request-id>
decline <request-id>
abort-receive <request-id>
set-transfer-policy pause_discovery=<0|1> visible_during_transfer=<0|1>
quit
```

The helper prints stable event lines such as:

```text
SESSION_READY
RADIO_READY
PEER
PEER_GONE
FOUND
LOST
SEND_FILE_START
SEND_FILE_PROGRESS
SEND_CANCELLED
RECEIVE_REQUEST
RECEIVE_FILE_PROGRESS
RECEIVE_ABORTED
```

### Owly

Owly provides:

- AWDL PSF/MIF/action frames
- peer discovery and channel sequences
- AirDrop `/Discover`, `/Ask` and `/Upload`
- mDNS service registration and browsing
- TLS identity and AirDrop archive handling

The K20 fork adds the fixed-channel and K20 radio workarounds.

### Kernel

The K20 kernel provides the pieces Android normally does not expose:

- `wlan.con_mode=4` monitor mode
- the hidden injection STA (`wlan_inj*`)
- monitor TX through the WMI path
- safe teardown between monitor and station mode

The kernel changes must be in the boot image. They are not supplied by the
app or by a root manager.

## Radio modes

### Normal Wi-Fi

```text
con_mode=0
wlan0 = managed station
Android Wi-Fi works normally
no wlan_inj*
no awdl0
```

### Transfer mode

```text
con_mode=4
wlan0 = monitor
wlan_inj* = hidden STA used as the WMI TX tag
awdl0 = TAP interface for IPv6/TCP
Wi-Fi disabled
```

The monitor MAC is the AWDL identity. The hidden STA MAC is not used as the
peer address.

`awdl0` carries the IPv6 packets that the AirDrop sender and receiver use.
Owly turns those packets into AWDL data frames and back.

## Entering and leaving Transfer mode

The app calls `owly-mode.sh` with root:

```text
owly-mode.sh enter <app-pid>
owly-mode.sh exit
owly-mode.sh status
```

The live switch:

1. disables Android Wi-Fi
2. brings `wlan0` down
3. writes `4` to `/sys/module/wlan/parameters/con_mode`
4. brings `wlan0` up as monitor
5. waits for the hidden injection STA and `awdl0`
6. starts the helper session

Exit reverses the sequence and restores normal Wi-Fi.

The app also arms a kernel owner PID and a root watchdog. If the app process
disappears without a clean exit, the watchdog restores `con_mode=0` and Wi-Fi.

## Discovery

There are three discovery signals:

1. **AWDL peers** — the monitor hears PSF/MIF/action frames. The helper emits
   `PEER` and `PEER_GONE`.
2. **AirDrop mDNS** — the helper browses `_airdrop._tcp` and emits `FOUND` and
   `LOST`.
3. **BLE wake** — the app advertises an Apple Continuity-style wake frame so
   the iPhone wakes its AirDrop stack.

A named AWDL peer can be tried through `/Discover` even when mDNS is missing.
mDNS is treated as an availability hint, not the only source of truth.

The peer record carries:

```text
mac
name
device class
RSSI
best supported AWDL channel
AirDrop state: READY / NOT_READY / UNKNOWN
```

The UI keeps a named peer visible while it is still in the AWDL peer list.
A peer is removed only on `PEER_GONE`; an mDNS `LOST` changes availability,
not presence.

## Channel selection

AWDL social channels are:

```text
6, 44, 149
```

Auto scans:

```text
149 → 44 → 6
```

The peer advertises a 16-slot channel sequence. The helper exposes the best
supported social channel from that sequence. Auto matches that channel before
locking.

This matters because a fixed monitor cannot hop with the iPhone. If Auto
stays on 6 while the iPhone spends most slots on 149, only a few slots carry
data, which appears as a 512 KB stall. Matching the peer's supported channel
avoids that case.

Channel 6 remains available for true 2.4 GHz-only operation. A manual fixed
channel is for troubleshooting and shows a warning in the app.

## Sending

The app stages a file, starts BLE, and sends a `send` command to the helper.

The helper:

1. waits for the selected AWDL peer
2. resolves the direct peer address and cached mDNS address
3. requires `/Discover` to answer
4. opens `/Ask`
5. waits for the iPhone prompt
6. uploads the compressed archive over `/Upload`
7. reports progress and the final result

The direct peer address is tried before cached mDNS. `/Discover` is the
receiver-port gate: it prevents a doomed `/Ask` connection when the iPhone's
AirDrop port is not open.

### Cancellation

Send cancellation is owned by the Owly sender:

1. the helper receives `cancel <id>`
2. the sender stops reading and writing
3. the connection is closed with a retryable TLS/TCP close, then an abortive
   close
4. the helper emits `SEND_CANCELLED`

The session, receiver and peer discovery stay alive.

## Receiving

The helper binds an AirDrop receiver on the AWDL interface and registers the
`_airdrop._tcp` service.

When an iPhone sends an offer:

1. the receiver emits a request
2. the app shows the incoming dialog or notification
3. the user accepts or declines
4. the receiver writes the upload into a staging directory
5. the helper emits per-file progress and completion events
6. the final file is moved into `/sdcard/Download/CrossPair`

`abort-receive <id>` cancels only that request. The receiver keeps listening
for other transfers.

## Root and kernel boundary

The app needs root to:

- run `owly-mode.sh`
- start the helper as root
- open the monitor interface

The root manager only grants root in this flow. It does not provide the
monitor-mode kernel code. The kernel changes are compiled into the boot image.

A userspace package can install the APK and helper, but it cannot add the
monitor hooks, hidden STA or WMI TX support that live in the kernel.

## Failure behavior

- A missing kernel patch means Transfer mode cannot start; normal Wi-Fi
  remains available.
- A force-stopped app triggers the watchdog Wi-Fi restore.
- A failed send leaves the receiver and radio session running.
- A failed receive is isolated to that request.
- mDNS loss does not remove a named AWDL peer.
- A peer is removed only after the AWDL peer disappears.

## Source map

```text
android-app/app/src/main/java/io/moonbase/crosspair/
  MainActivity.kt          Compose UI
  CrossPairController.kt    state, commands, peers, transfers
  CrossPairService.kt       foreground service
  ControlReceiver.kt       ADB controls

android-app/helper/src/main.rs
  session loop, send/receive jobs, mDNS, peer events

third_party/owly/
  crates/owly-awdl/        AWDL frames, channels, peers
  crates/owly-airdrop/     AirDrop TLS, mDNS, sender, receiver
  crates/owly-radio/       monitor/injection radio backend

kernel/
  K20 monitor/injection patches
```
