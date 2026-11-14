# Unified Home and transfer tracking — proposed plan

Status: **implemented on `feature/unified-home`; live validation in progress**
Branch: `feature/unified-home`
Last updated: 2026-11-14

This document captures the current design discussion. Nothing below should be
implemented beyond what is already on the branch until the approval checklist
at the end is accepted.

## Goals

- Keep the Rust helper as the radio/protocol engine.
- Move user-facing state and policy into Kotlin.
- Make the Home screen the single place for discovery, sending, receiving, and
  received files.
- Track every outgoing and incoming file separately.
- Show live progress in the app and in system notifications.
- Make incoming requests obvious without creating noise for discovery.
- Prioritize the active transfer over background discovery work.

## Ownership split

### Kotlin owns

- Home/session UI state
- receiver lifecycle state and automatic receive state
- peer list, selection, sorting, and pinning
- file actions and Android pickers
- incoming request dialog/notification behavior
- transfer list and per-file state
- notification IDs, channels, actions, and grouping
- auto-channel policy
- file-source handling: Files, Photos, Videos, Folder, Camera, Audio, Paste

### Rust helper owns

- monitor and `awdl0`
- raw AWDL RX/TX
- mDNS browse/registration
- AirDrop TLS and transfer protocol
- receiver TCP server
- sending/receiving bytes
- emitting structured transfer and peer events

## Home layout

The top-level bottom navigation remains:

```text
Home    Logs    Settings
```

Home header:

```text
Droidy
Nearby AirDrop and received files
```

Home content:

```text
NEARBY AIRDROP
[ RenziPhone ] [ peer ] [ peer ] →

┌─────────────────────────────────────┐
│   Sent (2)     │    Received (5)    │
└─────────────────────────────────────┘

[content for the selected inner tab]
```

- The peer carousel stays visible above the inner tabs.
- The inner tabs are **Sent** and **Received**, not “Transfers” and “Received”.
- “Transfers” was rejected because it sounds bidirectional and makes the
  second tab ambiguous.
- `Sent` means outgoing files.
- `Received` means incoming files.

## Sent tab

The Sent tab contains every outgoing file:

- waiting for receiver
- waiting for acceptance
- actively uploading
- completed
- failed
- cancelled

Wireframe:

```text
SENDING
┌────────────────────────────────────┐
│ ↑  photo.jpg → RenziPhone          │
│    ███████████░░░░  63%            │
│    4.2 / 6.7 MB · 1.8 MB/s     X   │
└────────────────────────────────────┘

SENT
┌────────────────────────────────────┐
│ ✓  document.pdf → RenziPhone       │
│    Delivered · 2.1 MB · 18:42      │
└────────────────────────────────────┘
```

Each file gets its own card. A folder sent as a zip is one file card named after
the archive unless per-entry tracking is added later.

## Received tab

The Received tab contains every incoming transfer and received file:

- incoming request waiting for Accept/Decline
- active download
- completed files
- failed downloads

Wireframe:

```text
RECEIVING
┌────────────────────────────────────┐
│ ↓  video.mov ← RenziPhone          │
│    ████░░░░░░░░░░  22%             │
│    1.2 / 8.4 MB · 1.4 MB/s         │
└────────────────────────────────────┘

RECEIVED
┌────────────────────────────────────┐
│ photo.jpg                          │
│ 6.7 MB · from RenziPhone · 18:41   │
│ [Open] [Share] [Delete]            │
└────────────────────────────────────┘
```

## Incoming request behavior

Incoming requests use both app and system surfaces, with a foreground/background
priority rule.

### App in foreground

Show a Material 3 dialog immediately:

```text
Incoming AirDrop

RenziPhone wants to send:
2 files · 12.4 MB

[Decline]                    [Accept]
```

### App in background

Post a high-priority system notification with actions:

```text
Incoming AirDrop from RenziPhone
2 files · 12.4 MB

[Decline] [Accept]
```

Tapping the notification opens the app and shows the same dialog.

### Explicitly not notified

Do not post a notification for:

- receiver ready
- receiver started/stopped
- peer discovery
- channel search
- mDNS browse events

The current `RECEIVE_READY` notification is considered a bug.

## Transfer model

Kotlin owns a per-file model:

```kotlin
enum class Direction { SENT, RECEIVED }

enum class TransferStatus {
    WAITING,
    OFFERED,
    ACCEPTED,
    TRANSFERRING,
    COMPLETE,
    FAILED,
    CANCELLED,
}

data class TransferItem(
    val id: String,
    val direction: Direction,
    val peerName: String,
    val fileName: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val status: TransferStatus,
    val error: String? = null,
    val localPath: String? = null,
    val startedAt: Long,
    val completedAt: Long? = null,
)
```

