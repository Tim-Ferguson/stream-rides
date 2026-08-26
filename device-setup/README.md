# SARO Tablet Setup and Control

This directory contains the Android helper and host-side ADB tooling for the
standalone SARO Local Ride overlay.

## Runtime Design

The current helper binds directly to the Affernet sensor service. It
receives cadence, power, and resistance through Binder callbacks and renders a
custom overlay containing:

- TIME
- EST MPH
- EST MI
- CAD
- WATTS
- RES
- KJ
- EST CAL

The overlay is draggable and includes touch controls for pause/resume and a
guarded end action. Direct telemetry does not require original-app PiP, screen
pixel reading, the legacy window agent, or a computer connection.

The helper also supplies a tablet hub for starting or returning to a ride,
opening installed media apps, opening the original bike app, launching the app
store, reordering or hiding services, and launching six bike-sensor games.

SARO 0.6.5 uses touch-native Peacock, Paramount+, and MGM+ phone/tablet builds.
It retains a separate, optional accessibility service only for compatible
legacy Android TV variants. The service is disabled by default, package/event
scoped, has no gesture capability, and resolves a Leanback launcher before it
retrieves a provider window root. Manage Apps hides the setting when no TV
variant is installed.

SARO 0.6.6 uses neutral public paths, commands, UI labels, and configuration
exports. Existing package IDs, signer continuity, and legacy configuration
imports remain intact so the update preserves Android-managed app state.

An optional Zwift Companion bridge converts the same direct watts and cadence
into standard BLE Cycling Power and CSC notifications. A phone running Zwift
Companion is required to relay them back to Zwift over the local network; see
[`../docs/zwift.md`](../docs/zwift.md).

## Requirements

- An RB1VO exercise bike on the Android generation tested by this project.
- USB debugging and ADB access for initial installation and recovery.
- Android platform-tools.
- Android SDK platform/build-tools compatible with `ride-starter/build.sh`.
- A compatible JDK, or `JAVA_HOME` set to one.

Local SDKs, JDKs, downloaded APKs, and signing secrets must remain outside Git.
The scripts should derive repository paths from their own location or accept
standard environment overrides.

## Build

From this directory:

```sh
cd ride-starter
./build.sh
```

The generated helper APK is written under `ride-starter/build/` and should not
be committed.

## Initial Deployment

Connect the bike. Before any mutating ADB operation, read
[`../docs/recovery.md`](../docs/recovery.md), create the private read-only
baseline it describes, and preserve the host-side prior-Home record outside
Git. Then run:

```sh
./saro-control check
BACKUP_ROOT="$HOME/SARO-Backups/$(date +%Y%m%d-%H%M%S)"
./saro-control capture-original-state "$BACKUP_ROOT/prechange-baseline"
./saro-control verify-original-state "$BACKUP_ROOT/prechange-baseline"
./saro-control bootstrap-tablet
./saro-control backup "$BACKUP_ROOT/post-bootstrap-checkpoint" --with-apks
./saro-control verify-backup "$BACKUP_ROOT/post-bootstrap-checkpoint"
./saro-control audit-live "$BACKUP_ROOT/post-bootstrap-checkpoint"
```

This is a setup command, not a runtime dependency. The neutral helper deployment,
Home role, accessibility binding, and cold-reboot recovery are verified on the
tested RB1VO bike. `bootstrap-tablet` validates and records the prior Home before
its first device mutation and stops if no safe rollback target is available. A
different hardware generation requires a separate audit. Create the full
checkpoint before any other app install or update; after setup, create a second
checkpoint under a different name for the desired configured state.

## Tablet-Only Workflow

Once installation, accessibility, and Home selection are configured:

1. Tap the center **P** in the bottom system navigation bar. SARO is the
   configured Android Home app, so this opens the ride hub even from the
   original login screen.
2. Open a streaming app and select content.
3. Tap the center **P** again to return to SARO.
4. Tap Start Ride.
5. Use the draggable stats overlay while content remains full-screen.
6. Pause/resume or end the ride from the overlay or hub.

