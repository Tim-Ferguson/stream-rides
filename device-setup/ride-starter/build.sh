#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$WORKSPACE_DIR/tools/android-sdk}"
JAVA_HOME="${JAVA_HOME:-$WORKSPACE_DIR/tools/jdk17/Contents/Home}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-35.0.0}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-android-30}"
export JAVA_HOME

BUILD_TOOLS="$ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION"
ANDROID_JAR="$ANDROID_SDK_ROOT/platforms/$ANDROID_PLATFORM/android.jar"
OUTPUT_DIR="$SCRIPT_DIR/build"
BUILD_VARIANT="${SARO_BUILD_VARIANT:-debug}"

case "$BUILD_VARIANT" in
  debug) ;;
  release)
    [[ -n "${SARO_KEYSTORE:-}" ]] || {
      echo "SARO_KEYSTORE is required for a release build" >&2
      exit 1
    }
    [[ -n "${SARO_KEY_ALIAS:-}" ]] || {
      echo "SARO_KEY_ALIAS is required for a release build" >&2
      exit 1
    }
    [[ -n "${SARO_KEYSTORE_PASSWORD:-}" ]] || {
      echo "SARO_KEYSTORE_PASSWORD is required for a release build" >&2
      exit 1
    }
    ;;
  *)
    echo "Unsupported SARO_BUILD_VARIANT: $BUILD_VARIANT" >&2
    exit 1
    ;;
esac

TASK_TMP_ROOT="${TMPDIR:-/tmp}"
TEMP_DIR="$(mktemp -d "$TASK_TMP_ROOT/saro-ride-starter.XXXXXX")"

cleanup() {
  local status="$1"
  trap - EXIT
  case "$TEMP_DIR" in
    "$TASK_TMP_ROOT"/saro-ride-starter.*)
      find "$TEMP_DIR" -depth -delete
      ;;
    *)
      echo "Refusing to clean unexpected temporary path: $TEMP_DIR" >&2
      ;;
  esac
  exit "$status"
}
trap 'cleanup "$?"' EXIT

for required in \
  "$BUILD_TOOLS/aapt" \
  "$BUILD_TOOLS/d8" \
  "$BUILD_TOOLS/zipalign" \
  "$BUILD_TOOLS/apksigner" \
  "$ANDROID_JAR" \
  "$JAVA_HOME/bin/javac" \
  "$JAVA_HOME/bin/jar" \
  "$JAVA_HOME/bin/keytool"; do
  if [[ ! -e "$required" ]]; then
    echo "Missing build dependency: $required" >&2
    exit 1
  fi
done

mkdir -p "$TEMP_DIR/gen" "$TEMP_DIR/classes" "$TEMP_DIR/dex" "$OUTPUT_DIR"

# Keep the signing identity for in-place updates, but never leave outputs from a
# previous compiler layout or a failed build looking current.
find "$OUTPUT_DIR" -mindepth 1 ! -name debug.keystore -depth -delete

"$BUILD_TOOLS/aapt" package -f -m \
  -J "$TEMP_DIR/gen" \
  -M "$SCRIPT_DIR/AndroidManifest.xml" \
  -S "$SCRIPT_DIR/res" \
  -I "$ANDROID_JAR"

"$JAVA_HOME/bin/javac" \
  -source 8 \
  -target 8 \
  -cp "$ANDROID_JAR" \
  -d "$TEMP_DIR/classes" \
  "$TEMP_DIR/gen/com/pelotonhack/ridestarter/R.java" \
  "$SCRIPT_DIR"/src/com/pelotonhack/ridestarter/*.java

"$JAVA_HOME/bin/jar" cf "$TEMP_DIR/classes.jar" -C "$TEMP_DIR/classes" .
"$BUILD_TOOLS/d8" \
  --min-api 23 \
  --lib "$ANDROID_JAR" \
  --output "$TEMP_DIR/dex" \
  "$TEMP_DIR/classes.jar"

"$BUILD_TOOLS/aapt" package -f \
  -M "$SCRIPT_DIR/AndroidManifest.xml" \
  -S "$SCRIPT_DIR/res" \
  -I "$ANDROID_JAR" \
  -F "$TEMP_DIR/ride-starter-unsigned.apk" \
  "$TEMP_DIR/dex"

"$BUILD_TOOLS/zipalign" -f 4 \
  "$TEMP_DIR/ride-starter-unsigned.apk" \
  "$TEMP_DIR/ride-starter-aligned.apk"

case "$BUILD_VARIANT" in
  debug)
    KEYSTORE="$OUTPUT_DIR/debug.keystore"
    KEY_ALIAS="debug"
    KEYSTORE_PASSWORD="android"
    KEY_PASSWORD="android"
    OUTPUT_APK="$OUTPUT_DIR/ride-starter.apk"
    if [[ ! -f "$KEYSTORE" ]]; then
      "$JAVA_HOME/bin/keytool" -genkeypair \
        -keystore "$KEYSTORE" \
        -storepass "$KEYSTORE_PASSWORD" \
        -keypass "$KEY_PASSWORD" \
        -alias "$KEY_ALIAS" \
        -dname "CN=SARO Local Debug,O=Local Development,C=US" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000
    fi
    ;;
  release)
    KEYSTORE="$SARO_KEYSTORE"
    KEY_ALIAS="$SARO_KEY_ALIAS"
    KEYSTORE_PASSWORD="$SARO_KEYSTORE_PASSWORD"
    KEY_PASSWORD="${SARO_KEY_PASSWORD:-$SARO_KEYSTORE_PASSWORD}"
    OUTPUT_APK="$OUTPUT_DIR/ride-starter-release.apk"
    if [[ ! -f "$KEYSTORE" ]]; then
      echo "Release keystore not found: $KEYSTORE" >&2
      exit 1
    fi
    ;;
esac

"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEYSTORE_PASSWORD" \
  --key-pass "pass:$KEY_PASSWORD" \
  --v4-signing-enabled false \
  --out "$OUTPUT_APK" \
  "$TEMP_DIR/ride-starter-aligned.apk"

"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$OUTPUT_APK"
if command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "$OUTPUT_APK" >"$OUTPUT_APK.sha256"
elif command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$OUTPUT_APK" >"$OUTPUT_APK.sha256"
else
  echo "A SHA-256 tool is required (shasum or sha256sum)." >&2
  exit 1
fi
echo "Built $OUTPUT_APK"