Controller state:

```kotlin
activeTransfers: List<TransferItem>
completedTransfers: List<TransferItem>
incomingRequest
```

A transfer ID must be stable across helper events, UI updates, and
notifications.

## Helper event contract

### Send

```text
SEND_FILE_START id=<id> name=<file> total=<bytes>
SEND_FILE_PROGRESS id=<id> bytes=<n> total=<n>
SEND_FILE_DONE id=<id> path=<path>
SEND_FILE_ERR id=<id> reason=<text>
```

### Receive

```text
RECEIVE_REQUEST id=<id> peer=<addr> files=<n> total=<bytes> sender=<name>
RECEIVE_REQUEST_FILES id=<id> names=<list>
RECEIVE_ACCEPTED id=<id> total=<bytes>
RECEIVE_FILE_START id=<id> name=<file> total=<bytes>
RECEIVE_FILE_PROGRESS id=<id> name=<file> bytes=<n> total=<n>
RECEIVE_FILE_DONE id=<id> name=<file> path=<path>
RECEIVE_DONE id=<id>
RECEIVE_FAILED id=<id> reason=<text>
```

The existing aggregate progress can remain for backwards compatibility, but the
UI and notification layer should consume per-file events.

## Notification design

Two channels:

```text
droidy_requests   HIGH    incoming AirDrop requests
droidy_transfers  LOW     send/receive progress
```

### Active send

```text
Sending photo.jpg
RenziPhone · 4.2 / 6.7 MB · 1.8 MB/s
[Cancel]
```

### Active receive

```text
Receiving video.mov
RenziPhone · 1.2 / 8.4 MB · 1.4 MB/s
```

### Group summary

```text
Droidy transfers · 2 active
```

### Completed

```text
File sent
photo.jpg → RenziPhone
```

Rules:

- One notification per active transfer/file.
- Unique notification ID derived from transfer ID.
- Use a notification group and summary.
- No fixed `SEND_ID`/`RECEIVE_ID` reuse.
- Use Android 16 `ProgressStyle` where available.
- Incoming requests use a separate high-priority channel and Accept/Decline
  actions.
- No notification for discovery or receiver readiness.

## Transfer priority

During an active send or receive:

1. Keep the transfer task and progress reporting active.
2. Pause peer-list updates.
3. Pause mDNS browse events.
4. Stop the monitor name sniffer.
5. Defer non-urgent receiver events.
6. Keep actual incoming `/Ask` requests able to interrupt as high priority.
7. Resume discovery and receiver processing after the transfer.

The UI should show that discovery is paused during transfer rather than silently
freezing.

## Async architecture

The helper command loop must not await a transfer.

```text
Monitor / Native
  ├── peer watcher
  ├── mDNS browse
  ├── receiver server
  ├── mDNS registration
  ├── send task
  └── command loop (accept/decline/send/quit)
```

- Send runs as a detached task.
- Only one send task at a time.
- Receiver events and accept/decline continue while send is active.
- Discovery can be paused by policy during transfer.

## Auto channel

- Auto tries the last successful channel first.
- Then the remaining social channels: 6, 44, 149.
- Current dwell target: 2.5 s per channel.
- Lock onto the first channel with peers.
- Register the receiver service on the locked channel.
- During sweep, the receiver may be briefly re-registered; once locked, it
  remains stable.
- Remember the last successful channel for faster subsequent sessions.

## Peer identity and pinning

Available fields:

- AWDL MAC
- AirDrop display name (`dnm`)
- mDNS instance
- mDNS hostname
- device class
- RSSI

Plan:

- Pin by AWDL MAC as the primary key.
- Use mDNS host/instance + normalized display name as a fallback fingerprint.
- If the MAC changes but the name/host matches, show “Possible match” and ask
  the user to confirm instead of silently merging.
- Test the same iPhone across two AirDrop sessions before claiming automatic
  re-identification.

## Performance investigation

Measured so far:

- 132-byte file: about 1.49 s end-to-end.
- 20 MB zero file: compressed to about 104 KB; not representative.
- 20 MB random file: roughly 25 KB/s.
- Standalone helper send is also slow, so the async task is not the cause.

Likely causes to test, in order:

1. `tx_noack=true` in fixed mode causing TCP retransmissions.
2. Name sniffer, peer updates, and mDNS work competing during transfer.
3. Basic fixed TX rate and lack of A-MSDU/ADDBA.
4. TCP buffer/window tuning.
5. TAP/monitor MTU verification.

An uncommitted experiment sets `no_ack=false`; it must be measured before
keeping it.

## Implementation phases

