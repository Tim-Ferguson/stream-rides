#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
CONTROL="$WORKSPACE_DIR/device-setup/saro-control"
AUDIT_APKS="$WORKSPACE_DIR/tools/security/audit-apks.sh"
AUDIT_DOWNLOADS="$WORKSPACE_DIR/tools/security/audit-downloads.sh"
FAKE_ADB="$SCRIPT_DIR/fake-adb.sh"
FAKE_AAPT="$SCRIPT_DIR/fake-aapt.sh"
FAKE_APKSIGNER="$SCRIPT_DIR/fake-apksigner.sh"

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/saro-host-tests.XXXXXX")"
cleanup() {
  case "$TMP_ROOT" in
    "${TMPDIR:-/tmp}"/saro-host-tests.*) find "$TMP_ROOT" -depth -delete ;;
    *) echo "Refusing to clean unexpected test path: $TMP_ROOT" >&2 ;;
  esac
}
trap cleanup EXIT

PASS=0
fail() {
  echo "FAIL: $*" >&2
  exit 1
}
pass() {
  PASS=$((PASS + 1))
  printf 'ok %d - %s\n' "$PASS" "$1"
}
expect_failure() {
  local label="$1"
  shift
  if "$@" >"$TMP_ROOT/last.stdout" 2>"$TMP_ROOT/last.stderr"; then
    fail "$label unexpectedly succeeded"
  fi
  pass "$label"
}
sha256_file() {
  shasum -a 256 "$1" | awk '{print $1}'
}

SDK="$TMP_ROOT/sdk"
mkdir -p "$SDK/build-tools/35.0.0"
ln -s "$FAKE_AAPT" "$SDK/build-tools/35.0.0/aapt"
ln -s "$FAKE_APKSIGNER" "$SDK/build-tools/35.0.0/apksigner"

SIGNER_A="1111111111111111111111111111111111111111111111111111111111111111"
SIGNER_B="2222222222222222222222222222222222222222222222222222222222222222"

make_plain_apk() {
  local output="$1"
  local package="$2"
  local version_code="$3"
  local version_name="$4"
  local split="$5"
  local signer="$6"
  printf 'PACKAGE=%s\nVERSION_CODE=%s\nVERSION_NAME=%s\nSPLIT=%s\nSIGNER=%s\nSIGNATURE_VALID=true\nPERMISSION=android.permission.INTERNET\n' \
    "$package" "$version_code" "$version_name" "$split" "$signer" >"$output"
}

write_apk_manifest() {
  local bundle="$1"
  local base="$bundle/apks/com.example.video/base.apk"
  local split="$bundle/apks/com.example.video/config.en.apk"
  printf 'package\tversion_code\tversion_name\tapk\tsha256\n' >"$bundle/apks/manifest.tsv"
  printf 'com.example.video\t7\t7.0\tcom.example.video/base.apk\t%s\n' \
    "$(sha256_file "$base")" >>"$bundle/apks/manifest.tsv"
  printf 'com.example.video\t7\t7.0\tcom.example.video/config.en.apk\t%s\n' \
    "$(sha256_file "$split")" >>"$bundle/apks/manifest.tsv"
}

write_obb_manifest() {
  local bundle="$1"
  local obb="$bundle/obb/com.example.video/main.7.com.example.video.obb"
  printf 'package\tversion_code\tobb\tbytes\tsha256\n' >"$bundle/obb/manifest.tsv"
  printf 'com.example.video\t7\tcom.example.video/main.7.com.example.video.obb\t%s\t%s\n' \
    "$(wc -c <"$obb" | tr -d ' ')" "$(sha256_file "$obb")" >>"$bundle/obb/manifest.tsv"
}

write_top_manifest() {
  local bundle="$1"
  local config_name="saro-setup.json"
  if [[ ! -f "$bundle/$config_name" && -f "$bundle/peloton-setup.json" ]]; then
    config_name="peloton-setup.json"
  fi
  printf 'path\tsha256\n' >"$bundle/manifest.tsv"
  printf 'ride-starter.apk\t%s\n' "$(sha256_file "$bundle/ride-starter.apk")" >>"$bundle/manifest.tsv"
  printf '%s\t%s\n' "$config_name" "$(sha256_file "$bundle/$config_name")" >>"$bundle/manifest.tsv"
  printf 'device-info.txt\t%s\n' "$(sha256_file "$bundle/device-info.txt")" >>"$bundle/manifest.tsv"
  if [[ -f "$bundle/previous-home.txt" ]]; then
    printf 'previous-home.txt\t%s\n' "$(sha256_file "$bundle/previous-home.txt")" >>"$bundle/manifest.tsv"
  fi
  if [[ -f "$bundle/apks/manifest.tsv" ]]; then
    printf 'apks/manifest.tsv\t%s\n' "$(sha256_file "$bundle/apks/manifest.tsv")" >>"$bundle/manifest.tsv"
  fi
  if [[ -f "$bundle/obb/manifest.tsv" ]]; then
    printf 'obb/manifest.tsv\t%s\n' "$(sha256_file "$bundle/obb/manifest.tsv")" >>"$bundle/manifest.tsv"
  fi
}

make_bundle() {
  local bundle="$1"
  mkdir -p "$bundle/apks/com.example.video" "$bundle/obb/com.example.video"
  make_plain_apk "$bundle/ride-starter.apk" com.pelotonhack.ridestarter 1 0.1.0 base "$SIGNER_A"
  make_plain_apk "$bundle/apks/com.example.video/base.apk" com.example.video 7 7.0 base "$SIGNER_B"
  make_plain_apk "$bundle/apks/com.example.video/config.en.apk" com.example.video 7 7.0 config.en "$SIGNER_B"
  printf 'test expansion content\n' >"$bundle/obb/com.example.video/main.7.com.example.video.obb"
  printf '{"schemaVersion":1,"launcher":{}}\n' >"$bundle/saro-setup.json"
  printf 'com.peloton.activity/.MainActivity\n' >"$bundle/previous-home.txt"
  printf 'model=PLTN-RB1VO-2\nproduct=RB1VO\ndevice=RB1VO\nandroid_release=11\napi=30\nabi_list=arm64-v8a,armeabi-v7a,armeabi\nsecurity_patch=2022-10-05\nfingerprint=Peloton/RB1VO/test\n' \
    >"$bundle/device-info.txt"
  write_apk_manifest "$bundle"
  write_obb_manifest "$bundle"
  write_top_manifest "$bundle"
}

new_adb_state() {
  local name="$1"
  local state="$TMP_ROOT/adb-$name"
  mkdir -p "$state"
  printf 'com.peloton.activity/.MainActivity\n' >"$state/home"
  printf '%s\n' "$state"
}

run_control() {
  local state="$1"
  shift
  env ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 FAKE_ADB_STATE_DIR="$state" \
    FAKE_ADB_DRAIN_SETTINGS_STDIN=1 \
    SARO_CONTROL_STATE_DIR="$state/host-state" ANDROID_SDK_ROOT="$SDK" \
    BUILD_TOOLS_VERSION=35.0.0 CONFIG_STATUS_SLEEP=0 CONFIG_STATUS_ATTEMPTS=2 \
    APK_AUDIT_TOOL="$AUDIT_APKS" "$CONTROL" "$@"
}

install_count() {
  local state="$1"
  if [[ ! -f "$state/adb.log" ]]; then
    printf '0\n'
    return
  fi
  grep -Ec '(^| )install(-multiple)?( |$)' "$state/adb.log" || true
}

obb_push_count() {
  local state="$1"
  if [[ ! -f "$state/adb.log" ]]; then
    printf '0\n'
    return
  fi
  grep -Ec '(^| )push .*[/]((main|patch)[.].*[.]obb[.]saro-part)( |$)' \
    "$state/adb.log" || true
}

