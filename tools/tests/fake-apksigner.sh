#!/usr/bin/env bash
set -euo pipefail

apk="${!#}"
if meta="$(unzip -p "$apk" META-INF/saro-test-meta 2>/dev/null)"; then
  :
else
  meta="$(cat "$apk")"
fi
value() {
  sed -n "s/^$1=//p" <<<"$meta" | head -1
}

if [[ "$(value SIGNATURE_VALID)" == "false" ]]; then
  echo "DOES NOT VERIFY" >&2
  exit 1
fi
signer="$(value SIGNER)"
[[ "$signer" =~ ^[0-9a-f]{64}$ ]] || {
  echo "Missing fake signer" >&2
  exit 1
}
printf 'Verifies\n'
printf 'Verified using v1 scheme (JAR signing): true\n'
printf 'Verified using v2 scheme (APK Signature Scheme v2): true\n'
printf 'Verified using v3 scheme (APK Signature Scheme v3): false\n'
printf 'Verified using v3.1 scheme (APK Signature Scheme v3.1): false\n'
printf 'Verified using v4 scheme (APK Signature Scheme v4): false\n'
printf 'Verified for SourceStamp: false\n'
printf 'Signer #1 certificate DN: CN=SARO deterministic test\n'
printf 'Signer #1 certificate SHA-256 digest: %s\n' "$signer"
