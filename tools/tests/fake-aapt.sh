#!/usr/bin/env bash
set -euo pipefail

metadata() {
  local apk="$1"
  if unzip -p "$apk" META-INF/saro-test-meta 2>/dev/null; then
    return 0
  fi
  cat "$apk"
}

if [[ "${1:-}" != "dump" || -z "${3:-}" ]]; then
  echo "fake-aapt: unsupported arguments: $*" >&2
  exit 2
fi

mode="$2"
apk="$3"
meta="$(metadata "$apk")"
value() {
  sed -n "s/^$1=//p" <<<"$meta" | head -1
}

case "$mode" in
  badging)
    package="$(value PACKAGE)"
    version_code="$(value VERSION_CODE)"
    version_name="$(value VERSION_NAME)"
    split="$(value SPLIT)"
    [[ -n "$package" && -n "$version_code" && -n "$version_name" ]] || exit 1
    if [[ -n "$split" && "$split" != "base" ]]; then
      printf "package: name='%s' versionCode='%s' versionName='%s' split='%s'\n" \
        "$package" "$version_code" "$version_name" "$split"
    else
      printf "package: name='%s' versionCode='%s' versionName='%s'\n" \
        "$package" "$version_code" "$version_name"
    fi
    ;;
  permissions)
    sed -n "s/^PERMISSION=/uses-permission: name='/p" <<<"$meta" | sed "s/$/'/"
    ;;
  *)
    echo "fake-aapt: unsupported dump mode: $mode" >&2
    exit 2
    ;;
esac
