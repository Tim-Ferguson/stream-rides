#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export SARO_BUILD_VARIANT=release
exec "$SCRIPT_DIR/build.sh"
