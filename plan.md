# SARO Delivery Plan

## Product Goal

Deliver a standalone bike ride launcher and overlay that is dependable during
long rides, responsive on the bike touchscreen, easy to recover, and safe to
publish for other owners. A computer is allowed for initial setup and backup,
but never as a runtime dependency.

## 1. Direct Ride Overlay - Complete

- Bind to the Affernet service directly.
- Display time, speed, distance, cadence, watts, resistance, kilojoules, and
  calories without PiP or screen reading.
- Keep the overlay compact, draggable, and stable over full-screen media.
- Provide pause/resume and a guarded end action.
- Handle stale sensor connections with local reconnect attempts.
- Report sensor/overlay health on Home and provide touchscreen-only recovery.

## 2. Streaming Hub - Complete

- Launch installed streaming apps from the bike.
- Provide original bike app and app-store actions.
- Persist app order and hide/show preferences.
- Support touch-sized controls and stable landscape layout.
- Add common services without claiming unverified compatibility.

## 3. App Store Compatibility - Complete

- Patch Aurora Store list-key crashes seen on this Android build.
- Preserve an ARM64 Android TV spoof profile.
- Verify store launch from the hub.
- Publish the patch and reproducible build notes instead of a third-party APK.

## 4. Public Packaging - Complete

- Use neutral helper and window-agent namespaces.
- Rebuild generated artifacts from neutral source paths.
- Migrate the live tablet from the development package to the neutral package.
- Scan source, APK contents, resources, docs, and build output for personal
  identifiers and absolute workstation paths.

Exit criteria: a clean clone builds without developer-specific paths, and the
release APK contains only neutral project identifiers.

## 5. Touchscreen Ride Flow - Complete

- Make the helper the verified default Android Home app.
- Add an Apps button to the ride overlay.
- Allow one-tap return to the last media app and a clear return to the original
  bike app.
- Make opening and closing apps predictable while a ride remains active.
- Keep all controls at least 48 dp, prevent overlap, and verify landscape layout
  at the bike's effective density.
- Handle app-not-installed, app-crashed, stale-session, and sensor-disconnected
  states without blocking the ride UI.

Exit criteria: a rider can start a ride, choose media, switch apps, pause/resume,
and end/save using only the bike touchscreen.

## 6. Configuration Backup and Restore - Complete

- Define a versioned JSON schema for project-owned state.
- Export app order, hidden apps, desired installed services, launcher settings,
  and setup version to tablet-accessible storage.
- Add host commands to pull an exported configuration and push/import one.
- Add an idempotent reinstall path that applies the config after deployment.
- Validate malformed files, unknown schema versions, and missing apps safely.
- Document that passwords, cookies, DRM keys, and protected service tokens are
  never included.

Exit criteria: a fresh installation can restore the user's launcher setup from
a configuration file, with explicit prompts to reinstall or sign in where app
data was wiped.

## 7. Utilities and Optional Receiving - Complete With Documented Limits

- Install official Zoom, ChatGPT, Audible, Kindle, Termux, and Kodi packages.
- Run Codex CLI in Termux as an explicitly unsupported Android experiment.
- Install official uBlock Origin in Firefox; reject higher-risk YouTube clients.
- Verify Kodi UPnP/DLNA discovery and document modern AirPlay/DRM limits.
- Leave AirScreen's installed-app data collection consent to the device owner.

Exit criteria: every optional component has provenance recorded, privacy and
compatibility limits are visible, and none is required by the core ride flow.

## 8. Sensor-Driven Games - Complete

- Add Cadence Drag with persistent engine, grip, and aero upgrades.
- Add Cadence Flyer, Resistance Ridge, Power Reactor, Rhythm Runner, and
  Orbital Courier.
- Use live direct telemetry and visibly label simulated input when sensors are
  stale.
- Generate and package a local sprite sheet without a runtime network need.
- Persist scores, credits, and upgrades in SARO configuration exports.

## 9. Reliability Verification - Substantially Complete

- Capture active-pedaling telemetry with nonzero cadence, watts, and resistance.
- Run repeated ride start, pause/resume, prompt, end/save, and overlay-drag tests.
- Force-stop and relaunch every supported streaming app.
- Cold-boot the bike and verify Home selection, accessibility, direct sensor
  reconnect, app ordering, and sign-in persistence.
