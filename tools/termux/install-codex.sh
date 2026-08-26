#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

CODEX_VERSION="${CODEX_VERSION:-0.147.0}"

if [[ "$(uname -m)" != "aarch64" ]]; then
  echo "This SARO workaround is verified only on Android ARM64 (aarch64)." >&2
  exit 1
fi

cat >&2 <<'NOTICE'
Codex does not officially support Android/Termux. This installs the official
Linux ARM64 npm package through an unsupported package alias. Review this
script and the pinned version before continuing.
NOTICE

pkg update -y
pkg install -y nodejs-lts git curl
npm install -g --force \
  "@openai/codex-linux-arm64@npm:@openai/codex@${CODEX_VERSION}-linux-arm64"

codex --version
