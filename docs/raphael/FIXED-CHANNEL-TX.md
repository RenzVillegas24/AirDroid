# Fixed-channel TX scheduling

The K20 monitor is fixed to one AWDL channel (`Presence::Fixed`). The AWDL
node still gated unicast TX on the peer's advertised channel sequence:

```rust
fn same_channel(&self, peer: &Peer, t: Time) -> bool {
    let slot = self.clock.slot(t);
    let own = self.own_channel(slot);
    let theirs = peer.sequence.map_or(0, |s| s.channel(slot));
    own != 0 && own == theirs
}
```

The iPhone's sequence does not put the K20's fixed channel in most slots. RX
worked, but TX opened only in short windows, producing ~11.4-second holes and
the receiver eventually closing the upload.

The fix makes a fixed monitor treat the channel as shared:

```rust
fn same_channel(&self, peer: &Peer, t: Time) -> bool {
    if self.fixed() {
        return true;
    }
    let slot = self.clock.slot(t);
    let own = self.own_channel(slot);
    let theirs = peer.sequence.map_or(0, |s| s.channel(slot));
    own != 0 && own == theirs
}
```

This trades perfect slot alignment for continuous TX; TCP recovers any frames
sent while the peer is off-channel. The observed result was continuous 64 KiB
progress instead of multi-second stalls.

## Rebuild

The reference tree and `android-app/assets/` are intentionally gitignored.
Apply the change in:

```text
workspaces/shared/reference/owly-ref/crates/owly-awdl/src/node.rs
```

Then rebuild and install:

```bash
cd android-app/helper
~/.cargo/bin/cargo build --release --target aarch64-unknown-linux-musl
cp target/aarch64-unknown-linux-musl/release/owly-k20-helper ../assets/owly-k20-helper
cd ..
./gradlew :app:assembleRelease -x lintVitalRelease -x lintRelease
```

The app session also starts the helper with `OWLY_TX_NO_ACK=1`; the AirDrop
protocol still handles its own response/retry behavior.
