# TV Remote Review Remediation

Date: 2026-08-12

## Review 1 Disposition

| Finding | Resolution |
| --- | --- |
| Sole-candidate OK | Removed. Semantic OK requires an explicit prior directional selection. |
| Mixed focus domains | Removed accessibility-focus fallback. Semantic movement requires input focus and arms an exact target. |
| Stale Back | Back revalidates a supported foreground application window immediately before the global action. |
| Unbounded work | Each tree walk is capped at depth 64, 512 nodes, 128 candidates, and 75 ms. |
| Bounds-only duplicate | Same-bounds nodes are ranked by actionability and must remain focusable. |
| Accessibility privilege coupling | Split into `SARO Ride Overlay` and separately enabled `SARO TV Remote`. The ride service is original-app-only; the TV service is limited to three providers and declares no gesture capability. |
| Candidate/recovery mismatch | Prototype evidence remains labeled 0.6.0. Exact hardened source, installed APK, audit record, and recovery checkpoint converge on 0.6.2/versionCode 13. |
| Exceptional node leak | Focused, next, candidate, root, and window cleanup uses outer `finally` blocks and safe recycling. |
| Import while attached | Config import asks the connected TV service to rebuild its overlay from restored preferences. |

## Review 2 Disposition

| Finding | Resolution |
| --- | --- |
| Target identity too weak | The armed fingerprint includes package, window, source identity, bounds, class, view ID, actions, focusability, clickability, enabled/focused/visible state, hashed text/description/state, and clickable-ancestor identity. |
| Target could change between validation and click | OK performs two refresh/resolve passes and refreshes the resolved clickable ancestor immediately before `ACTION_CLICK`. |
| Provider background window could win | Foreground selection is layer-first across all window types and rejects a rank-zero or non-application top window. |
| Selection not tied to visible window | Selection and every command are bound to the exact validated foreground window ID. |
| Failure/overlay lifecycle could leave an armed action | Window transitions, Back, Home, collapse, reload, failure, unsupported foreground, stop, interrupt, and unbind clear selection. |
| Peacock opaque OK lacked confirmation | Peacock requires two OK taps inside a 1.8-second confirmation interval. |
| Capability wording was too strong | Documentation now states that package filtering limits events but controller-level window/package validation is the command boundary. |
| Source-only release checks | The release gate now inspects compiled accessibility XML and its compiled manifest association in addition to source declarations. |

## Exact Artifact State

Two frozen-source builds of SARO 0.6.2/versionCode 13 were byte-identical.
The private recovery bundle pins the tested APK and signer; those
installation-specific fingerprints are intentionally absent from the public
tree.

It is installed on the target bike. Only `SARO Ride Overlay` is enabled and
bound; `SARO TV Remote` is visibly off. A complete tablet-started ride strip
ran over MGM+ with the optional service disabled and ended through its guarded
control. The exact configuration remained byte-identical.

A kernel-boot-ID-confirmed reboot restored SARO Home, fresh direct telemetry,
and only the ride service. The optional remote stayed off after a delayed
check. The verified ignored recovery checkpoint is
`local-backups/saro-20260812-062-tv-remote-opt-in`.

Review 2's final follow-up found no code or release-gate blocker in scope. The
remaining work is the owner's explicit service opt-in and authenticated
provider playback validation, not remediation of a known implementation
finding.
