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

ARCHIVE="${1:-}"
OUTPUT="${2:-/dev/stdout}"

if [[ ! -d "$ARCHIVE" || ! -f "$ARCHIVE/manifest.tsv" ]]; then
  echo "Usage: audit-apks.sh <recovery-apks-directory> [output.tsv]" >&2
  exit 1
fi
ARCHIVE="$(cd "$ARCHIVE" && pwd)"
MANIFEST="$ARCHIVE/manifest.tsv"
for tool in "$AAPT" "$APKSIGNER"; do
  if [[ ! -x "$tool" ]]; then
    echo "Missing Android build tool: $tool" >&2
    exit 1
  fi
done

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

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/saro-apk-audit.XXXXXX")"
cleanup() {
  case "$TMP_DIR" in
    "${TMPDIR:-/tmp}"/saro-apk-audit.*) find "$TMP_DIR" -depth -delete ;;
    *) echo "Refusing to clean unexpected path: $TMP_DIR" >&2 ;;
  esac
}
trap cleanup EXIT

VALIDATED="$TMP_DIR/validated.tsv"
if ! awk -F '\t' '
  NR == 1 {
    if (NF != 5 || $1 != "package" || $2 != "version_code" ||
        $3 != "version_name" || $4 != "apk" || $5 != "sha256") exit 20
    next
  }
  {
    if (NF != 5 || $1 !~ /^[A-Za-z0-9_]+([.][A-Za-z0-9_]+)+$/ ||
        $2 !~ /^[0-9]+$/ || $3 == "" ||
        $4 !~ /^[A-Za-z0-9_]+([.][A-Za-z0-9_]+)+\/[A-Za-z0-9._-]+[.]apk$/ ||
        length($5) != 64 || $5 !~ /^[0-9a-f]+$/) exit 21
    if (index($4, "../") || index($4, "/..") || substr($4, 1, 1) == "/") exit 22
    split($4, parts, "/")
    if (parts[1] != $1) exit 23
    if (seen_path[$4]++ || seen_row[$0]++) exit 24
    identity=$2 SUBSEP $3
    if (seen_package[$1]++ && package_identity[$1] != identity) exit 26
    package_identity[$1] = identity
    print
    count++
  }
  END {if (count == 0) exit 25}
' "$MANIFEST" >"$VALIDATED"; then
  echo "APK manifest is malformed, duplicated, unsafe, or header-only." >&2
  exit 1
fi

EXPECTED="$TMP_DIR/expected"
ACTUAL="$TMP_DIR/actual"
BAD_TYPES="$TMP_DIR/bad-types"
awk -F '\t' '{print $4}' "$VALIDATED" | LC_ALL=C sort -u >"$EXPECTED"
: >"$ACTUAL"
: >"$BAD_TYPES"
while IFS= read -r -d '' file; do
  relative="${file#"$ARCHIVE"/}"
  [[ "$relative" == "manifest.tsv" ]] && continue
  if [[ "$relative" == *$'\n'* || "$relative" == *$'\t'* ]]; then
    printf 'unsafe-name\n' >>"$BAD_TYPES"
  else
    printf '%s\n' "$relative" >>"$ACTUAL"
  fi
done < <(find "$ARCHIVE" -mindepth 1 -type f -print0)
while IFS= read -r -d '' file; do
  printf '%s\n' "${file#"$ARCHIVE"/}" >>"$BAD_TYPES"
done < <(find "$ARCHIVE" -mindepth 1 ! -type d ! -type f -print0)
if [[ -s "$BAD_TYPES" ]]; then
  echo "APK archive contains symlinks, special files, or unsafe names." >&2
  exit 1
fi
LC_ALL=C sort -u "$ACTUAL" -o "$ACTUAL"
if ! cmp -s "$EXPECTED" "$ACTUAL"; then
  echo "APK manifest/file bijection failed. Missing or extra files:" >&2
  diff -u "$EXPECTED" "$ACTUAL" >&2 || true
  exit 1
fi

REPORT="$TMP_DIR/report.tsv"
printf 'package\tversion_code\tversion_name\tapk\tapk_role\tsha256\tsize_bytes\tsigner_sha256\tsigner_dn\tsignature_schemes\tsource_stamp_verified\tsource_stamp_sha256\tpermissions\n' >"$REPORT"