- Test in-place helper and streaming APK updates without clearing data.
- Run a long-duration ride with multiple app switches and no host connection.
- Record failures and compatibility limits instead of masking them.
- Verify Zoom camera/microphone access and ChatGPT voice/authentication behavior.
- Diagnose the reported Max freeze from live logs and test protected playback
  where account state permits.
- Reboot-test optional utilities, Kodi discovery, Firefox extensions, and game
  progress along with the core ride flow.

Completed evidence includes a 2:47 ride, cold boot during an active session,
Home/accessibility/overlay recovery, Hulu and Kindle session persistence,
pause/resume, drag, guarded end, fresh tablet-only ride start, game progress,
Kodi discovery, Codex, uBlock Origin, Aurora profile persistence, deterministic
Home navigation, automatic subscription-banner dismissal, and an owner-pedaled
test with simultaneous nonzero cadence, watts, and resistance. SARO 0.5.2 also
passed a controlled Accessibility disable/open-settings/restore cycle and
returned to fresh stationary telemetry without a reboot. Its one-tap start,
Hulu overlay, app drawer, Home return, guarded end, and touchscreen
configuration restore also passed as a single regression. Hulu protected
playback also passed with an active DRM session, hardware decoder, overlay
controls, and exact player-task restoration. Netflix protected playback passed
with active audio, three DRM sessions, a hardware decoder, and the same resumed
player through overlay-drawer open/close. Prime's Android TV package failed at
playback with an HDMI-connection error; SARO 0.5.1 now migrates its saved slot
to the official touch-native mobile package. The TV APK was removed after the
mobile migration and pruned recovery bundle passed. After owner sign-in, mobile
Prime passed protected playback with a secure decoder, DRM session, advancing
audio, uninterrupted overlay controls, and login/playback persistence after a
full reboot. Provider-authorized DRM playback for the remaining services
remains. After owner-completed consent and sign-in, YouTube 5.30.320
public playback passed with its hardware decoder and audio active while SARO's
ride controls and overlay app drawer remained fully functional.

Disney+ subsequently passed owner-authenticated protected playback with active
DRM, secure hardware AVC decoding, advancing audio, the complete ride strip,
overlay app switching, ride pause/resume, guarded End, and exact configuration
restoration.

Apple TV 2.5.0 subsequently passed entitled protected playback with media state
`PLAYING`, three DRM sessions, a secure hardware AVC decoder, advancing audio,
and uninterrupted SARO drawer/ride controls. Its authenticated catalog and
protected playback survived a full reboot; a brief post-boot `BUFFERING` state
resolved normally without intervention.

Max's Android TV package reached a signed-in catalog but failed at player
initialization even with SARO idle. Official mobile Max 7.8.1.2 then passed
protected playback, overlay controls, package-state migration, and signed-in
catalog persistence after a full reboot. SARO 0.5.3 and the host compatibility
tool now prefer the mobile package while retaining a tested TV-only fallback.

The finalized 0.5.2/pruned device state also passed a full Android reboot. Android
autonomously resumed SARO Home with Accessibility, fresh direct telemetry,
byte-identical configuration, and retained Hulu login state. A post-boot
tablet-only ride started over Hulu, exercised pause and guarded End, removed its
overlay, and restored the prior summary using SARO's touchscreen Import action.

The exact SARO 0.6.2 build also passed an in-place default-off update, complete
ride-strip coexistence over MGM+, guarded End, byte-identical configuration
restore, and a kernel-boot-ID-confirmed reboot. SARO returned as Home with fresh
direct telemetry and only its original-app-scoped ride service enabled; the optional
provider remote stayed disabled after a delayed check.

Exit criteria: core tablet-only flows pass from cold boot and during a long ride;
Tubi and Pluto remain explicitly compatibility-limited unless new evidence
changes their status.

## 10. Security and Independent Review - Complete

- Inventory every downloaded or installed third-party package with version,
  source, hashes, signing identity, permissions, and known security concerns.
- Review package provenance and known-malware reports without treating a clean
  hash lookup as proof of safety.
