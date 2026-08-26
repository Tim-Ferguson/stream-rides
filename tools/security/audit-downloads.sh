#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$WORKSPACE_DIR/tools/android-sdk}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-35.0.0}"
JAVA_HOME="${JAVA_HOME:-$WORKSPACE_DIR/tools/jdk17/Contents/Home}"
export JAVA_HOME
AAPT="$ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION/aapt"
APKSIGNER="$ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION/apksigner"
TARGET_ABI="${AUDIT_TARGET_ABI:-arm64-v8a}"
ALLOW_INCOMPATIBLE_ABI="${AUDIT_ALLOW_INCOMPATIBLE_ABI:-0}"

if [[ "$ALLOW_INCOMPATIBLE_ABI" != "0" && "$ALLOW_INCOMPATIBLE_ABI" != "1" ]]; then
  echo "AUDIT_ALLOW_INCOMPATIBLE_ABI must be 0 or 1." >&2
  exit 1
fi

ROOT="${1:-}"
OUTPUT="${2:-/dev/stdout}"
SOURCE_METADATA="${3:-}"

if [[ ! -d "$ROOT" ]]; then
  echo "Usage: audit-downloads.sh <artifact-directory> [output.tsv] [source-metadata.tsv]" >&2
  exit 1
fi
ROOT="$(cd "$ROOT" && pwd)"
if [[ -z "$SOURCE_METADATA" && -f "$ROOT/sources.tsv" ]]; then
  SOURCE_METADATA="$ROOT/sources.tsv"
fi
for tool in "$AAPT" "$APKSIGNER"; do
  if [[ ! -x "$tool" ]]; then
    echo "Missing Android build tool: $tool" >&2
    exit 1
  fi
done
if ! command -v unzip >/dev/null 2>&1; then
  echo "unzip is required to audit XAPK containers." >&2
  exit 1
fi

sha256_file() {
  local path="$1"
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$path" | awk '{print $1}'
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$path" | awk '{print $1}'
  else
    echo "A SHA-256 tool is required (shasum or sha256sum)." >&2
    return 1
  fi
}

file_size() {
  if stat -f '%z' "$1" >/dev/null 2>&1; then
    stat -f '%z' "$1"
  else
    stat -c '%s' "$1"
  fi
}

signer_values() {
  local verify="$1"
  local field="$2"
  awk -F': ' -v field="$field" '
    $0 ~ "^Signer (#[0-9]+|\\(minSdkVersion=.*\\)) certificate " field ":" {
      if (!seen[$2]++) {
        printf "%s%s", separator, $2
        separator=" | "
      }
    }
  ' <<<"$verify"
}

signature_schemes() {
  awk '
    /^Verified using v[0-9.]+ scheme .*: true$/ {
      scheme=$0
      sub(/^Verified using /, "", scheme)
      sub(/ scheme.*/, "", scheme)
      printf "%s%s", separator, scheme
      separator=","
    }
  ' <<<"$1"
}

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/saro-download-audit.XXXXXX")"
cleanup() {
  case "$TMP_DIR" in
    "${TMPDIR:-/tmp}"/saro-download-audit.*) find "$TMP_DIR" -depth -delete ;;
    *) echo "Refusing to clean unexpected path: $TMP_DIR" >&2 ;;
  esac
}
trap cleanup EXIT

ARTIFACTS="$TMP_DIR/artifacts"
: >"$ARTIFACTS"
bad_artifact=0
while IFS= read -r -d '' artifact; do
  relative="${artifact#"$ROOT"/}"
  if [[ "$relative" == *$'\n'* || "$relative" == *$'\t'* || "$relative" == *'\\'* ||
    "$relative" == /* || "$relative" == [A-Za-z]:* || "$relative" == ../* ||
    "$relative" == */../* || "$relative" == */.. ]]; then
    echo "Unsafe downloaded artifact path: $relative" >&2
    bad_artifact=1
  else
    printf '%s\n' "$relative" >>"$ARTIFACTS"
  fi
