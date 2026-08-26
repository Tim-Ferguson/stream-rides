#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
AUDIT="$WORKSPACE_DIR/tools/audit-public-tree.sh"
EXPORTER="$WORKSPACE_DIR/tools/export-public-snapshot.sh"
PNG_VALIDATOR="$WORKSPACE_DIR/tools/validate-png.pl"
GITLEAKS_BIN="${SARO_GITLEAKS_BIN:-$WORKSPACE_DIR/tools/gitleaks/gitleaks}"
PNG_RELATIVE="device-setup/ride-starter/res/drawable-nodpi/game_sprites.png"
SOURCE_PNG="$WORKSPACE_DIR/$PNG_RELATIVE"

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/saro-public-tests.XXXXXX")"
cleanup() {
  case "$TMP_ROOT" in
    "${TMPDIR:-/tmp}"/saro-public-tests.*) find "$TMP_ROOT" -depth -delete ;;
    *) echo "Refusing to clean unexpected path: $TMP_ROOT" >&2 ;;
  esac
}
trap cleanup EXIT

PASS=0
fail() {
  echo "FAIL: $*" >&2
  exit 1
}
pass() {
  PASS=$((PASS + 1))
  printf 'ok %d - %s\n' "$PASS" "$1"
}
expect_failure() {
  local label="$1"
  shift
  if "$@" >"$TMP_ROOT/last.stdout" 2>"$TMP_ROOT/last.stderr"; then
    fail "$label unexpectedly succeeded"
  fi
  pass "$label"
}
sha256_file() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    sha256sum "$1" | awk '{print $1}'
  fi
}

write_binary_manifest() {
  local root="$1"
  local png="$root/$PNG_RELATIVE"
  printf 'path\tmime_type\tsha256\treview\n' >"$root/tools/public-binary-manifest.tsv"
  printf '%s\timage/png\t%s\treviewed test fixture\n' "$PNG_RELATIVE" \
    "$(sha256_file "$png")" >>"$root/tools/public-binary-manifest.tsv"
}

make_audit_fixture() {
  local root="$1"
  mkdir -p "$root/tools" "$root/screenshots" "$(dirname "$root/$PNG_RELATIVE")"
  cp "$AUDIT" "$root/tools/audit-public-tree.sh"
  cp "$PNG_VALIDATOR" "$root/tools/validate-png.pl"
  cp "$SOURCE_PNG" "$root/$PNG_RELATIVE"
  chmod +x "$root/tools/audit-public-tree.sh" "$root/tools/validate-png.pl"
  printf '# SARO public fixture\n' >"$root/README.md"
  write_binary_manifest "$root"
}

inject_png_text_chunk() {
  local source="$1"
  local destination="$2"
  perl -MCompress::Zlib=crc32 -e '
    use strict;
    use warnings;
    local $/;
    open my $input, "<:raw", $ARGV[0] or die $!;
    my $data = <$input>;
    close $input;
    my $type = "tEXt";
    my $payload = "Comment\0fixture metadata";
    my $position = 8;
    while ($position < length($data)) {
      my $length = unpack("N", substr($data, $position, 4));
      last if substr($data, $position + 4, 4) eq "IDAT";
      $position += 12 + $length;
    }
    die "IDAT not found" if $position >= length($data);
    my $body = $type . $payload;
    my $chunk = pack("N", length($payload)) . $body . pack("N", crc32($body));
    substr($data, $position, 0, $chunk);
    open my $output, ">:raw", $ARGV[1] or die $!;
    print {$output} $data;
    close $output;
  ' "$source" "$destination"
}

inject_png_chunk() {
  local source="$1"
  local destination="$2"
  local type="$3"
  local payload="$4"
  perl -MCompress::Zlib=crc32 -e '
    use strict;
    use warnings;
    local $/;
    open my $input, "<:raw", $ARGV[0] or die $!;
    my $data = <$input>;
    close $input;
    my ($type, $payload) = @ARGV[2, 3];
    my $position = rindex($data, "IEND");
    die "IEND not found" if $position < 4;
    my $body = $type . $payload;
    my $chunk = pack("N", length($payload)) . $body . pack("N", crc32($body));
    substr($data, $position - 4, 0, $chunk);
    open my $output, ">:raw", $ARGV[1] or die $!;
    print {$output} $data;
    close $output;
  ' "$source" "$destination" "$type" "$payload"
}