assert_preflight_failure() {
  local label="$1"
  local bundle="$2"
  local state
  state="$(new_adb_state "$label")"
  expect_failure "$label" run_control "$state" restore-backup "$bundle"
  [[ "$(install_count "$state")" == "0" ]] || fail "$label performed an install before failing"
}

CANONICAL="$TMP_ROOT/canonical"
make_bundle "$CANONICAL"

BASELINE_STATE="$(new_adb_state original-state)"
printf 'com.example.reader/.Accessibility\n' \
  >"$BASELINE_STATE/setting-enabled_accessibility_services"
printf '1\n' >"$BASELINE_STATE/setting-accessibility_enabled"
BASELINE="$TMP_ROOT/original-state"
run_control "$BASELINE_STATE" capture-original-state "$BASELINE" >/dev/null
[[ -f "$BASELINE/manifest.tsv" && -f "$BASELINE/previous-home.txt" &&
  -f "$BASELINE/third-party-packages.tsv" ]] ||
  fail "original-state capture omitted required baseline files"
[[ "$(cat "$BASELINE/previous-home.txt")" == "com.peloton.activity/.MainActivity" ]] ||
  fail "original-state capture recorded the wrong rollback Home"
if grep -Eq ' install| uninstall|shell settings (put|delete)|shell wm (size|density) reset|set-home-activity' \
  "$BASELINE_STATE/adb.log"; then
  fail "original-state capture mutated the fake device"
fi
baseline_adb_lines="$(wc -l <"$BASELINE_STATE/adb.log" | tr -d ' ')"
run_control "$BASELINE_STATE" verify-original-state "$BASELINE" >/dev/null
[[ "$(wc -l <"$BASELINE_STATE/adb.log" | tr -d ' ')" == "$baseline_adb_lines" ]] ||
  fail "offline original-state verification contacted ADB"
pass "original-state baseline is atomic, hash-verified, and device-read-only"

printf 'tampered\n' >>"$BASELINE/display-size.txt"
expect_failure "tampered original-state baseline" run_control \
  "$BASELINE_STATE" verify-original-state "$BASELINE"
pass "original-state verification fails closed on tampering"

FAILED_BASELINE_STATE="$(new_adb_state failed-original-state)"
FAILED_BASELINE="$TMP_ROOT/failed-original-state"
expect_failure "failed original-state capture" env FAKE_EXTRA_PACKAGE=com.example.unknown \
  ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 FAKE_ADB_STATE_DIR="$FAILED_BASELINE_STATE" \
  SARO_CONTROL_STATE_DIR="$FAILED_BASELINE_STATE/host-state" \
  "$CONTROL" capture-original-state "$FAILED_BASELINE"
[[ ! -e "$FAILED_BASELINE" ]] ||
  fail "failed original-state capture published a partial baseline"
[[ -z "$(find "$TMP_ROOT" -maxdepth 1 -name '.failed-original-state.tmp.*' -print -quit)" ]] ||
  fail "failed original-state capture left a staging directory"
pass "failed original-state capture cleans staging and publishes nothing"

BACKUP_STATE="$(new_adb_state backup)"
cp "$CANONICAL/ride-starter.apk" "$BACKUP_STATE/helper.apk"
cp "$CANONICAL/apks/com.example.video/base.apk" "$BACKUP_STATE/video.apk"
cp "$CANONICAL/obb/com.example.video/main.7.com.example.video.obb" "$BACKUP_STATE/video.obb"
GENERATED_BACKUP="$TMP_ROOT/generated-backup"
run_control "$BACKUP_STATE" backup "$GENERATED_BACKUP" --with-apks >/dev/null
[[ -f "$GENERATED_BACKUP/manifest.tsv" && -f "$GENERATED_BACKUP/apks/manifest.tsv" &&
  -f "$GENERATED_BACKUP/obb/manifest.tsv" &&
  -f "$GENERATED_BACKUP/previous-home.txt" ]] || fail "backup did not create every recovery layer"
[[ ! -e "$GENERATED_BACKUP/apks/com.pelotonhack.ridestarter" ]] || \
  fail "backup duplicated SARO in the third-party archive"
grep -q $'^apks/manifest.tsv\t' "$GENERATED_BACKUP/manifest.tsv" || \
  fail "top-level manifest does not cover the APK manifest"
grep -q $'^obb/manifest.tsv\t' "$GENERATED_BACKUP/manifest.tsv" || \
  fail "top-level manifest does not cover the expansion-file manifest"
ANDROID_SDK_ROOT="$SDK" BUILD_TOOLS_VERSION=35.0.0 \
  "$AUDIT_APKS" "$GENERATED_BACKUP/apks" "$TMP_ROOT/generated-backup-audit.tsv" >/dev/null
pass "backup emits APK/OBB manifests and excludes SARO from third-party APKs"

VERIFY_STATE="$(new_adb_state verify-backup)"
env ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 FAKE_ADB_STATE_DIR="$VERIFY_STATE" \
  ANDROID_SDK_ROOT="$SDK" BUILD_TOOLS_VERSION=35.0.0 APK_AUDIT_TOOL="$AUDIT_APKS" \
  "$CONTROL" verify-backup "$GENERATED_BACKUP" >/dev/null
[[ "$(install_count "$VERIFY_STATE")" == "0" ]] || fail "verify-backup installed an APK"
pass "backup verification is complete and non-installing"

printf 'com.pelotonhack.ridestarter/.MainActivity\n' >"$BACKUP_STATE/home"
printf 'com.example.reader/.Accessibility:com.pelotonhack.ridestarter/com.pelotonhack.ridestarter.RideStarterAccessibilityService\n' \
  >"$BACKUP_STATE/setting-enabled_accessibility_services"
printf '1\n' >"$BACKUP_STATE/setting-accessibility_enabled"
run_control "$BACKUP_STATE" audit-live "$GENERATED_BACKUP" >/dev/null
pass "live audit accepts unrelated accessibility services while enforcing SARO's safe state"

printf '%s\n' \
  'com.example.reader/.Accessibility:com.pelotonhack.ridestarter/com.pelotonhack.ridestarter.RideStarterAccessibilityService:com.pelotonhack.ridestarter/com.pelotonhack.ridestarter.TvRemoteAccessibilityService' \
  >"$BACKUP_STATE/setting-enabled_accessibility_services"
expect_failure "live audit rejects optional TV Remote accessibility" run_control \
  "$BACKUP_STATE" audit-live "$GENERATED_BACKUP"
printf 'com.example.reader/.Accessibility\n' \
  >"$BACKUP_STATE/setting-enabled_accessibility_services"
expect_failure "live audit rejects missing ride accessibility" run_control \
  "$BACKUP_STATE" audit-live "$GENERATED_BACKUP"
printf 'com.example.reader/.Accessibility:com.pelotonhack.ridestarter/com.pelotonhack.ridestarter.RideStarterAccessibilityService\n' \
  >"$BACKUP_STATE/setting-enabled_accessibility_services"
pass "live audit enforces ride accessibility without disabling unrelated services"

printf '0\n' >"$BACKUP_STATE/setting-force_resizable_activities"
expect_failure "live audit rejects non-null legacy window override" run_control \
  "$BACKUP_STATE" audit-live "$GENERATED_BACKUP"
rm -f "$BACKUP_STATE/setting-force_resizable_activities"
pass "live audit requires legacy window globals to be absent"

expect_failure "live audit rejects extra checkpoint-package OBB" env FAKE_EXTRA_OBB=1 \
  ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 FAKE_ADB_STATE_DIR="$BACKUP_STATE" \
  SARO_CONTROL_STATE_DIR="$BACKUP_STATE/host-state" ANDROID_SDK_ROOT="$SDK" \
  BUILD_TOOLS_VERSION=35.0.0 CONFIG_STATUS_SLEEP=0 CONFIG_STATUS_ATTEMPTS=2 \
  APK_AUDIT_TOOL="$AUDIT_APKS" "$CONTROL" audit-live "$GENERATED_BACKUP"
