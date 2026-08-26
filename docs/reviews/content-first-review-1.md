# Content-First Ride Review 1

Date: 2026-08-12

Scope: independent read-only review of SARO 0.6.5's in-place ride-start
control, accessibility boundary, lifecycle, configuration, provider claims,
tests, and release evidence.

## Initial Verdict

Blocked pending one accessibility fix.

## Findings

1. Banner automation called `getWindows()` and recursively traversed every
   returned tree. Android's XML package filter limits event delivery, not
   window retrieval, so provider trees could be visited while a media app was
   foregrounded. The reviewer required root validation plus strict work and
   elapsed-time budgets.
2. The process-only ride-start arm was not cleared when the accessibility
   service was destroyed.
3. Importing saved ride-start coordinates did not immediately detach and
   rebuild an attached overlay.
4. One streaming-test paragraph still described Peacock as signed out after
   authenticated playback had passed.
5. Lifecycle, package-boundary, gesture-threshold, and configuration behavior
   relied primarily on source assertions and live tests.

The reviewer found the Paramount+ and MGM+ status accurate: both were
login-ready, while paid playback remained untested without active
subscriptions.

Resolution is recorded in
[`content-first-remediation.md`](content-first-remediation.md).
