# SARO Project Context

## Objective

Build a reliable, fast, touchscreen-first RB1VO exercise-bike experience that can:

- Start a SARO Local Ride without a connected computer.
- Show complete ride stats over a full-screen streaming app.
- Open, close, and switch streaming apps during a ride.
- Launch Zwift locally while keeping SARO ride controls visible.
- Optionally relay direct watts and cadence to Zwift through a phone running
  Zwift Companion, without a computer at ride time.
- Keep launcher preferences and app data across ordinary restarts and updates.
- Export a non-secret setup configuration for recovery after a device wipe.
- Be published without developer-specific paths, names, or package identifiers.

The one-time install and recovery process may use ADB. Normal riding and media
use must not depend on USB, a laptop, or a network service hosted by a computer.

## Current Architecture

### Direct telemetry

`device-setup/ride-starter/` binds directly to the tablet's
`com.onepeloton.affernetservice` service. Its Binder callback receives cadence,
power, and resistance samples. The helper derives the remaining displayed ride
values and renders them in its own accessibility overlay.

This is the current architecture. It does not read pixels, depend on
accessibility nodes for telemetry, or require an original-app PiP window to keep
stats alive. Accessibility supplies the overlay window and, when explicitly enabled,
best-effort dismissal of one exact cancelled-subscription banner. It is scoped
to `com.peloton.activity` for event observation and never controls an official
manufacturer workout.

SARO 0.6.2 added a second, separately enabled accessibility service for legacy
D-pad-only provider TV interfaces. SARO 0.6.4 now uses touch-native Peacock,
Paramount+, and MGM+ builds and resolves an installed Leanback launcher before
that optional service can retrieve a provider root. The canonical bike has the
service disabled and its Manage Apps row hidden.

SARO 0.6.3 makes touch-native Paramount+ `com.cbs.app` canonical and preserves
the TV package as fallback. The package alias migration applies uniformly to
saved order, hidden/desired/installed state, and last-app state.

SARO 0.6.4 migrates Peacock and MGM+ to their phone/tablet delivery variants
under the same package IDs. Its recovery installer supports exact same-signer
numeric downgrades without uninstalling app data, required by Peacock's lower
mobile versionCode.

SARO 0.6.5 replaces Peacock's unreliable content-first Home/PiP transition
with a draggable, dismissible `START RIDE` accessibility overlay armed by a
SARO media launch. It starts the local sensor-backed ride without changing
tasks, persists position in schema v2, and does not inspect provider nodes.

### Launcher and ride UI

The same helper APK provides a full-screen hub with:

- Start/return-to-ride, pause/resume, and guarded end controls.
- Original bike app and app-store actions.
- Installed streaming apps in a touch-friendly grid.
- Persistent order and hide/show controls.
- Android launcher categories so it can become the tablet Home app.
- A six-mode game launcher driven by the same direct bike-sensor connection.

The direct overlay displays time, estimated speed, estimated distance, cadence,
watts, resistance, kilojoules, and estimated calories. It can be dragged to
another part of the screen. The
games suppress the overlay while their own full-screen telemetry UI is active,
then restore it automatically over external apps.

### Host tooling

`device-setup/saro-control` remains useful for first installation, APK
deployment, diagnostics, screenshots, and recovery. Its older split-screen and
PiP commands are retained for debugging and historical compatibility; they are
not required by the direct telemetry overlay.

`device-setup/window-agent/` is a legacy local-only window-management helper. It
was necessary for earlier PiP experiments but is not part of the intended
standalone direct-overlay runtime.

### App store

Aurora Store 4.8.4 is built from pinned source with a tracked compatibility
patch, fails closed without private release signing, and is installed with an
owner-controlled signer. The patch avoids duplicate Compose list keys and
preserves a custom ARM64 Android TV spoof profile. That profile is stored at
`tools/aurora-profiles/saro-rb1vo-android-tv-arm64.properties`.

Use an ARM64 phone profile such as `OnePlus8Pro_EEA` for phone-only Play
listings including Audible, Kindle, and ChatGPT. Use the custom Android TV
profile only when acquiring a TV-specific package. Leaving the TV spoof active
caused phone apps to appear unsupported or report version zero.