- Run one independent package-by-package architecture/code review.
- Run a second independent critique of the first review and proposed fixes.
- Apply accepted fixes and preserve both review reports in the repository.

## 11. Release - Complete

- Update all public documentation from final test results.
- Confirm the strict neutral-layout screenshot manifest and every link resolve.
- Run repository and APK identifier scans.
- Run build, install, and smoke-test commands from a clean clone.
- Review staged files so SDKs, APK downloads, secrets, and device data are absent.
- Commit and push the verified source, patches, allowlisted layouts, and documentation.

Package-by-package provenance review, the first independent architecture review,
and an independent critique are retained under `docs/`. Accepted reliability,
signing, recovery, accessibility, sensor, and game-loop fixes are implemented.
The prior release milestone passed the complete release gate from a clean clone
and was pushed to `origin/main`. Protected playback, camera/microphone, and voice
checks remain reliability validation, not unshipped release work.

## 12. Zwift Sensor Bridge - Android Layer Complete

- Convert direct Affernet watts to the standard BLE Cycling Power Service.
- Convert direct cadence to crank-revolution CSC notifications.
- Keep the bridge in SARO's persistent accessibility runtime while Zwift is
  foreground.
- Add an explicit saved touchscreen toggle and schema-v2 backup field.
- Preserve and restore Bluetooth ownership across normal and abrupt process
  lifecycle changes.
- Verify service registration, advertising, clean shutdown, Bluetooth restore,
  owner-on preservation, active package update, cold-reboot persistence,
  payload unit tests, and configuration export on the live RB1VO bike.
- Require Zwift Companion on a phone because the tablet cannot scan its own BLE
  advertisement.

Remaining validation: subscribe from a real phone, confirm nonzero power and
cadence in Zwift, and confirm avatar movement. The Android peripheral layer is
complete; the end-to-end Zwift path is not yet certified and was explicitly
shelved by the owner on 2026-08-11.

## 13. D-Pad TV Provider Remote - Superseded by Mobile Variants

- Diagnose touch-unresponsive Peacock, Paramount+, and MGM+ Android TV builds.
- Provide an on-bike draggable, collapsible D-pad with Back, OK, and SARO Home.
- Keep the capability in a separate, owner-enabled accessibility service rather
  than broadening the gesture-capable bike ride service.
- Limit provider events to the three exact packages, disable gestures, validate
  the top foreground application window on every command, and bind one-shot OK
  actions to an exact target and window.
- Require a second confirmation for Peacock's opaque virtual OK action.
- Bound accessibility traversal work and clear pending actions on every window,
  package, overlay, failure, interrupt, and lifecycle transition.
- Persist only the overlay position in schema-v2 configuration.
- Run two independent reviews and enforce source plus compiled-APK restrictions
  in the release gate.
- Install and reboot-test exact 0.6.2 with the optional service disabled by
  default, preserving core rides, Home, sensors, and byte-identical config.
- Create and verify a current recovery checkpoint.

Implementation, review, exact default-off installation, core-ride regression,
reboot, and recovery gates are complete. SARO 0.6.4 keeps this as a flavor-gated
legacy fallback, but the canonical bike uses touch-native mobile variants and
hides the remote setting.

## 14. Paramount+ Touch-Native Migration - Complete for Login Readiness

- Audit and install official mobile `com.cbs.app` 16.18.0 with its required
  ARM64 and density splits.
- Verify touch focus and on-screen keyboard input without entering credentials.
- Make mobile canonical while retaining only Paramount+ TV as a fallback.
- Migrate order, hidden/desired/installed state, and last-app configuration.
- Preserve the optional TV Remote's default-off and TV-only scope.
- Run an independent review, remediate findings, verify exact recovery bytes,
  and pass the complete release gate.

Package provenance, touch focus, keyboard invocation, SARO tile launch,
configuration migration, default-off in-place update, independent review, and
offline recovery verification pass. Direct touch, native Sign In, credential
focus, and keyboard behavior satisfy the owner's current acceptance criterion.
Paid playback is not tested because no active subscription is available.

## 15. Peacock and MGM+ Touch Migration - Complete for Current Scope

- Acquire exact Android 11 ARM64 phone delivery sets from Google Play without
  committing proprietary APKs or authentication material.
