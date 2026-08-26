#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
AUDIT_MODE="${2:-public}"
PRIVATE_DENYLIST="${SARO_PRIVATE_DENYLIST:-}"
if [[ ! -d "$ROOT" || ( "$AUDIT_MODE" != "public" && "$AUDIT_MODE" != "private-source" ) ]]; then
  echo "Usage: audit-public-tree.sh [tree-root] [public|private-source]" >&2
  exit 2
fi
ROOT="$(cd "$ROOT" && pwd -P)"
BINARY_MANIFEST="$ROOT/tools/public-binary-manifest.tsv"
PNG_VALIDATOR="$ROOT/tools/validate-png.pl"

failures=0
fail() {
  echo "public-tree audit: $*" >&2
  failures=$((failures + 1))
}

sha256_file() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    echo "A SHA-256 tool is required (shasum or sha256sum)." >&2
    return 1
  fi
}

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/saro-public-audit.XXXXXX")"
cleanup() {
  case "$TMP_ROOT" in
    "${TMPDIR:-/tmp}"/saro-public-audit.*) find "$TMP_ROOT" -depth -delete ;;
    *) echo "Refusing to clean unexpected path: $TMP_ROOT" >&2 ;;
  esac
}
trap cleanup EXIT

FILES="$TMP_ROOT/files"
UNSAFE_FILENAMES="$TMP_ROOT/unsafe-filenames"
: >"$UNSAFE_FILENAMES"
GIT_ROOT="$(git -C "$ROOT" rev-parse --show-toplevel 2>/dev/null || true)"
if [[ -n "$GIT_ROOT" && "$(cd "$GIT_ROOT" && pwd -P)" == "$ROOT" ]]; then
  RAW_FILES="$TMP_ROOT/raw-files"
  git -C "$ROOT" ls-files --cached --others --exclude-standard -z >"$RAW_FILES"
  while IFS= read -r -d '' relative; do
    if [[ "$relative" == *$'\n'* || "$relative" == *$'\t'* ]]; then
      printf 'unsafe\n' >>"$UNSAFE_FILENAMES"
    else
      printf '%s\n' "$relative"
    fi
  done <"$RAW_FILES" >"$TMP_ROOT/file-lines"
else
  RAW_FILES="$TMP_ROOT/raw-files"
  find "$ROOT" -path "$ROOT/.git" -prune -o ! -type d -print0 >"$RAW_FILES"
  while IFS= read -r -d '' path; do
    relative="${path#"$ROOT"/}"
    if [[ "$relative" == *$'\n'* || "$relative" == *$'\t'* ]]; then
      printf 'unsafe\n' >>"$UNSAFE_FILENAMES"
    else
      printf '%s\n' "$relative"
    fi
  done <"$RAW_FILES" >"$TMP_ROOT/file-lines"
fi
if [[ -s "$UNSAFE_FILENAMES" ]]; then
  fail "publishable tree contains a tab or newline in a filename"
fi
LC_ALL=C sort -u "$TMP_ROOT/file-lines" >"$FILES"

[[ -s "$FILES" ]] || fail "tree contains no publishable files"
[[ -f "$BINARY_MANIFEST" ]] || fail "missing public binary manifest"
[[ -x "$PNG_VALIDATOR" ]] || fail "missing executable PNG validator"

mac_home='/Us''ers/'
device_serial='IAPLDS[0-9][0-9]*'
email_pattern='[[:alnum:]._%+-]+@[[:alnum:].-]+[.][[:alpha:]]{2,}'
token_pattern='(gh[pousr]_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9_-]{20,}|AKIA[0-9A-Z]{16})'
local_hash_pattern='(helper|configuration|config|checkpoint).{0,120}[0-9a-f]{64}|[0-9a-f]{64}.{0,120}(helper|configuration|config|checkpoint)'
local_helper_row='^com[.]pelo''tonhack[.]ridestarter[[:space:]]'
local_store_row='^com[.]aurora[.]store[[:space:]]'
private_key_header='BEGIN[[:space:]]+((RSA|EC|DSA|OPENSSH|ENCRYPTED)[[:space:]]+)?PRIVATE[[:space:]]+KEY|BEGIN[[:space:]]+PGP[[:space:]]+PRIVATE[[:space:]]+KEY[[:space:]]+BLOCK'