The patched store home screen and launch from the ride hub are verified. The
repository should contain the reproducible patch and build instructions, not a
downloaded third-party APK.

## Verified State

- Direct Affernet service binding works on the target bike.
- Sensor callbacks have produced live resistance values; idle values correctly
  show zero.
- The standalone overlay renders all planned stat fields and remains visible
  over native streaming apps.
- The overlay can be dragged.
- Pause/resume and guarded end interactions have been exercised.
- A ride has been ended with the overlay dismissed.
- The hub opens the original bike app and the patched app store.
- Installed app order and hidden state persist through helper UI recreation.
- Netflix, Hulu, Disney+, Max, Prime Video, Apple TV, Peacock, Paramount+, MGM+,
  YouTube, YouTube TV, and Firefox have reached native application UI on the
  bike.
- Owner-consented YouTube public playback passed with an active hardware AVC
  decoder and stereo audio while SARO pause/resume, the overlay app drawer, and
  guarded End remained functional.
- Signed-in Apple TV 2.5.0 protected playback passed with media state `PLAYING`,
  three DRM sessions, secure AVC decoding, and advancing audio. Drawer
  open/close, ride pause/resume, guarded End, login persistence, and protected
  playback after a full reboot all passed.
- The helper source uses neutral project namespaces.
- The neutral helper is installed, its accessibility service is enabled, and the
  obsolete development package has been removed.
- Android Home opens the helper and remains selected after a cold boot.
- The explicit `SARO HOME` route retains SARO 0.4.2's native-PiP suppression and
  task-back behavior. Two Hulu hub/return cycles restored the same
  `PlayerActivity`.
- SARO 0.5.0 makes `APPS` a second type-2032 accessibility overlay rather than
  launching the hub. It respects saved app order and hidden state; open, close,
  scroll, app launch, pause/resume, and explicit Home controls pass live.
- Signed-in Netflix protected playback was verified with Android media state
  `PLAYING`, active audio, three DRM sessions, a hardware AVC decoder, and
  secure screenshot blocking. The same `PlayerActivity` remained resumed while
  the app drawer opened and closed.
- Signed-in Hulu protected playback was verified with Android media state
  `PLAYING`, an active DRM session, a hardware AVC decoder, and the SARO overlay
  visible above the player. The protected video surface correctly blocked
  screenshot capture.
- Signed-in Disney+ protected playback was verified with Android media state
  `PLAYING`, an active DRM session, secure hardware AVC decoding, advancing
  48 kHz audio, and the SARO overlay. App-drawer, ride pause/resume, guarded
  End, and exact configuration restoration passed.
- Configuration schema v2 export/import, v1 compatibility, correlated transfer
  acknowledgements, and atomic recovery-bundle creation are verified.
- Game scores, credits, and upgrades round-trip through the configuration file.
- Six games run on the bike with generated local sprites and direct telemetry:
  Cadence Drag, Cadence Flyer, Resistance Ridge, Power Reactor, Rhythm Runner,
  and Orbital Courier.
- Official Audible, Kindle, ChatGPT, Zoom, and Termux packages reach native UI.
- Kindle reused the existing Amazon session without credential extraction.
- Codex CLI 0.147.0 runs in Termux and reaches the official ChatGPT sign-in
  flow through an unsupported Linux ARM64 package alias.
- Official uBlock Origin is installed in Firefox. SmartTube was rejected due to
  its historical distribution/key-compromise risk.
- Official Kodi 21.3 ARM64 launches and advertises its UPnP service over the
  local network. Kodi's AirPlay option is enabled, but modern AirPlay discovery
  was not observed and must remain classified as experimental.
- A cold boot preserved SARO as Home, accessibility enablement, the active ride
  clock, overlay reconnection, launcher state, game progress, Hulu and Kindle
  sessions, Kodi discovery, Termux/Codex, uBlock Origin, and the selected Aurora
  phone profile.
- Home always returns to the hub after Games was moved to an isolated Android
  task.
