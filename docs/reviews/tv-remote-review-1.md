# SARO TV Remote Independent Review 1

Date: 2026-08-12

Scope: read-only review of the uncommitted provider-remote implementation,
Android accessibility architecture, configuration changes, tests, security
boundary, and live evidence. Findings are ordered by severity.

## Findings

### High: OK could activate a sole candidate without explicit focus

The initial semantic fallback clicked when exactly one candidate existed. That
candidate could be Subscribe, Start Trial, Agree, or Purchase.

Required fix: remove this fallback and require an explicitly navigated target.

### High: input and accessibility focus were mixed

Navigation could fall back from Android input focus to accessibility focus,
while OK still preferred a possibly stale input-focused node. This could move a
visible focus indicator without changing the node later selected.

Required fix: use one focus domain or track a separate explicit cursor and bind
selection to it.

### Medium: Back could affect an unrelated foreground app

The remote remained visible briefly after an unsupported transition, and its
Back action did not revalidate the foreground package.

Required fix: resolve a supported foreground root immediately before global
Back.

### Medium: traversal was depth-bounded but not work-bounded

A 64-level depth cap still permitted thousands of synchronous Binder calls and
allocations across a broad WebView or Compose tree on the accessibility main
thread.

Required fix: add node, candidate, and elapsed-time budgets.

### Medium: bounds-only deduplication could retain the wrong node

Nested provider nodes can share a rectangle but differ in label, focusability,
clickability, and actions.

Required fix: retain the most actionable duplicate rather than the first node.

### Medium: documented accessibility scope understated actual capability

Package-filtered events did not eliminate the original ride service's broader
interactive-window and gesture capabilities. The absence of
`INJECT_EVENTS` was not sufficient evidence that gesture injection was
unavailable.

Required fix: document the real boundary and preferably separate provider
navigation from subscription-banner automation's gesture-enabled service.

### Medium: the candidate was not yet releasable or recoverable

Live evidence covered a 0.6.0 prototype, while the hardened source, security
audit hash, exact signed APK, and recovery bundle had not converged.

Required fix: version the hardened build separately, live-test that exact
artifact, update the audit, and create a verified recovery checkpoint.

### Low: exceptional focus search could leak a node wrapper

Some exceptions could escape before the focused node was recycled.

Required fix: put focused nodes under a safe outer `finally`.

### Low: configuration import did not reposition an attached remote

Import updated preferences but an already attached overlay did not rebuild
until a later hide/show cycle.

Required fix: explicitly reload the remote after import.

## Residual Risks And Gaps

- Android-level tests did not yet cover real accessibility nodes, foreground
  window selection, failure recovery, or node recycling.
- A long provider/ride soak had not measured heap growth, poll cost, UI latency,
  or ride-checkpoint accuracy.
- The exact hardened APK still required live drag, collapse, Back, SARO Home,
  auto-hide, ride coexistence, config, and reboot validation.
- Provider accessibility contracts are update-sensitive.
- Authenticated Peacock, Paramount+, and MGM+ playback remained owner-gated.

The reviewer found no concrete defect in additive schema-v1/v2 parsing, the
non-focusable overlay flags, 56 dp command targets, no-network behavior, or
tracked screenshot privacy. Five pure-Java suites and all 59 host recovery tests
passed after remediation began.