pass "live audit compares the exact checkpoint-owned OBB path set"

expect_failure "live audit detects APK hash drift" env FAKE_ADB_HASH_DRIFT_PACKAGE=com.example.video \
  ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 FAKE_ADB_STATE_DIR="$BACKUP_STATE" \
  SARO_CONTROL_STATE_DIR="$BACKUP_STATE/host-state" ANDROID_SDK_ROOT="$SDK" \
  BUILD_TOOLS_VERSION=35.0.0 CONFIG_STATUS_SLEEP=0 CONFIG_STATUS_ATTEMPTS=2 \
  APK_AUDIT_TOOL="$AUDIT_APKS" "$CONTROL" audit-live "$GENERATED_BACKUP"
pass "live audit fails closed on installed APK hash drift"

expect_failure "live audit detects package drift" env FAKE_EXTRA_PACKAGE=com.example.untracked \
  ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 FAKE_ADB_STATE_DIR="$BACKUP_STATE" \
  SARO_CONTROL_STATE_DIR="$BACKUP_STATE/host-state" ANDROID_SDK_ROOT="$SDK" \
  BUILD_TOOLS_VERSION=35.0.0 CONFIG_STATUS_SLEEP=0 CONFIG_STATUS_ATTEMPTS=2 \
  APK_AUDIT_TOOL="$AUDIT_APKS" "$CONTROL" audit-live "$GENERATED_BACKUP"
pass "live audit fails closed on installed-package drift"

INFER_HOME_STATE="$(new_adb_state infer-home)"
cp "$CANONICAL/ride-starter.apk" "$INFER_HOME_STATE/helper.apk"
printf 'com.pelotonhack.ridestarter/.MainActivity\n' >"$INFER_HOME_STATE/home"
run_control "$INFER_HOME_STATE" record-home-rollback >/dev/null
[[ "$(cat "$INFER_HOME_STATE/host-state/previous-home-FAKE123")" == \
  "com.peloton.activity/.MainActivity" ]] || fail "original Home inference recorded the wrong component"
pass "original Home is inferred when SARO is already Home and one safe candidate exists"

CHOOSER_HOME_STATE="$(new_adb_state chooser-home)"
mkdir -p "$CHOOSER_HOME_STATE/host-state"
printf 'com.peloton.activity/.MainActivity\n' \
  >"$CHOOSER_HOME_STATE/host-state/previous-home-FAKE123"
printf 'android/com.android.internal.app.ResolverActivity\n' >"$CHOOSER_HOME_STATE/home"
run_control "$CHOOSER_HOME_STATE" record-home-rollback >/dev/null
[[ "$(cat "$CHOOSER_HOME_STATE/host-state/previous-home-FAKE123")" == \
  "com.peloton.activity/.MainActivity" ]] ||
  fail "launcher chooser replaced the validated original Home"
pass "transient launcher chooser preserves an existing valid Home rollback"

AMBIGUOUS_HOME_STATE="$(new_adb_state ambiguous-home)"
cp "$CANONICAL/ride-starter.apk" "$AMBIGUOUS_HOME_STATE/helper.apk"
printf 'com.pelotonhack.ridestarter/.MainActivity\n' >"$AMBIGUOUS_HOME_STATE/home"
expect_failure "ambiguous original Home inference" env \
  FAKE_EXTRA_HOME_COMPONENT=com.example.alternate/.HomeActivity \
  ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 FAKE_ADB_STATE_DIR="$AMBIGUOUS_HOME_STATE" \
  SARO_CONTROL_STATE_DIR="$AMBIGUOUS_HOME_STATE/host-state" "$CONTROL" record-home-rollback
[[ ! -e "$AMBIGUOUS_HOME_STATE/host-state/previous-home-FAKE123" ]] || \
  fail "ambiguous Home inference published a rollback record"
pass "original Home inference fails closed when multiple candidates exist"

HELPER_ALIAS_STATE="$(new_adb_state helper-alias-home)"
mkdir -p "$HELPER_ALIAS_STATE/host-state"
printf 'com.pelotonhack.ridestarter/.AlternateHome\n' \
  >"$HELPER_ALIAS_STATE/host-state/previous-home-FAKE123"
expect_failure "helper package Home alias rollback" run_control \
  "$HELPER_ALIAS_STATE" restore-home
pass "rollback rejects every Home alias owned by the SARO helper package"

FAILED_BACKUP_STATE="$(new_adb_state failed-backup)"
cp "$CANONICAL/ride-starter.apk" "$FAILED_BACKUP_STATE/helper.apk"
cp "$CANONICAL/apks/com.example.video/base.apk" "$FAILED_BACKUP_STATE/video.apk"
FAILED_BACKUP="$TMP_ROOT/failed-backup"
expect_failure "failed backup is not published" env FAKE_ADB_FAIL_PULL_MATCH=/data/app/saro/base.apk \
  ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 FAKE_ADB_STATE_DIR="$FAILED_BACKUP_STATE" \
  SARO_CONTROL_STATE_DIR="$FAILED_BACKUP_STATE/host-state" ANDROID_SDK_ROOT="$SDK" \
  BUILD_TOOLS_VERSION=35.0.0 CONFIG_STATUS_SLEEP=0 CONFIG_STATUS_ATTEMPTS=2 \
  APK_AUDIT_TOOL="$AUDIT_APKS" "$CONTROL" backup "$FAILED_BACKUP" --with-apks
[[ ! -e "$FAILED_BACKUP" ]] || fail "failed backup left a published destination"
[[ -z "$(find "$TMP_ROOT" -maxdepth 1 -name '.failed-backup.tmp.*' -print -quit)" ]] || \
  fail "failed backup left a staging directory"
pass "failed backup staging is cleaned completely"

VALID_STATE="$(new_adb_state valid)"
printf '%s\n' \
  'com.example.reader/.Accessibility:com.pelotonhack.ridestarter/com.pelotonhack.ridestarter.TvRemoteAccessibilityService' \
  >"$VALID_STATE/setting-enabled_accessibility_services"
printf '1\n' >"$VALID_STATE/setting-accessibility_enabled"
printf '1\n' >"$VALID_STATE/setting-force_resizable_activities"
printf '1\n' >"$VALID_STATE/setting-enable_freeform_support"
run_control "$VALID_STATE" restore-backup "$CANONICAL" >/dev/null
[[ "$(install_count "$VALID_STATE")" == "2" ]] || fail "valid restore did not install one package and one helper"
[[ "$(cat "$VALID_STATE/home")" == "com.pelotonhack.ridestarter/.MainActivity" ]] || fail "valid restore did not assign SARO Home"
[[ "$(cat "$VALID_STATE/setting-enabled_accessibility_services")" == \
  'com.example.reader/.Accessibility:com.pelotonhack.ridestarter/com.pelotonhack.ridestarter.RideStarterAccessibilityService' ]] ||
  fail "restore did not preserve unrelated accessibility or remove the optional SARO TV Remote"
[[ ! -e "$VALID_STATE/setting-force_resizable_activities" &&
  ! -e "$VALID_STATE/setting-enable_freeform_support" ]] ||
  fail "restore did not clear legacy global window overrides"
journal="$(find "$VALID_STATE/host-state/restore-journals" -type f -name '*.jsonl' -print -quit)"
[[ -s "$journal" ]] || fail "valid restore did not create a journal"
awk '!/^\{"schema":1,.*\}$/ {exit 1}' "$journal" || fail "restore journal is not JSONL"
first_install_count="$(install_count "$VALID_STATE")"
first_obb_push_count="$(obb_push_count "$VALID_STATE")"
run_control "$VALID_STATE" restore-backup "$CANONICAL" >/dev/null
[[ "$(install_count "$VALID_STATE")" == "$first_install_count" ]] || fail "resumed restore repeated completed installs"
[[ "$(obb_push_count "$VALID_STATE")" == "$first_obb_push_count" ]] || fail "resumed restore repeated a verified OBB push"
grep -q ' mv -f .*obb[.]saro-part .*obb' "$VALID_STATE/adb.log" || fail "OBB restore was not atomically published"
[[ -z "$(find "$VALID_STATE" -name 'pushed-*.saro-part' -print -quit)" ]] || fail "OBB restore left a staging file"
pass "valid restore is journaled, resumable, and idempotent"