mutate_png_ihdr() {
  local source="$1"
  local destination="$2"
  perl -MCompress::Zlib=crc32 -e '
    use strict;
    use warnings;
    local $/;
    open my $input, "<:raw", $ARGV[0] or die $!;
    my $data = <$input>;
    close $input;
    substr($data, 24, 1, "\x01");
    my $body = substr($data, 12, 17);
    substr($data, 29, 4, pack("N", crc32($body)));
    open my $output, ">:raw", $ARGV[1] or die $!;
    print {$output} $data;
    close $output;
  ' "$source" "$destination"
}

inject_idat_trailing_payload() {
  local source="$1"
  local destination="$2"
  perl -MCompress::Zlib=crc32 -e '
    use strict;
    use warnings;
    local $/;
    open my $input, "<:raw", $ARGV[0] or die $!;
    my $data = <$input>;
    close $input;
    my $offset = 8;
    my $last_idat = -1;
    my $last_length = 0;
    while ($offset < length($data)) {
      my $length = unpack("N", substr($data, $offset, 4));
      my $type = substr($data, $offset + 4, 4);
      if ($type eq "IDAT") {
        $last_idat = $offset;
        $last_length = $length;
      }
      $offset += 12 + $length;
    }
    die "IDAT not found" if $last_idat < 0;
    my $type = "IDAT";
    my $payload = substr($data, $last_idat + 8, $last_length) .
      "HIDDEN-IDAT-PAYLOAD";
    my $body = $type . $payload;
    substr($data, $last_idat, 12 + $last_length,
      pack("N", length($payload)) . $body . pack("N", crc32($body)));
    open my $output, ">:raw", $ARGV[1] or die $!;
    print {$output} $data;
    close $output;
  ' "$source" "$destination"
}

DENYLIST="$TMP_ROOT/private-denylist.tsv"
printf 'scope\tvalue\nall\tPRIVATE-TREE-SENTINEL\nall\tHISTORY-ONLY-SENTINEL\n' \
  >"$DENYLIST"
chmod 600 "$DENYLIST"

AUDIT_FIXTURE="$TMP_ROOT/audit-fixture"
make_audit_fixture "$AUDIT_FIXTURE"
SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$AUDIT_FIXTURE/tools/audit-public-tree.sh" \
  "$AUDIT_FIXTURE" >/dev/null
pass "reviewed minimal tree passes the public audit"

printf '%s\n' '#!/usr/bin/env bash' 'printf "0.0.0\\n"' \
  >"$TMP_ROOT/wrong-gitleaks"
chmod +x "$TMP_ROOT/wrong-gitleaks"
expect_failure "wrong Gitleaks version is rejected" env \
  SARO_GITLEAKS_BIN="$TMP_ROOT/wrong-gitleaks" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$AUDIT_FIXTURE/tools/audit-public-tree.sh" "$AUDIT_FIXTURE"

printf 'PRIVATE-TREE-SENTINEL\n' >"$AUDIT_FIXTURE/private-note.txt"
expect_failure "private denylist content is rejected" env \
  SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$AUDIT_FIXTURE/tools/audit-public-tree.sh" \
  "$AUDIT_FIXTURE"
find "$AUDIT_FIXTURE/private-note.txt" -delete

printf '%s%s\n' 'ghp_' 'AAAAAAAAAAAAAAAAAAAAAAAA' >"$AUDIT_FIXTURE/token-note.txt"
expect_failure "secret-like token is rejected" env SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$AUDIT_FIXTURE/tools/audit-public-tree.sh" "$AUDIT_FIXTURE"
find "$AUDIT_FIXTURE/token-note.txt" -delete

