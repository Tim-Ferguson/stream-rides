# TV Provider Remote Live Test

## 0.6.4 Disposition

The canonical bike no longer needs this remote for Peacock, Paramount+, or
MGM+: all three now use verified touch-native phone/tablet builds. SARO 0.6.4
retains the service as a legacy Android TV fallback, but checks for a Leanback
launcher before obtaining any provider window root. Known mobile variants hide
the overlay immediately, and Manage Apps omits the remote row unless a TV
variant exists or the service is already enabled. Unit tests cover mobile
exclusion, cache expiry, and retry after resolver failure. Live testing with the
service temporarily enabled produced no remote window over Peacock mobile or
MGM+ mobile; the canonical state has the service disabled.

Date: 2026-08-11 through 2026-08-12

## Scope

- Hardware: `PLTN-RB1VO-2` / `RB1VO`
- Android: 11 / API 30
- Live-tested prototype: locally signed SARO 0.6.0
- Exact hardened build: locally signed SARO 0.6.2 / versionCode 13
- Follow-up launcher build: locally signed SARO 0.6.3 / versionCode 14
- Exact local artifacts remain pinned in private recovery.
- Provider packages: Peacock `com.peacocktv.peacockandroid` 7.6.100,
  Paramount+ `com.cbs.ott` 16.18.0, and MGM+
  `com.epix.epix.now` 236.0.2026236001

These Android TV builds render on the bike but expect D-pad input. Direct
touch either did nothing or only scrolled parts of their UI. Replacing every
package with a mobile build was not viable: Paramount+ uses a distinct mobile
package, while the current Peacock and MGM+ Play listings share package IDs
across form factors. The bike also had only about 513 MB free during diagnosis.

## Implementation

SARO 0.6.2 adds a separate, owner-enabled `SARO TV Remote` accessibility
service. It is independent of the always-needed `SARO Ride Overlay` service,
which remains scoped only to the original app. The optional service declares
only window-state events for the three exact provider packages, can retrieve
window content, and declares `canPerformGestures=false`. It does not request
`INJECT_EVENTS` or `INTERNET`.

When enabled, the draggable and collapsible remote appears only while one of
the three supported provider packages owns the validated foreground
application window. It provides Back, four directions, OK, collapse, and SARO
Home. Command hit areas are at least 56 dp tall. The non-focusable,
not-touch-modal window does not take provider input focus, and its saved
position is an optional schema-v2 configuration field.

Paramount+ and MGM+ expose native focusable nodes. SARO uses Android input-focus
search followed by a bounded geometric fallback. Peacock instead exposes an
opaque `VirtualDpadXTVWebView` accessibility provider; SARO invokes its
documented directional actions by content description. Because Peacock's OK
action cannot expose its target semantically, the first OK tap arms a 1.8-second
confirmation and the second dispatches it.

Semantic OK is also guarded. A directional action must first arm a one-shot
selection bound to package, window, source identity, bounds, class, view ID,
actions, focus state, visibility, and hashed text/state fields. SARO refreshes
and resolves that same target twice, including its clickable ancestor,
immediately before `ACTION_CLICK`. The arm expires after four seconds and is
cleared by window changes, package changes, Back, Home, collapse, reload,
failure, unsupported foregrounds, stop, interrupt, and unbind. Traversal is
capped at depth 64, 512 nodes, 128 candidates, and 75 ms.

Android's package filter limits delivered events but is not treated as a full
authorization boundary. Every command independently selects the highest
active/focused window across all window types, rejects a non-application top
window, and verifies the exact provider package and window ID. The service can
still inspect provider login screens while enabled, so activation remains an
explicit owner trust decision.

## Live Results

The 0.6.0 prototype exposed and then fixed a null-content-description crash
while probing Peacock. Its successful navigation sequences retained the same
SARO process:

- **Peacock:** repeated virtual-D-pad commands moved the yellow focus among
  catalog and legal controls. The remote focused `SIGN IN` and opened the
  activation flow. SARO immediately backed out; no one-time code is retained.
- **Paramount+:** Down focused `SIGN IN`, and OK opened the chooser offering
  on-device credentials, web linking, mobile-app linking, and provider
  connection. No method was selected.
- **MGM+:** Down opened the navigation rail and focused Home. Up moved to Log
  In; OK opened the Google, web, and remote sign-in chooser. No offer or login
  method was selected.
- **Disney+:** separately, the owner-signed-in app played protected content
  while Android reported media state `PLAYING`, active DRM, secure hardware AVC
  decoding, and advancing 48 kHz audio. SARO's ride strip, app drawer,
  pause/resume, and guarded End controls all passed.

Two independent reviews then drove the 0.6.2 service split and fail-closed
selection/window hardening. All eight focused host suites and all 59 host
recovery tests pass.

The exact 0.6.2 APK was installed in place with only `SARO Ride Overlay`
enabled and bound. `SARO TV Remote` appeared separately in Android
Accessibility and remained off. A tablet-started local ride launched the saved
MGM+ task and displayed the complete stats strip, including resistance 55,
while the optional remote was disabled. Pause-independent guarded End removed
the strip, and the pretest configuration was restored byte-for-byte.

A subsequent reboot was confirmed by a changed kernel boot ID rather than an
early ADB reconnect. SARO returned as default Home, direct sensors reported
`CAD 0 | 0 W | RES 55`, only the ride service was enabled and bound, and the TV
Remote remained off after an additional 45-second observation. The
configuration before install, after install, after the ride restore, and after
reboot was byte-identical.
Speaker media volume was reset to zero.

SARO 0.6.3 subsequently migrated Paramount+ to its distinct touch-native mobile
package `com.cbs.app`. Its empty email field and on-screen keyboard passed touch
testing, and the SARO tile launched that package without enabling TV Remote.
The TV package remains a fallback, so the scoped remote implementation is
retained for older installations. The 0.6.3 update again left only the ride
service enabled.

The ignored recovery checkpoint
`local-backups/saro-20260812-062-tv-remote-opt-in` verifies offline. It contains
21 third-party packages across 48 APK/base/split rows and the exact helper and
configuration above.

The newer ignored checkpoint
`local-backups/saro-20260812-063-paramount-mobile` also verifies offline. It
adds the three audited mobile Paramount+ APK/splits and exact 0.6.3 helper for
22 third-party packages across 51 APK/base/split rows.

The private checkpoint contains 20 third-party packages, 45 APK/base/split
rows, exact SARO 0.6.4 bytes, and touch-native Peacock/MGM+ delivery sets.

## Remaining Owner Gate

The remote no longer needs owner activation for the canonical state. The owner
must sign in through each touch-native app, then verify entitled playback and
login persistence across a normal reboot without recording credentials,
profile details, QR codes, or activation codes.

## Evidence Retention

The original provider-remote and reboot captures were inspected at original
resolution, then removed from the publishable tree. They contained no account
identifier, credential, QR code, activation code, or protected video frame.
