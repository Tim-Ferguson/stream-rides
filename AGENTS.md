# SARO Agent Instructions

Read [`ai-contributors.md`](ai-contributors.md) before changing code or a
connected device. Read [`docs/recovery.md`](docs/recovery.md) before any ADB
operation that can modify device state.

## Mandatory Device Safety Gate

Before the first install, uninstall, package-data change, Home assignment,
Accessibility change, `settings put/delete`, or `wm` change on an owner's
device:

1. Confirm that exactly one authorized ADB target is selected and record its
   model, product, Android/API version, ABI, build fingerprint, and current Home
   component in a private owner-controlled directory outside Git.
2. Explain that ordinary non-root ADB cannot create a complete restorable image
   of protected app data, account sessions, DRM material, or the manufacturer
   firmware. Never describe the baseline below as a factory-image backup.
3. From `device-setup/`, run the read-only check and create the atomic,
   hash-verified baseline before making any device change:

   ```sh
   ./saro-control check
   BACKUP_ROOT="$HOME/SARO-Backups/$(date +%Y%m%d-%H%M%S)"
   ./saro-control capture-original-state "$BACKUP_ROOT/prechange-baseline"
   ./saro-control verify-original-state "$BACKUP_ROOT/prechange-baseline"
   ```

4. Preserve both the complete baseline directory and the generated
   `.state/previous-home-*` file in owner-controlled storage outside the
   repository. They contain device identifiers and installed-app information,
   so never commit or publish them.
5. Stop if the prior Home target is missing, invalid, or ambiguous. Do not infer
   a destructive recovery path merely to continue installation.

`bootstrap-tablet` independently records and validates the prior Home before its
first device mutation, but that guard is not a substitute for preserving the
host-side record somewhere the owner controls.

Immediately after the minimal bootstrap, before installing or updating any
other package, create and verify a full rollback checkpoint. This captures the
eligible pre-existing third-party APK/split bytes and OBBs in addition to SARO:

```sh
./saro-control bootstrap-tablet
./saro-control backup "$BACKUP_ROOT/post-bootstrap-checkpoint" --with-apks
./saro-control verify-backup "$BACKUP_ROOT/post-bootstrap-checkpoint"
./saro-control audit-live "$BACKUP_ROOT/post-bootstrap-checkpoint"
```

Do not update a package that existed in the baseline until this checkpoint has
succeeded. It is still not the untouched original state and cannot recover
protected app data. After the owner finishes configuring SARO, create a second,
separately named checkpoint for that desired state.

## Reverting

With the owner's explicit approval, use `./saro-control safe-uninstall` to
restore the validated prior Home, remove only SARO's Accessibility services,
reset SARO's display/window overrides, and uninstall the helper. Compare the
remaining third-party packages with the private pre-change inventory. Remove an
added app only after the owner approves that exact package; never clear or
uninstall manufacturer packages, factory-reset the device, or erase app data as
an assumed cleanup step.
