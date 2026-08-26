# Streaming Playback and Task Return Test

Date: 2026-08-11 through 2026-08-12

## Scope

- Hardware: `PLTN-RB1VO-2` / `RB1VO`
- Android: 11 / API 30
- Helper: locally signed SARO 0.4.2, 0.5.0, 0.5.1, 0.5.2, 0.5.3,
  0.6.0 provider-remote prototype, exact hardened 0.6.2/versionCode 13,
  Paramount-mobile migration 0.6.3/versionCode 14, touch-provider migration
  0.6.4/versionCode 15, and in-place ride start 0.6.5/versionCode 16
- Ride mode: tablet-started SARO Local Ride
- Providers exercised: Hulu 6.32.0, Netflix 9.40.0 build 7, Max TV 7.8.1.4,
  Max mobile 7.8.1.2, Prime Video TV 6.24.4, Prime Video mobile 3.0.466.2047,
  Apple TV 2.5.0, Disney+ 26.12.1, Peacock mobile 7.8.10, Paramount+ mobile
  16.18.0, MGM+ mobile 237.1.2026237011, and YouTube 5.30.320

The Hulu and Netflix workflows were performed through tablet controls while ADB
inspected and captured evidence. The later Prime diagnosis and playback checks
used repeatable ADB input against the same on-device controls; the owner entered
all account authentication directly. SARO's normal ride, launcher, and overlay
runtime has no host dependency.

## Hulu Protected Playback

Normal Hulu `PLAY` navigation reached
`com.hulu.features.playback.PlayerActivity` while the SARO accessibility overlay
remained shown and visible as Android window type 2032.

The live device reported all of the following at the same time:

- Media session state 3 (`PLAYING`), speed 1, and no playback error.
- Playback position advanced through at least 57 seconds.
- The Hulu process owned an active Google DRM session.
- The Hulu process owned an active MediaTek
  `OMX.MTK.VIDEO.DECODER.AVC` hardware decoder.
- The SARO strip remained visible, and its `PAUSE` control paused only the local
  ride. `RESUME` remained available from the hub.

Android returned zero screenshot bytes while protected playback was active.
That is expected secure-surface behavior. No protected video frame or account
information was committed to this repository.

## Native PiP Regression And Fix

The first switching test exposed a real Android 11 regression: opening the hub
from Hulu allowed Hulu's activity to detach into a native pinned task. Returning
then opened Hulu's catalog instead of restoring the active player.

SARO 0.4.2 fixes the transition in two coordinated steps:

1. `MediaLauncher.openHub` launches the hub with Android's
   `android:activity.disallowEnterPictureInPictureWhileLaunching` activity
   option and marks the launch as a media-task return.
2. `MainActivity` handles `RETURN TO VIDEO` by moving its temporary hub task to
   the back, revealing the original media task without reconstructing it.

The Hulu PiP app-op was restored to Android's default after diagnosis. Two full
`APPS` / `RETURN TO VIDEO` cycles then produced no pinned task and restored the
same `PlayerActivity` each time.

Hulu may pause its own media when backgrounded. The normal Hulu or Android media
control resumes it; SARO does not force third-party playback state.

Netflix exposed the remaining weakness in the Activity-based switch. Opening
the full SARO hub caused Netflix to finish its own `PlayerActivity` and return to
the catalog. No pinned task was created, and the active DRM pipeline had not
failed. The player was reacting to being backgrounded by another Activity.

## Netflix Protected Playback And Overlay Drawer

During signed-in Netflix playback, the live device reported all of the
following concurrently:

- The media session was state 3 (`PLAYING`) at speed 1 with no playback error.
- The Netflix process owned a started movie `AudioTrack`.
- The process owned three active Google DRM sessions.
- A MediaTek `OMX.MTK.VIDEO.DECODER.AVC` hardware decoder was active.
- Netflix owned the player `SurfaceView`, and Android reported advancing frame
  accounting.
