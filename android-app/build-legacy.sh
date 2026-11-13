#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-/usr/lib/android-sdk}"
RES_API=33
COMPILE_API=36
RES_JAR="$SDK/platforms/android-$RES_API/android.jar"
ANDROID_JAR="$SDK/platforms/android-$COMPILE_API/android.jar"
KOTLINC="$APP_DIR/tools/kotlinc/bin/kotlinc"
KOTLIN_STDLIB="$APP_DIR/tools/kotlinc/lib/kotlin-stdlib.jar"
D8_JAR="$APP_DIR/tools/r8.jar"
AAPT="${AAPT:-$(command -v aapt)}"
APKSIGNER="${APKSIGNER:-$(command -v apksigner)}"
ZIPALIGN="${ZIPALIGN:-$(command -v zipalign)}"
KEYSTORE="$APP_DIR/debug.keystore"
OUT="$APP_DIR/out/owly-radio.apk"

for f in "$RES_JAR" "$ANDROID_JAR" "$KOTLINC" "$KOTLIN_STDLIB" "$D8_JAR"; do
  [ -e "$f" ] || { echo "missing: $f" >&2; exit 1; }
done

rm -rf "$APP_DIR/build" "$APP_DIR/gen" "$APP_DIR/out"
mkdir -p "$APP_DIR/build/classes" "$APP_DIR/build/dex" "$APP_DIR/gen" "$APP_DIR/out"

echo "== resources =="
"$AAPT" package -f -m -J "$APP_DIR/gen" \
  -M "$APP_DIR/AndroidManifest.xml" \
  -S "$APP_DIR/res" \
  -I "$RES_JAR"

echo "== R.java =="
mapfile -t JAVA_FILES < <(find "$APP_DIR/gen" -name '*.java')
javac --release 8 -cp "$ANDROID_JAR" -d "$APP_DIR/build/classes" "${JAVA_FILES[@]}"

echo "== Kotlin =="
mapfile -t KOTLIN_FILES < <(find "$APP_DIR/src" -name '*.kt')
"$KOTLINC" -no-stdlib -no-reflect -jvm-target 1.8 \
  -classpath "$ANDROID_JAR:$KOTLIN_STDLIB:$APP_DIR/build/classes" \
  -d "$APP_DIR/build/classes" \
  "${KOTLIN_FILES[@]}"

echo "== dex =="
mapfile -t CLASS_FILES < <(find "$APP_DIR/build/classes" -name '*.class')
java -cp "$D8_JAR" com.android.tools.r8.D8 \
  --min-api 23 --lib "$ANDROID_JAR" \
  --output "$APP_DIR/build/dex" \
  "$KOTLIN_STDLIB" "${CLASS_FILES[@]}"

echo "== package =="
"$AAPT" package -f \
  -M "$APP_DIR/AndroidManifest.xml" \
  -S "$APP_DIR/res" \
  -I "$RES_JAR" \
  -F "$APP_DIR/build/unsigned.apk"

cd "$APP_DIR/build"
cp dex/classes.dex .
cp -r "$APP_DIR/assets" assets
"$AAPT" add unsigned.apk classes.dex
for f in assets/*; do
  [ -f "$f" ] && "$AAPT" add unsigned.apk "$f"
done
cd "$APP_DIR"

echo "== align/sign =="
"$ZIPALIGN" -f 4 "$APP_DIR/build/unsigned.apk" "$APP_DIR/build/aligned.apk"

if [ ! -e "$KEYSTORE" ]; then
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -alias androiddebugkey \
    -storepass android -keypass android \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

"$APKSIGNER" sign \
  --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT" "$APP_DIR/build/aligned.apk"

"$APKSIGNER" verify --verbose --print-certs "$OUT"
ls -lh "$OUT"