DOWNGRADE_STATE="$(new_adb_state exact-downgrade)"
mkdir -p "$DOWNGRADE_STATE/installed/com.example.video"
make_plain_apk "$DOWNGRADE_STATE/installed/com.example.video/0.apk" \
  com.example.video 8 8.0 base "$SIGNER_B"
env FAKE_INSTALLED_VIDEO_VERSION_CODE=8 ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 \
  FAKE_ADB_STATE_DIR="$DOWNGRADE_STATE" \
  SARO_CONTROL_STATE_DIR="$DOWNGRADE_STATE/host-state" ANDROID_SDK_ROOT="$SDK" \
  BUILD_TOOLS_VERSION=35.0.0 CONFIG_STATUS_SLEEP=0 CONFIG_STATUS_ATTEMPTS=2 \
  APK_AUDIT_TOOL="$AUDIT_APKS" "$CONTROL" restore-backup "$CANONICAL" >/dev/null
grep -q 'install-multiple -r -d .*com[.]example[.]video/base[.]apk' \
  "$DOWNGRADE_STATE/adb.log" ||
  fail "exact-version restore did not request a same-package downgrade"
if grep -q ' uninstall com[.]example[.]video' "$DOWNGRADE_STATE/adb.log"; then
  fail "exact-version downgrade removed app-private data"
fi
pass "exact-version restore uses package-manager downgrade without uninstalling app data"

rm -rf "$VALID_STATE/installed"
rm -f "$VALID_STATE"/pushed-*.obb "$VALID_STATE"/setting-enabled_accessibility_services \
  "$VALID_STATE"/setting-accessibility_enabled
printf 'com.peloton.activity/.MainActivity\n' >"$VALID_STATE/home"
run_control "$VALID_STATE" restore-backup "$CANONICAL" >/dev/null
[[ "$(install_count "$VALID_STATE")" == "$((first_install_count + 2))" ]] || \
  fail "completed journal suppressed APK restoration after a simulated wipe"
[[ "$(obb_push_count "$VALID_STATE")" == "$((first_obb_push_count + 1))" ]] || \
  fail "completed journal suppressed OBB restoration after a simulated wipe"
pass "restore revalidates journaled device state after a wipe"

LOW_SPACE_STATE="$(new_adb_state low-space)"
expect_failure "OBB capacity is preflighted before installation" env FAKE_ADB_FREE_KB=1 \
  ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 FAKE_ADB_STATE_DIR="$LOW_SPACE_STATE" \
  SARO_CONTROL_STATE_DIR="$LOW_SPACE_STATE/host-state" ANDROID_SDK_ROOT="$SDK" \
  BUILD_TOOLS_VERSION=35.0.0 CONFIG_STATUS_SLEEP=0 CONFIG_STATUS_ATTEMPTS=2 \
  APK_AUDIT_TOOL="$AUDIT_APKS" "$CONTROL" restore-backup "$CANONICAL"
[[ "$(install_count "$LOW_SPACE_STATE")" == "0" ]] || fail "low-storage restore installed an APK"
[[ "$(obb_push_count "$LOW_SPACE_STATE")" == "0" ]] || fail "low-storage restore pushed an OBB"
pass "low-storage restore leaves device packages and expansion files untouched"

CHANGED_SPACE_STATE="$(new_adb_state changed-space)"
expect_failure "OBB capacity is rechecked after APK installation" env \
  FAKE_ADB_FREE_KB=131073 FAKE_ADB_FREE_KB_AFTER_INSTALL=1 \
  ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 FAKE_ADB_STATE_DIR="$CHANGED_SPACE_STATE" \
  SARO_CONTROL_STATE_DIR="$CHANGED_SPACE_STATE/host-state" ANDROID_SDK_ROOT="$SDK" \
  BUILD_TOOLS_VERSION=35.0.0 CONFIG_STATUS_SLEEP=0 CONFIG_STATUS_ATTEMPTS=2 \
  APK_AUDIT_TOOL="$AUDIT_APKS" "$CONTROL" restore-backup "$CANONICAL"
[[ "$(install_count "$CHANGED_SPACE_STATE")" == "1" ]] ||
  fail "changed-capacity test did not reach post-APK OBB check"
[[ "$(obb_push_count "$CHANGED_SPACE_STATE")" == "0" ]] ||
  fail "changed-capacity restore pushed an OBB after storage fell below the margin"
pass "post-install storage loss stops OBB transfer before staging"

STALE_STAGE_STATE="$(new_adb_state stale-stage)"
dd if=/dev/zero of="$STALE_STAGE_STATE/pushed-main.7.com.example.video.obb.saro-part" \
  bs=1024 count=2 2>/dev/null
FAKE_ADB_FREE_KB=131073 run_control "$STALE_STAGE_STATE" restore-backup "$CANONICAL" \
  >/dev/null
stale_rm_line="$(grep -n ' rm -f .*obb[.]saro-part' "$STALE_STAGE_STATE/adb.log" |
  head -1 | cut -d: -f1)"
first_df_line="$(grep -n ' df -k /sdcard' "$STALE_STAGE_STATE/adb.log" |
  head -1 | cut -d: -f1)"
[[ -n "$stale_rm_line" && -n "$first_df_line" && "$stale_rm_line" -lt "$first_df_line" ]] ||
  fail "stale OBB staging cleanup did not run before capacity preflight"
pass "stale OBB staging files are removed before capacity preflight"

PUSH_RESUME_STATE="$(new_adb_state push-resume)"
expect_failure "interrupted OBB transfer is journaled" env FAKE_ADB_FAIL_PUSH_ONCE=1 \
  ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 FAKE_ADB_STATE_DIR="$PUSH_RESUME_STATE" \
  SARO_CONTROL_STATE_DIR="$PUSH_RESUME_STATE/host-state" ANDROID_SDK_ROOT="$SDK" \
  BUILD_TOOLS_VERSION=35.0.0 CONFIG_STATUS_SLEEP=0 CONFIG_STATUS_ATTEMPTS=2 \
  APK_AUDIT_TOOL="$AUDIT_APKS" "$CONTROL" restore-backup "$CANONICAL"
[[ ! -e "$PUSH_RESUME_STATE/pushed-main.7.com.example.video.obb" ]] || \
  fail "interrupted OBB transfer published a final file"
[[ -z "$(find "$PUSH_RESUME_STATE" -name 'pushed-*.saro-part' -print -quit)" ]] || \
  fail "interrupted OBB transfer left a staging file"
run_control "$PUSH_RESUME_STATE" restore-backup "$CANONICAL" >/dev/null
[[ -e "$PUSH_RESUME_STATE/pushed-main.7.com.example.video.obb" ]] || \
  fail "resumed OBB transfer did not publish the verified file"
pass "interrupted OBB transfer resumes through a clean staging file"

RESUME_STATE="$(new_adb_state resume)"
expect_failure "interrupted restore is journaled" env FAKE_ADB_FAIL_INSTALL_ONCE=1 \
  ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 FAKE_ADB_STATE_DIR="$RESUME_STATE" \
  SARO_CONTROL_STATE_DIR="$RESUME_STATE/host-state" ANDROID_SDK_ROOT="$SDK" \
  BUILD_TOOLS_VERSION=35.0.0 CONFIG_STATUS_SLEEP=0 APK_AUDIT_TOOL="$AUDIT_APKS" \
  "$CONTROL" restore-backup "$CANONICAL"
