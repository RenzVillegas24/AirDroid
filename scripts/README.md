# Production scripts

- `build-app.sh` — build the Rust helper from `third_party/owly`, copy it into
  `android-app/assets/`, and build the APK.
- `crosspair-adb.sh` — ADB control/log interface for the running app.

The app bundles only:

```text
owly-mode.sh
owly-k20-helper
```

`owly-mode.sh` is tracked in `android-app/app/src/main/assets/`.

Monitor/injection C tools, capture helpers, and bring-up shell experiments are
kept on the investigation branches:

```text
investigation/exclusive
investigation/shared
experiments/investigation-tools
```