printf '%s%s\n' 'glpat-' 'abcdefghijklmnopqrstuvwxyz123456' \
  >"$AUDIT_FIXTURE/gitlab-note.txt"
expect_failure "maintained scanner rejects a GitLab token" env \
  SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$AUDIT_FIXTURE/tools/audit-public-tree.sh" "$AUDIT_FIXTURE"
find "$AUDIT_FIXTURE/gitlab-note.txt" -delete

printf '%s%s%s\n' '-----BEGIN OPEN' 'SSH PRIVATE ' 'KEY-----' \
  >"$AUDIT_FIXTURE/key-note.txt"
expect_failure "OpenSSH private-key header is rejected" env \
  SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$AUDIT_FIXTURE/tools/audit-public-tree.sh" "$AUDIT_FIXTURE"
find "$AUDIT_FIXTURE/key-note.txt" -delete

printf 'not publishable\n' >"$AUDIT_FIXTURE/local.env"
expect_failure "sensitive filename is rejected" env SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$AUDIT_FIXTURE/tools/audit-public-tree.sh" "$AUDIT_FIXTURE"
find "$AUDIT_FIXTURE/local.env" -delete

ln -s README.md "$AUDIT_FIXTURE/linked-readme"
expect_failure "symbolic link is rejected" env SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$AUDIT_FIXTURE/tools/audit-public-tree.sh" "$AUDIT_FIXTURE"
find "$AUDIT_FIXTURE/linked-readme" -delete

printf 'printable opaque content\n' >"$AUDIT_FIXTURE/opaque.bin"
expect_failure "printable opaque file must be manifested" env \
  SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$AUDIT_FIXTURE/tools/audit-public-tree.sh" "$AUDIT_FIXTURE"
find "$AUDIT_FIXTURE/opaque.bin" -delete

printf '<svg xmlns="http://www.w3.org/2000/svg"><text>hidden</text></svg>\n' \
  >"$AUDIT_FIXTURE/diagram.svg"
expect_failure "SVG must be manifested" env SARO_GITLEAKS_BIN="$GITLEAKS_BIN" \
  SARO_PRIVATE_DENYLIST="$DENYLIST" "$AUDIT_FIXTURE/tools/audit-public-tree.sh" \
  "$AUDIT_FIXTURE"
find "$AUDIT_FIXTURE/diagram.svg" -delete

printf '<svg xmlns="http://www.w3.org/2000/svg"/>\n' \
  >"$AUDIT_FIXTURE/screenshots/leak.svg"
expect_failure "any top-level screenshot is rejected" env SARO_GITLEAKS_BIN="$GITLEAKS_BIN" \
  SARO_PRIVATE_DENYLIST="$DENYLIST" "$AUDIT_FIXTURE/tools/audit-public-tree.sh" \
  "$AUDIT_FIXTURE"
find "$AUDIT_FIXTURE/screenshots/leak.svg" -delete

mkdir -p "$AUDIT_FIXTURE/screenshots/nested"
cp "$SOURCE_PNG" "$AUDIT_FIXTURE/screenshots/nested/leak.png"
expect_failure "nested screenshot is rejected" env SARO_GITLEAKS_BIN="$GITLEAKS_BIN" \
  SARO_PRIVATE_DENYLIST="$DENYLIST" "$AUDIT_FIXTURE/tools/audit-public-tree.sh" \
  "$AUDIT_FIXTURE"
find "$AUDIT_FIXTURE/screenshots/nested" -depth -delete

printf '[private capture](screenshots/private-capture.png)\n' \
  >>"$AUDIT_FIXTURE/README.md"
expect_failure "screenshot documentation reference is rejected" env \
  SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$AUDIT_FIXTURE/tools/audit-public-tree.sh" "$AUDIT_FIXTURE"
sed -i.bak '$d' "$AUDIT_FIXTURE/README.md"
find "$AUDIT_FIXTURE/README.md.bak" -delete