run_control "$RESUME_STATE" restore-backup "$CANONICAL" >/dev/null
resume_journal="$(find "$RESUME_STATE/host-state/restore-journals" -type f -name '*.jsonl' -print -quit)"
grep -q '"status":"failed"' "$resume_journal" || fail "interrupted restore did not journal failure"
grep -q '"status":"success"' "$resume_journal" || fail "resumed restore did not journal success"
pass "failed restore resumes deterministically from its JSONL journal"

POSTCONDITION_STATE="$(new_adb_state postcondition)"
expect_failure "final restore postconditions" env FAKE_CONFIG_EXPORT_DRIFT=1 \
  ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 FAKE_ADB_STATE_DIR="$POSTCONDITION_STATE" \
  SARO_CONTROL_STATE_DIR="$POSTCONDITION_STATE/host-state" ANDROID_SDK_ROOT="$SDK" \
  BUILD_TOOLS_VERSION=35.0.0 CONFIG_STATUS_SLEEP=0 CONFIG_STATUS_ATTEMPTS=2 \
  APK_AUDIT_TOOL="$AUDIT_APKS" "$CONTROL" restore-backup "$CANONICAL"
postcondition_journal="$(find "$POSTCONDITION_STATE/host-state/restore-journals" \
  -type f -name '*.jsonl' -print -quit)"
grep -q '"step":"restore","status":"failed"' "$postcondition_journal" ||
  fail "failed final restore verification was not journaled"
pass "restore is not marked successful until final state verification passes"

DISPLAY_FAILURE_STATE="$(new_adb_state display-reset-failure)"
expect_failure "restore display reset failure" env FAKE_ADB_FAIL_WM_RESET=1 \
  ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 FAKE_ADB_STATE_DIR="$DISPLAY_FAILURE_STATE" \
  SARO_CONTROL_STATE_DIR="$DISPLAY_FAILURE_STATE/host-state" ANDROID_SDK_ROOT="$SDK" \
  BUILD_TOOLS_VERSION=35.0.0 CONFIG_STATUS_SLEEP=0 CONFIG_STATUS_ATTEMPTS=2 \
  APK_AUDIT_TOOL="$AUDIT_APKS" "$CONTROL" restore-backup "$CANONICAL"
display_failure_journal="$(find "$DISPLAY_FAILURE_STATE/host-state/restore-journals" \
  -type f -name '*.jsonl' -print -quit)"
grep -q '"step":"display","status":"failed"' "$display_failure_journal" ||
  fail "display reset failure was not journaled"
[[ "$(cat "$DISPLAY_FAILURE_STATE/home")" == "com.peloton.activity/.MainActivity" ]] ||
  fail "restore assigned SARO Home after display reset failure"
pass "restore fails closed and journals an unverifiable display reset"

BOOTSTRAP_DISPLAY_STATE="$(new_adb_state bootstrap-display-reset-failure)"
expect_failure "bootstrap display reset failure" env FAKE_ADB_FAIL_WM_RESET=1 \
  ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 FAKE_ADB_STATE_DIR="$BOOTSTRAP_DISPLAY_STATE" \
  SARO_CONTROL_STATE_DIR="$BOOTSTRAP_DISPLAY_STATE/host-state" ANDROID_SDK_ROOT="$SDK" \
  BUILD_TOOLS_VERSION=35.0.0 "$CONTROL" bootstrap-tablet
[[ "$(install_count "$BOOTSTRAP_DISPLAY_STATE")" == "0" ]] ||
  fail "bootstrap installed the helper after display reset failure"
[[ "$(cat "$BOOTSTRAP_DISPLAY_STATE/host-state/previous-home-FAKE123")" == \
  "com.peloton.activity/.MainActivity" ]] ||
  fail "bootstrap did not preserve the original Home before its first mutation"
home_query_line="$(grep -n 'cmd package resolve-activity --brief' \
  "$BOOTSTRAP_DISPLAY_STATE/adb.log" | head -1 | cut -d: -f1)"
display_mutation_line="$(grep -n 'shell wm size reset' \
  "$BOOTSTRAP_DISPLAY_STATE/adb.log" | head -1 | cut -d: -f1)"
[[ -n "$home_query_line" && -n "$display_mutation_line" &&
  "$home_query_line" -lt "$display_mutation_line" ]] ||
  fail "bootstrap queried the rollback target after its first device mutation"
pass "bootstrap records Home before verifying display cleanup or installing the helper"

run_control "$VALID_STATE" restore-home >/dev/null
[[ "$(cat "$VALID_STATE/home")" == "com.peloton.activity/.MainActivity" ]] || fail "restore-home selected the wrong component"
run_control "$VALID_STATE" restore-backup "$CANONICAL" >/dev/null
[[ "$(cat "$VALID_STATE/home")" == "com.pelotonhack.ridestarter/.MainActivity" ]] || \
  fail "resumed restore did not repair externally changed Home state"
run_control "$VALID_STATE" restore-home >/dev/null
run_control "$VALID_STATE" safe-uninstall >/dev/null
[[ "$(tail -1 "$VALID_STATE/home")" == "com.peloton.activity/.MainActivity" ]] || fail "safe-uninstall changed Home incorrectly"
grep -q 'uninstall com.pelotonhack.ridestarter' "$VALID_STATE/adb.log" || fail "safe-uninstall did not uninstall SARO"
accessibility_delete_line="$(grep -n 'settings delete secure enabled_accessibility_services' \
  "$VALID_STATE/adb.log" | tail -1 | cut -d: -f1)"
uninstall_line="$(grep -n 'uninstall com.pelotonhack.ridestarter' "$VALID_STATE/adb.log" |
  tail -1 | cut -d: -f1)"
[[ -n "$accessibility_delete_line" && "$accessibility_delete_line" -lt "$uninstall_line" ]] ||
  fail "safe-uninstall did not disable SARO accessibility before uninstall"
pass "restore-home and safe-uninstall restore the recorded component first"

STALE_HOME_STATE="$(new_adb_state stale-home-record)"
mkdir -p "$STALE_HOME_STATE/host-state"
printf 'com.example.alternate/.HomeActivity\n' \
  >"$STALE_HOME_STATE/host-state/previous-home-FAKE123"
expect_failure "checkpoint and host Home disagreement" env \
  FAKE_EXTRA_HOME_COMPONENT=com.example.alternate/.HomeActivity \
  ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 FAKE_ADB_STATE_DIR="$STALE_HOME_STATE" \
  SARO_CONTROL_STATE_DIR="$STALE_HOME_STATE/host-state" ANDROID_SDK_ROOT="$SDK" \
  BUILD_TOOLS_VERSION=35.0.0 CONFIG_STATUS_SLEEP=0 APK_AUDIT_TOOL="$AUDIT_APKS" \
  "$CONTROL" restore-backup "$CANONICAL"
[[ "$(install_count "$STALE_HOME_STATE")" == "0" ]] ||
  fail "Home rollback disagreement installed before refusal"
pass "restore rejects host/checkpoint Home disagreement before device mutation"

NON_HOME_STATE="$(new_adb_state non-home-rollback)"
mkdir -p "$NON_HOME_STATE/host-state"
printf 'com.example.video/.MainActivity\n' >"$NON_HOME_STATE/host-state/previous-home-FAKE123"
expect_failure "installed non-Home rollback component" run_control "$NON_HOME_STATE" restore-home
pass "restore-home rejects installed activities that are not current Home candidates"

case_bundle() {
  local name="$1"
  local destination="$TMP_ROOT/$name"
  cp -R "$CANONICAL" "$destination"
  printf '%s\n' "$destination"
}