- The cancelled-subscription banner is automatically dismissed using the
  measured bounds of its compact subscription overlay window.
- Max TV 7.8.1.4 reached a signed-in catalog but reproducibly crashed while
  initializing playback, including with SARO idle. Official ARM64 mobile Max
  7.8.1.2 instead passed protected Widevine playback with secure hardware AVC,
  active audio, advancing media state, and the SARO overlay. SARO 0.5.3 migrates
  saved TV-package references to mobile and the signed-in catalog persisted
  across a full tablet reboot.
- A 2:47 standalone ride passed pause/resume, drag, end confirmation, end, and
  a fresh tablet-only ride start.
- The v0.2.0 helper survived a cold reboot during an active local ride, rebound
  accessibility and the direct sensor service, and preserved volume at zero.
- The 0.4.2 in-place upgrade preserved the active local ride. App return,
  overlay pause/resume, guarded end at 53:54, overlay removal, SARO Home return,
  and speaker media volume zero all passed on the live tablet.
- The 0.5.0 in-place upgrade preserved a second active ride plus signed-in
  Netflix and Hulu state. The 29:07 ride exercised protected Netflix playback,
  both drawer close paths, scrolling, app launch, pause/resume, explicit SARO
  Home/return, and guarded end while the drawer was open; both overlay windows
  were removed and the summary persisted.
- Prime Video's Android TV package reached a guest catalog, but selecting its
  offered playback path produced an HDMI-connection error before media state or
  decoding started. Its APK was subsequently removed with keep-data semantics;
  exact copies remain only in historical ignored recovery archives.
- Official mobile Prime Video 3.0.466.2047 is installed from Aurora, matches
  Amazon's Kindle/Prime signer, and reaches a touch-native secure sign-in flow.
  SARO 0.5.1 prefers that package and canonicalizes the old TV package in saved
  order, hidden/desired state, and last-app configuration.
- After owner sign-in, mobile Prime resumed entitled content with an active DRM
  session, secure MediaTek AVC decoder, and advancing 48 kHz audio. The same
  player survived overlay-drawer open/close, ride pause/resume, and guarded End.
  A full reboot preserved the signed-in catalog and protected playback. Prime's
  secure windows returned zero screenshot bytes, so no account or protected
  content was captured.
- The 0.5.1 in-place update preserved a third active ride and the foreground
  secure Prime activity. Exported schema-v2 configuration proved Prime remained
  in the fifth launcher slot under the mobile package. Drawer launch, pause,
  guarded end, overlay removal, and the persisted 57:09 summary passed live.
- SARO 0.5.2 adds explicit Home states for overlay disabled, sensor connecting,
  first-sample wait, stale data, and fresh data. A controlled live test proved
  **ENABLE OVERLAY** opens Android Accessibility settings and that restoring the
  service returns Home to fresh `CAD 0 | 0 W | RES 55` telemetry without a
  reboot. Stale starts now request a local Affernet reconnect instead of
  creating a ride with unavailable telemetry.
- A subsequent 0.5.2 touchscreen regression exported configuration, started a
  fresh ride into signed-in Hulu, displayed the complete overlay, opened the
  in-video app drawer, returned Home, ended through confirmation, and imported
  the configuration to restore the prior 12:59 local summary.
- The finalized 0.5.2/pruned state then passed a full Android reboot. Android
  resumed SARO Home without a host launch, Accessibility remained enabled,
  stationary `CAD 0 | 0 W | RES 55` telemetry was already fresh, the exported
  configuration hash was byte-identical, and Hulu reopened signed in. A fresh
  post-boot ride displayed the complete type-2032 overlay, paused, ended through
  its timed guard, removed the overlay, and restored the pre-test summary through
  the tablet Import action. See `docs/reboot-052-live-test.md`.
- An owner-pedaled local ride physically verified simultaneous direct telemetry:
  one sample showed cadence 51 RPM, output 40 W, and resistance 37; a second
  showed cadence 15 RPM, output 11 W, and resistance 55. Cadence and watts reset
  to zero after stopping while resistance remained live. Pause/resume, guarded
  end, overlay removal, and persisted summary also passed.