PNG="$AUDIT_FIXTURE/$PNG_RELATIVE"
inject_png_text_chunk "$SOURCE_PNG" "$TMP_ROOT/metadata.png"
cp "$TMP_ROOT/metadata.png" "$PNG"
write_binary_manifest "$AUDIT_FIXTURE"
expect_failure "hash-approved PNG metadata chunk is rejected" env \
  SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$AUDIT_FIXTURE/tools/audit-public-tree.sh" \
  "$AUDIT_FIXTURE"

inject_png_chunk "$SOURCE_PNG" "$TMP_ROOT/oversized-srgb.png" sRGB 'ABCD'
cp "$TMP_ROOT/oversized-srgb.png" "$PNG"
write_binary_manifest "$AUDIT_FIXTURE"
expect_failure "hash-approved oversized allowed PNG chunk is rejected" env \
  SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$AUDIT_FIXTURE/tools/audit-public-tree.sh" \
  "$AUDIT_FIXTURE"

inject_png_chunk "$SOURCE_PNG" "$TMP_ROOT/one-srgb.png" sRGB $'\001'
inject_png_chunk "$TMP_ROOT/one-srgb.png" "$TMP_ROOT/duplicate-srgb.png" sRGB $'\001'
cp "$TMP_ROOT/duplicate-srgb.png" "$PNG"
write_binary_manifest "$AUDIT_FIXTURE"
expect_failure "hash-approved duplicate PNG singleton chunk is rejected" env \
  SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$AUDIT_FIXTURE/tools/audit-public-tree.sh" "$AUDIT_FIXTURE"

inject_png_chunk "$SOURCE_PNG" "$TMP_ROOT/invalid-sbit.png" sBIT 'AAAA'
cp "$TMP_ROOT/invalid-sbit.png" "$PNG"
write_binary_manifest "$AUDIT_FIXTURE"
expect_failure "hash-approved malformed PNG ancillary value is rejected" env \
  SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$AUDIT_FIXTURE/tools/audit-public-tree.sh" "$AUDIT_FIXTURE"

mutate_png_ihdr "$SOURCE_PNG" "$TMP_ROOT/invalid-ihdr.png"
cp "$TMP_ROOT/invalid-ihdr.png" "$PNG"
write_binary_manifest "$AUDIT_FIXTURE"
expect_failure "hash-approved malformed IHDR is rejected" env \
  SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$AUDIT_FIXTURE/tools/audit-public-tree.sh" "$AUDIT_FIXTURE"

inject_idat_trailing_payload "$SOURCE_PNG" "$TMP_ROOT/idat-payload.png"
cp "$TMP_ROOT/idat-payload.png" "$PNG"
write_binary_manifest "$AUDIT_FIXTURE"
expect_failure "hash-approved unused IDAT payload is rejected" env \
  SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$AUDIT_FIXTURE/tools/audit-public-tree.sh" "$AUDIT_FIXTURE"

cp "$SOURCE_PNG" "$PNG"
printf 'trailing-data' >>"$PNG"
write_binary_manifest "$AUDIT_FIXTURE"
expect_failure "hash-approved PNG trailing data is rejected" env \
  SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$AUDIT_FIXTURE/tools/audit-public-tree.sh" \
  "$AUDIT_FIXTURE"
cp "$SOURCE_PNG" "$PNG"
write_binary_manifest "$AUDIT_FIXTURE"

EXPORT_REPO="$TMP_ROOT/export-source"
make_audit_fixture "$EXPORT_REPO"
cp "$SOURCE_PNG" "$EXPORT_REPO/screenshots/private-capture.png"
cp "$EXPORTER" "$EXPORT_REPO/tools/export-public-snapshot.sh"
chmod +x "$EXPORT_REPO/tools/export-public-snapshot.sh"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'if [[ "${SARO_TEST_GATE_FAIL:-0}" == "1" ]]; then exit 41; fi' \
  'root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"' \
  '"$root/tools/audit-public-tree.sh" "$root" private-source >/dev/null' \
  >"$EXPORT_REPO/tools/verify-release.sh"