LEGACY_BUNDLE="$(case_bundle legacy-config-name)"
mv "$LEGACY_BUNDLE/saro-setup.json" "$LEGACY_BUNDLE/peloton-setup.json"
write_top_manifest "$LEGACY_BUNDLE"
LEGACY_STATE="$(new_adb_state legacy-config-name)"
run_control "$LEGACY_STATE" restore-backup "$LEGACY_BUNDLE" >/dev/null
grep -q 'saro-setup.json' "$LEGACY_STATE/adb.log" || \
  fail "legacy recovery configuration was not staged under the neutral device filename"
pass "legacy recovery configuration names restore through the neutral transfer path"

DUAL_CONFIG_BUNDLE="$(case_bundle dual-config-name)"
cp "$DUAL_CONFIG_BUNDLE/saro-setup.json" "$DUAL_CONFIG_BUNDLE/peloton-setup.json"
write_top_manifest "$DUAL_CONFIG_BUNDLE"
assert_preflight_failure "ambiguous dual configuration names" "$DUAL_CONFIG_BUNDLE"
pass "recovery rejects ambiguous neutral and legacy configuration files"

BUNDLE="$(case_bundle apk-tamper)"
printf 'x' >>"$BUNDLE/apks/com.example.video/config.en.apk"
assert_preflight_failure "tampered APK" "$BUNDLE"

BUNDLE="$(case_bundle extra-apk)"
make_plain_apk "$BUNDLE/apks/com.example.video/extra.apk" com.example.video 7 7.0 config.extra "$SIGNER_B"
assert_preflight_failure "extra unlisted APK" "$BUNDLE"

BUNDLE="$(case_bundle missing-apk-manifest)"
rm "$BUNDLE/apks/manifest.tsv"
assert_preflight_failure "missing APK manifest" "$BUNDLE"

BUNDLE="$(case_bundle header-only-top)"
printf 'path\tsha256\n' >"$BUNDLE/manifest.tsv"
assert_preflight_failure "header-only top manifest" "$BUNDLE"

BUNDLE="$(case_bundle header-only-apks)"
printf 'package\tversion_code\tversion_name\tapk\tsha256\n' >"$BUNDLE/apks/manifest.tsv"
write_top_manifest "$BUNDLE"
assert_preflight_failure "header-only APK manifest" "$BUNDLE"

BUNDLE="$(case_bundle duplicate-row)"
tail -1 "$BUNDLE/apks/manifest.tsv" >>"$BUNDLE/apks/manifest.tsv"
write_top_manifest "$BUNDLE"
assert_preflight_failure "duplicate APK row" "$BUNDLE"

BUNDLE="$(case_bundle traversal-row)"
printf 'com.example.video\t7\t7.0\t../escape.apk\t%s\n' "$(sha256_file "$BUNDLE/apks/com.example.video/base.apk")" \
  >>"$BUNDLE/apks/manifest.tsv"
write_top_manifest "$BUNDLE"
assert_preflight_failure "traversal APK row" "$BUNDLE"

BUNDLE="$(case_bundle missing-row-target)"
printf 'com.example.video\t7\t7.0\tcom.example.video/missing.apk\t%s\n' \
  "$(sha256_file "$BUNDLE/apks/com.example.video/base.apk")" >>"$BUNDLE/apks/manifest.tsv"
write_top_manifest "$BUNDLE"
assert_preflight_failure "extra manifest APK row" "$BUNDLE"

BUNDLE="$(case_bundle helper-tamper)"
printf 'x' >>"$BUNDLE/ride-starter.apk"
assert_preflight_failure "helper tampering" "$BUNDLE"

BUNDLE="$(case_bundle config-tamper)"
printf 'x' >>"$BUNDLE/saro-setup.json"
assert_preflight_failure "config tampering" "$BUNDLE"

BUNDLE="$(case_bundle obb-tamper)"
printf 'x' >>"$BUNDLE/obb/com.example.video/main.7.com.example.video.obb"
assert_preflight_failure "tampered expansion file" "$BUNDLE"

BUNDLE="$(case_bundle obb-unsafe-name)"
printf 'com.example.video\t7\tcom.example.video/patch.7.com.other.obb\t1\t%s\n' \
  "$(sha256_file "$BUNDLE/obb/com.example.video/main.7.com.example.video.obb")" \
  >>"$BUNDLE/obb/manifest.tsv"
write_top_manifest "$BUNDLE"
assert_preflight_failure "unsafe expansion-file manifest row" "$BUNDLE"

BUNDLE="$(case_bundle obb-version-mismatch)"
sed -i '' 's/com.example.video\t7\tcom.example.video\/main.7/com.example.video\t8\tcom.example.video\/main.8/' \
  "$BUNDLE/obb/manifest.tsv"
mv "$BUNDLE/obb/com.example.video/main.7.com.example.video.obb" \
  "$BUNDLE/obb/com.example.video/main.8.com.example.video.obb"
write_top_manifest "$BUNDLE"
assert_preflight_failure "expansion-file APK version mismatch" "$BUNDLE"

BUNDLE="$(case_bundle missing-obb-manifest)"
rm "$BUNDLE/obb/manifest.tsv"
assert_preflight_failure "missing expansion-file manifest" "$BUNDLE"

BUNDLE="$(case_bundle invalid-helper-signature)"
sed -i '' 's/SIGNATURE_VALID=true/SIGNATURE_VALID=false/' "$BUNDLE/ride-starter.apk"
write_top_manifest "$BUNDLE"
assert_preflight_failure "invalid helper signature" "$BUNDLE"

BUNDLE="$(case_bundle duplicate-helper-archive)"
mkdir -p "$BUNDLE/apks/com.pelotonhack.ridestarter"
cp "$BUNDLE/ride-starter.apk" "$BUNDLE/apks/com.pelotonhack.ridestarter/base.apk"
printf 'com.pelotonhack.ridestarter\t1\t0.1.0\tcom.pelotonhack.ridestarter/base.apk\t%s\n' \
  "$(sha256_file "$BUNDLE/apks/com.pelotonhack.ridestarter/base.apk")" >>"$BUNDLE/apks/manifest.tsv"
write_top_manifest "$BUNDLE"
assert_preflight_failure "SARO duplicated in third-party archive" "$BUNDLE"

BUNDLE="$(case_bundle malformed-previous-home)"
printf '../not-a-component\n' >"$BUNDLE/previous-home.txt"
write_top_manifest "$BUNDLE"
assert_preflight_failure "malformed previous Home" "$BUNDLE"

BUNDLE="$(case_bundle helper-alias-previous-home)"
printf 'com.pelotonhack.ridestarter/.AlternateHome\n' >"$BUNDLE/previous-home.txt"
write_top_manifest "$BUNDLE"
assert_preflight_failure "helper alias previous Home" "$BUNDLE"

INVALID_LIVE_HOME_BUNDLE="$(case_bundle invalid-live-home)"
printf 'com.example.video/.MainActivity\n' >"$INVALID_LIVE_HOME_BUNDLE/previous-home.txt"
write_top_manifest "$INVALID_LIVE_HOME_BUNDLE"
INVALID_LIVE_HOME_STATE="$(new_adb_state invalid-live-home)"
cp "$CANONICAL/ride-starter.apk" "$INVALID_LIVE_HOME_STATE/helper.apk"
cp "$CANONICAL/apks/com.example.video/base.apk" "$INVALID_LIVE_HOME_STATE/video.apk"
cp "$CANONICAL/obb/com.example.video/main.7.com.example.video.obb" \
  "$INVALID_LIVE_HOME_STATE/video.obb"
printf 'com.pelotonhack.ridestarter/.MainActivity\n' >"$INVALID_LIVE_HOME_STATE/home"
printf 'com.pelotonhack.ridestarter/com.pelotonhack.ridestarter.RideStarterAccessibilityService\n' \
  >"$INVALID_LIVE_HOME_STATE/setting-enabled_accessibility_services"
printf '1\n' >"$INVALID_LIVE_HOME_STATE/setting-accessibility_enabled"
mkdir -p "$INVALID_LIVE_HOME_STATE/host-state"
printf 'com.example.video/.MainActivity\n' \
  >"$INVALID_LIVE_HOME_STATE/host-state/previous-home-FAKE123"