done < <(find "$ROOT" -type f \( -name '*.apk' -o -name '*.xapk' -o -name '*.obb' \) -print0)
if find "$ROOT" -type l -print -quit | grep -q .; then
  echo "Download directory contains a symlink; refusing ambiguous audit input." >&2
  bad_artifact=1
fi
[[ "$bad_artifact" == "0" ]] || exit 1
LC_ALL=C sort -u "$ARTIFACTS" -o "$ARTIFACTS"
if [[ ! -s "$ARTIFACTS" ]]; then
  echo "No APK, XAPK, or OBB artifacts found under $ROOT" >&2
  exit 1
fi

SOURCES="$TMP_DIR/sources"
: >"$SOURCES"
if [[ -n "$SOURCE_METADATA" ]]; then
  if [[ ! -f "$SOURCE_METADATA" ]]; then
    echo "Source metadata file not found: $SOURCE_METADATA" >&2
    exit 1
  fi
  if ! awk -F '\t' '
    NR == 1 {
      if (NF != 4 || $1 != "artifact" || $2 != "source_url" ||
          $3 != "retrieved_at" || $4 != "source_status") exit 20
      next
    }
    {
      if (NF != 4 || $1 == "" || $1 ~ /(^\/|^[A-Za-z]:|(^|\/)\.\.($|\/)|\\|[[:cntrl:]])/ || seen[$1]++) exit 21
      if ($4 == "resolved") {
        if ($2 !~ /^https?:\/\// || $3 !~ /^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$/) exit 22
      } else if ($4 == "unresolved") {
        if ($2 != "-" || ($3 != "-" && $3 !~ /^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$/)) exit 23
      } else exit 24
      print
      count++
    }
    END {if (count == 0) exit 25}
  ' "$SOURCE_METADATA" >"$SOURCES"; then
    echo "Source metadata is malformed, duplicated, or unsafe." >&2
    exit 1
  fi
  if awk -F '\t' 'NR==FNR {artifact[$1]=1; next} !artifact[$1] {print $1}' "$ARTIFACTS" "$SOURCES" | grep -q .; then
    echo "Source metadata names an artifact that is not present." >&2
    exit 1
  fi
fi

source_fields() {
  local artifact="$1"
  local row
  row="$(awk -F '\t' -v artifact="$artifact" '$1 == artifact {print; exit}' "$SOURCES")"
  if [[ -n "$row" ]]; then
    SOURCE_URL="$(cut -f2 <<<"$row")"
    RETRIEVED_AT="$(cut -f3 <<<"$row")"
    SOURCE_STATUS="$(cut -f4 <<<"$row")"
  else
    SOURCE_URL="-"
    RETRIEVED_AT="-"
    SOURCE_STATUS="unresolved"
  fi
}

REPORT="$TMP_DIR/report.tsv"
printf 'artifact\tentry\tkind\tartifact_sha256\tentry_sha256\tentry_size_bytes\tpackage\tversion_code\tversion_name\tapk_role\tsigner_sha256\tsigner_dn\tsignature_schemes\tsource_stamp_verified\tsource_stamp_sha256\tsource_url\tretrieved_at\tsource_status\n' >"$REPORT"

audit_apk() {
  local artifact="$1"
  local entry="$2"
  local kind="$3"
  local artifact_sha="$4"
  local apk="$5"
  local badging verify split_name

  if ! verify="$("$APKSIGNER" verify --verbose --print-certs "$apk" </dev/null 2>&1)"; then
    echo "APK signature verification failed: $artifact ($entry)" >&2
    echo "$verify" >&2
    return 1
  fi
  badging="$("$AAPT" dump badging "$apk" </dev/null 2>&1 || true)"
  MEASURED_PACKAGE="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$badging")"
  MEASURED_VERSION_CODE="$(sed -n "s/^package: .* versionCode='\([^']*\)'.*/\1/p" <<<"$badging")"
  MEASURED_VERSION_NAME="$(sed -n "s/^package: .* versionName='\([^']*\)'.*/\1/p" <<<"$badging")"
  split_name="$(sed -n "s/^package: .* split='\([^']*\)'.*/\1/p" <<<"$badging")"
  MEASURED_SIGNER="$(signer_values "$verify" 'SHA-256 digest')"
  signer_dn="$(signer_values "$verify" 'DN' | tr '\t\r\n' '   ')"
  if [[ -z "$MEASURED_PACKAGE" || -z "$MEASURED_VERSION_CODE" || -z "$MEASURED_SIGNER" ]]; then
    echo "Could not independently measure APK identity: $artifact ($entry)" >&2
    return 1
  fi
  if [[ -z "$split_name" ]]; then
    MEASURED_ROLE="base"
    MEASURED_SPLIT=""
  else
    MEASURED_ROLE="split:$split_name"
    MEASURED_SPLIT="$split_name"
  fi
  entry_sha="$(sha256_file "$apk")"
  schemes="$(signature_schemes "$verify")"
  stamp_verified="$(awk -F': ' '/^Verified for SourceStamp:/{print $2; exit}' <<<"$verify")"
  stamp_sha="$(awk -F': ' '/^Source Stamp Signer certificate SHA-256 digest:/{print $2; exit}' <<<"$verify")"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$artifact" "$entry" "$kind" "$artifact_sha" "$entry_sha" "$(file_size "$apk")" \
    "$MEASURED_PACKAGE" "$MEASURED_VERSION_CODE" "$MEASURED_VERSION_NAME" \
    "$MEASURED_ROLE" "$MEASURED_SIGNER" "$signer_dn" "$schemes" \
    "$stamp_verified" "$stamp_sha" "$SOURCE_URL" "$RETRIEVED_AT" "$SOURCE_STATUS" >>"$REPORT"
}

