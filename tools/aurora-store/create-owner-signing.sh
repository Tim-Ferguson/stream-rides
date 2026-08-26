#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
JAVA_HOME="${JAVA_HOME:-$WORKSPACE_DIR/tools/jdk21/Contents/Home}"
OUTPUT_DIR="${1:-$WORKSPACE_DIR/local-backups/signing}"
SIGNING_PROPERTIES="${2:-$WORKSPACE_DIR/tools/AuroraStore-src/signing.properties}"
KEYSTORE="$OUTPUT_DIR/aurora-local-release.keystore"
PASSWORD_FILE="$OUTPUT_DIR/aurora-local-release.password"
ALIAS="aurora"

for required in "$JAVA_HOME/bin/keytool" openssl; do
  if [[ "$required" == */* ]]; then
    [[ -x "$required" ]] || { echo "Missing tool: $required" >&2; exit 1; }
  else
    command -v "$required" >/dev/null 2>&1 || {
      echo "Missing tool: $required" >&2
      exit 1
    }
  fi
done
if [[ -e "$KEYSTORE" || -e "$PASSWORD_FILE" || -e "$SIGNING_PROPERTIES" ]]; then
  echo "Refusing to replace existing Aurora signing material." >&2
  exit 1
fi

umask 077
mkdir -p "$OUTPUT_DIR" "$(dirname "$SIGNING_PROPERTIES")"
PASSWORD="$(openssl rand -hex 32)"
export PASSWORD
"$JAVA_HOME/bin/keytool" -genkeypair \
  -keystore "$KEYSTORE" \
  -storepass:env PASSWORD \
  -keypass:env PASSWORD \
  -alias "$ALIAS" \
  -dname "CN=SARO Local App Store,O=Local Build,C=US" \
  -keyalg RSA \
  -keysize 3072 \
  -validity 10000
printf '%s\n' "$PASSWORD" >"$PASSWORD_FILE"
printf 'STORE_FILE=%s\nKEY_ALIAS=%s\nKEY_PASSWORD=%s\n' \
  "$KEYSTORE" "$ALIAS" "$PASSWORD" >"$SIGNING_PROPERTIES"
chmod 600 "$KEYSTORE" "$PASSWORD_FILE" "$SIGNING_PROPERTIES"

"$JAVA_HOME/bin/keytool" -list -v \
  -keystore "$KEYSTORE" \
  -storepass:env PASSWORD \
  -alias "$ALIAS" |
  sed -n '/SHA256:/p'
echo "Created per-installation Aurora signing material. Keep all three files private."
