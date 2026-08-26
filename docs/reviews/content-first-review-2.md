# Content-First Ride Review 2

Date: 2026-08-12

Scope: independent read-only review of the first review's findings, the
resulting remediation, the full SARO 0.6.5 diff, recovery state, touch
ergonomics, configuration compatibility, and provider-status wording.

## Conditional Verdict

No remaining code-level release blocker was found. Approval was conditional on
publishing a replacement recovery checkpoint containing the remediated APK.

## Findings

1. The then-current checkpoint still contained a pre-remediation APK, while
   the built and installed helper had changed. The reviewer required a new
   verified checkpoint before release.
2. Automated coverage still did not exercise package rejection, traversal
   exhaustion, arm expiry/reset, or drag-versus-click behavior.
3. The traversal budget was created once per original-app window rather than once
   per complete scan. This did not expose provider trees, but allowed total
   main-thread work to exceed the intended bound when several original-app windows
   existed.
4. A historical provider-migration review still listed Peacock authentication
   and playback as pending.

## Confirmed Resolutions

- Every non-original-app root is rejected before recursion, and every descendant is
  package-validated.
- Accessibility-service destruction clears the transient media arm.
- Schema v2 remains backward compatible and imports apply saved overlay
  positions to the running overlay manager.
- The 56 dp control height, 48 x 56 dp dismiss target, drag threshold,
  non-focusable/non-modal flags, bounded placement, and visible labeling are
  appropriate for the 1920 x 1080 touchscreen.
- Paramount+ and MGM+ are accurately labeled login-ready with paid playback
  untested.

Resolution of the remaining conditions is recorded in
[`content-first-remediation.md`](content-first-remediation.md).
