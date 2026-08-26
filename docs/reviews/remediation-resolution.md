# Review Remediation Resolution

This file records the disposition of the two independent reviews dated
2026-08-11. The original reports remain unchanged so their findings are not
rewritten after implementation.

## Implemented Release Blockers

- One process-wide `SensorRepository` owns Affernet registration, validation,
  freshness, Binder death handling, and bounded reconnect.
- `RideEngine` owns an explicit SARO Local Ride state machine and independent
  accounting heartbeat. Official workout parsing and controls were removed.
- Ride persistence uses monotonic same-boot accounting, bounded cross-boot wall
  time, a stale-session policy, synchronous transition commits, and a final
  summary.
- Games require fresh live sensors or an explicit Demo selection. Demo does not
  earn persistent progress; live dropout pauses play; physics uses a bounded
  fixed timestep.
- Accessibility observation is original-app scoped. Banner automation is
  owner-disabled by default, exact-text matched, and throttled. Exported host
  bridges require Android's shell-only `DUMP` permission; unused components and
  the helper's network permission were removed.
- SARO v0.2.0 and Aurora have fail-closed private release signing. Aurora's unsafe
  public-key mode has a different package ID, version suffix, and visible label.
- Recovery has atomic publication, exact manifest/file bijection, independent
  APK inspection, helper validation, hardware preflight, and resumable JSONL
  journals. Config transfer uses correlated acknowledgements.
- Overlay and Game controls have at least 48 dp targets. The prior Home is
  recorded, health checked, restorable, and protected by `safe-uninstall`.
- The legacy window agent is quarantined behind an explicit research command,
  capability authentication, serialization, idle shutdown, and a stop command.
- Pure-Java ride tests, fake-ADB host tests, repository security assertions,
  build/signature checks, provenance records, third-party notices, and asset
  provenance are part of the release gate.
- SARO 0.5.3 migrates legacy Max TV package references to the working mobile
  package. The host tool prefers mobile and resolves the real launcher activity;
  fake-ADB tests retain the TV-only fallback. Protected playback, overlay
  controls, in-place state retention, and rebooted login persistence passed live.

## Deliberate Deferrals and Residuals

- No official manufacturer workout backend is shipped. Local rides do not enter
  manufacturer workout history. A future backend requires versioned protocol fixtures and a
  distinct adapter.
- The app catalog remains bundled source data. Config stores desired state and
  package variants, but the catalog is not remotely signed or updated.
- “Close app” means task switching. Force-stopping arbitrary packages would
  require broader privilege and is not granted to the helper.
- The game canvas does not expose complete virtual accessibility nodes. The
  current target is a large landscape touchscreen; native semantic controls are
  follow-up work for broader accessibility support.
- The subscription banner has no stable resource ID on the tested build. The
  owner-enabled exact-message path still ends with a measured coordinate tap.
  This remains best effort and must be disabled if firmware changes the window.
- Recovery hashes detect accidental or malicious byte changes relative to the
  local manifest but are not an authenticity signature for cloud transfer.
- Utility configuration, provider sessions, DRM material, consent, and Termux
  private state remain outside supported recovery.

## Owner-Interactive Gates

Physical pedaling has validated simultaneous nonzero cadence, watts, and
resistance; estimated speed, distance, and calorie calibration remains future
work. Account/consent tests are still required for unverified streaming
providers, Zoom camera/microphone, ChatGPT authentication/voice, and receiver-app
privacy terms. Max mobile, Prime mobile, and Apple TV protected playback now
pass; the Max TV and Prime TV variants are documented playback failures. A
destructive restore rehearsal also requires explicit owner approval.
