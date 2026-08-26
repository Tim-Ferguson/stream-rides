# SARO TV Remote Independent Review 2

Date: 2026-08-12

Scope: independent adversarial review of the revised provider remote, Review 1
findings, accessibility privilege separation, foreground-window selection,
action authorization, release gates, tests, and live-state claims.

## Initial Findings

### High: an armed target needed stronger identity and final revalidation

Package, window, and coarse node fields did not fully rule out a provider
reusing the same rectangle for a changed action between navigation and OK.

Required fix: fingerprint all stable action-relevant fields, bind the clickable
ancestor, and refresh/resolve the target immediately before dispatch.

### High: foreground selection needed to account for all window types

Choosing among application windows first could operate behind a focused system
dialog or other higher-layer window.

Required fix: select the highest active/focused window across all types, then
reject it unless it is a supported application window.

### Medium: selection needed an exact visible-window binding

A package-bound selection alone could survive a same-package activity/window
replacement.

Required fix: bind each arm and command to the exact foreground window ID and
clear it on every transition.

### Medium: exceptional cleanup and overlay lifecycle needed fail-closed behavior

Interrupt, unbind, failed attachment, collapse, and reload paths had to clear
any pending semantic or Peacock confirmation state.

Required fix: centralize invalidation and exercise every lifecycle path.

### Medium: same-bounds replacement must preserve navigation eligibility

Ranking could replace a focusable candidate with a more clickable but
non-focusable node occupying the same rectangle.

Required fix: require focusability at candidate admission and replacement.

### Medium: Peacock's opaque virtual OK needed owner confirmation

The virtual provider exposes directional commands but no semantic description
of the currently selected OK target.

Required fix: require two deliberate OK taps within a short interval.

### Medium: documentation overstated package-filter enforcement

Android accessibility `packageNames` filters event delivery but should not be
described as the sole authority for every interactive-window lookup.

Required fix: describe the boundary precisely and enforce package/window checks
inside every command.

### Medium: release validation needed compiled-artifact checks

Source grep alone could miss packaging or resource-association errors.

Required fix: inspect the built APK's accessibility XML, bind permission,
metadata declaration, and exact metadata resource association.

## Follow-Up Findings

After the initial fixes, a narrow follow-up identified two remaining issues:

- the selected target needed to include the exact window ID throughout the
  controller path; and
- the release gate needed to derive and validate the compiled XML resource ID
  rather than depend on source ordering or a hard-coded ID.

Both were corrected before final disposition.

## Final Disposition

No code or release-gate blocker remains in the requested scope. The final
review accepted the layer-first foreground policy, package/window-bound
selection, two-pass target resolution, Peacock confirmation, split services,
and compiled manifest/resource gate.

The reviewer ran all eight focused Java suites, all 59 host recovery tests, a
clean temporary APK build, compiled manifest/XML inspection, shell syntax
checks, and `git diff --check`. Remaining risk is provider UI drift and the
owner-gated authenticated live test, both documented rather than represented
as completed.