- Android returned zero screenshot bytes, as expected for the secure surface.

SARO 0.5.0 changes the strip's `APPS` action to a second non-focusable Android
window type 2032 accessibility overlay. It does not launch or background an
Activity. The ordered, scrollable drawer uses the same saved app order and
hidden state as SARO Home.

The exact same Netflix `PlayerActivity` record remained `RESUMED` before the
drawer opened, while it was open, and after both the drawer-header and strip
close paths. Media playback, DRM sessions, audio, and the decoder remained
active. The test also exercised drawer scrolling, launching Hulu, local-ride
pause/resume while the drawer was visible, explicit `SARO HOME`, and returning
to the preserved Hulu task.

No profile names, account identifiers, protected frames, or title-specific
details were committed. The drawer screenshot was captured over Netflix's
non-protected catalog UI.

## Max Diagnosis

The Android TV package `com.wbd.hbomax` 7.8.1.4 rendered its landing, Sign In,
and signed-in catalog interfaces responsively. It survived exact-task SARO
round trips, but reproducibly failed when protected playback initialized. The
failure also occurred with SARO idle and no ride overlay, isolating it from the
helper. Logcat reported:

```text
n49: lateinit property player has not been initialized
tv.youi.videolib.PlayerSDKPlayerManager
```

The older mobile package 6.16.0.70 was blocked by Max's mandatory update flow.
Aurora then installed the current ARM64 mobile package `com.wbd.stream` 7.8.1.2
with its required language and density splits. All four APKs verify under the
same Warner certificate as the TV build and carry a verified Google Play Source
Stamp.

After the owner signed in, the mobile build passed protected playback. Android
reported media state 3 (`PLAYING`) at normal speed with advancing position,
active Widevine DRM sessions, an active audio track, and the secure MediaTek
`OMX.MTK.VIDEO.DECODER.AVC.secure` decoder. No app crash or playback error was
observed. SARO's overlay stayed visible; pause/resume changed only the local ride
while Max playback continued, and guarded ride end removed the overlay normally.

SARO 0.5.3 exposes only the working mobile package and canonicalizes saved TV
package references in order, hidden state, desired/installed state, and the last
app field. The in-place update retained Home, accessibility, app data, and all
unrelated launcher choices. The host compatibility command now also resolves
the installed package's actual launcher activity and prefers mobile when both
variants exist.

The TV APK was removed with keep-data semantics after migration and is omitted
from the current recovery checkpoint. A full Android reboot then restored SARO
Home, fresh direct telemetry, the canonical configuration, and the signed-in
Max mobile catalog without host setup. Secure playback screenshots remain black
by design; only the SARO accessibility-overlay strip is capturable.

## Prime Video Package Selection

The previously installed `com.amazon.amazonvideo.livingroom` Android TV build
reached its guest catalog. Selecting the offered playback path produced the
app's own HDMI-connection error before playback began. Its Android media session
remained state 0, and no successful audio, DRM, or decoder pipeline was observed.
This package is therefore launch-compatible but playback-incompatible with the
integrated display.

The per-installation locally signed Aurora Store then installed official mobile package
`com.amazon.avod.thirdpartyclient` 3.0.466.2047. The exact installed base is
ARM64, verifies under Amazon signer SHA-256
`2f19adeb284eb36f7f07786152b9a1d14b21653203ad0b04ebbf9c73ab6d7625`,
and has APK SHA-256
`4f04bf217150887ec6bb503ff11f400894bb2dec311831118213d9f9802ea5ae`.
Its touch-native secure authentication Activity rendered without crash or ANR.
The owner completed authentication on the tablet, and all declared sensitive
runtime permissions remained denied.

SARO 0.5.1 replaces the TV tile with the mobile package. Existing saved order,
hidden state, desired state, and last-app references to the TV package migrate
to the mobile package. A schema-v2 export proved Prime remained in the fifth
launcher slot and became the canonical last app. Selecting that tile from the
in-ride drawer opened the mobile package and closed the drawer.