if [[ -n "$PRIVATE_DENYLIST" ]]; then
  if [[ ! -f "$PRIVATE_DENYLIST" || -L "$PRIVATE_DENYLIST" ]]; then
    fail "private denylist is missing or unsafe"
  elif [[ "$(stat -f '%Lp' "$PRIVATE_DENYLIST" 2>/dev/null || stat -c '%a' "$PRIVATE_DENYLIST")" != "600" ]]; then
    fail "private denylist must have mode 0600"
  elif ! awk -F '\t' '
    NR == 1 {if (NF != 2 || $1 != "scope" || $2 != "value") exit 1; next}
    NF != 2 || ($1 != "all" && $1 != "tree") || length($2) < 4 || seen[$1 SUBSEP $2]++ {exit 1}
    END {if (NR < 2) exit 1}
  ' "$PRIVATE_DENYLIST"; then
    fail "private denylist is malformed"
  fi
fi

BINARY_FILES="$TMP_ROOT/binary-files"
SECRET_SCAN_ROOT="$TMP_ROOT/secret-scan"
: >"$BINARY_FILES"
mkdir -p "$SECRET_SCAN_ROOT"

while IFS= read -r relative; do
  [[ -n "$relative" ]] || continue
  if [[ "$relative" == screenshots/* ]]; then
    if [[ "$AUDIT_MODE" == "private-source" ]]; then
      continue
    fi
    fail "screenshots are prohibited from the public tree: $relative"
    continue
  fi
  path="$ROOT/$relative"
  if [[ -L "$path" || ! -f "$path" ]]; then
    fail "non-regular publishable path: $relative"
    continue
  fi
  case "$relative" in
    .DS_Store|*/.DS_Store|*/.git/*|.git/*)
      fail "repository or Finder metadata included: $relative"
      ;;
  esac
  lower_relative="$(printf '%s\n' "$relative" | tr '[:upper:]' '[:lower:]')"
  if grep -Eiq "$mac_home|$device_serial|$email_pattern|$token_pattern" \
    <<<"$relative"; then
    fail "personal identifier or secret-like token in filename: $relative"
  fi
  case "$lower_relative" in
    *.apk|*.xapk|*.apks|*.aab|*.obb|*.jks|*.keystore|*.p12|*.pfx|*.pem|*.key|*.mobileprovision|*.env|*.env.*|*/signing.properties|*password*)
      fail "sensitive or redistributable artifact included: $relative"
      ;;
  esac
  mime="$(file -b --mime-type "$path" 2>/dev/null || true)"
  extension="${relative##*.}"
  if [[ "$extension" == "$relative" ]]; then
    extension=""
  fi
  case "$extension:$mime" in
    command:text/x-shellscript|command:text/plain|java:text/x-java|java:text/plain|md:text/plain|patch:text/x-diff|patch:text/plain|pl:text/x-perl|pl:text/plain|properties:text/plain|sh:text/x-shellscript|sh:text/plain|tsv:text/plain|xml:text/plain|xml:application/xml|gitignore:text/plain|:text/plain|:text/x-shellscript)
      is_reviewable_text=1
      ;;
    *)
      is_reviewable_text=0
      ;;
  esac

  if [[ "$is_reviewable_text" == "1" ]]; then
    if grep -EIni "$mac_home|$device_serial|$private_key_header|$email_pattern|$token_pattern" \
      "$path" >/dev/null; then
      grep -EIni "$mac_home|$device_serial|$private_key_header|$email_pattern|$token_pattern" \
        "$path" >&2 || true
      fail "personal identifier, credential marker, or secret-like token in $relative"
    fi
    case "$relative" in
      docs/security/current-apk-inventory.tsv|docs/security/downloaded-artifact-inventory.tsv|docs/security/installed-apk-inventory.tsv)
        ;;
      *)
        if grep -Eiq "$local_hash_pattern" "$path"; then
          fail "installation-specific helper/config/checkpoint hash in $relative"
        fi
        ;;
    esac
    if grep -Eq "$local_helper_row|$local_store_row" \
      "$path"; then
      fail "locally signed package row in publishable evidence: $relative"
    fi
    if [[ "$relative" == *.md ]] &&
      grep -Eiq 'screenshots/[A-Za-z0-9._/-]+[.]png' "$path"; then
      fail "screenshot reference is prohibited from public documentation: $relative"
    fi
  else
    printf '%s\t%s\t%s\n' "$relative" "$mime" "$(sha256_file "$path")" >>"$BINARY_FILES"
    if [[ "$mime" == "image/png" ]]; then
      "$PNG_VALIDATOR" "$path" || fail "strict PNG validation failed: $relative"
    fi
  fi
  mkdir -p "$(dirname "$SECRET_SCAN_ROOT/$relative")"
  cp "$path" "$SECRET_SCAN_ROOT/$relative"
