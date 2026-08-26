#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
HELPER_DIR="$WORKSPACE_DIR/device-setup/ride-starter"
APK="$HELPER_DIR/build/ride-starter.apk"

fail() {
  echo "release gate failed: $*" >&2
  exit 1
}

for required in LICENSE README.md AGENTS.md THIRD_PARTY_NOTICES.md ai-contributors.md \
  ZWIFT_HANDOFF.md docs/config-schema-v2.md docs/direct-sensor-live-test.md \
  docs/sensor-recovery-live-test.md \
  docs/recovery.md docs/release-signing.md docs/streaming-live-test.md \
  docs/privacy-audit.md docs/public-release.md \
  docs/tv-remote-live-test.md \
  docs/game-asset-provenance.md docs/security/package-security-audit.md \
  docs/security/current-apk-inventory.tsv \
  docs/reviews/architecture-review-1.md docs/reviews/architecture-review-2.md \
  docs/reviews/remediation-resolution.md \
  docs/reviews/zwift-recovery-review-1.md \
  docs/reviews/zwift-recovery-review-2.md \
  docs/reviews/zwift-recovery-resolution.md \
  docs/reviews/tv-remote-review-1.md \
  docs/reviews/tv-remote-review-2.md \
  docs/reviews/tv-remote-remediation.md \
  docs/reviews/paramount-mobile-review.md \
  docs/reviews/touch-provider-migration-review.md \
  docs/reviews/content-first-review-1.md \
  docs/reviews/content-first-review-2.md \
  docs/reviews/content-first-remediation.md \
  tools/audit-public-tree.sh tools/export-public-snapshot.sh tools/install-gitleaks.sh \
  tools/public-binary-manifest.tsv tools/validate-png.pl \
  tools/tests/test-public-release.sh; do
  [[ -f "$WORKSPACE_DIR/$required" ]] || fail "missing $required"
done

GITLEAKS_BIN="${SARO_GITLEAKS_BIN:-}"
REQUIRED_GITLEAKS_VERSION="8.30.1"
if [[ -z "$GITLEAKS_BIN" && -x "$WORKSPACE_DIR/tools/gitleaks/gitleaks" ]]; then
  GITLEAKS_BIN="$WORKSPACE_DIR/tools/gitleaks/gitleaks"
elif [[ -z "$GITLEAKS_BIN" ]]; then
  GITLEAKS_BIN="$(command -v gitleaks || true)"
fi
[[ -n "$GITLEAKS_BIN" && -x "$GITLEAKS_BIN" ]] ||
  fail "Gitleaks is required; run tools/install-gitleaks.sh"
[[ "$("$GITLEAKS_BIN" version 2>/dev/null | tr -d '\r\n')" == \
  "$REQUIRED_GITLEAKS_VERSION" ]] ||
  fail "Gitleaks $REQUIRED_GITLEAKS_VERSION is required"
export SARO_GITLEAKS_BIN="$GITLEAKS_BIN"

"$WORKSPACE_DIR/tools/audit-public-tree.sh" "$WORKSPACE_DIR" private-source

cleanup_release_gate() {
  local status="$1"
  trap - EXIT
  exit "$status"
}
trap 'cleanup_release_gate "$?"' EXIT

if git -C "$WORKSPACE_DIR" ls-files | grep -E '\.(apk|xapk|apks|jks|keystore|p12|pem|key)$' >/dev/null; then
  fail "tracked APK or signing-secret artifact"
fi

identifier_found=0
personal_pattern='/Us''ers/[^/]+'
while IFS= read -r -d '' candidate; do
  [[ -f "$WORKSPACE_DIR/$candidate" ]] || continue
  if grep -InEI "$personal_pattern" "$WORKSPACE_DIR/$candidate"; then
    identifier_found=1
  fi
done < <(git -C "$WORKSPACE_DIR" ls-files --cached --others --exclude-standard -z)
if [[ "$identifier_found" == "1" ]]; then
  fail "personal identifier or absolute macOS user path in publishable files"
fi

