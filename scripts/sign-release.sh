#!/usr/bin/env bash
# Sign an existing unsigned release APK. Secrets are read from the environment
# or prompted, never passed on the command line.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK_DIR="$ROOT/android-app/app/build/outputs/apk/release"
UNSIGNED="${1:-$APK_DIR/app-release-unsigned.apk}"
OUTPUT="${2:-$APK_DIR/app-release.apk}"

if [[ ! -f "$UNSIGNED" ]]; then
  echo "missing $UNSIGNED" >&2
  echo "run: scripts/build-app.sh release" >&2
  exit 1
fi

KEYSTORE="${DROIDY_KEYSTORE:-}"
ALIAS="${DROIDY_KEY_ALIAS:-droidy}"

if [[ -z "$KEYSTORE" ]]; then
  read -r -p "Release keystore path: " KEYSTORE
fi
if [[ ! -f "$KEYSTORE" ]]; then
  echo "keystore not found: $KEYSTORE" >&2
  exit 1
fi

if [[ -z "${DROIDY_KEYSTORE_PASSWORD:-}" ]]; then
  read -r -s -p "Keystore password: " DROIDY_KEYSTORE_PASSWORD
  echo
fi
if [[ -z "${DROIDY_KEY_PASSWORD:-}" ]]; then
  read -r -s -p "Key password (blank = same as keystore): " DROIDY_KEY_PASSWORD
  echo
  DROIDY_KEY_PASSWORD="${DROIDY_KEY_PASSWORD:-$DROIDY_KEYSTORE_PASSWORD}"
fi
export DROIDY_KEYSTORE_PASSWORD DROIDY_KEY_PASSWORD

ZIPALIGN="${ZIPALIGN:-$(command -v zipalign || true)}"
APKSIGNER="${APKSIGNER:-$(command -v apksigner || true)}"

if [[ -z "$ZIPALIGN" || -z "$APKSIGNER" ]]; then
  SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -z "$SDK" && -f "$ROOT/android-app/local.properties" ]]; then
    SDK="$(sed -n 's/^sdk.dir=//p' "$ROOT/android-app/local.properties" | head -1)"
  fi
  if [[ -n "$SDK" ]]; then
    BUILD_TOOLS="$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1)"
    ZIPALIGN="${ZIPALIGN:-$BUILD_TOOLS/zipalign}"
    APKSIGNER="${APKSIGNER:-$BUILD_TOOLS/apksigner}"
  fi
fi

if [[ ! -x "$ZIPALIGN" || ! -x "$APKSIGNER" ]]; then
  echo "zipalign/apksigner not found; install Android build-tools or set ZIPALIGN/APKSIGNER" >&2
  exit 1
fi

ALIGNED="$(mktemp "${TMPDIR:-/tmp}/droidy-aligned.XXXXXX.apk")"
trap 'rm -f "$ALIGNED"' EXIT

"$ZIPALIGN" -p -f 4 "$UNSIGNED" "$ALIGNED"
"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$ALIAS" \
  --ks-pass env:DROIDY_KEYSTORE_PASSWORD \
  --key-pass env:DROIDY_KEY_PASSWORD \
  --out "$OUTPUT" \
  "$ALIGNED"

"$APKSIGNER" verify --print-certs "$OUTPUT"
ls -lh "$OUTPUT"