After the mobile migration and tablet-only SARO 0.5.2 regression passed, the TV
package was removed with `pm uninstall -k --user 0`. Its APK path is absent and
the mobile package remains installed and canonical. Android retains uninstall
metadata/private data, and the exact TV APK remains available only in older,
ignored recovery archives. The current pruned recovery bundle cannot reinstall
it accidentally.

After owner sign-in, an entitled Continue Watching item reached
`com.amazon.avod.app.PlayerActivity`. During playback Android reported all of
the following concurrently:

- The Prime process owned an active Google DRM session and the secure MediaTek
  `OMX.MTK.VIDEO.DECODER.AVC.secure` hardware decoder.
- It owned an active 48 kHz stereo audio track whose server frame counter
  advanced by approximately eight seconds across an eight-second sample.
- The same player Activity remained resumed while SARO's app drawer opened and
  closed, changing the type-2032 accessibility-window count from one to two and
  back to one.
- SARO pause/resume changed only the local ride. Guarded End removed the final
  overlay while Prime playback continued.

Prime's Media3 session incorrectly remained state 2 (`PAUSED`) with an unknown
position during this test. The active secure decoder, DRM session, audio track,
advancing server frames, and visible player Activity are therefore the reliable
playback evidence for this package.

A full Android reboot then restored SARO Home and the canonical configuration.
A cold Prime launch returned directly to its authenticated catalog, including
the normal Home, My Stuff, Downloads, and Continue Watching navigation. The
same entitled item resumed in `PlayerActivity` with a new active DRM session,
secure AVC decoder, and another advancing 48 kHz audio sample. This verifies
login and protected-playback persistence across reboot on the tested tablet.

Android screencap returned zero bytes for Prime's authentication, catalog, and
player windows. No activation code, credential, account identifier, protected
frame, or title-specific evidence was captured or committed.

## Apple TV Protected Playback

Official Android TV package `com.apple.atve.androidtv.appletv` 2.5.0 cold
launched into its existing authenticated catalog. An explicit Resume action on
entitled Continue Watching content started protected playback in the same
`com.apple.android.tv.MainActivity`.

During playback Android reported all of the following concurrently:

- Media state 3 (`PLAYING`) at speed 1 with advancing position and no playback
  error.
- Three active DRM sessions and the secure MediaTek
  `OMX.MTK.VIDEO.DECODER.AVC.secure` hardware decoder.
- An active 48 kHz stereo audio track whose server frame counter advanced by
  approximately eight seconds across an eight-second sample.
- The same Apple TV Activity remained resumed while SARO's app drawer opened
  and closed; the type-2032 overlay count changed from one to two and back.
- SARO pause/resume changed only the local ride. Apple TV's position continued
  advancing, and guarded End removed the overlay without stopping playback.

Before the persistence check, SARO restored its pre-test configuration
byte-for-byte. After a full Android reboot, SARO Home and Accessibility
recovered automatically with speaker media volume still zero. Apple TV cold
launched back into the authenticated catalog and retained the entitled Resume
action. Playback initially reported state 6 (`BUFFERING`), then reached state 3
with a fresh secure decoder, three DRM sessions, and advancing 48 kHz audio.
The SARO configuration remained byte-identical after reboot.

Apple TV's protected picture was black in Android screencap, while subtitles
and the accessibility overlay remained capturable. The transient capture was
reviewed and later removed under the neutral-layout-only screenshot policy.

## Disney+ Protected Playback

After the owner signed in, Disney+ protected playback ran concurrently with a
SARO Local Ride. Android reported media state 3 (`PLAYING`), an active DRM
session, secure hardware AVC decoding, and advancing 48 kHz audio. The ride
strip remained visible; its app drawer and pause/resume controls worked, and
the guarded End action removed the strip without stopping Disney playback. The
same schema-v2 configuration exported before and after the test with an exact
byte-for-byte match.