The overlay `APPS` button opens the hub without ending the ride. `RETURN TO
VIDEO` reopens the most recently selected catalog app. **BIKE HOME** opens
the original interface; the center **P** returns to SARO afterward.

If a supported Android TV flavor is installed, open **Manage apps**, tap TV
Remote **Settings**, and enable **SARO TV Remote** in Android Accessibility. Its
draggable overlay appears only over supported provider windows and includes
directions, OK, Back, collapse, and SARO Home. Peacock's opaque virtual OK
requires two taps. Leave this service off when it is not needed; recovery
enables only the original-app-scoped ride service.

For the experimental Zwift path, turn **Zwift Companion sensor bridge** on in
**Manage apps**, wait for `ADVERTISING`, and use Zwift's phone/Companion pairing
route. The bridge preference is saved; turn it off when unused.

## Useful Host Commands

These commands exist in `saro-control` today:

```sh
./saro-control check
./saro-control status
./saro-control bootstrap-tablet
./saro-control deploy-helper
./saro-control capture-original-state "$HOME/SARO-Backups/prechange-baseline"
./saro-control verify-original-state "$HOME/SARO-Backups/prechange-baseline"
./saro-control export-config ../saro-setup.json
./saro-control import-config ../saro-setup.json
./saro-control backup ../saro-backup --with-apks
./saro-control verify-backup ../saro-backup
./saro-control audit-live ../saro-backup
./saro-control restore-backup ../saro-backup
./saro-control record-home-rollback
./saro-control restore-home
./saro-control safe-uninstall
./saro-control just-ride
./saro-control overlay-current
./saro-control close-overlay
./saro-control screenshot
```

Legacy split-screen and PiP commands are still available for browser, YouTube,
Netflix, Disney+, Max, Hulu, and Apple TV. Run `./saro-control help` for the
full list. They are debugging and fallback tools, not part of the target direct
overlay workflow.

## Components

### `ride-starter/`

Android source for:

- The direct Affernet sensor client.
- Ride-session state and derived metrics.
- The draggable stats overlay.
- Local ride launch, pause/resume, guarded ending, and exact subscription-banner
  automation when the owner enables it.
- The streaming hub and persistent order/hide preferences.
- Original bike app and app-store launch actions.
- Android Home/launcher integration.
- The optional BLE Cycling Power/CSC bridge for Zwift Companion.
- The separately owner-enabled, provider-scoped TV navigation remote.
- Six direct-sensor games with persistent progress and generated local sprites.

### `window-agent/`

A quarantined `app_process` helper used by the earlier pinned-window approach.
Its fixed loopback command set requires a per-launch capability, serializes
commands, and exits after an idle timeout. Starting it also requires the
explicit research command and shell-risk acknowledgement. It is not part of the
direct sensor runtime.

### `saro-control`

Host-side setup and diagnostics. It can build/deploy the helper, inspect tablet
state, launch legacy layouts, capture screenshots, round-trip the SARO config,
and build or restore a recovery bundle. Optional APK backup does not bypass
provider authentication or preserve app-private credentials after a wipe.
APK bundles include a hardware record, a validated prior-Home rollback target, a top-level manifest,
`apks/manifest.tsv` with package versions, signing identity, and per-split
SHA-256 hashes, plus a size/hash manifest for any OBB expansion files. Backup
publication is atomic. Restore verifies all bytes and device compatibility
before installation, records resumable JSONL progress, and excludes
`com.peloton.*` and `com.onepeloton.*` packages. Use `verify-backup` for an
offline, non-installing check. Use `audit-live` to require an exact match for
checkpoint packages, APK hashes and versions, checkpoint-owned OBB paths and
hashes, configuration, SARO Home, a live-valid rollback Home, physical display
state, and SARO accessibility policy. Unrelated accessibility services are
preserved; SARO's optional TV Remote must be absent.

## Streaming App Status

