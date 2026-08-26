#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
JAVA_HOME="${JAVA_HOME:-$WORKSPACE_DIR/tools/jdk17/Contents/Home}"
TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/saro-ride-test.XXXXXX")"

cleanup() {
  case "$TEMP_DIR" in
    "${TMPDIR:-/tmp}"/saro-ride-test.*) find "$TEMP_DIR" -depth -delete ;;
    *) echo "Refusing to clean unexpected path: $TEMP_DIR" >&2 ;;
  esac
}
trap cleanup EXIT

"$JAVA_HOME/bin/javac" -d "$TEMP_DIR" \
  "$WORKSPACE_DIR/device-setup/ride-starter/src/com/pelotonhack/ridestarter/RideAccumulator.java" \
  "$WORKSPACE_DIR/device-setup/ride-starter/src/com/pelotonhack/ridestarter/DragGesturePolicy.java" \
  "$WORKSPACE_DIR/device-setup/ride-starter/src/com/pelotonhack/ridestarter/MediaPackageAliases.java" \
  "$WORKSPACE_DIR/device-setup/ride-starter/src/com/pelotonhack/ridestarter/BikeAppAccessibilityPolicy.java" \
  "$WORKSPACE_DIR/device-setup/ride-starter/src/com/pelotonhack/ridestarter/RideStartOverlayArm.java" \
  "$WORKSPACE_DIR/device-setup/ride-starter/src/com/pelotonhack/ridestarter/SensorHealth.java" \
  "$WORKSPACE_DIR/device-setup/ride-starter/src/com/pelotonhack/ridestarter/TvFocusNavigator.java" \
  "$WORKSPACE_DIR/device-setup/ride-starter/src/com/pelotonhack/ridestarter/TvRemotePackages.java" \
  "$WORKSPACE_DIR/device-setup/ride-starter/src/com/pelotonhack/ridestarter/TvRemoteVariantCache.java" \
  "$WORKSPACE_DIR/device-setup/ride-starter/src/com/pelotonhack/ridestarter/TvRemoteSelection.java" \
  "$WORKSPACE_DIR/device-setup/ride-starter/src/com/pelotonhack/ridestarter/TvRemoteServiceState.java" \
  "$WORKSPACE_DIR/device-setup/ride-starter/src/com/pelotonhack/ridestarter/TvRemoteTargetKey.java" \
  "$WORKSPACE_DIR/device-setup/ride-starter/src/com/pelotonhack/ridestarter/TvRemoteWindowPolicy.java" \
  "$WORKSPACE_DIR/device-setup/ride-starter/src/com/pelotonhack/ridestarter/ZwiftBlePayload.java" \
  "$SCRIPT_DIR/java/com/pelotonhack/ridestarter/RideAccumulatorTest.java" \
  "$SCRIPT_DIR/java/com/pelotonhack/ridestarter/DragGesturePolicyTest.java" \
  "$SCRIPT_DIR/java/com/pelotonhack/ridestarter/MediaPackageAliasesTest.java" \
  "$SCRIPT_DIR/java/com/pelotonhack/ridestarter/BikeAppAccessibilityPolicyTest.java" \
  "$SCRIPT_DIR/java/com/pelotonhack/ridestarter/RideStartOverlayArmTest.java" \
  "$SCRIPT_DIR/java/com/pelotonhack/ridestarter/SensorHealthTest.java" \
  "$SCRIPT_DIR/java/com/pelotonhack/ridestarter/TvFocusNavigatorTest.java" \
  "$SCRIPT_DIR/java/com/pelotonhack/ridestarter/TvRemoteSelectionTest.java" \
  "$SCRIPT_DIR/java/com/pelotonhack/ridestarter/TvRemoteServiceStateTest.java" \
  "$SCRIPT_DIR/java/com/pelotonhack/ridestarter/TvRemoteTargetKeyTest.java" \
  "$SCRIPT_DIR/java/com/pelotonhack/ridestarter/TvRemoteVariantCacheTest.java" \
  "$SCRIPT_DIR/java/com/pelotonhack/ridestarter/TvRemoteWindowPolicyTest.java" \
  "$SCRIPT_DIR/java/com/pelotonhack/ridestarter/ZwiftBlePayloadTest.java"
"$JAVA_HOME/bin/java" -cp "$TEMP_DIR" com.pelotonhack.ridestarter.RideAccumulatorTest
"$JAVA_HOME/bin/java" -cp "$TEMP_DIR" com.pelotonhack.ridestarter.DragGesturePolicyTest
"$JAVA_HOME/bin/java" -cp "$TEMP_DIR" com.pelotonhack.ridestarter.MediaPackageAliasesTest
"$JAVA_HOME/bin/java" -cp "$TEMP_DIR" com.pelotonhack.ridestarter.BikeAppAccessibilityPolicyTest
"$JAVA_HOME/bin/java" -cp "$TEMP_DIR" com.pelotonhack.ridestarter.RideStartOverlayArmTest
"$JAVA_HOME/bin/java" -cp "$TEMP_DIR" com.pelotonhack.ridestarter.SensorHealthTest
"$JAVA_HOME/bin/java" -cp "$TEMP_DIR" com.pelotonhack.ridestarter.TvFocusNavigatorTest
"$JAVA_HOME/bin/java" -cp "$TEMP_DIR" com.pelotonhack.ridestarter.TvRemoteSelectionTest
"$JAVA_HOME/bin/java" -cp "$TEMP_DIR" com.pelotonhack.ridestarter.TvRemoteServiceStateTest
"$JAVA_HOME/bin/java" -cp "$TEMP_DIR" com.pelotonhack.ridestarter.TvRemoteTargetKeyTest
"$JAVA_HOME/bin/java" -cp "$TEMP_DIR" com.pelotonhack.ridestarter.TvRemoteVariantCacheTest
"$JAVA_HOME/bin/java" -cp "$TEMP_DIR" com.pelotonhack.ridestarter.TvRemoteWindowPolicyTest
"$JAVA_HOME/bin/java" -cp "$TEMP_DIR" com.pelotonhack.ridestarter.ZwiftBlePayloadTest