chmod +x "$EXPORT_REPO/tools/verify-release.sh"

git -C "$EXPORT_REPO" init -q -b main
git -C "$EXPORT_REPO" config user.name 'SARO Test'
git -C "$EXPORT_REPO" config user.email "saro-test@"'users.noreply.invalid'
printf 'HISTORY-ONLY-SENTINEL\n' >"$EXPORT_REPO/historical-private.txt"
git -C "$EXPORT_REPO" add -A
git -C "$EXPORT_REPO" commit -q -m 'Historical private state'
git -C "$EXPORT_REPO" rm -q historical-private.txt
printf 'ignored-local.txt\n' >"$EXPORT_REPO/.gitignore"
printf 'current public state\n' >>"$EXPORT_REPO/README.md"
git -C "$EXPORT_REPO" add -A
git -C "$EXPORT_REPO" commit -q -m 'Public source state'
printf 'PRIVATE-TREE-SENTINEL\n' >"$EXPORT_REPO/ignored-local.txt"

GATE_FAILURE_DEST="$TMP_ROOT/gate-failure-export"
expect_failure "exporter propagates the full release gate" env SARO_TEST_GATE_FAIL=1 \
  SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$EXPORT_REPO/tools/export-public-snapshot.sh" \
  "$GATE_FAILURE_DEST"
[[ ! -e "$GATE_FAILURE_DEST" ]] || fail "failed release gate published a destination"

EXPORT_DEST="$TMP_ROOT/public-snapshot"
SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$EXPORT_REPO/tools/export-public-snapshot.sh" \
  "$EXPORT_DEST" >/dev/null
[[ ! -e "$EXPORT_DEST/.git" ]] || fail "export copied private Git metadata"
[[ ! -e "$EXPORT_DEST/ignored-local.txt" ]] || fail "export copied an ignored local file"
[[ ! -e "$EXPORT_DEST/historical-private.txt" ]] || fail "export copied a deleted historical file"
[[ ! -e "$EXPORT_DEST/screenshots" ]] || fail "export copied private screenshots"
if find "$EXPORT_DEST" -type f -exec grep -FIl 'HISTORY-ONLY-SENTINEL' {} + |
  grep -q .; then
  fail "export retained deleted historical content"
fi
pass "export contains only committed HEAD with no ignored files or Git history"

git -C "$EXPORT_DEST" init -q -b main
git -C "$EXPORT_DEST" config user.name 'SARO Project'
git -C "$EXPORT_DEST" config user.email "saro-project@"'users.noreply.invalid'
git -C "$EXPORT_DEST" add -A
git -C "$EXPORT_DEST" commit -q -m 'Initial public release'
[[ "$(git -C "$EXPORT_DEST" rev-list --count HEAD)" == "1" ]] ||
  fail "new public repository has more than one commit"
if git -C "$EXPORT_DEST" rev-parse HEAD^ >/dev/null 2>&1; then
  fail "new public root commit has a parent"
fi
pass "export initializes as one parentless public root commit"

printf 'dirty\n' >>"$EXPORT_REPO/README.md"
expect_failure "exporter refuses modified tracked files" env \
  SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$EXPORT_REPO/tools/export-public-snapshot.sh" \
  "$TMP_ROOT/dirty-export"
git -C "$EXPORT_REPO" checkout -q -- README.md

printf 'untracked\n' >"$EXPORT_REPO/untracked.txt"
expect_failure "exporter refuses untracked publishable files" env \
  SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$EXPORT_REPO/tools/export-public-snapshot.sh" \
  "$TMP_ROOT/untracked-export"
find "$EXPORT_REPO/untracked.txt" -delete

expect_failure "exporter refuses a destination inside the private repository" env \
  SARO_GITLEAKS_BIN="$GITLEAKS_BIN" SARO_PRIVATE_DENYLIST="$DENYLIST" \
  "$EXPORT_REPO/tools/export-public-snapshot.sh" \
  "$EXPORT_REPO/public-child"

printf '1..%d\n' "$PASS"