| Service | Package | Status |
| --- | --- | --- |
| Netflix | `com.netflix.mediaclient` | Launch and signed-in profile state verified after same-signer reinstall |
| Hulu | `com.hulu.plus` | Launch and sign-in state observed |
| Disney+ | `com.disney.disneyplus` | Signed-in protected playback, secure decoding/audio, and SARO overlay controls verified |
| Max | `com.wbd.stream` | Mobile 7.8.1.2 protected playback and rebooted login persistence verified; TV 7.8.1.4 playback crashes |
| Prime Video | `com.amazon.avod.thirdpartyclient` | Mobile protected playback, overlay controls, and rebooted login/playback persistence verified; TV package playback fails its HDMI check. |
| Apple TV | `com.apple.atve.androidtv.appletv` | Protected playback, overlay controls, and rebooted login/playback persistence verified |
| Peacock | `com.peacocktv.peacockandroid` | Mobile 7.8.10 direct touch, signed-in protected playback, secure AVC decoding, complete ride strip, and login/profile persistence across a full reboot verified; dismissible missing-Play-services advisory remains |
| Paramount+ | `com.cbs.app` (`com.cbs.ott` fallback) | Mobile direct touch, native Sign In, empty-field focus, keyboard, in-ride drawer launch, and complete strip coexistence verified; paid playback not tested without an active subscription |
| MGM+ | `com.epix.epix.now` | Mobile 237.1 direct touch, native Log In, empty-field focus, keyboard, and SARO-control coexistence verified after a transient Play Store warning; paid playback not tested without an active subscription |
| YouTube | `com.google.android.youtube.tv` | Launch verified |
| YouTube TV | `com.google.android.youtube.tvunplugged` | Historical launch verified; account/playback testing skipped and APK omitted from current checkpoint |
| Firefox | `org.mozilla.firefox` | Launch verified |
| Audible | `com.audible.application` | Official Play package; launch verified |
| Kindle | `com.amazon.kindle` | Official Play package; launch and existing Amazon session verified |
| Zoom | `us.zoom.videomeetings` | Official package; launch verified, currently APK-dormant for storage, hardware meeting test pending |
| ChatGPT | `com.openai.chatgpt` | Official package; onboarding verified, auth/voice pending |
| Termux | `com.termux` | Official GitHub package; Codex CLI experiment works |
| Kodi | `org.xbmc.kodi` | Official 21.3 ARM64 package; UPnP discovery verified |
| Zwift | `com.zwift.zwiftgame` | Official 1.119.0 package; direct launch and BLE GATT registration verified, real-phone Companion relay pending, 2 GB RAM below minimum |
| AirScreen | `com.ionitech.airscreen` | APK-dormant for storage; owner consent required for disclosed data collection |
| Tubi | `com.tubitv` | Compatibility-limited: requires Google Play Services |
| Pluto TV | `tv.pluto.android` | Compatibility-limited: remains on splash screen |
| Plex | `com.plexapp.android` | Launcher support; verification pending |
| Crunchyroll | `com.crunchyroll.crunchyroid` | Launcher support; verification pending |
| ESPN | `com.espn.score_center` | Launcher support; verification pending |
| Sling | `com.sling` | Launcher support; verification pending |

Launch verification means the app reached native UI. It is not a guarantee of
all-account compatibility or DRM playback. Android TV applications may expose
remote-oriented controls that are less comfortable on a touchscreen.

## App Store

The tested setup uses a per-installation locally signed Aurora Store 4.8.4 compatibility build
from pinned source and the tracked patch. The patch addresses duplicate list
keys and profile de-duplication on this device. Use an
ARM64 phone spoof such as `OnePlus8Pro_EEA` for phone-only apps including
Audible, Kindle, and ChatGPT. Use
`../tools/aurora-profiles/saro-rb1vo-android-tv-arm64.properties` only for Android
TV packages.

Do not commit a downloaded or rebuilt Aurora APK or its signing key. The
reproducible patch, fail-closed signing changes, and build instructions are under
`../tools/aurora-store/`.

