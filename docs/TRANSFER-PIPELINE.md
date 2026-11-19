# Transfer pipeline

This branch replaces the send/receive retry patches with one transport flow.

## Invariants

- The helper session owns the radio, receiver, mDNS browse, peer cache and
  channel for the whole Transfer-mode session.
- A transfer never stops or restarts the session to cancel itself.
- The app sends intents; the helper owns the transfer state machine.
- Every terminal transfer event means the peer connection is closed.
- Only one outgoing send is active at a time.
- Auto is the default. Selecting a fixed channel shows a user-facing warning.
- Auto scan prefers 149 then 44; channel 6 is the last fallback for
  2.4 GHz-only networks.

## Send states

```text
WAIT_FOR_PEER
  -> DISCOVER_PORT
  -> CONNECT_ASK
  -> WAIT_ACCEPT
  -> UPLOAD
  -> DONE | FAILED | CANCELLED
```

- `DISCOVER_PORT` is the receiver-port gate. Every attempt passes
  `/Discover` before `/Ask`; a cached mDNS address is only an address.
- `CONNECT_ASK` retries the `/Ask` connection directly after a short pause.
  It does not run another `/Discover`, which can consume the receiver port
  the retry needs.
- `CANCELLED` is emitted by the sender only after the TLS/TCP connection has
  been closed abortively with `SO_LINGER=0`.
- The app shows `Cancelling...` until it receives `SEND_CANCELLED`.

## Receive states

```text
OFFERED
  -> ACCEPTED
  -> RECEIVING
  -> COMPLETE | FAILED | ABORTED
```

- `ReceiverControl::abort_request` signals the request task; it does not abort
  the receiver listener.
- The task closes the connection abortively and emits `ReceiverEvent::Aborted`.
- The helper prints `RECEIVE_ABORTED` only after that event.
- Per-file progress comes from the receiver's `Data` events. Directory
  children are covered by aggregate progress, not invented per-file rows.

## Peer readiness

- `PEER` / `PEER_GONE` describe the AWDL peer list.
- `FOUND` / `LOST` describe the AirDrop mDNS service.
- A named AWDL peer can be used through `/Discover`; an unnamed raw peer is
  not an auto-scan candidate.
- `LOST` hides a peer until a fresh `FOUND`; terminal transfers do not
  invent readiness for a peer that has never advertised AirDrop.

## Channel policy

- Auto scan order is 149 then 44, with 6 as the last fallback.
- A named/ready peer locks the channel; blank transient peers are ignored.
- Channel 6 is selected only when no usable 5 GHz peer is found.
- The peer's advertised AWDL sequence exposes its dominant channel. If
  Auto sees a peer on a different channel, it switches to the dominant
  channel before locking. This prevents a fixed 2.4 GHz monitor from
  staying on channel 6 while the iPhone spends most slots on 149.
- The receiver advertises VHT80 2SS and tunes the monitor to 80 MHz on 149 or
  44, with a 20 MHz fallback. Sends stay on the 54 Mbps management-TX path.

## Test matrix

1. Two consecutive 20 MB sends on 149.
2. Cancel a send at ~512 KB and confirm the iPhone stops immediately.
3. Cancel a receive at ~5 MB and confirm the iPhone stops immediately.
4. Receive a file to completion.
5. Auto scan: lock 149/44 when available; lock 6 only when no 5 GHz peer is found.
6. Settings toggles changed during Transfer mode.
7. AirDrop off/on and peer card recovery.
8. No-ACK A/B remains an investigation-only experiment; production uses the
   ACK/retry path.
