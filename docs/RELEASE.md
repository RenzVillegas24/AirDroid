# Release signing

Release builds are unsigned by default. Use a private keystore; never commit it.

## 1. Create the release keystore once

```bash
keytool -genkeypair -v \
  -keystore "$HOME/.android/droidy-release.jks" \
  -alias droidy \
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
export DROIDY_KEYSTORE="$HOME/.android/droidy-release.jks"
export DROIDY_KEY_ALIAS=droidy
export DROIDY_KEYSTORE_PASSWORD='...'
export DROIDY_KEY_PASSWORD='...'

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

## Rules

- Never commit `*.jks`, `*.keystore`, passwords, or signing environment files.
- Keep the release key out of CI logs.
- Bump `versionCode`/`versionName` in
  `android-app/app/build.gradle.kts` before each release.
- The local `android-app/debug.keystore` is not a release key.
