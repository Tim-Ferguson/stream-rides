# Zwift Bridge and Recovery Review 2

Date: 2026-08-11

Scope: independent adjudication of Review 1 after its first remediation pass.

## Adjudication

- The server-wide notification FIFO is the correct response to Android's
  completion-callback requirement. Parallel per-device sends would still risk
  violating the platform contract.
- Mandatory Sensor Location and the non-distributed Cycling Power feature value
  were correctly added.
- APK/helper hashes, OBB hashes, accessibility state, configuration, and Home
  revalidation fixed the stale-journal failure.
- Generation tokens fixed service and advertising callbacks, but read and
  descriptor callbacks still had a check/use race.
- Preserving Bluetooth during a package/service restart is intentional. The
  remaining defect was that the OFF-confirmation watcher used the accessibility
  service handler and could be canceled during destruction.
- The 2.5-second bridge-specific freshness limit fixed stale power replay.
- Temporary OBB verification and same-directory rename were sound, but stale
  `.saro-part` cleanup and capacity changes after APK installation remained.

## Required Before Release

1. Handle all GATT requests on one handler and keep generation check plus
   response atomic.
2. Move Bluetooth OFF confirmation to application-scoped state.
3. Remove only known OBB staging names before capacity preflight.
4. Transfer OBBs before APK installation and recheck capacity before every push.
5. Correct server-wide queue wording and do not claim live registration until
   the revised build is measured on the bike.

Real Zwift Companion subscription, alternating notifications, nonzero physical
telemetry, and avatar movement remain owner-interactive validation.