while IFS= read -r relative; do
  artifact="$ROOT/$relative"
  artifact_sha="$(sha256_file "$artifact")"
  source_fields "$relative"
  case "$artifact" in
    *.apk)
      audit_apk "$relative" "-" "apk" "$artifact_sha" "$artifact"
      ;;
    *.obb)
      filename="${relative##*/}"
      if [[ ! "$filename" =~ ^(main|patch)[.]([0-9]+)[.]([A-Za-z0-9_]+([.][A-Za-z0-9_]+)+)[.]obb$ ]]; then
        echo "OBB filename does not encode a safe package and version: $relative" >&2
        exit 1
      fi
      printf '%s\t-\tobb\t%s\t%s\t%s\t%s\t%s\t-\texpansion:%s\t-\t-\t-\t-\t-\t%s\t%s\t%s\n' \
        "$relative" "$artifact_sha" "$artifact_sha" "$(file_size "$artifact")" \
        "${BASH_REMATCH[3]}" "${BASH_REMATCH[2]}" "${BASH_REMATCH[1]}" \
        "$SOURCE_URL" "$RETRIEVED_AT" "$SOURCE_STATUS" >>"$REPORT"
      ;;
    *.xapk)
      member_list="$TMP_DIR/member-list"
      if ! unzip -Z1 "$artifact" >"$member_list"; then
        echo "Could not list XAPK container: $relative" >&2
        exit 1
      fi
      if [[ ! -s "$member_list" ]]; then
        echo "XAPK container is empty: $relative" >&2
        exit 1
      fi
      if awk '
        /(^\/|^[A-Za-z]:|(^|\/)\.\.($|\/)|\\|[[:cntrl:]])/ {bad=1}
        seen[$0]++ {bad=1}
        END {exit !bad}
      ' "$member_list"; then
        echo "XAPK contains an unsafe or duplicate member path: $relative" >&2
        exit 1
      fi
      printf '%s\t-\txapk-container\t%s\t-\t%s\t-\t-\t-\t-\t-\t-\t-\t-\t-\t%s\t%s\t%s\n' \
        "$relative" "$artifact_sha" "$(file_size "$artifact")" \
        "$SOURCE_URL" "$RETRIEVED_AT" "$SOURCE_STATUS" >>"$REPORT"

      member_index=0
      apk_count=0
      base_count=0
      native_seen=0
      target_abi_seen=0
      expected_package=""
      expected_version_code=""
      expected_version_name=""
      expected_signer=""
      split_names="$TMP_DIR/split-names"
      : >"$split_names"
      while IFS= read -r member; do
        [[ "$member" == *.apk ]] || continue
        apk_count=$((apk_count + 1))
        member_index=$((member_index + 1))
        extracted="$TMP_DIR/member-$member_index.apk"
        if ! unzip -p "$artifact" "$member" >"$extracted"; then
          echo "Could not extract XAPK member: $relative ($member)" >&2
          exit 1
        fi
        audit_apk "$relative" "$member" "xapk-member" "$artifact_sha" "$extracted"
        if [[ -z "$expected_package" ]]; then
          expected_package="$MEASURED_PACKAGE"
          expected_version_code="$MEASURED_VERSION_CODE"
          expected_version_name="$MEASURED_VERSION_NAME"
          expected_signer="$MEASURED_SIGNER"
        elif [[ "$MEASURED_PACKAGE" != "$expected_package" ||
          "$MEASURED_VERSION_CODE" != "$expected_version_code" ||
          ( -n "$MEASURED_VERSION_NAME" && -n "$expected_version_name" &&
            "$MEASURED_VERSION_NAME" != "$expected_version_name" ) ||
          "$MEASURED_SIGNER" != "$expected_signer" ]]; then
          echo "XAPK mixes package, version, or signer identities: $relative" >&2
          exit 1
        fi
        if [[ -z "$expected_version_name" && -n "$MEASURED_VERSION_NAME" ]]; then
          expected_version_name="$MEASURED_VERSION_NAME"
        fi
        if [[ "$MEASURED_ROLE" == "base" ]]; then
          base_count=$((base_count + 1))
        else
          if grep -Fxq "$MEASURED_SPLIT" "$split_names"; then
            echo "XAPK contains duplicate split identity '$MEASURED_SPLIT': $relative" >&2
            exit 1
          fi
          printf '%s\n' "$MEASURED_SPLIT" >>"$split_names"
        fi
        apk_members="$TMP_DIR/apk-members"
        if unzip -Z1 "$extracted" >"$apk_members" 2>/dev/null; then
          while IFS= read -r apk_member; do
            case "$apk_member" in
              lib/*/*)
                native_seen=1
                abi="${apk_member#lib/}"
                abi="${abi%%/*}"
                [[ "$abi" == "$TARGET_ABI" ]] && target_abi_seen=1
                ;;
            esac
          done <"$apk_members"
        fi
      done <"$member_list"
      if [[ "$apk_count" == "0" || "$base_count" != "1" ]]; then
        echo "XAPK must contain APK members and exactly one base APK: $relative (APKs=$apk_count, bases=$base_count)" >&2
        exit 1
      fi
      if [[ "$native_seen" == "1" && "$target_abi_seen" != "1" ]]; then
        if [[ "$ALLOW_INCOMPATIBLE_ABI" == "1" ]]; then
          echo "WARNING: inventorying XAPK without $TARGET_ABI payload: $relative" >&2
        else
          echo "XAPK contains native code but no $TARGET_ABI payload: $relative" >&2
          exit 1
        fi
      fi
      ;;
  esac
done <"$ARTIFACTS"

if [[ "$OUTPUT" == "/dev/stdout" || "$OUTPUT" == "-" ]]; then
  cat "$REPORT"
else
  cp "$REPORT" "$OUTPUT"
fi
echo "Wrote verified download audit to $OUTPUT" >&2
