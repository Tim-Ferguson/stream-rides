# Touch Provider Migration Review

Date: 2026-08-12

Scope: independent read-only review of Peacock/MGM+ mobile migration, SARO
0.6.4 TV-flavor gating, security evidence, recovery behavior, and tests.

## Findings and Resolution

| Finding | Resolution |
| --- | --- |
| Peacock mobile has a lower versionCode than the installed TV flavor, so ordinary exact restore would fail. | Recovery measures the installed version and adds package-manager `-d` only when the verified archive is numerically older. It does not uninstall. A fake-device test verifies the flag and absence of uninstall. |
| Mobile APK evidence was not yet durable. | The current tracked inventory was regenerated from the verified 20-package/45-row checkpoint. Exact Peacock/MGM+ hashes, signers, Source Stamps, splits, permissions, and Play source URLs are recorded. |
| Peacock expands declared permission surface, including `SYSTEM_ALERT_WINDOW`. | The package audit records the expansion. Live app-op checks show default/no active overlay access for Peacock and MGM+. |
| Leanback gating originally obtained a provider root before rejecting a mobile flavor. | The accessibility event package is checked first. SARO resolves an installed Leanback launcher before `getWindows()` or `getRoot()`, hides immediately for known mobile variants, and suppresses the setting row when no TV flavor exists. |
| A transient resolver failure could be cached as unsupported. | Exceptions fail closed without caching. Positive and negative results use a 30-second TTL. Unit tests cover mobile exclusion, expiry, and failure retry. |
| The old overlay could remain visible briefly over a known mobile provider. | A positively identified mobile variant now clears selection and hides immediately. Live checks reported zero TV Remote windows over both mobile providers. |

## Residual Boundary

The accessibility-service XML still lists the shared Peacock and MGM+ package
IDs because Android cannot scope by Play delivery flavor. Default-off state,
event-first package checks, Leanback resolution before root retrieval, and the
hidden setting minimize that residual surface. The owner should leave the
service disabled unless intentionally reinstalling a supported TV flavor.

This review records the signed-out migration baseline. It is superseded for
Peacock by the later authenticated playback, reboot-persistence, and in-place
ride-start evidence in `docs/streaming-live-test.md`. Paramount+ and MGM+ are
login-ready; paid playback remains untested without active subscriptions. No
plan, trial, or purchase was selected during unattended testing.

## Final Independent Pass

A second read-only reviewer found no release blocker. It identified three low
risks:

- If the optional TV Remote service reconnects while a legacy TV app is already
  stationary, the remote can remain hidden until the app emits another window
  event or is reopened. This is retained intentionally: startup does not inspect
  a foreground root before an event identifies a supported TV package.
- YouTube TV and Paramount+ TV were still labeled as current in two package
  review rows. Those rows now say `Removed, archive only`, matching the current
  inventory and recovery checkpoint.
- The Android-specific resolver ordering has source-gate and live API-30
  coverage, but no instrumentation test. The platform-independent cache has unit
  coverage; instrumentation remains a residual test gap because this repository
  has no Android test runner or emulator fixture.