expect_failure "matching but non-Home live rollback records" run_control \
  "$INVALID_LIVE_HOME_STATE" audit-live "$INVALID_LIVE_HOME_BUNDLE"
pass "live audit validates matching rollback records against current Home candidates"

MISMATCH_STATE="$(new_adb_state mismatch)"
expect_failure "device mismatch" env FAKE_DEVICE_MODEL=OTHER ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 \
  FAKE_ADB_STATE_DIR="$MISMATCH_STATE" SARO_CONTROL_STATE_DIR="$MISMATCH_STATE/host-state" \
  ANDROID_SDK_ROOT="$SDK" BUILD_TOOLS_VERSION=35.0.0 APK_AUDIT_TOOL="$AUDIT_APKS" \
  "$CONTROL" restore-backup "$CANONICAL"
[[ "$(install_count "$MISMATCH_STATE")" == "0" ]] || fail "device mismatch installed before refusal"
env FAKE_DEVICE_MODEL=OTHER ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 FAKE_ADB_STATE_DIR="$MISMATCH_STATE" \
  SARO_CONTROL_STATE_DIR="$MISMATCH_STATE/override-state" ANDROID_SDK_ROOT="$SDK" \
  BUILD_TOOLS_VERSION=35.0.0 CONFIG_STATUS_SLEEP=0 APK_AUDIT_TOOL="$AUDIT_APKS" \
  "$CONTROL" restore-backup "$CANONICAL" --allow-device-mismatch >/dev/null
pass "device mismatch blocks all installs unless explicitly overridden"

AMBIGUOUS_STATE="$(new_adb_state ambiguous)"
printf 'List of devices attached\nONE\tdevice\nTWO\tdevice\n' >"$AMBIGUOUS_STATE/devices"
expect_failure "ambiguous ADB devices" env -u ANDROID_SERIAL ADB="$FAKE_ADB" \
  FAKE_ADB_STATE_DIR="$AMBIGUOUS_STATE" "$CONTROL" check
pass "ANDROID_SERIAL selection rejects ambiguity"

PATH_STATE="$(new_adb_state path)"
mkdir -p "$TMP_ROOT/fake-bin"
ln -s "$FAKE_ADB" "$TMP_ROOT/fake-bin/adb"
env -u ADB -u ANDROID_SERIAL PATH="$TMP_ROOT/fake-bin:$PATH" FAKE_ADB_STATE_DIR="$PATH_STATE" \
  "$CONTROL" check >/dev/null
grep -q -- '-s FAKE123 get-state' "$PATH_STATE/adb.log" || fail "PATH ADB fallback did not bind the selected serial"
pass "ADB resolves from PATH and pins the sole discovered serial"

MAX_DUAL_STATE="$(new_adb_state max-dual)"
mkdir -p "$MAX_DUAL_STATE/installed/com.wbd.stream" "$MAX_DUAL_STATE/installed/com.wbd.hbomax"
printf 'mobile\n' >"$MAX_DUAL_STATE/installed/com.wbd.stream/0.apk"
printf 'tv\n' >"$MAX_DUAL_STATE/installed/com.wbd.hbomax/0.apk"
run_control "$MAX_DUAL_STATE" max >/dev/null
grep -q -- '-n com.wbd.stream/com.wbd.fuse.appcore.FuseActivity' "$MAX_DUAL_STATE/adb.log" || \
  fail "Max did not launch the installed mobile activity"
if grep -q -- '-n com.wbd.hbomax/com.wbd.beam.BeamActivity' "$MAX_DUAL_STATE/adb.log"; then
  fail "Max selected the TV package while the working mobile package was installed"
fi
pass "Max prefers the working mobile package and resolves its launcher activity"

MAX_TV_STATE="$(new_adb_state max-tv-fallback)"
mkdir -p "$MAX_TV_STATE/installed/com.wbd.hbomax"
printf 'tv\n' >"$MAX_TV_STATE/installed/com.wbd.hbomax/0.apk"
run_control "$MAX_TV_STATE" max >/dev/null
grep -q -- '-n com.wbd.hbomax/com.wbd.beam.BeamActivity' "$MAX_TV_STATE/adb.log" || \
  fail "Max did not fall back to the installed TV activity"
pass "Max retains a resolved TV-package fallback for older installations"

HEALTH_STATE="$(new_adb_state health)"
expect_failure "helper health check" env FAKE_HELPER_HEALTH_FAIL=1 ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 \
  FAKE_ADB_STATE_DIR="$HEALTH_STATE" SARO_CONTROL_STATE_DIR="$HEALTH_STATE/host-state" \
  ANDROID_SDK_ROOT="$SDK" BUILD_TOOLS_VERSION=35.0.0 CONFIG_STATUS_SLEEP=0 \
  APK_AUDIT_TOOL="$AUDIT_APKS" "$CONTROL" restore-backup "$CANONICAL"
[[ "$(cat "$HEALTH_STATE/home")" == "com.peloton.activity/.MainActivity" ]] || fail "Home changed despite failed helper health check"
pass "helper health failure prevents Home assignment"

ACK_STATE="$(new_adb_state ack)"
expect_failure "negative config acknowledgement" env FAKE_CONFIG_ACK_FAIL=1 ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 \
  FAKE_ADB_STATE_DIR="$ACK_STATE" SARO_CONTROL_STATE_DIR="$ACK_STATE/host-state" \
  ANDROID_SDK_ROOT="$SDK" BUILD_TOOLS_VERSION=35.0.0 CONFIG_STATUS_SLEEP=0 \
  APK_AUDIT_TOOL="$AUDIT_APKS" "$CONTROL" restore-backup "$CANONICAL"
[[ "$(cat "$ACK_STATE/home")" == "com.peloton.activity/.MainActivity" ]] || fail "Home changed after negative config acknowledgement"
pass "configuration import requires a matching positive Activity acknowledgement"

if sed -n '/^export_config()/,/^}/p; /^import_config()/,/^}/p' "$CONTROL" | grep -q 'am start -W'; then
  fail "NoDisplay configuration bridge still uses Android's hanging am start -W mode"
fi
pass "NoDisplay configuration bridge uses status acknowledgement without am start -W"

AUDIT_REPORT="$TMP_ROOT/apk-audit.tsv"
ANDROID_SDK_ROOT="$SDK" BUILD_TOOLS_VERSION=35.0.0 "$AUDIT_APKS" "$CANONICAL/apks" "$AUDIT_REPORT" >/dev/null
[[ "$(wc -l <"$AUDIT_REPORT" | tr -d ' ')" == "3" ]] || fail "APK audit did not emit every base and split"
MUTATED="$(case_bundle audit-mutation)"
printf 'sentinel\n' >"$TMP_ROOT/mutation-report.tsv"
printf 'x' >>"$MUTATED/apks/com.example.video/base.apk"
expect_failure "audit one-byte mutation" env ANDROID_SDK_ROOT="$SDK" BUILD_TOOLS_VERSION=35.0.0 \
  "$AUDIT_APKS" "$MUTATED/apks" "$TMP_ROOT/mutation-report.tsv"
[[ "$(cat "$TMP_ROOT/mutation-report.tsv")" == "sentinel" ]] || fail "failed APK audit replaced output with unverified data"
pass "APK audit measures all files and fails closed on a one-byte mutation"

