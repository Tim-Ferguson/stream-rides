# Content-First Ride Remediation

Date: 2026-08-12

Both independent reviews are resolved for SARO 0.6.5.

| Finding | Resolution |
| --- | --- |
| Provider accessibility trees could be traversed by subscription-banner automation. | `BikeAppAccessibilityPolicy` rejects missing or non-original app package names. The Android adapter validates the root before recursion and validates every descendant. The optional provider remote remains a separate, default-off, gesture-disabled service. |
| Traversal work was bounded per window. | One shared 256-node/25-ms budget is now created before the complete window scan. Exhaustion fails closed. Pure JVM tests exercise package rejection, exact node exhaustion, and elapsed-time exhaustion. |
| The transient ride-start arm survived service teardown and lacked behavioral coverage. | `RideStartOverlayArm` owns arm, expiry, and reset behavior. Service destruction explicitly resets it, and pure JVM tests cover expiry and immediate disarm. |
| Drag-versus-click behavior lacked a behavioral test. | `DragGesturePolicy` centralizes the strict 8 dp threshold. JVM tests verify movement at the threshold remains a click and movement beyond it on either axis becomes a drag. Final-device touch testing confirmed the refactor still starts a ride. |
| Imported positions did not apply to the running manager. | Schema-v2 import commits the full overlay preference set, then asks the running ride service to detach and rebuild its overlays. A live export/import acknowledged successfully and preserved `rideStart` coordinates `(1394,577)`. Android instrumentation remains a residual coverage gap because this repository has no instrumentation runner. |
| Peacock documentation was stale. | Current compatibility and live-test records say protected playback and reboot persistence pass. The historical migration review is explicitly marked superseded for that account gate. |
| Recovery contained an older helper. | A replacement ignored checkpoint was created after installing the final build and passed offline `verify-backup`. It contains 20 third-party packages across 45 APK/split rows and pins the exact local helper and configuration bytes without publishing their installation-specific fingerprints. |

The final APK was pulled back from the tablet and was byte-identical to the
host build. Live checks on that exact build passed SARO Home, fresh sensors,
Peacock launch, the compact start control, in-place local ride start, the full
stats strip, guarded End, overlay removal, and zero media volume. Only `SARO
Ride Overlay` was enabled; Android reported no crashed accessibility service.