done <"$FILES"

GITLEAKS_BIN="${SARO_GITLEAKS_BIN:-}"
REQUIRED_GITLEAKS_VERSION="8.30.1"
if [[ -z "$GITLEAKS_BIN" && -x "$ROOT/tools/gitleaks/gitleaks" ]]; then
  GITLEAKS_BIN="$ROOT/tools/gitleaks/gitleaks"
elif [[ -z "$GITLEAKS_BIN" ]]; then
  GITLEAKS_BIN="$(command -v gitleaks || true)"
fi
if [[ -z "$GITLEAKS_BIN" || ! -x "$GITLEAKS_BIN" ]]; then
  fail "Gitleaks is required; run tools/install-gitleaks.sh"
elif [[ "$("$GITLEAKS_BIN" version 2>/dev/null | tr -d '\r\n')" != \
  "$REQUIRED_GITLEAKS_VERSION" ]]; then
  fail "Gitleaks $REQUIRED_GITLEAKS_VERSION is required"
elif ! "$GITLEAKS_BIN" dir --no-banner --no-color --redact --log-level error \
  "$SECRET_SCAN_ROOT"; then
  fail "Gitleaks detected a secret in the publishable tree"
fi

if [[ -n "$PRIVATE_DENYLIST" && -f "$PRIVATE_DENYLIST" ]]; then
  while IFS=$'\t' read -r private_scope private_value; do
    [[ "$private_scope" != "scope" ]] || continue
    while IFS= read -r relative; do
      [[ -n "$relative" ]] || continue
      if grep -Fqi -- "$private_value" <<<"$relative"; then
        fail "private denylist value in filename: $relative"
      fi
      if grep -Fqai -- "$private_value" "$ROOT/$relative"; then
        fail "private denylist value in publishable file: $relative"
      fi
    done <"$FILES"
  done <"$PRIVATE_DENYLIST"
fi

if [[ -f "$BINARY_MANIFEST" ]]; then
  EXPECTED_BINARIES="$TMP_ROOT/expected-binaries"
  if ! awk -F '\t' '
    NR == 1 {
      if (NF != 4 || $1 != "path" || $2 != "mime_type" || $3 != "sha256" || $4 != "review") exit 1
      next
    }
    NF != 4 || $1 !~ /^[A-Za-z0-9_.\/-]+$/ || $2 !~ /^[a-z0-9.+-]+\/[a-z0-9.+-]+$/ ||
      length($3) != 64 || $3 !~ /^[0-9a-f]+$/ || $4 == "" || seen[$1]++ {exit 1}
    {print $1 "\t" $2 "\t" $3}
    END {if (NR < 2) exit 1}
  ' "$BINARY_MANIFEST" | LC_ALL=C sort >"$EXPECTED_BINARIES"; then
    fail "public binary manifest is malformed"
  fi
  LC_ALL=C sort -u "$BINARY_FILES" -o "$BINARY_FILES"
  if ! cmp -s "$EXPECTED_BINARIES" "$BINARY_FILES"; then
    diff -u "$EXPECTED_BINARIES" "$BINARY_FILES" >&2 || true
    fail "publishable binary files do not match reviewed hashes"
  fi
  if awk -F '\t' 'NR > 1 && $1 ~ /^screenshots\// {found=1} END {exit !found}' \
    "$BINARY_MANIFEST"; then
    fail "public binary manifest must not allow screenshots"
  fi
fi

if [[ "$failures" != "0" ]]; then
  echo "SARO public-tree audit: FAIL ($failures finding(s))" >&2
  exit 1
fi
echo "SARO public-tree audit: PASS"