The protected picture was not retained. No profile or account identifier is in
the repository.

## TV Provider Remote

SARO 0.6.0's provider-remote prototype solved the touch-unresponsive navigation
boundary for Peacock, Paramount+, and MGM+. Peacock's virtual D-pad moved focus
and opened its activation flow; Paramount+ reached its four sign-in choices;
MGM+ opened its navigation rail and login-method chooser. No plan, trial,
terms, or login method was selected.

The independently reviewed 0.6.2 implementation moves this capability into a
separate accessibility service that is disabled by default, package/event
scoped to these three providers, and unable to perform gestures. The exact
0.6.2 APK passed installation, core-ride coexistence, byte-identical config,
boot-ID-confirmed reboot, and offline recovery checks while the TV Remote
remained off. Exact implementation, security scope, evidence, and the remaining
owner opt-in/account gate are in
[`tv-remote-live-test.md`](tv-remote-live-test.md).

## Touch-native Peacock and MGM+ Migration

On 2026-08-12, a disposable Android 11 ARM64 phone-profile Google Play session
delivered different APK flavors under Peacock's and MGM+'s existing package
IDs. Both selected delivery sets have valid APK signatures and verified Google
Play Source Stamps. Their launch manifests use ordinary `LAUNCHER`, require
fake touch, and omit `LEANBACK_LAUNCHER`.

- Peacock `com.peacocktv.peacockandroid` 7.8.10 launched from SARO, displayed an
  advisory unsupported-Google-Play-services dialog, continued after OK, opened
  Sign In by touch, focused the empty email field, and opened the on-screen
  keyboard. No credential was entered and no plan was selected.
- MGM+ `com.epix.epix.now` 237.1 launched from SARO, opened Log In by touch,
  focused the empty email field, and opened the on-screen keyboard. No credential,
  offer, Restore Purchase, or subscription action was used.
- Peacock mobile versionCode `124070810` is lower than TV `230706100`. Android
  accepted a same-signer `install-multiple -r -d` update without uninstalling.
  Recovery now adds `-d` only when an exact archive is numerically older than
  the installed package.
- SARO 0.6.4 resolves a Leanback launcher before provider window retrieval.
  With its optional TV service temporarily enabled, both mobile apps produced
  zero SARO TV Remote windows. The service was then disabled again.
- Live app-op checks showed default/no active `SYSTEM_ALERT_WINDOW` access for
  both providers. Volume stayed zero and `system_server` PID 1171 remained
  stable throughout.

The signed-out migration captures were reviewed and then removed from the
publishable tree. This section is the retained text record. Peacock was
authenticated and playback-tested later; MGM+ paid playback remains untested
without an active subscription.

### Signed-out active-ride switching

A subsequent 25-minute local ride exercised all three touch-native providers
without entering an account flow. The user-level path was SARO Home, its app
tiles, and the overlay `APPS` drawer; host ADB only injected equivalent taps and
observed process/window state. No host component is part of the runtime path.

- MGM+ passed SARO tile launch, dismissed a transient missing-Play-Store update
  warning without intervention, reached its catalog, and retained the complete
  strip with resistance `55`.
- The in-ride drawer opened over MGM+ and switched directly to Paramount+.
  Paramount+ completed a roughly 13-second cold start and reached its mobile
  landing page with the same ride session and strip intact.
- The same drawer switched to Peacock. An initial stale-process launch had
  shown a generic provider error. A clean process restart, which did not clear
  data, then produced the known unsupported-Play-services advisory; after its
  `OK` action, Peacock remained stable at its catalog under the complete strip.
- The ride returned to SARO Home, required the explicit Android `END RIDE`
  confirmation, and ended at `25:13`. Sensors remained ready, only the
  original-app-scoped ride accessibility service remained enabled, and volume
  remained zero.

