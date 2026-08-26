# SARO Recovery

Recovery starts before installation with a private, read-only baseline and a
validated original-Home rollback record. After installation, it has two more
layers: a small non-secret configuration export and an optional local APK
archive. None of these layers can export streaming credentials, DRM material,
Termux private files, another app's protected data, or a complete firmware
image.

## Before The First Device Change

An agent must attempt the safest available baseline before running any command
that installs or removes packages, changes Home or Accessibility, writes Android
settings, changes display configuration, clears app data, or otherwise mutates
the device.

```sh
cd device-setup
./saro-control check
BACKUP_ROOT="$HOME/SARO-Backups/$(date +%Y%m%d-%H%M%S)"
./saro-control capture-original-state "$BACKUP_ROOT/prechange-baseline"
./saro-control verify-original-state "$BACKUP_ROOT/prechange-baseline"
```

These commands leave Android state unchanged. Capture writes an atomic,
hash-manifested directory plus a per-device `.state/previous-home-*` file on the
host. Copy both to a private, owner-controlled backup location outside the
repository before proceeding.
`bootstrap-tablet` repeats the validation and records the Home target before its
first device mutation, then fails closed if no safe target can be established.

The baseline contains read-only output for the following:

- ADB serial plus model, product/device, Android/API, ABI, security patch, and
  build fingerprint.
- The current Home component, `wm size`, and `wm density`.
- `enabled_accessibility_services`, `accessibility_enabled`,
  `force_resizable_activities`, and `enable_freeform_support`.
- The pre-existing third-party package list and versions.

These records are a rollback baseline, not a complete backup. Stock non-root ADB
cannot read protected `/data` content or reconstruct account sessions, DRM keys,
manufacturer firmware, or an unbootable device. Do not commit the baseline: its
serial, fingerprint, package list, and local state can identify an installation.
Do not proceed when the Home target is missing or ambiguous.

## Create And Verify Rollback Checkpoints

Run the minimal bootstrap, then immediately create the first full checkpoint
before installing or updating any other app:

```sh
cd device-setup
./saro-control bootstrap-tablet
./saro-control backup "$BACKUP_ROOT/post-bootstrap-checkpoint" --with-apks
./saro-control verify-backup "$BACKUP_ROOT/post-bootstrap-checkpoint"
./saro-control audit-live "$BACKUP_ROOT/post-bootstrap-checkpoint"
```

Backup creation writes into a hidden sibling staging directory. It publishes the
requested destination only after every pull, metadata write, and hash succeeds;
a failed operation removes the stage and leaves no apparently complete backup.
This is a post-install SARO recovery checkpoint, not the untouched original
state. Because it is made before other package changes, it archives the
eligible pre-existing third-party APK/split bytes and OBBs that ADB exposes.
Do not update an existing third-party package until this checkpoint succeeds.
After setup is complete, create a second, separately named checkpoint to retain
the owner's desired SARO configuration and app versions.

The top manifest covers the helper, config, device metadata, prior Home
component, APK manifest, and optional OBB manifest. Backup creation fails
closed when it cannot validate or unambiguously infer that rollback target.
The APK manifest covers
every archived base/split file. The OBB manifest records package, matching APK
version code, safe expansion filename, byte count, and SHA-256. Validation
enforces an exact file/manifest bijection, regular files only, safe names,
package/version agreement, SHA-256 integrity, helper package/signature validity,
and APK signature/audit checks. OBBs have no independent Android signature, so
their exact source and hash are security-critical. `verify-backup` is offline
and never connects to or modifies a device.

## Restore

Restore is destructive device work. Review the bundle and connected serial, then
run it only with the device owner's explicit approval:

```sh
cd device-setup
./saro-control restore-backup ../saro-backup
```

Preflight completes before the first install. Model, product, API, and ABI must
match the recorded device. `--allow-device-mismatch` exists for a deliberately
reviewed migration and must not be used as a routine workaround.

