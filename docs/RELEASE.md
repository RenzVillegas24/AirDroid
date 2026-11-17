# Release signing

Release builds are unsigned by default. Use a private keystore; never commit it.

## 1. Create the release keystore once

```bash
keytool -genkeypair -v \
  -keystore "$HOME/.android/airdroid-release.jks" \
  -alias airdroid \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storetype JKS
```

Choose a strong password and back up the keystore and password somewhere safe.
Losing this key means you cannot update an installed release APK with a new
signature.

## 2. Build the unsigned release APK

```bash
scripts/build-app.sh release
```

Output:

```text
android-app/app/build/outputs/apk/release/app-release-unsigned.apk
```

## 3. Sign it

Interactive:

```bash
scripts/sign-release.sh
```

Or non-interactive via environment variables:

```bash
export AIRDROID_KEYSTORE="$HOME/.android/airdroid-release.jks"
export AIRDROID_KEY_ALIAS=airdroid
export AIRDROID_KEYSTORE_PASSWORD='...'
export AIRDROID_KEY_PASSWORD='...'

scripts/sign-release.sh
```

The script zipaligns, signs, verifies, and writes:

```text
android-app/app/build/outputs/apk/release/app-release.apk
```

Passwords are read from the environment or prompted; they are not placed on
the command line.

## 4. Verify

```bash
apksigner verify --print-certs \
  android-app/app/build/outputs/apk/release/app-release.apk
```

## 5. Install/update

```bash
adb install -r android-app/app/build/outputs/apk/release/app-release.apk
```

Android requires the same signing key for updates. If you install a release
APK over a debug build signed with a different key, uninstall the old app
first (which removes app data).

## GitHub releases

Releases are **manual only**. There is no push, tag, or schedule trigger.
Nothing is published until one of these workflows is started from the GitHub
Actions tab:

```text
Release App
Release Kernel
```

Both workflows take a `version` and an optional `tag`. If `tag` is empty,
`v<version>` is used. The app asset is `AirDroid-<version>.apk`; the kernel
asset is `boot-airdroid-<version>.img`.

### Repository secrets for the app workflow

Add these under **Settings → Secrets and variables → Actions**:

```text
AIRDROID_KEYSTORE_BASE64
AIRDROID_KEYSTORE_PASSWORD
AIRDROID_KEY_PASSWORD
AIRDROID_KEY_ALIAS              (optional; defaults to airdroid)
```

Create the base64 value locally without printing the key:

```bash
base64 -w0 "$HOME/.android/airdroid-release.jks" > /tmp/airdroid-release.b64
```

Paste the file contents into `AIRDROID_KEYSTORE_BASE64`. Never commit the
keystore, the base64 value, or either password.

The app workflow builds the ARM64 helper with `cross`, builds the release APK,
zipaligns and signs it, uploads an artifact, and creates or updates the GitHub
release.

### Kernel workflow

`Release Kernel` builds the pinned K20 `kernel/` submodule with the
`sm8150-perf_defconfig` plus the `vendor/xiaomi/raphael.config` fragment. It
packs the result with the tracked `out/ramdisk` and `out/dtb` and publishes:

```text
boot-airdroid-<version>.img
boot-airdroid-<version>.img.sha256
```

This workflow is intentionally manual because kernel output is device-specific
and must be checked before distribution. It does not flash anything.

## Rules

- Never commit `*.jks`, `*.keystore`, passwords, or signing environment files.
- Keep the release key out of CI logs.
- Bump `versionCode`/`versionName` in
  `android-app/app/build.gradle.kts` before each release.
- The local `android-app/debug.keystore` is not a release key.
- Only publish from the manual release workflows; do not add automatic release
  triggers.