- Verify package, version, selected splits, signatures, Source Stamps,
  launcher categories, and declared permissions.
- Install in place without clearing data; support Peacock's same-signer lower
  mobile versionCode in recovery.
- Verify SARO tile launch, touch navigation, sign-in field focus, and keyboard
  without entering credentials or selecting an offer.
- Gate the optional TV Remote before accessibility-root retrieval, hide it for
  mobile variants, and suppress its settings row when unnecessary.
- Run independent review, unit/host/live checks, create an exact recovery
  checkpoint, and pass release/security gates.

Acquisition, signature/provenance checks, touch/keyboard tests, reviewed flavor
gating, live install, active-ride strip coexistence, in-ride switching across
all three providers, guarded ride end, and offline recovery verification pass.
Peacock authentication, protected playback, and rebooted login/profile
persistence also pass. MGM+ direct touch, native Log In, field focus, and
keyboard satisfy the current acceptance criterion; paid playback is not tested
without an active subscription.

## 16. Content-First In-Place Ride Start - Complete

- Replace the Peacock content-first Home/PiP path that produced a focus-event
  ANR with a provider-neutral in-place ride action.
- Arm the action only after an explicit SARO media launch and only when no local
  ride is active.
- Keep it non-focusable, draggable, dismissible, time-bounded, and independent
  of provider accessibility-node inspection.
- Persist its position through schema-v2 export/import.
- Verify touch-through, secure playback continuity, complete stats, guarded
  End, and a real reboot with signed-in Peacock.

SARO 0.6.5 passes all implementation and live acceptance checks. The control
starts the direct-sensor ride without switching tasks or invoking provider PiP.
Two independent reviews are recorded and remediated. The final installed APK
is byte-identical to the host build, its behavioral policy tests pass, and the
replacement 20-package/45-row recovery checkpoint verifies offline.

## 17. Public Copy and Path Neutralization - Complete

- Keep one plain-language compatibility sentence at the start of the README.
- Remove unnecessary brand references from documentation and tablet UI copy.
- Rename public setup paths, commands, profiles, and new configuration exports
  to SARO/device-neutral names.
- Preserve Android package IDs, signer continuity, device fingerprints, deep
  links, and legacy import aliases where changing them would break an update or
  recovery.
- Enforce the boundary in the release gate and test old-backup restore.

SARO 0.6.6 implements this migration. Fresh exports use `saro-setup.json` and
`subscriptionPromptAutomation`; legacy files and keys remain import-only.

## 18. Privacy Audit and History-Free Publication - Complete

- Remove historical account, catalog, telemetry, authentication, and transient
  diagnostic screenshots from the publishable tree.
- Retain only six neutral interface layouts, pinned by MIME type and SHA-256 and
  validated through strict PNG parsing and decompression.
- Keep installation-specific names, paths, serials, signer fingerprints,
  helper hashes, configuration hashes, and checkpoint hashes in ignored private
  state rather than tracked audit code or documentation.
- Require a mode-`0600` private denylist and pinned Gitleaks scan in the release
  gate and extracted snapshot audit.
- Export only committed `HEAD` with `git archive`, reject dirty trees and
  in-repository destinations, and verify that ignored files and `.git` metadata
  cannot enter the result.
- Initialize the exported directory as a brand-new repository with one
  parentless root commit. Never publish, fork, mirror, shallow-clone, or reuse
  the private engineering repository's history.

The current publishable tree passes the complete release gate, 25 adversarial
publication tests, a private-identifier denylist, and Gitleaks. Reachable Git
history has no scanner findings but remains private because it contains personal
metadata and historical device evidence.

## Acceptance Criteria

- No computer is required after setup for normal ride and media workflows.
- Direct telemetry remains visible over full-screen content without PiP.
- The UI is responsive, stable, and usable by touch during a ride.
- Normal reboot and same-signature in-place updates preserve project and
  streaming app data where Android permits it.
- Wipe/uninstall limitations and credential boundaries are documented honestly.
- Configuration export/import restores project-owned setup state.
- Public source and release artifacts contain no personal identifiers or
  workstation-specific paths.
- The public repository starts from one new root commit and contains no private
  repository object database or history.
