# Helper patches

The Owly reference checkout lives under `workspaces/shared/`, which is
gitignored. Patches in this directory keep required radio fixes reproducible
from a clean clone.

Apply before rebuilding the helper:

```bash
cd /path/to/owly-droid
git apply android-app/helper/patches/fixed-channel-tx.patch
cd android-app/helper
~/.cargo/bin/cargo build --release --target aarch64-unknown-linux-musl
cp target/aarch64-unknown-linux-musl/release/owly-k20-helper ../assets/owly-k20-helper
```

`fixed-channel-tx.patch` is already applied in the local working tree used to
build the current APK. It makes a fixed-channel monitor send unicast without
waiting for the peer's advertised channel sequence, removing the observed
~11.4-second TX holes.
