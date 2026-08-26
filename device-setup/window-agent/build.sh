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
TASK_TMP_ROOT="${TMPDIR:-/tmp}"
TEMP_DIR="$(mktemp -d "$TASK_TMP_ROOT/saro-window-agent.XXXXXX")"

cleanup() {
  case "$TEMP_DIR" in
    "$TASK_TMP_ROOT"/saro-window-agent.*) find "$TEMP_DIR" -depth -delete ;;
    *) echo "Refusing to clean unexpected temporary path: $TEMP_DIR" >&2 ;;
  esac
}
trap cleanup EXIT

for required in \
  "$BUILD_TOOLS/d8" \
  "$ANDROID_JAR" \
  "$JAVA_HOME/bin/javac" \
  "$JAVA_HOME/bin/jar"; do
  if [[ ! -e "$required" ]]; then
    echo "Missing build dependency: $required" >&2
    exit 1
  fi
done

mkdir -p "$TEMP_DIR/classes" "$TEMP_DIR/dex" "$OUTPUT_DIR"
find "$OUTPUT_DIR" -mindepth 1 -depth -delete

"$JAVA_HOME/bin/javac" \
  -source 8 \
  -target 8 \
  -cp "$ANDROID_JAR" \
  -d "$TEMP_DIR/classes" \
  "$SCRIPT_DIR/src/com/pelotonhack/windowagent/TabletWindowAgent.java"

"$JAVA_HOME/bin/jar" cf "$TEMP_DIR/classes.jar" -C "$TEMP_DIR/classes" .
"$BUILD_TOOLS/d8" \
  --min-api 23 \
  --lib "$ANDROID_JAR" \
  --output "$TEMP_DIR/dex" \
  "$TEMP_DIR/classes.jar"
"$JAVA_HOME/bin/jar" cf "$OUTPUT_DIR/saro-window-agent.jar" \
  -C "$TEMP_DIR/dex" classes.dex

echo "Built $OUTPUT_DIR/saro-window-agent.jar"