### Phase A — Inner tabs and per-file state

- Add Sent/Received inner tabs.
- Add `TransferItem` state.
- Move received files into Received.
- Add active/failed/completed file cards.

### Phase B — Notification cleanup

- Remove discovery/READY receive notifications.
- Add foreground request dialog.
- Add background request notification with actions.
- Add per-file progress notifications and group summary.

### Phase C — Helper per-file events

- Emit send per-file start/progress/done.
- Emit receive per-file start/progress/done.
- Keep aggregate events for compatibility.

### Phase D — Transfer priority

- Pause peer/mDNS/name-sniffer work during transfer.
- Resume afterward.
- Keep urgent incoming requests able to interrupt.

### Phase E — Performance

- Measure `no_ack=false`.
- Measure with discovery paused.
- Test rate/aggregation options.
- Keep only changes with measured improvement.

## Acceptance checks

1. Home has Sent and Received inner tabs.
2. A sent file appears in Sent while waiting, transferring, and completed.
3. A received file appears in Received while transferring and completed.
4. Each file has its own progress card.
5. Notification progress matches the file card.
6. No notification appears from receiver readiness or peer discovery.
7. Foreground incoming request shows a dialog.
8. Background incoming request shows a high-priority notification with actions.
9. Peer list pauses during transfer and resumes afterward.
10. A 20 MB random transfer shows a measured speed improvement before the
    performance work is considered complete.
11. Pin survives app restart.
12. Auto channel locks onto the peer channel and remembers it.

## Open decisions for approval

- Confirm inner tab names: **Sent** and **Received**.
- Confirm incoming request behavior: dialog in foreground, notification in
  background.
- Confirm notifications per file, grouped under “Droidy transfers”.
- Confirm transfer priority: pause discovery during send/receive.
- Confirm no auto-accept by default.
- Confirm whether completed sent files should persist across app restarts.
- Confirm whether failed/cancelled sends stay in Sent history.
- Confirm next version bump after this UI work.

## Implementation status (2026-11-14)

Implemented on `feature/unified-home`:

- Sent/Received inner tabs
- per-file transfer model and cards
- foreground incoming-request dialog
- background incoming-request notification with Accept/Decline actions
- per-transfer progress notifications and group summary
- removal of discovery/READY receive notifications
- transfer-priority pause/resume in the helper
- ACK/retry experiment: the app session now starts the helper with
  `OWLY_TX_NO_ACK=1` to avoid per-frame 802.11 ACK stalls; the helper still
  supports `OWLY_TX_NO_ACK=0` for comparison
- per-file send events and stable receive indices
- all offered receive filenames
- original source filenames in Sent cards (no cache-prefix names)
- average transfer speed on completed cards
- `DroidyService` foreground service keeps the receiver/controller alive and
  unfrozen while the app is backgrounded
- notification Accept/Decline handled without opening the activity while the
  service is alive
- received Open/Share uses the FileProvider `external-path` for
  `Download/Droidy` directly; no cache copy

Live validation (2026-11-14):

- 20 MB random file sent to iPhone: delivered successfully.
- 2.4 MB photo received from iPhone; background notification Accept worked.
- Received photo opens through the system chooser and shares from the received
  directory without a cache copy.
- First send failed only because AirDrop had not been restarted; after the
  iPhone AirDrop restart the same send succeeded.
- Fixed-channel TX scheduling patch removed the ~11.4 s send holes. A 20 MB
  random upload reached 12.7 MB continuously before the test was stopped.
- Receive progress is steady (~150 KB/s in the captured sample); receive speed
  improvement remains a later phase.
- ADB debug surface: `scripts/droidy-adb.sh` and `docs/raphael/ADB-DEBUG.md`;
  all output is mirrored to `logcat` tag `DroidyLog`.

Current limitations:

- Owly's receiver API has no true per-file progress; receive cards use
  aggregate progress distributed across files. Deferred to the next phase.
- The foreground service requires a quiet, ongoing system notification while
  the receiver runs in the background. This is platform-required, not a
  discovery/readiness alert.
- If Android kills the whole app process, a restarted service recreates the
  controller and auto-starts the session when the monitor kernel is ready, but
  an in-flight request cannot be recovered.
- Transfer history is session-only by decision.
- Precise sustained throughput measurement is still pending; the TX patch
  removed the observed stalls but a full 20 MB run still needs a clean
  measurement.
- Low Power Mode and a locked iPhone screen throttle AWDL heavily; keep the
  iPhone unlocked and Low Power Mode off during speed tests.
- Completed cards show average upload/download speed.
- Receive throughput is still lower than the link's burst rate; improving it
  is the next transfer phase.
