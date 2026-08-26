#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
WORKSPACE_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"
DESTINATION="${1:-}"
PRIVATE_DENYLIST="${SARO_PRIVATE_DENYLIST:-}"
GITLEAKS_BIN="${SARO_GITLEAKS_BIN:-}"
REQUIRED_GITLEAKS_VERSION="8.30.1"

if [[ -z "$DESTINATION" ]]; then
  echo "Usage: export-public-snapshot.sh <new-empty-directory>" >&2
  exit 2
fi
if [[ -n "$(git -C "$WORKSPACE_DIR" status --porcelain --untracked-files=normal)" ]]; then
  echo "Refusing to export a dirty tree. Commit and verify the private source first." >&2
  exit 1
fi
if [[ -z "$PRIVATE_DENYLIST" || ! -f "$PRIVATE_DENYLIST" ]]; then
  echo "SARO_PRIVATE_DENYLIST must name the ignored private release denylist." >&2
  exit 1
fi
if [[ -z "$GITLEAKS_BIN" && -x "$WORKSPACE_DIR/tools/gitleaks/gitleaks" ]]; then
  GITLEAKS_BIN="$WORKSPACE_DIR/tools/gitleaks/gitleaks"
elif [[ -z "$GITLEAKS_BIN" ]]; then
  GITLEAKS_BIN="$(command -v gitleaks || true)"
fi
if [[ -z "$GITLEAKS_BIN" || ! -x "$GITLEAKS_BIN" ]]; then
  echo "Gitleaks is required. Run tools/install-gitleaks.sh first." >&2
  exit 1
fi
GITLEAKS_BIN="$(cd "$(dirname "$GITLEAKS_BIN")" && pwd -P)/$(basename "$GITLEAKS_BIN")"
if [[ "$("$GITLEAKS_BIN" version 2>/dev/null | tr -d '\r\n')" != \
  "$REQUIRED_GITLEAKS_VERSION" ]]; then
  echo "Gitleaks $REQUIRED_GITLEAKS_VERSION is required." >&2
  exit 1
fi
export SARO_GITLEAKS_BIN="$GITLEAKS_BIN"

SARO_PRIVATE_DENYLIST="$PRIVATE_DENYLIST" "$WORKSPACE_DIR/tools/verify-release.sh"
if [[ -n "$(git -C "$WORKSPACE_DIR" status --porcelain --untracked-files=normal)" ]]; then
  echo "Release gates changed the private tree; refusing to export." >&2
  exit 1
fi

while [[ "$DESTINATION" != "/" && "$DESTINATION" == */ ]]; do
  DESTINATION="${DESTINATION%/}"
done
PARENT="$(cd "$(dirname "$DESTINATION")" && pwd -P)"
BASE="$(basename "$DESTINATION")"
DESTINATION="$PARENT/$BASE"
if [[ -e "$DESTINATION" || -L "$DESTINATION" ]]; then
  echo "Destination must not exist: $DESTINATION" >&2
  exit 1
fi
case "$BASE" in
  ""|.|..) echo "Refusing unsafe destination: $DESTINATION" >&2; exit 1 ;;
esac
case "$DESTINATION/" in
  "$WORKSPACE_DIR/"*)
    echo "Destination must be outside the private working repository." >&2
    exit 1
    ;;
esac

STAGE="$(mktemp -d "$PARENT/.${BASE}.tmp.XXXXXX")"
cleanup() {
  local status="$1"
  trap - EXIT
  case "$STAGE" in
    "$PARENT"/."$BASE".tmp.*)
      [[ ! -e "$STAGE" ]] || find "$STAGE" -depth -delete
      ;;
    *) echo "Refusing to clean unexpected path: $STAGE" >&2 ;;
  esac
  exit "$status"
}
trap 'cleanup "$?"' EXIT

# The tar stream is never retained or published. Extraction drops Git's archive
# commit header, and the resulting directory contains no object database.
git -C "$WORKSPACE_DIR" archive --format=tar HEAD -- . \
  ':(exclude)screenshots' ':(exclude)screenshots/**' | tar -xf - -C "$STAGE"
[[ ! -e "$STAGE/.git" ]] || {
  echo "Export unexpectedly contains Git metadata." >&2
  exit 1
}
SARO_PRIVATE_DENYLIST="$PRIVATE_DENYLIST" "$STAGE/tools/audit-public-tree.sh" "$STAGE"
mv "$STAGE" "$DESTINATION"
trap - EXIT

echo "History-free audited snapshot created at: $DESTINATION"
echo "Initialize Git in that directory and create one new root commit before publishing."
