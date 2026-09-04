# PLTN-RB1VO Support

This directory is the hardware-specific entry point for the original
`PLTN-RB1VO` tablet model. It deliberately sits beside the existing
`PLTN-RB1VO-2` implementation under `device-setup/`.

The measured tablet reports:

- Model: `PLTN-RB1VO`
- Product/device: `RB1VO`
- Android/API: `11` / `30`
- ABI: `arm64-v8a`
- Build: `Peloton/RB1VO/RB1VO:11/RO.250111.A/43:user/release-keys`
- Security patch: `2022-10-05`
- Display: `1920x1080` at `240` dpi
- Bike app: `com.peloton.activity` `2.8.3404`
- Sensor service: `com.onepeloton.affernetservice` `3.1.1`
- Original Home: `com.peloton.launcher/.LauncherActivity`

The wrapper verifies every value before delegating to the shared SARO tooling.
It refuses another model, firmware, display configuration, bike-app version, or
sensor-service version rather than treating `RB1VO` product identity as proof of
compatibility.

Run all device operations for this model through this entry point:

```sh
cd devices/pltn-rb1vo
./saro-control preflight
./saro-control capture-original-state "$BACKUP_ROOT/prechange-baseline"
./saro-control verify-original-state "$BACKUP_ROOT/prechange-baseline"
./saro-control bootstrap-tablet
./saro-control backup "$BACKUP_ROOT/post-bootstrap-checkpoint" --with-apks
./saro-control verify-backup "$BACKUP_ROOT/post-bootstrap-checkpoint"
./saro-control audit-live "$BACKUP_ROOT/post-bootstrap-checkpoint"
```

The Android helper source remains shared because this tablet exposes the same
firmware build, API, ABI, display geometry, bike app, and Affernet service
version as the existing implementation. Matching identifiers alone do not prove
all hardware behavior, so the verified and untested scopes are recorded below.

## Live Verification

On 2026-09-04, the measured configuration above passed exact-model preflight,
atomic original-state capture and offline verification, helper installation,
Accessibility enablement, Home assignment, and exact post-bootstrap/configured
checkpoint verification. Owner-pedaled cadence, watts, and resistance all
updated through the Affernet callback. The privately signed patched app-store
build also installed and launched from its native launcher activity.

App download and installation, media overlays, games, optional TV Remote, BLE
bridging, cold-reboot recovery, and `safe-uninstall` remain untested on this
model.

## Removal

With the bike owner present and the private baseline available, remove SARO
through this model-specific wrapper:

```sh
./saro-control safe-uninstall
```

This restores the validated original Home, removes only SARO's Accessibility
services, resets SARO display/window overrides, and uninstalls the helper.
Compare the remaining third-party packages with the private pre-change
inventory. Do not remove another package or clear its data without separate
owner approval.
