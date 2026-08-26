#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
VERSION="8.30.1"
SYSTEM="$(uname -s)"
MACHINE="$(uname -m)"

case "$SYSTEM:$MACHINE" in
  Darwin:arm64)
    PLATFORM="darwin_arm64"
    EXPECTED_SHA256="b40ab0ae55c505963e365f271a8d3846efbc170aa17f2607f13df610a9aeb6a5"
    ;;
  Darwin:x86_64)
    PLATFORM="darwin_x64"
    EXPECTED_SHA256="dfe101a4db2255fc85120ac7f3d25e4342c3c20cf749f2c20a18081af1952709"
    ;;
  Linux:aarch64|Linux:arm64)
    PLATFORM="linux_arm64"
    EXPECTED_SHA256="e4a487ee7ccd7d3a7f7ec08657610aa3606637dab924210b3aee62570fb4b080"
    ;;
  Linux:x86_64|Linux:amd64)
    PLATFORM="linux_x64"
    EXPECTED_SHA256="551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb"
    ;;
  *)
    echo "No pinned Gitleaks build is configured for $SYSTEM $MACHINE." >&2
    exit 1
    ;;
esac

ARCHIVE="gitleaks_${VERSION}_${PLATFORM}.tar.gz"
URL="https://github.com/gitleaks/gitleaks/releases/download/v${VERSION}/${ARCHIVE}"
DESTINATION="$SCRIPT_DIR/gitleaks/gitleaks"
TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/saro-gitleaks.XXXXXX")"
cleanup() {
  case "$TEMP_ROOT" in
    "${TMPDIR:-/tmp}"/saro-gitleaks.*) find "$TEMP_ROOT" -depth -delete ;;
    *) echo "Refusing to clean unexpected path: $TEMP_ROOT" >&2 ;;
  esac
}
trap cleanup EXIT

curl --proto '=https' --tlsv1.2 --fail --location --silent --show-error \
  --output "$TEMP_ROOT/$ARCHIVE" "$URL"
if command -v shasum >/dev/null 2>&1; then
  ACTUAL_SHA256="$(shasum -a 256 "$TEMP_ROOT/$ARCHIVE" | awk '{print $1}')"
elif command -v sha256sum >/dev/null 2>&1; then
  ACTUAL_SHA256="$(sha256sum "$TEMP_ROOT/$ARCHIVE" | awk '{print $1}')"
else
  echo "A SHA-256 tool is required (shasum or sha256sum)." >&2
  exit 1
fi
if [[ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]]; then
  echo "Gitleaks archive hash mismatch for $ARCHIVE." >&2
  exit 1
fi

tar -xzf "$TEMP_ROOT/$ARCHIVE" -C "$TEMP_ROOT" gitleaks
mkdir -p "$(dirname "$DESTINATION")"
cp "$TEMP_ROOT/gitleaks" "$DESTINATION"
chmod 755 "$DESTINATION"
"$DESTINATION" version
echo "Installed pinned Gitleaks at $DESTINATION"