- Official Zwift 1.119.0 expanded and reached its animated onboarding UI. SARO
  0.4.0 launches the exported main activity after removing the 1.76 GB OBB and
  returns through the overlay app switcher without ending the local ride.
- SARO 0.4.0 registers complete BLE Cycling Power and CSC GATT services,
  including mandatory power Sensor Location, and serializes notifications
  through Android's completion callback. Advertising, owner-on/owner-off
  Bluetooth preservation, in-place package update, and cold-reboot automatic
  re-registration pass live. Same-controller scan loopback is unavailable, so
  a phone running Zwift Companion is required as the runtime relay.
- original app package storage inspection found about 20 MB of private data, less
  than 1 MB of cache, and no cached videos worth deleting.
- The earlier full recovery bundle verifies offline and contains 25 third-party
  packages across 61 APK/base/split files plus the exact Zwift OBB. The newest
  private current-state bundle also verifies offline and pins 20 installed
  third-party packages across 45 APK/base/split
  files, the exact mobile Prime, Max, Paramount+, Peacock, and MGM+ APKs,
  schema-v2 configuration, and SARO 0.6.5 helper. It omits Max TV, Prime TV,
  Paramount+ TV, YouTube TV, and FLauncher APKs.
- SARO 0.6.0's prototype D-pad overlay navigated Peacock into activation,
  Paramount+ into its sign-in choices, and MGM+ into its login-method chooser.
  Two independent reviews produced the split, fail-closed 0.6.2 service. That
  historical build was installed; the current state still enables only `SARO
  Ride Overlay`.
- Exact 0.6.2 default-off validation passed a tablet-started complete ride strip
  over MGM+, guarded End, byte-identical configuration restore, and a
  kernel-boot-ID-confirmed reboot. SARO returned as Home with fresh
  `CAD 0 | 0 W | RES 55`; the TV Remote remained disabled after a delayed check.
- Official mobile Paramount+ 16.18.0 has valid v3 signatures and verified Play
  Source Stamps on all three selected APKs. Its landing and empty sign-in fields
  accept touch, on-screen keyboard opens, and the SARO 0.6.3 tile launches it.
  The installed, recovered, and freshly rebuilt helper bytes matched exactly.
  The direct install and app launch left the same firmware process running;
  only the earlier Aurora/package-installer handoff triggered the documented
  PermissionController crash cycle.
- Official mobile Peacock 7.8.10 and MGM+ 237.1 have valid signatures and
  verified Play Source Stamps. SARO tile launch, touch sign-in navigation,
  empty email focus, and on-screen keyboard invocation pass for both.
  Its optional TV service produced zero remote windows over either mobile app,
  and neither provider has active Android overlay special access.
- The reviewed SARO 0.6.5 release build and its ignored replacement recovery
  checkpoint match exactly. The checkpoint contains 20 third-party packages
  across 45 APK/split rows and passes offline verification.
- SARO 0.6.6 neutralizes public copy, repository paths, command names, and newly
  exported configuration while retaining compatibility aliases for the 0.6.5
  configuration filename/key and all Android-facing device identifiers. The
  live-installed bytes match the host build, and the private checkpoint
  verifies 20 third-party packages across 45 APK/split rows with only
  `saro-setup.json`.
- The current private checkpoint also passes the hardened live drift audit for
  exact packages/splits, versions and hashes, checkpoint-owned OBB paths,
  configuration, SARO Home, live-valid original-Home rollback, accessibility
  policy, and physical display state. Unrelated accessibility services are
  preserved while SARO's optional TV Remote remains absent.
- Public release is a history-free snapshot only. The working repository and
  all of its refs remain private. `tools/export-public-snapshot.sh` requires a
  clean committed tree, ignored mode-`0600` denylist, pinned Gitleaks, complete
  release gate, strict binary/PNG audit, and an outside destination. The result
  is initialized separately with one new parentless root commit.
- Package provenance review, prior architecture reviews, and the two TV Remote
  reviews are stored under `docs/security/` and `docs/reviews/`; accepted fixes
  are implemented.