brand_pattern='[Pp][Ee][Ll][Oo][Tt][Oo][Nn]'
brand_copy_found=0
while IFS= read -r -d '' candidate; do
  [[ -f "$WORKSPACE_DIR/$candidate" ]] || continue
  if [[ "$candidate" =~ $brand_pattern ]] &&
    [[ "$candidate" != */com/pelotonhack/* ]]; then
    echo "$candidate: branded publishable filename" >&2
    brand_copy_found=1
  fi
  grep -Iq . "$WORKSPACE_DIR/$candidate" || continue
  while IFS=: read -r line_number line_text; do
    scrubbed="$(printf '%s\n' "$line_text" | sed -E \
      -e "s#com[./](one)?${brand_pattern}(hack)?[[:alnum:]_./*:+-]*##g" \
      -e "s#${brand_pattern}://[[:alnum:]_./?&=%:+-]*##g" \
      -e "s#${brand_pattern}/RB1VO/[[:alnum:]_./:+-]*##g" \
      -e "s#/system/app/${brand_pattern}/base[.]apk##g" \
      -e "s#${brand_pattern}-setup[.](json|status)##g" \
      -e "s#${brand_pattern}Automation##g" \
      -e "s#${brand_pattern}_CONTROL_STATE_DIR##g" \
      -e "s#LAUNCH_${brand_pattern}_ACTIVITIES##g")"
    if grep -Eqi "$brand_pattern" <<<"$scrubbed"; then
      printf '%s:%s:%s\n' "$candidate" "$line_number" "$line_text" >&2
      brand_copy_found=1
    fi
  done < <(grep -InE "$brand_pattern" "$WORKSPACE_DIR/$candidate" || true)
done < <(git -C "$WORKSPACE_DIR" ls-files --cached --others --exclude-standard -z)
if [[ "$brand_copy_found" == "1" ]]; then
  fail "trademark copy policy permits compatibility literals only and no branded public copy"
fi

if grep -q 'android.permission.INTERNET' "$HELPER_DIR/AndroidManifest.xml"; then
  fail "core helper unexpectedly requests INTERNET"
fi
grep -q 'android.permission.BLUETOOTH"' "$HELPER_DIR/AndroidManifest.xml" ||
  fail "Zwift bridge is missing legacy Bluetooth permission"
grep -q 'android.permission.BLUETOOTH_ADMIN"' "$HELPER_DIR/AndroidManifest.xml" ||
  fail "Zwift bridge is missing legacy Bluetooth-admin permission"
grep -q 'android.hardware.bluetooth_le' "$HELPER_DIR/AndroidManifest.xml" ||
  fail "Zwift bridge is missing BLE feature declaration"
grep -q 'CYCLING_POWER_SENSOR_LOCATION' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/ZwiftBleBridge.java" ||
  fail "Zwift bridge is missing mandatory Cycling Power Sensor Location"
grep -q 'onNotificationSent' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/ZwiftBleBridge.java" ||
  fail "Zwift bridge does not serialize notifications through completion callbacks"
grep -q 'android:packageNames="com.peloton.activity"' \
  "$HELPER_DIR/res/xml/accessibility_service.xml" ||
  fail "ride accessibility service is not original-app-only"
grep -q 'android:packageNames="com.peacocktv.peacockandroid,com.cbs.ott,com.epix.epix.now"' \
  "$HELPER_DIR/res/xml/tv_remote_accessibility_service.xml" ||
  fail "TV remote accessibility scope is not limited to supported providers"
grep -q 'android:canPerformGestures="false"' \
  "$HELPER_DIR/res/xml/tv_remote_accessibility_service.xml" ||
  fail "TV remote service unexpectedly has gesture capability"
grep -q 'android:accessibilityEventTypes="typeWindowStateChanged"' \
  "$HELPER_DIR/res/xml/tv_remote_accessibility_service.xml" ||
  fail "TV remote service observes events beyond provider window transitions"
grep -q 'android:name=".TvRemoteAccessibilityService"' \
  "$HELPER_DIR/AndroidManifest.xml" ||
  fail "TV remote accessibility service is not registered"
if grep -q 'TvRemoteController' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/RideStarterAccessibilityService.java"; then
  fail "provider navigation is coupled to the gesture-capable ride service"
fi
grep -q '"com.peloton.activity".contentEquals(packageName)' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/RideStarterAccessibilityService.java" ||
  fail "provider events can trigger the ride-overlay refresh path"
if ! sed -n '/private View buildTvRemoteRow()/,/private boolean isAccessibilityServiceEnabled/p' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/MainActivity.java" | \
  grep -q 'Settings.ACTION_ACCESSIBILITY_SETTINGS'; then
  fail "Home does not provide tablet-side TV remote enablement"
fi
grep -q 'TYPE_ACCESSIBILITY_OVERLAY' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/TvRemoteOverlay.java" ||
  fail "TV remote is not implemented as an accessibility overlay"
grep -q 'FLAG_NOT_FOCUSABLE' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/TvRemoteOverlay.java" ||
  fail "TV remote can take focus away from the provider app"
if grep -q 'android.permission.INJECT_EVENTS' "$HELPER_DIR/AndroidManifest.xml"; then
  fail "TV remote unexpectedly requests privileged input injection"
fi
grep -q 'selection.consume' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/TvRemoteController.java" ||
  fail "TV remote OK is not guarded by a one-shot selection"
if grep -q 'candidates.size() == 1' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/TvRemoteController.java"; then
  fail "TV remote can select a sole candidate without explicit navigation"
fi
grep -q 'MAX_TREE_NODES' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/TvRemoteController.java" ||
  fail "TV remote traversal is missing a node budget"
grep -q 'TREE_BUDGET_MS' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/TvRemoteController.java" ||
  fail "TV remote traversal is missing an elapsed-time budget"
grep -q 'Intent.CATEGORY_LEANBACK_LAUNCHER' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/TvRemoteVariantResolver.java" ||
  fail "TV remote does not distinguish touch-native and TV package variants"
grep -q 'activeProviderPackage == null' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/TvRemoteController.java" ||
  fail "TV remote can inspect a root before an event identifies a TV variant"
grep -q 'new TvRemoteVariantResolver(this).hasInstalledTvVariant()' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/MainActivity.java" ||
  fail "Manage Apps exposes TV Remote without checking installed variants"
grep -q 'android:activity.disallowEnterPictureInPictureWhileLaunching' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/MediaLauncher.java" ||
  fail "streaming task switch does not suppress outgoing native PiP"
grep -q 'moveTaskToBack(true)' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/MainActivity.java" ||
  fail "streaming task return does not reveal the preserved media task"
grep -q 'Settings.ACTION_ACCESSIBILITY_SETTINGS' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/MainActivity.java" ||
  fail "Home does not provide tablet-side Accessibility recovery"
grep -q 'reconnectSensors()' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/MainActivity.java" ||
  fail "Home does not provide tablet-side sensor reconnect"
grep -q 'SensorHealth.evaluate' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/MainActivity.java" ||
  fail "Home does not render explicit sensor health"
grep -q 'new OverlayAppDrawer' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/StatsOverlayManager.java" ||
  fail "ride overlay does not provide the provider-neutral app drawer"
grep -q 'MediaLauncher.isRideStartOverlayArmed' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/StatsOverlayManager.java" ||
  fail "media launch does not expose the in-place ride-start control"
grep -q 'Dismiss start ride control' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/StatsOverlayManager.java" ||
  fail "in-place ride-start control cannot be dismissed"
grep -q 'overlay.put("rideStart"' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/ConfigStore.java" ||
  fail "ride-start overlay position is missing from configuration export"
grep -q '!isBikeAppNode(rootNode)' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/StatsOverlayManager.java" ||
  fail "banner automation can traverse a non-original-app window root"
grep -q 'BANNER_MAX_TREE_NODES' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/StatsOverlayManager.java" ||
  fail "banner automation traversal is missing a node budget"
grep -q 'BANNER_TREE_BUDGET_MS' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/StatsOverlayManager.java" ||
  fail "banner automation traversal is missing an elapsed-time budget"
budget_line="$(grep -n 'new BikeAppAccessibilityPolicy.TraversalBudget' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/StatsOverlayManager.java" | \
  head -1 | cut -d: -f1)"
window_loop_line="$(grep -n 'for (AccessibilityWindowInfo window' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/StatsOverlayManager.java" | \
  head -1 | cut -d: -f1)"
[[ -n "$budget_line" && -n "$window_loop_line" && "$budget_line" -lt "$window_loop_line" ]] ||
  fail "banner traversal budget is not shared across the complete window scan"
grep -q 'BikeAppAccessibilityPolicy.allowsPackage' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/StatsOverlayManager.java" ||
  fail "banner traversal does not enforce the tested package policy"
grep -q 'MediaLauncher.resetTransientOverlayState' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/RideStarterAccessibilityService.java" ||
  fail "accessibility teardown does not clear transient media overlay state"
grep -q 'RideStarterAccessibilityService.reloadOverlayPositions' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/ConfigStore.java" ||
  fail "configuration import does not apply restored overlay positions"
grep -q 'MediaLauncher.visibleInstalledApps' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/OverlayAppDrawer.java" ||
  fail "overlay app drawer does not honor launcher order and hidden state"
grep -q 'TYPE_ACCESSIBILITY_OVERLAY' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/OverlayAppDrawer.java" ||
  fail "app drawer is not implemented as an accessibility overlay"
grep -q 'FLAG_NOT_FOCUSABLE' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/OverlayAppDrawer.java" ||
  fail "app drawer can take focus away from protected playback"
grep -q 'new MediaApp("Prime Video", MediaPackageAliases.PRIME_MOBILE)' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/MediaLauncher.java" ||
  fail "launcher does not prefer the touch-native Prime Video package"
grep -q 'PRIME_TV.equals(packageName)' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/MediaPackageAliases.java" ||
  fail "legacy Prime TV configuration does not migrate to the mobile package"
grep -q 'android:name="com.amazon.avod.thirdpartyclient"' \
  "$HELPER_DIR/AndroidManifest.xml" ||
  fail "mobile Prime Video is missing from package visibility queries"
grep -q 'new MediaApp("Max", MediaPackageAliases.MAX_MOBILE)' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/MediaLauncher.java" ||
  fail "launcher does not prefer the working touch-native Max package"
grep -q 'MAX_TV.equals(packageName)' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/MediaPackageAliases.java" ||
  fail "legacy Max TV configuration does not migrate to the mobile package"
if grep -q 'new MediaApp("Max.*MAX_TV\|new MediaApp("Max.*com.wbd.hbomax' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/MediaLauncher.java"; then
  fail "launcher still exposes the incompatible Max TV package"
fi
grep -q 'android:name="com.wbd.stream"' \
  "$HELPER_DIR/AndroidManifest.xml" ||
  fail "mobile Max is missing from package visibility queries"
grep -q 'new MediaApp("Paramount+", MediaPackageAliases.PARAMOUNT_MOBILE)' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/MediaLauncher.java" ||
  fail "launcher does not prefer touch-native Paramount+"
grep -q 'PARAMOUNT_TV.equals(packageName)' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/MediaPackageAliases.java" ||
  fail "legacy Paramount+ TV configuration does not migrate to mobile"
grep -q 'PARAMOUNT_MOBILE, PARAMOUNT_TV' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/MediaPackageAliases.java" ||
  fail "Paramount+ mobile preference is missing its TV fallback"
if grep -q 'MAX_MOBILE, MAX_TV\|PRIME_MOBILE, PRIME_TV' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/MediaPackageAliases.java"; then
  fail "known-incompatible Max or Prime TV package is a runtime fallback"
fi
grep -q 'android:name="com.cbs.app"' \
  "$HELPER_DIR/AndroidManifest.xml" ||
  fail "mobile Paramount+ is missing from package visibility queries"
if grep -q 'new MediaApp("Paramount+".*com.cbs.ott' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/MediaLauncher.java"; then
  fail "launcher still exposes Paramount+ TV as the canonical package"
fi
grep -q $'^com.cbs.app\t420000795\t16.18.0\tcom.cbs.app/base.apk\tbase\t2c2ae1d48738cabcdd0048186b57625f3a5677d9218f51c91f2dc1a1bbe26c49\t' \
  "$WORKSPACE_DIR/docs/security/current-apk-inventory.tsv" ||
  fail "current APK inventory does not pin tested Paramount+ mobile 16.18.0"
grep -q 'a7520085336e50053de6b8b92e5ab89b6f62b5c22653e5684870586d02fb014e' \
  "$WORKSPACE_DIR/docs/security/current-apk-inventory.tsv" ||
  fail "current APK inventory does not pin the Paramount+ mobile signer"
grep -q $'^paramount-mobile-16.18.0-base.apk\t-\tapk\t2c2ae1d48738cabcdd0048186b57625f3a5677d9218f51c91f2dc1a1bbe26c49\t' \
  "$WORKSPACE_DIR/docs/security/downloaded-artifact-inventory.tsv" ||
  fail "download inventory does not pin the Paramount+ mobile base artifact"
grep -q $'^com.peacocktv.peacockandroid\t124070810\t7.8.10\tcom.peacocktv.peacockandroid/base.apk\tbase\ta390c9b54b700109a014daa2880d8f0f0e8c97d6ebc8d54a267d27fa4d3658f1\t' \
  "$WORKSPACE_DIR/docs/security/current-apk-inventory.tsv" ||
  fail "current APK inventory does not pin touch-native Peacock 7.8.10"
grep -q $'^com.epix.epix.now\t2026237011\t237.1.2026237011\tcom.epix.epix.now/base.apk\tbase\ta69bcfdb5830f45a4c3c00b55d975cf7fa7d58b7f70d9485507b88f51d6e5097\t' \
  "$WORKSPACE_DIR/docs/security/current-apk-inventory.tsv" ||
  fail "current APK inventory does not pin touch-native MGM+ 237.1"
grep -q $'^peacock-mobile-7.8.10-base.apk\t-\tapk\ta390c9b54b700109a014daa2880d8f0f0e8c97d6ebc8d54a267d27fa4d3658f1\t' \
  "$WORKSPACE_DIR/docs/security/downloaded-artifact-inventory.tsv" ||
  fail "download inventory does not pin Peacock mobile base"
grep -q $'^mgm-mobile-237.1-base.apk\t-\tapk\ta69bcfdb5830f45a4c3c00b55d975cf7fa7d58b7f70d9485507b88f51d6e5097\t' \
  "$WORKSPACE_DIR/docs/security/downloaded-artifact-inventory.tsv" ||
  fail "download inventory does not pin MGM+ mobile base"
grep -q 'if package_installed com.wbd.stream' \
  "$WORKSPACE_DIR/device-setup/saro-control" ||
  fail "host Max launcher does not prefer the mobile package"
grep -q 'resolve_launcher_activity' \
  "$WORKSPACE_DIR/device-setup/saro-control" ||
  fail "host Max launcher does not resolve installed launcher activities"
grep -q 'install_flags+=(\-d)' \
  "$WORKSPACE_DIR/device-setup/saro-control" ||
  fail "exact recovery cannot restore a verified lower-version package variant"
if grep -q 'com.wbd.stream/com.wbd.beam.BeamActivity' \
  "$WORKSPACE_DIR/device-setup/saro-control"; then
  fail "host Max launcher retains the obsolete mobile activity name"
fi
grep -q $'^com.wbd.stream\t1800016388\t7.8.1.2\t' \
  "$WORKSPACE_DIR/docs/security/current-apk-inventory.tsv" ||
  fail "current APK inventory does not pin tested Max mobile 7.8.1.2"
if sed -n '/appsButton = buildControlButton/,/controls.addView(appsButton)/p' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/StatsOverlayManager.java" |
  grep -q 'MediaLauncher.openHub'; then
  fail "ride-strip APPS action launches the Activity hub"
fi
[[ "$(grep -c 'android:permission="android.permission.DUMP"' \
  "$HELPER_DIR/AndroidManifest.xml")" == "2" ]] ||
  fail "host bridge activities are not shell-permission protected"
if grep -q 'OverlayCommandService\|DirectSensorClient\|JustRideLauncher' \
  "$HELPER_DIR/AndroidManifest.xml" "$HELPER_DIR"/src/com/pelotonhack/ridestarter/*.java; then
  fail "retired ride implementation is still referenced"
fi
if grep -q 'OFFICIAL_WORKOUT_ADAPTER_ENABLED\|clickOfficialWorkoutControl\|END_WORKOUT_CONFIRM' \
  "$HELPER_DIR/src/com/pelotonhack/ridestarter/StatsOverlayManager.java"; then
  fail "official workout-control adapter is still referenced"
fi
if sed -n '/^export_config()/,/^backup_third_party_apks()/p' \
  "$WORKSPACE_DIR/device-setup/saro-control" | grep -q 'am start -W'; then
  fail "NoDisplay configuration bridge can hang under am start -W"
fi

"$WORKSPACE_DIR/tools/tests/test-ride-accumulator.sh"
"$WORKSPACE_DIR/tools/tests/test-host-tools.sh"
"$WORKSPACE_DIR/tools/tests/test-public-release.sh"
[[ -x "$HELPER_DIR/build-release.sh" ]] || fail "release build wrapper is not executable"
if env -u SARO_KEYSTORE -u SARO_KEY_ALIAS -u SARO_KEYSTORE_PASSWORD \
  -u SARO_KEY_PASSWORD SARO_BUILD_VARIANT=release "$HELPER_DIR/build.sh" \
  >/dev/null 2>&1; then
  fail "release build unexpectedly succeeded without private signing"
fi
"$HELPER_DIR/build.sh"
[[ -s "$APK" && -s "$APK.sha256" ]] || fail "helper build or checksum missing"

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$WORKSPACE_DIR/tools/android-sdk}"
JAVA_HOME="${JAVA_HOME:-$WORKSPACE_DIR/tools/jdk17/Contents/Home}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-35.0.0}"
export JAVA_HOME
AAPT="$ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION/aapt"
APKSIGNER="$ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION/apksigner"
[[ -x "$AAPT" && -x "$APKSIGNER" ]] || fail "Android build tools unavailable"
certificate_details="$("$APKSIGNER" verify --verbose --print-certs "$APK" 2>&1)"
badging="$($AAPT dump badging "$APK")"
grep -q "package: name='com.pelotonhack.ridestarter'" <<<"$badging" ||
  fail "unexpected helper package"
grep -q "versionCode='17'" <<<"$badging" || fail "unexpected helper version code"
grep -q "versionName='0.6.6'" <<<"$badging" || fail "unexpected helper version name"
tv_service_metadata="$($AAPT dump xmltree "$APK" \
  res/xml/tv_remote_accessibility_service.xml)"
compiled_manifest="$($AAPT dump xmltree "$APK" AndroidManifest.xml)"
compiled_resources="$($AAPT dump resources "$APK")"
grep -q 'android:accessibilityEventTypes.*0x20' <<<"$tv_service_metadata" ||
  fail "compiled TV remote event scope is not window-state-only"
grep -q 'android:packageNames.*com.peacocktv.peacockandroid,com.cbs.ott,com.epix.epix.now' \
  <<<"$tv_service_metadata" ||
  fail "compiled TV remote package scope is incorrect"
grep -q 'android:canRetrieveWindowContent.*0xffffffff' <<<"$tv_service_metadata" ||
  fail "compiled TV remote cannot retrieve provider navigation nodes"
grep -q 'android:canPerformGestures.*0x0' <<<"$tv_service_metadata" ||
  fail "compiled TV remote unexpectedly supports gesture dispatch"
tv_service_manifest="$(sed -n \
  '/android:name.*TvRemoteAccessibilityService/,+12p' <<<"$compiled_manifest")"
tv_service_resource_id="$(sed -n \
  's/.*spec resource \(0x[0-9a-fA-F]*\).*:xml\/tv_remote_accessibility_service:.*/\1/p' \
  <<<"$compiled_resources" | head -1)"
[[ "$tv_service_resource_id" =~ ^0x[0-9a-fA-F]+$ ]] ||
  fail "compiled TV remote metadata resource is missing"
grep -q 'android:permission.*android.permission.BIND_ACCESSIBILITY_SERVICE' \
  <<<"$tv_service_manifest" ||
  fail "compiled TV remote is missing the accessibility bind permission"
grep -q 'android:name.*android.accessibilityservice' <<<"$tv_service_manifest" ||
  fail "compiled TV remote is missing accessibility metadata"
grep -q "android:resource.*@$tv_service_resource_id" <<<"$tv_service_manifest" ||
  fail "compiled TV remote does not reference the restricted service metadata"
archive_pattern='/Us''ers/[^/]+'
if unzip -Z1 "$APK" | grep -Ei "$archive_pattern" >/dev/null; then
  fail "personal identifier in helper APK paths"
fi
if unzip -p "$APK" | strings | grep -Ei "$archive_pattern" >/dev/null; then
  fail "personal identifier or workstation path in helper APK contents"
fi
if grep -Ei "$archive_pattern" <<<"$certificate_details" >/dev/null; then
  fail "personal identifier or workstation path in helper signing metadata"
fi

if [[ -n "${SARO_PRIVATE_DENYLIST:-}" && -f "$SARO_PRIVATE_DENYLIST" ]]; then
  apk_paths="$(mktemp "${TMPDIR:-/tmp}/saro-apk-paths.XXXXXX")"
  apk_strings="$(mktemp "${TMPDIR:-/tmp}/saro-apk-strings.XXXXXX")"
  unzip -Z1 "$APK" >"$apk_paths"
  unzip -p "$APK" | strings >"$apk_strings"
  while IFS=$'\t' read -r private_scope private_value; do
    [[ "$private_scope" == "all" ]] || continue
    if grep -Fqi -- "$private_value" "$apk_paths" ||
      grep -Fqi -- "$private_value" "$apk_strings" ||
      grep -Fqi -- "$private_value" <<<"$certificate_details"; then
      rm -f "$apk_paths" "$apk_strings"
      fail "private denylist value in helper APK paths, contents, or signing metadata"
    fi
  done <"$SARO_PRIVATE_DENYLIST"
  rm -f "$apk_paths" "$apk_strings"
fi

"$WORKSPACE_DIR/tools/audit-public-tree.sh" "$WORKSPACE_DIR" private-source

echo "SARO release gates: PASS"
