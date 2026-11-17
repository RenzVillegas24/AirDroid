#!/usr/bin/env bash
# Reproducible app build: Owly submodule -> helper -> APK.
# The kernel submodule is not needed to build the APK.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${1:-debug}"

case "$MODE" in
  debug|release) ;;
  *) echo "usage: $0 [debug|release]" >&2; exit 2 ;;
esac

cd "$ROOT"

echo "== Owly submodule =="
git submodule update --init third_party/owly

CARGO="${CARGO:-}"
if [[ -z "$CARGO" ]]; then
  if command -v cargo >/dev/null 2>&1; then
    CARGO="$(command -v cargo)"
  elif [[ -x "$HOME/.cargo/bin/cargo" ]]; then
    CARGO="$HOME/.cargo/bin/cargo"
  else
    echo "cargo not found; install Rust and the aarch64-unknown-linux-musl target" >&2
    exit 1
  fi
fi

if [[ "${SKIP_HELPER_BUILD:-0}" == "1" ]]; then
  echo "== helper (prebuilt) =="
  [[ -x "$ROOT/android-app/assets/owly-k20-helper" ]] || {
    echo "missing android-app/assets/owly-k20-helper" >&2
    exit 1
  }
else
  echo "== helper =="
  cd "$ROOT/android-app/helper"
  "$CARGO" build --release --target aarch64-unknown-linux-musl
  mkdir -p ../assets
  cp target/aarch64-unknown-linux-musl/release/owly-k20-helper ../assets/owly-k20-helper
fi

echo "== APK ($MODE) =="
cd "$ROOT/android-app"

GRADLE_ARGS=()
AAPT2_OVERRIDE="${AAPT2_OVERRIDE:-$HOME/Android/Sdk/aapt2-qemu/aapt2}"
if [[ -x "$AAPT2_OVERRIDE" ]]; then
  GRADLE_ARGS+=("-Pandroid.aapt2FromMavenOverride=$AAPT2_OVERRIDE")
fi

if [[ "$MODE" == "release" ]]; then
  ./gradlew :app:assembleRelease -x lintVitalRelease -x lintRelease "${GRADLE_ARGS[@]}"
  echo "APK: android-app/app/build/outputs/apk/release/"
else
  ./gradlew :app:assembleDebug "${GRADLE_ARGS[@]}"
  echo "APK: android-app/app/build/outputs/apk/debug/app-debug.apk"
fi