## Compatibility-Limited Apps

- Tubi (`com.tubitv`): the current official APK opens but refuses to continue
  without Google Play Services.
- Pluto TV (`tv.pluto.android`): the current Android TV APK remains on its
  splash screen on this device.
- Zwift (`com.zwift.zwiftgame`): UI launch and SARO's BLE peripheral layer work,
  but the tablet's 2 GB RAM is below Zwift's 3 GB minimum and one idle ANR was
  observed. Phone-side Zwift Companion subscription and avatar movement remain
  unverified and were explicitly shelved by the owner on 2026-08-11.

Both remain valid launcher entries because a future compatible release or a
different package variant may work. They must not be described as working until
that is demonstrated.

## Pending Verification

- Shelved until the owner resumes it: pair a phone running Zwift Companion to
  SARO's advertised power/cadence services and verify nonzero values plus avatar
  movement before claiming full ride integration.
- Verify full protected playback for the remaining streaming services. Hulu,
  Netflix, Disney+, Max mobile, Prime mobile, Apple TV, and Peacock now pass; a
  successful app launch is not equivalent to DRM playback certification for
  any other provider. Paramount+ and MGM+ pass direct-touch login readiness,
  but paid playback is not tested because no active subscriptions are available.
  YouTube TV account/playback testing was explicitly skipped because the owner
  has no YouTube TV sign-in.
- Peacock owner authentication, selected-profile persistence, protected
  playback, secure decoding, and the complete ride overlay pass after reboot.
  Private profile evidence remains outside Git.
- A signed-out 25-minute local ride switched MGM+ to Paramount+ to Peacock
  through SARO's own tiles and overlay drawer. All three retained the complete
  strip and live resistance; the ride returned Home and ended through the
  guarded dialog. MGM+ and Peacock report the absent Play Store/Play services,
  and one Peacock generic-error path self-exited cleanly. Android recorded
  `EXIT_SELF` with status zero, not a crash or low-memory kill; three subsequent
  cold launches all held the catalog. No app data was cleared.
- SARO 0.6.5 touch-through, dismissal, drag persistence, full strip, secure
  playback, guarded End, and boot-ID-confirmed reboot persistence pass live.
- Verify Zoom camera/microphone availability, ChatGPT voice input, and account
  authentication without assuming Google Play Services compatibility.
- Perform a destructive recovery rehearsal only with explicit owner approval.

## Persistence and Recovery Boundaries

Android normally preserves an app's private data across a device reboot and an
in-place update signed with the same certificate. Streaming sessions should
therefore usually remain signed in in those cases.

App data is not preserved when the app is uninstalled, its data is cleared, the
device is factory-reset, or a system update wipes the user-data partition.
Streaming credentials and DRM tokens are app-private and often hardware-bound.
This project will not attempt to extract or export them.

The versioned configuration file covers project-owned state such as app order,
hidden apps, desired packages, last media app, overlay position, optional
subscription-banner automation, optional Zwift Companion bridge state,
optional TV Remote position, last local-ride summary, and game progress. It can
make reinstalling predictable, but it cannot promise password-free recovery
after a wipe. The schema is documented in `docs/config-schema-v2.md`.

The optional APK bundle excludes manufacturer-owned service packages, records every
archived split and OBB version, size, and SHA-256 hash, validates exact
file/manifest correspondence before install, checks device compatibility, and
journals resumable restore steps. It still cannot capture provider-private data
or packages installed inside Termux. The latter has a pinned reinstall script
under `tools/termux/`; full recovery and storage boundaries are in
`docs/recovery.md` and `docs/storage.md`.

## Repository Policy

- Use repository-relative paths in scripts and documentation.
- Keep source namespaces and product labels generic.
- Do not commit downloaded streaming APKs, credentials, tokens, signing secrets,
  local Android SDKs, or local JDKs.
- Do not commit third-party store APKs; commit reproducible patches and build
  instructions instead.
- Keep only neutral interface-layout screenshots in `screenshots/`; keep
  account, telemetry, login, and transient diagnostic captures outside Git.