make_zip_apk() {
  local output="$1"
  local package="$2"
  local version_code="$3"
  local version_name="$4"
  local split="$5"
  local signer="$6"
  local abi="${7:-}"
  local stage="$TMP_ROOT/apk-stage-$RANDOM"
  mkdir -p "$stage/META-INF"
  printf 'PACKAGE=%s\nVERSION_CODE=%s\nVERSION_NAME=%s\nSPLIT=%s\nSIGNER=%s\nSIGNATURE_VALID=true\n' \
    "$package" "$version_code" "$version_name" "$split" "$signer" >"$stage/META-INF/saro-test-meta"
  if [[ -n "$abi" ]]; then
    mkdir -p "$stage/lib/$abi"
    printf 'native\n' >"$stage/lib/$abi/libtest.so"
  fi
  (cd "$stage" && zip -q -r "$output" .)
  find "$stage" -depth -delete
}

make_xapk() {
  local output="$1"
  local base_package="$2"
  local split_package="$3"
  local base_version="$4"
  local split_version="$5"
  local base_signer="$6"
  local split_signer="$7"
  local second_role="${8:-config.en}"
  local abi="${9:-}"
  local members="$TMP_ROOT/xapk-members-$RANDOM"
  mkdir -p "$members"
  make_zip_apk "$members/base.apk" "$base_package" "$base_version" 1.0 base "$base_signer" "$abi"
  make_zip_apk "$members/config.apk" "$split_package" "$split_version" 1.0 "$second_role" "$split_signer" "$abi"
  (cd "$members" && zip -q "$output" base.apk config.apk)
  find "$members" -depth -delete
}

DOWNLOADS="$TMP_ROOT/downloads"
mkdir -p "$DOWNLOADS"
make_xapk "$DOWNLOADS/valid.xapk" com.example.stream com.example.stream 10 10 "$SIGNER_A" "$SIGNER_A" config.en arm64-v8a
printf 'expansion\n' >"$DOWNLOADS/main.10.com.example.stream.obb"
DOWNLOAD_REPORT="$TMP_ROOT/download-report.tsv"
ANDROID_SDK_ROOT="$SDK" BUILD_TOOLS_VERSION=35.0.0 "$AUDIT_DOWNLOADS" "$DOWNLOADS" "$DOWNLOAD_REPORT" >/dev/null
grep -q $'valid.xapk\t.*\tunresolved$' "$DOWNLOAD_REPORT" || fail "download audit did not record unresolved provenance"
grep -q $'main.10.com.example.stream.obb\t-\tobb\t.*\tcom.example.stream\t10\t-\texpansion:main\t' \
  "$DOWNLOAD_REPORT" || fail "download audit did not inventory the OBB expansion artifact"
pass "download audit accepts coherent XAPK/OBB artifacts and records unresolved source metadata"

printf 'artifact\tsource_url\tretrieved_at\tsource_status\nvalid.xapk\thttps://example.test/valid.xapk\t2026-08-11T12:00:00Z\tresolved\n' \
  >"$DOWNLOADS/sources.tsv"
ANDROID_SDK_ROOT="$SDK" BUILD_TOOLS_VERSION=35.0.0 \
  "$AUDIT_DOWNLOADS" "$DOWNLOADS" "$DOWNLOAD_REPORT" "$DOWNLOADS/sources.tsv" >/dev/null
grep -q $'https://example.test/valid.xapk\t2026-08-11T12:00:00Z\tresolved$' "$DOWNLOAD_REPORT" || \
  fail "download audit did not retain resolved source evidence"
pass "download audit records validated source URL and retrieval time"

BAD_OBB_ROOT="$TMP_ROOT/bad-obb"
mkdir -p "$BAD_OBB_ROOT"
printf 'bad\n' >"$BAD_OBB_ROOT/not-a-package.obb"
expect_failure "unsafe OBB artifact name" env ANDROID_SDK_ROOT="$SDK" BUILD_TOOLS_VERSION=35.0.0 \
  "$AUDIT_DOWNLOADS" "$BAD_OBB_ROOT" "$TMP_ROOT/bad-obb.tsv"

expect_bad_xapk() {
  local label="$1"
  local file="$2"
  local root="$TMP_ROOT/download-$label"
  mkdir -p "$root"
  cp "$file" "$root/test.xapk"
  expect_failure "$label" env ANDROID_SDK_ROOT="$SDK" BUILD_TOOLS_VERSION=35.0.0 \
    "$AUDIT_DOWNLOADS" "$root" "$TMP_ROOT/$label.tsv"
}

XAPK="$TMP_ROOT/mixed-package.xapk"
make_xapk "$XAPK" com.example.one com.example.two 1 1 "$SIGNER_A" "$SIGNER_A"
expect_bad_xapk "mixed package XAPK" "$XAPK"
XAPK="$TMP_ROOT/mixed-version.xapk"
make_xapk "$XAPK" com.example.one com.example.one 1 2 "$SIGNER_A" "$SIGNER_A"
expect_bad_xapk "mixed version XAPK" "$XAPK"
XAPK="$TMP_ROOT/mixed-signer.xapk"
make_xapk "$XAPK" com.example.one com.example.one 1 1 "$SIGNER_A" "$SIGNER_B"
expect_bad_xapk "mixed signer XAPK" "$XAPK"
XAPK="$TMP_ROOT/duplicate-base.xapk"
make_xapk "$XAPK" com.example.one com.example.one 1 1 "$SIGNER_A" "$SIGNER_A" base
expect_bad_xapk "duplicate base XAPK" "$XAPK"
XAPK="$TMP_ROOT/wrong-abi.xapk"
make_xapk "$XAPK" com.example.one com.example.one 1 1 "$SIGNER_A" "$SIGNER_A" config.en x86
expect_bad_xapk "missing target ABI XAPK" "$XAPK"

UNSAFE_ROOT="$TMP_ROOT/unsafe-download"
mkdir -p "$UNSAFE_ROOT/container"
make_zip_apk "$UNSAFE_ROOT/evil.apk" com.example.evil 1 1.0 base "$SIGNER_A"
(cd "$UNSAFE_ROOT/container" && zip -q "$UNSAFE_ROOT/unsafe.xapk" ../evil.apk)
rm "$UNSAFE_ROOT/evil.apk"
expect_failure "unsafe XAPK member path" env ANDROID_SDK_ROOT="$SDK" BUILD_TOOLS_VERSION=35.0.0 \
  "$AUDIT_DOWNLOADS" "$UNSAFE_ROOT" "$TMP_ROOT/unsafe.tsv"

WINDOW_STATE="$(new_adb_state window)"
expect_failure "legacy window-agent command remains quarantined" run_control "$WINDOW_STATE" start-window-agent
env ADB="$FAKE_ADB" ANDROID_SERIAL=FAKE123 FAKE_ADB_STATE_DIR="$WINDOW_STATE" \
  SARO_CONTROL_STATE_DIR="$WINDOW_STATE/host-state" SARO_ENABLE_UNSAFE_WINDOW_AGENT=1 \
  "$CONTROL" research-start-window-agent --acknowledge-shell-risk >/dev/null
token_file="$WINDOW_STATE/host-state/window-agent-token-FAKE123"
[[ "$(tr -d '\n' <"$token_file" | wc -c | tr -d ' ')" == "64" ]] || fail "window-agent token has wrong length"
mode="$(stat -f '%Lp' "$token_file" 2>/dev/null || stat -c '%a' "$token_file")"
[[ "$mode" == "600" ]] || fail "window-agent token mode is $mode, expected 600"
run_control "$WINDOW_STATE" window-agent-status >/dev/null
run_control "$WINDOW_STATE" stop-window-agent >/dev/null
[[ ! -e "$token_file" && ! -e "$WINDOW_STATE/window-agent-running" ]] || fail "window-agent stop did not remove capability/process state"
pass "research window agent uses a mode-0600 capability and authenticated shutdown"

if awk '
  /^bootstrap_tablet\(\)/ {inside=1}
  inside && /^}/ {exit found ? 1 : 0}
  inside && (/start_window_agent/ || /app_process/) {found=1}
' "$CONTROL"; then
  pass "normal bootstrap has no window-agent start path"
else
  fail "normal bootstrap can start the window agent"
fi

printf '1..%d\n' "$PASS"