Restore performs same-package `-r` installs, adding `-d` only when an installed
package has a higher numeric version code than the exact verified archive. This
is required for Play-delivered device variants such as Peacock mobile 7.8.10,
whose version code is lower than the TV flavor under the same package and
signer. Restore never uninstalls the app for this case, so Android can retain
its private data. Restore transfers OBBs before any app is
launched, enables SARO's original-app-scoped ride accessibility service,
removes SARO's optional TV Remote while preserving unrelated accessibility
services, imports configuration, and assigns SARO Home. The owner must enable
the optional provider-content remote from Android Accessibility.
It also resets display size/density and removes legacy global freeform-window
overrides. Before the journal records success, restore rechecks every archived
package and OBB, the helper, exact exported configuration, Home, display state,
and accessibility state.
Before installation it removes only SARO's known `.saro-part` staging
names, then verifies enough shared storage remains for every missing OBB plus a
128 MiB safety margin. Capacity is checked again immediately before each OBB
push, after any APK installation. Each OBB is pushed to a temporary filename,
verified by size and SHA-256, atomically renamed, and verified again.

A per-backup, per-device JSONL journal records started, successful, and failed
steps. A journal entry is only a resume hint: every previously completed APK is
rechecked against the installed base/split SHA-256 set, every OBB is rechecked
on device, and accessibility state is rechecked. Configuration import is
idempotently repeated. A firmware wipe on the same serial therefore cannot be
hidden by an old completed journal.

Before changing packages or settings, restore verifies that the checkpoint and
host-side prior-Home records agree. `restore-home` returns to that validated
Home component. `safe-uninstall` restores Home, disables both SARO
accessibility services, and only then uninstalls the helper. Keep the prior-Home
record with the backup.

If the owner wants to remove SARO rather than restore a checkpoint, run
`safe-uninstall` with the device connected. Then compare installed third-party
packages with the private pre-change inventory. Remove only packages that the
owner confirms were added for this setup. The cleanup path must never assume it
may clear app data, uninstall manufacturer packages, factory-reset the device,
or erase unrelated apps.

After creating or restoring a full checkpoint, compare it with the connected
tablet:

```sh
./saro-control audit-live ../saro-backup
```

This requires an exact package/split set and hash match, the exact helper and
configuration, the exact OBB path set owned by checkpoint packages, SARO Home,
the ride accessibility service with TV Remote absent, a live-valid recorded
rollback target, the physical display configuration, and no legacy
freeform-window overrides. Unrelated accessibility services are allowed. It
warns when shared storage is low.

## Boundaries

- The latest live-verified ignored checkpoint contains 20 third-party packages
  across 45 APK/base/split rows, SARO 0.6.6, the exact configuration, and the
  validated original Home component. Offline `verify-backup` and live
  `audit-live` both pass. Its local hashes and name are deliberately omitted
  from the public tree because they fingerprint one installation.
- Manufacturer-owned packages are excluded so recovery cannot replace system
  services.
- The SARO helper is archived separately from third-party APKs and covered by
  the top manifest.
- App-private sessions usually survive a reboot or same-signer update, but not
  uninstall, clear-data, factory reset, or a firmware wipe.
- Host-only `pm uninstall -k` can retain private data for a later same-signer
  reinstall on this firmware, but that dormant state is device-local and is not
  captured by this backup.
- Zwift's OBB is an installer input. The archive can restore that input, but not
  the 3.5 GB of expanded files under another app's private data directory. A
  clean restore therefore needs roughly 4 GB free for Zwift's first expansion.
- Termux's Node/Codex environment must be recreated with the pinned helper under
  `tools/termux/`.
- Android `am force-stop` deliberately marks an accessibility service stopped;
  use normal reboot/process-death tests for persistence validation.
- Store recovery bundles and signing material outside Git in protected storage.
- A public clone cannot reproduce another installation's private helper or
  app-store signer. Preserve the signing material used on each tablet or use a
  reviewed new installation after a wipe.