## Data and Sign-In Persistence

Android should preserve each app's private data across a normal reboot and a
same-signature in-place APK update. That usually preserves streaming sign-in
state. Deployment tooling must avoid clearing package data or uninstalling an
app merely to update it.

Sign-in state is lost when app data is cleared, the app is uninstalled, the
tablet is factory-reset, or a manufacturer update wipes user data. Streaming apps keep
credentials and DRM tokens in protected private storage; this project does not
and should not export them.

The ADB-only `pm uninstall -k` maintenance path is a narrow exception on this
firmware: it removes code while retaining private data for an exact same-signer
reinstall. SARO verified it with a disposable marker and restored Kindle and
Netflix state through it. This state is not portable and is destroyed by a
normal uninstall or data wipe.

The versioned configuration file backs up only project-owned settings,
including launcher order, hidden apps, desired services, overlay position,
setup metadata, and game progress. It makes reinstalling easier but cannot
restore protected streaming sessions.

## Known Limits

- This setup does not reactivate a cancelled manufacturer subscription. SARO Local
  Ride is independent of manufacturer workouts, does not control manufacturer workout
  state, and does not create entries in manufacturer workout history.
- Speed, distance, and calories are labeled as estimates. Cadence, watts, and
  resistance come from the direct sensor callback.
- The cancelled-subscription banner is dismissed only when owner automation is
  enabled and an exact known message is visible in the original app package. The
  final close action uses a measured target and remains best effort.
- Streaming services can change package requirements, DRM support, or Google
  Play Services dependencies without notice.
- Tubi and Pluto TV are currently compatibility-limited and must not be listed
  as working playback services.
- Active nonzero cadence, watts, and resistance passed an owner-pedaled test,
  including zero reset, pause/resume, guarded end, and summary persistence; see
  [`../docs/direct-sensor-live-test.md`](../docs/direct-sensor-live-test.md).
  Cold-boot persistence, neutral-package migration, and configuration
  round-trip are also complete.
- Kodi UPnP/DLNA discovery works locally. Modern AirPlay discovery, Cast DRM,
  and protected screen mirroring are not verified.
- Codex CLI in Termux uses an unsupported Linux ARM64 package alias. The native
  ChatGPT app or Firefox remains the supported fallback.
- Zwift launches, but the bike's 2 GB RAM is below Zwift's official 3 GB
  minimum and one idle ANR was observed. SARO's complete Cycling Power/CSC GATT
  table, both Bluetooth ownership baselines, active package update, and cold
  reboot are live-verified. The same controller cannot discover its own
  advertisement, so a real phone running Zwift Companion is required and its
  end-to-end subscription remains pending and explicitly shelved.
- Zwift 1.119.0 checks for its 1.76 GB installer OBB even after successful
  expansion. SARO launches its exported main activity directly after the OBB is
  removed; a future app update can change that private implementation detail.
- Android `am force-stop` marks an accessibility service stopped until the owner
  re-enables it or the package is updated. This diagnostic is not equivalent to
  ordinary process death or reboot.

## Recovery State

Configuration export/import and optional APK recovery bundles are implemented.
Keep the repository and local build prerequisites as well. Do not expect APK
backups to preserve protected account sessions after a wipe.

The current hardened local bundle contains 25 third-party packages across 61
APK/base/split files, one hash-pinned Zwift OBB, and the separately archived
SARO helper. Restore revalidates installed APK and remote OBB hashes, cleans
known staging names, checks capacity, and atomically publishes OBBs. The bundle
is intentionally Git-ignored. Termux's private Node/Codex
installation is outside an APK backup; use
`../tools/termux/install-codex.sh` after a destructive wipe. See
`../docs/recovery.md` and `../docs/storage.md` for the exact trust, storage, and
approval boundaries.

The target recovery flow is:

```text
build helper -> install/update without clearing data -> import versioned config
-> restore Home/accessibility settings -> reinstall missing apps -> sign in only
where Android app data was lost
```