The active-ride provider captures were removed from the publishable tree after
review. The sequence validates signed-out launch, ride coexistence, and on-bike
switching, not authenticated DRM playback or login persistence.

Android's process-exit history classified the one generic-error termination as
`EXIT_SELF`, status `0`, at foreground importance. There was no Java crash,
ANR, or low-memory reason, and the tablet later reported about 660 MB available.
Three additional force-stop/cold-start cycles, each followed only by dismissal
of the known Play-services advisory, all reached and held the Peacock catalog.
The force-stops were deliberate test boundaries and did not clear app data.

## Paramount+ Mobile Migration

Official mobile Paramount+ `com.cbs.app` 16.18.0/versionCode 420000795 was
retrieved from Google Play through Aurora. Its base and two selected splits all
verify with APK v3 signatures, the CBS Interactive Mobile certificate SHA-256
`a7520085336e50053de6b8b92e5ab89b6f62b5c22653e5684870586d02fb014e`,
and a verified Google Play Source Stamp. Its base APK SHA-256 is
`2c2ae1d48738cabcdd0048186b57625f3a5677d9218f51c91f2dc1a1bbe26c49`.

The mobile app cold-launched into a correctly scaled landscape upsell screen.
Touch opened its native sign-in form; tapping the empty email field focused it
and opened on-screen keyboard. No credentials, provider method, plan,
trial, or terms were entered or accepted. Direct `adb install-multiple` of the
same audited splits and subsequent app launches left the firmware
`system_server` PID unchanged.

An earlier install through Aurora's Android package-installer handoff coincided
with a firmware `PermissionControllerService`/`RoleControllerService` binding
crash loop. Android RescueParty reset Accessibility while recovering. Removing
the candidate, rebooting, restoring only the ride service, and later installing
the identical audited bytes directly produced no recurrence. The evidence
therefore isolates a fragile firmware/package-installer transition; it does not
show that Paramount+ application code caused the framework failure.

SARO 0.6.3 makes the mobile package canonical and retains `com.cbs.ott` as a
runtime fallback. Saved order, hidden/desired state, installed state, and the
last-app field migrate from TV to mobile. The signed in-place update preserved
the same framework PID, Home, fresh sensors, and exactly `SARO Ride Overlay`;
the optional TV Remote stayed off. Selecting the existing Paramount+ tile then
resumed `com.cbs.app`. Paid playback is not tested without an active
subscription.

## Peacock Authenticated Playback and In-Place Ride Start

Peacock was authenticated by the owner and the selected household profile was
confirmed privately. SARO does not read or store that profile name. Entitled
content reached media state 3 (`PLAYING`) with active audio and MediaTek's
secure AVC decoder. SARO's full local-ride strip remained visible and its
pause/resume, app drawer, and guarded End controls passed.

The previous content-first workflow depended on pressing Android Home while
Peacock's protected player was foregrounded. That triggered Peacock PiP and a
framework ANR: input dispatch waited five seconds for a focus-loss event in
`PlayerActivity`. Choosing Wait retained playback, but opening SARO to start a
ride later returned Peacock to its catalog. Disabling PiP avoided the ANR but
also stopped playback, so it was not an acceptable fix.

SARO 0.6.5 instead arms a 208 x 56 dp, non-focusable accessibility overlay only
after an explicit SARO media launch when no ride is active. Its `START RIDE`
action starts the direct-sensor local ride in the current provider task and
replaces itself with the complete strip; `X` dismisses it. Dragging persists a
separate schema-v2 position. The arm is process-memory-only, expires after four
hours, and is cleared when SARO returns to the foreground. It does not inspect
provider accessibility nodes or broaden the original-app-scoped service.