PACKAGES="$TMP_DIR/packages"
awk -F '\t' '!seen[$1]++ {print $1}' "$VALIDATED" >"$PACKAGES"
while IFS= read -r package; do
  package_rows="$TMP_DIR/package-rows"
  awk -F '\t' -v package="$package" '$1 == package {print}' "$VALIDATED" >"$package_rows"
  base_count=0
  expected_signer=""
  expected_version=""
  while IFS=$'\t' read -r manifest_package manifest_version_code manifest_version_name relative_path expected_hash; do
    apk="$ARCHIVE/$relative_path"
    measured_hash="$(sha256_file "$apk")"
    if [[ "$measured_hash" != "$expected_hash" ]]; then
      echo "APK hash mismatch: $relative_path" >&2
      exit 1
    fi
    if ! verify="$("$APKSIGNER" verify --verbose --print-certs "$apk" </dev/null 2>&1)"; then
      echo "APK signature verification failed: $relative_path" >&2
      echo "$verify" >&2
      exit 1
    fi
    signer_sha="$(signer_values "$verify" 'SHA-256 digest')"
    signer_dn="$(signer_values "$verify" 'DN' | tr '\t\r\n' '   ')"
    if [[ -z "$signer_sha" ]]; then
      echo "APK has no measured signer digest: $relative_path" >&2
      exit 1
    fi
    badging="$("$AAPT" dump badging "$apk" </dev/null 2>&1 || true)"
    measured_package="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$badging")"
    measured_version_code="$(sed -n "s/^package: .* versionCode='\([^']*\)'.*/\1/p" <<<"$badging")"
    measured_version_name="$(sed -n "s/^package: .* versionName='\([^']*\)'.*/\1/p" <<<"$badging")"
    split_name="$(sed -n "s/^package: .* split='\([^']*\)'.*/\1/p" <<<"$badging")"
    if [[ "$measured_package" != "$manifest_package" ||
      "$measured_version_code" != "$manifest_version_code" ||
      ( -n "$measured_version_name" && "$measured_version_name" != "$manifest_version_name" ) ]]; then
      echo "Measured APK metadata does not match manifest: $relative_path" >&2
      exit 1
    fi
    if [[ -z "$split_name" ]]; then
      apk_role="base"
      base_count=$((base_count + 1))
    else
      apk_role="split:$split_name"
    fi
    if [[ -z "$expected_signer" ]]; then
      expected_signer="$signer_sha"
      expected_version="$measured_version_code"
    elif [[ "$signer_sha" != "$expected_signer" || "$measured_version_code" != "$expected_version" ]]; then
      echo "Package has mixed signer or version APKs: $package" >&2
      exit 1
    fi
    schemes="$(signature_schemes "$verify")"
    stamp_verified="$(awk -F': ' '/^Verified for SourceStamp:/{print $2; exit}' <<<"$verify")"
    stamp_sha="$(awk -F': ' '/^Source Stamp Signer certificate SHA-256 digest:/{print $2; exit}' <<<"$verify")"
    permissions="$("$AAPT" dump permissions "$apk" </dev/null |
      sed -n "s/.*uses-permission: name='\([^']*\)'.*/\1/p" |
      LC_ALL=C sort -u |
      paste -sd, -)"
    if [[ -z "$permissions" ]]; then
      permissions="-"
    fi
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$measured_package" "$measured_version_code" "$measured_version_name" \
      "$relative_path" "$apk_role" "$measured_hash" "$(file_size "$apk")" \
      "$signer_sha" "$signer_dn" "$schemes" "$stamp_verified" "$stamp_sha" \
      "$permissions" >>"$REPORT"
  done <"$package_rows"
  if [[ "$base_count" != "1" ]]; then
    echo "Package must contain exactly one base APK: $package (found $base_count)" >&2
    exit 1
  fi
done <"$PACKAGES"

if [[ "$OUTPUT" == "/dev/stdout" || "$OUTPUT" == "-" ]]; then
  cat "$REPORT"
else
  cp "$REPORT" "$OUTPUT"
fi
echo "Wrote verified APK audit to $OUTPUT" >&2