Live validation passed launch, provider touch-through, drag persistence,
dismissal, in-place ride start, secure playback continuity, full stats,
pause/resume, app drawer, and guarded End. A boot-ID-confirmed reboot restored
SARO as Home, only `SARO Ride Overlay` enabled, fresh telemetry, media volume
zero, the saved control position, Peacock authentication, the privately
selected profile, secure protected playback, and the complete ride strip.

Neutral versions of the Start Ride and stats-strip layouts are retained in the
README. Provider playback and profile captures are intentionally absent.

## Provider Login Readiness

Cold-launch checks distinguish login readiness from paid playback:

- Peacock subsequently passed owner-authenticated protected playback and reboot
  persistence as documented above.
- Paramount+ reaches its native `SIGN IN` activity, focuses an empty credential
  field, and opens the on-screen keyboard.
- MGM+ reaches native `Log In`, focuses its credential field, and opens the
  on-screen keyboard.

SARO did not choose a plan, start a trial, or purchase a subscription.
Paramount+ and MGM+ paid playback is not tested because the owner has no active
subscriptions. Under this milestone's acceptance criterion they count as
working login-ready providers; this is deliberately not a DRM-playback claim.
YouTube TV launched, but the owner has no YouTube TV sign-in and explicitly
skipped account and playback testing.

## YouTube Public Playback

Official Android TV package `com.google.android.youtube.tv` 5.30.320 initially
stopped at its **Get started** Terms & Privacy screen. SARO did not accept those
terms or grant microphone permission for the owner. The owner later completed
first-run consent and sign-in directly on the bike.

During a subsequent local ride, the overlay app drawer opened YouTube and a
public Blender Foundation test video played full screen. The live device
reported all of the following concurrently:

- YouTube's media session was state 3 (`PLAYING`).
- Its process owned an active MediaTek `OMX.MTK.VIDEO.DECODER.AVC` hardware
  decoder and a 48 kHz stereo audio track.
- SARO's complete telemetry and controls remained visible above the video.
- SARO `PAUSE` froze the ride timer at 12:44 while YouTube continued playing;
  `RESUME` restarted only the ride timer.
- Opening and closing the overlay app drawer kept the same YouTube
  `MainActivity` resumed and changed the accessibility-window count from one to
  two and back to one.
- The two-step End action removed the overlay without host intervention.

The public playback and consent captures were reviewed and then removed from
the publishable tree because they are test evidence, not current layouts.

## Clean End State

The active local ride survived the in-place 0.4.2 upgrade and all provider/task
switches. It ended through the overlay's two-step confirmation at 53:54. The
overlay disappeared, Android Home returned to SARO with `START LOCAL RIDE`
available, and speaker media volume remained zero.

A second local ride survived the in-place 0.5.0 update and ended through the
guarded control at 29:07 while the app drawer was open. Both accessibility
overlay windows disappeared, SARO Home showed the persisted summary, and no
host action was needed for the ride workflow.

A third local ride survived the in-place 0.5.1 update and foreground secure
Prime authentication Activity. It also exercised the package migration,
drawer launch, and pause state before ending through the guarded control at
57:09. The overlay disappeared and SARO Home displayed the persisted summary.

A fourth local ride exercised owner-consented YouTube playback, ride-only
pause/resume, the overlay app drawer, and guarded End. It finished at SARO Home
with no overlay, stopped YouTube media, speaker volume zero, and the exact
pretest configuration restored through SARO's touchscreen Import action.

A fifth local ride used the exact 0.6.2 build with only the original-app-scoped ride
service enabled. It launched the saved MGM+ task, displayed every stat including
live resistance 55, and ended through the guarded overlay control. The optional
TV Remote did not appear, and the pretest configuration was restored
byte-for-byte. A confirmed reboot then returned to SARO Home with fresh direct
telemetry and the TV Remote still disabled.

## Screenshot Policy

All historical provider and account-flow captures were reviewed for visible
account information and then removed. Only neutral current SARO interface
layouts listed in the README are published.
