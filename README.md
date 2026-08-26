# SARO - Stream Anything Ride Overlay

Stream more freely on the exercise-bike tablet you own.

## Use At Your Own Risk

This is experimental, unsupported device-modification work. Commands and APKs
in this repository may break device software, erase data, prevent normal
startup, or permanently brick the tablet. Proceed only if you understand and
accept that risk, can recover Android devices with ADB, and are comfortable
losing access to the bike.

This repository is not intended to be a completely reproducible, one-command
solution. It is a hardware-specific guide and working reference for an LLM
coding agent to inspect and adapt to an owner's bike. Device state, firmware,
app versions, subscriptions, DRM, and hardware revisions vary. Every
operator is solely responsible for reviewing and authorizing changes to their
own device. The project and its contributors provide no warranty and accept no
liability for damage, lost data, account issues, or loss of service.

Installing or using this software may violate the device manufacturer's Terms
of Service, warranty conditions, acceptable-use rules, or the terms of third-
party applications and services. The operator alone must review those terms and
decide whether to proceed. The project makes no claim that any modification is
authorized, lawful, safe, reversible, or suitable for a particular device,
account, location, or purpose, and assumes no responsibility for the operator's
use or its consequences.

## Back Up Before Making Any Device Change

Before installing or uninstalling a package, changing package data, assigning
Home, changing Accessibility, writing Android settings, or changing display or
window configuration, read [`docs/recovery.md`](docs/recovery.md). Connect
exactly one authorized ADB target, then create and verify a private pre-change
baseline from `device-setup/`:

```sh
./saro-control check
BACKUP_ROOT="$HOME/SARO-Backups/$(date +%Y%m%d-%H%M%S)"
./saro-control capture-original-state "$BACKUP_ROOT/prechange-baseline"
./saro-control verify-original-state "$BACKUP_ROOT/prechange-baseline"
```

Keep the complete baseline and the generated `.state/previous-home-*` record in
private, owner-controlled storage outside this repository. Stop if the prior
Home component is missing, invalid, or ambiguous.

Ordinary non-root ADB cannot create a complete restorable device image. This
baseline cannot preserve protected app data, account sessions, DRM material,
manufacturer firmware, or recovery from every possible failure. It is a
verified record for careful rollback, not a factory-image backup.

Immediately after the minimal bootstrap, and before installing or updating any
other package, create and verify the first full rollback checkpoint:

```sh
./saro-control bootstrap-tablet
./saro-control backup "$BACKUP_ROOT/post-bootstrap-checkpoint" --with-apks
./saro-control verify-backup "$BACKUP_ROOT/post-bootstrap-checkpoint"
./saro-control audit-live "$BACKUP_ROOT/post-bootstrap-checkpoint"
```

After configuring SARO, create a second, separately named checkpoint for the
desired setup. Neither checkpoint can recover protected app data, credentials,
DRM material, or manufacturer firmware.

SARO is an experimental tablet-side launcher and ride-stat overlay for the
tested RB1VO exercise bike.
It keeps streaming content full-screen while showing a SARO Local Ride in a
compact, draggable strip. Local rides are independent of manufacturer workouts
and do not appear in the manufacturer's workout history.

After the one-time ADB installation, the current runtime reads the bike sensors
directly and runs on the tablet. A computer is not part of the normal ride or
streaming workflow.

This is an independent community project. It does not bypass a paid
manufacturer membership, streaming subscription, DRM, or service authentication.
It is not affiliated with, endorsed by, or sponsored by the device manufacturer
or any streaming provider.

## What Works

- Direct telemetry from the Affernet sensor service without screen scraping or
  an original-app PiP window.
- Home-screen health for the overlay and sensor telemetry, with tablet-side
  Accessibility and sensor reconnect actions.
- A draggable overlay with time, speed, distance, cadence, output, resistance,
  energy, and calories.
- An in-video app drawer that uses a second accessibility overlay, respects the
  saved app order and hidden state, and does not background the current player.
- A draggable, dismissible `START RIDE` control after media launch, allowing a
  local ride to begin without opening PiP or switching the provider task.
- Large touchscreen controls for pause/resume and guarded ride ending.
- A tablet launcher for installed streaming apps, the original bike app, and an
  app store.
- Persistent app ordering and hide/show preferences in the launcher.
- Mid-ride app switching from the overlay and one-tap return to the last video.
- Touch-native Peacock, Paramount+, and MGM+ phone/tablet builds, plus a
  flavor-gated TV remote retained only for compatible legacy Android TV builds.
- Six sensor-driven games using live cadence, watts, and resistance, with an
  explicitly labeled demo-input fallback when sensor telemetry is stale.
- Versioned configuration export/import and optional local APK/OBB recovery
  bundles.
- Recovery bundles record non-vendor package versions and per-APK SHA-256
  hashes, verify those hashes before restore, and include non-secret hardware
  metadata.
- A patched Aurora Store build that opens reliably on this Android build and
  supports both ARM64 phone and Android TV spoof profiles.
- Tablet utilities including Zoom, ChatGPT, Audible, Kindle, Termux with an
  experimental Codex CLI install, Firefox with uBlock Origin, and Kodi.
- Zwift 1.119.0 launches from SARO with the ride overlay after its one-time
  resource expansion, without retaining the 1.76 GB installer OBB on the bike.
- An experimental, owner-enabled Zwift Companion bridge advertises direct watts
  and cadence through standard BLE Cycling Power and CSC services.
- Tablet-side ride start and overlay operation after initial installation.
- Owner-enabled, best-effort dismissal of the exact cancelled-subscription
  banner through an original-app-scoped accessibility service.

## Current Status

| Area | Status | Notes |
| --- | --- | --- |
| Direct sensor connection | Physically verified | Affernet Binder callbacks produced simultaneous live cadence, watts, and resistance during an owner-pedaled test; zero reset and changing resistance also passed. SARO 0.5.2 adds live readiness and tablet-side recovery controls. |
| Standalone stats overlay | Working | Does not require PiP or a connected computer. |
| Ride controls | Working | Pause/resume, drag, guarded end, long-ride, and tablet-only restart flows pass after a cold boot. |
| Streaming launcher | Working | Installed apps can be opened, reordered, or hidden. |
| App store | Working | Per-installation locally signed Aurora Store compatibility build, launch, profile selection, and cold-boot persistence are verified. |
| Custom Home role | Working | Home selection survives cold boot; Games uses an isolated task so Home always returns to the hub. |
| App switching from the overlay | Working | `APPS` opens an ordered, scrollable accessibility-overlay drawer without backgrounding the current player. One 25-minute local ride switched MGM+ to Paramount+ to Peacock entirely through SARO controls, preserved the full strip and live resistance, returned Home, and ended through the guarded dialog. |
| Provider touch navigation | Working | Official mobile Peacock 7.8.10, Paramount+ 16.18.0, and MGM+ 237.1 accept direct touch, reach native sign-in fields and the on-screen keyboard, and coexist with SARO controls. Peacock also passes signed-in protected playback and reboot persistence. Paramount+ and MGM+ paid playback is not tested because no active subscriptions were available. SARO 0.6.5 hides its optional TV Remote unless an installed package exposes a Leanback launcher. |
| Configuration export/import | Working | Schema v2 round-trip, atomic host transfer, v1 import compatibility, and correlated status acknowledgements are verified. |
| Sensor-driven games | Working | Six modes, persistent scores/upgrades, generated sprites, and live direct-sensor input are deployed. |
| Audible and Kindle | Launch verified | Both reach native UI; Kindle reused the existing Amazon session. |
| Zwift | Experimental sensor bridge, resource-limited | SARO opens Zwift after expansion and advertises direct watts/cadence as standard BLE services. Registration, Bluetooth ownership, in-place update, and cold-boot persistence pass live. A phone running Zwift Companion is required as the relay; real-phone subscription/avatar movement remains unverified. The bike's 2 GB RAM is below Zwift's 3 GB minimum. |
| ChatGPT and Codex | Experimental | Native ChatGPT reaches onboarding; Codex CLI 0.147.0 reaches official sign-in in Termux through an unsupported Android workaround. |
| Local receiving | Partial | Kodi UPnP/DLNA discovery is verified; modern AirPlay and DRM casting are not. AirScreen remains explicit opt-in due its privacy notice. |
| Reboot recovery | Working | SARO 0.5.2 restored Home, accessibility, direct telemetry, configuration, and signed-in Hulu; 0.5.3 preserved signed-in Max; and exact 0.6.2 restored Home and fresh telemetry while keeping its optional TV Remote disabled. SARO 0.6.5 restored Home, its original-app-scoped service, fresh sensors, the dragged media ride control, and signed-in Peacock protected playback after another boot-ID-confirmed reboot. See [`docs/reboot-052-live-test.md`](docs/reboot-052-live-test.md). |
| Subscription banner | Working, best effort | The helper recognizes the subscription overlay and taps its measured close target automatically. |
| Package security audit | Complete | The private 20-package/45-file recovery checkpoint and publishable official/upstream inventories have hash, signature, Source Stamp, permission, provenance, and public-incident evidence in [`docs/security/`](docs/security/). Local helper and app-store fingerprints remain private. |
| Public release cleanup | Complete through 0.6.6 | Neutral copy, public paths, command names, owner signing, identifier scans, independent review, touch-native provider launch, exact flavor gating, and recovery compatibility tests pass. Peacock protected playback passes; Paramount+ and MGM+ paid playback is untested without active subscriptions. |

## Streaming Compatibility

"Launch verified" means the native app reached a usable sign-in, profile,
catalog, or playback UI on the bike. It does not guarantee that every account,
DRM mode, or future app update will work.

| Service | Status | Notes |
| --- | --- | --- |
| Hulu | Protected playback verified | A signed-in stream held an active DRM session and hardware decoder with Android media state `PLAYING`; two explicit hub/return cycles restored the exact player task. See [`docs/streaming-live-test.md`](docs/streaming-live-test.md). |
| Disney+ | Protected playback verified | After owner sign-in, protected content reached media state `PLAYING` with active DRM, secure hardware AVC decoding, advancing 48 kHz audio, and the SARO overlay. Drawer, ride pause/resume, guarded End, and an exact configuration restore all passed. |
| Max | Protected playback verified | Official ARM64 mobile package `com.wbd.stream` 7.8.1.2 remained `PLAYING` with advancing position, Widevine sessions, secure MediaTek AVC decoding, active audio, and the SARO overlay. Its signed-in catalog persisted across a full tablet reboot. TV package `com.wbd.hbomax` 7.8.1.4 crashed only when playback initialized and is removed from the current recovery checkpoint. |
| Prime Video | Protected playback verified | Official mobile package `com.amazon.avod.thirdpartyclient` 3.0.466.2047 resumed entitled content with an active DRM session, secure MediaTek AVC decoder, and advancing 48 kHz audio. SARO's drawer, ride pause/resume, and guarded End controls did not interrupt playback, and sign-in plus protected playback survived a full reboot. The Android TV build failed playback with an HDMI-connection error and is omitted from the current recovery checkpoint. |
| Apple TV | Protected playback verified | Official Android TV package 2.5.0 played entitled content with media state `PLAYING`, advancing position/audio, three DRM sessions, and a secure MediaTek AVC decoder. The same activity survived SARO drawer open/close, ride pause/resume, and guarded End. Its signed-in catalog and protected playback persisted across a full reboot. |
| Peacock | Protected playback verified | Official mobile `com.peacocktv.peacockandroid` 7.8.10 retained the owner-selected profile across a full reboot, then played entitled content with media state `PLAYING`, secure MediaTek AVC decoding, active audio, and SARO's complete ride strip. The in-place `START RIDE` control starts a sensor-backed ride without backgrounding Peacock; the advisory Play-services dialog remains dismissible. |
| Paramount+ | Touch and login flow verified | Official mobile `com.cbs.app` 16.18.0 accepts direct touch, reaches native Sign In, focuses an empty email field, opens the on-screen keyboard, and opens from the in-ride drawer with the strip intact. Paid protected playback is not tested because no active subscription was available. The older TV app remains a code-level fallback but is omitted from the current bike checkpoint. |
| MGM+ | Touch and login flow verified | Official mobile `com.epix.epix.now` 237.1 accepts direct touch, opens native Log In, focuses its email field, and coexists with SARO controls after its missing-Play-Store warning. Paid protected playback is not tested because no active subscription was available. |
| YouTube | Public playback and overlay verified | After the owner completed first-run consent and sign-in, Android TV 5.30.320 played public media with an active hardware AVC decoder, stereo audio track, and media state `PLAYING`. SARO pause/resume, the in-ride app drawer, and guarded End all worked without interrupting the player task. |
| YouTube TV | Launch verified, playback skipped | Android TV app opened with the overlay. The owner does not have a YouTube TV sign-in and explicitly skipped account/playback testing, so its APK is omitted from the current bike and recovery checkpoint. |
| Netflix | Protected playback verified | The signed-in app held active audio, three DRM sessions, and a hardware AVC decoder. The same `PlayerActivity` remained resumed while SARO's app drawer opened and closed, and protected capture remained blocked. |
| Firefox | Launch verified | Useful as a fallback for web services. |
| Audible | Launch verified | Official Play package reaches native onboarding. |
| Kindle | Launch verified | Official Play package reaches the library and reused existing Amazon sign-in state. |
| Zoom | Launch verified, dormant | Exact APK and private data are retained locally, but its 809 MB installed footprint is currently omitted from the bike to preserve free space. Camera/microphone meeting validation is pending. |
| ChatGPT | Launch verified, experimental | Official package reaches onboarding; authentication and voice input remain pending. |
| Kodi | Launch verified | Official Kodi 21.3 ARM64 package; UPnP/DLNA discovery is verified. |
| Zwift | Experimental | Launch and BLE peripheral registration are verified. A phone running Zwift Companion must relay the sensor to Zwift; see [`docs/zwift.md`](docs/zwift.md). One idle ANR was observed on this below-minimum-RAM tablet. |
| AirScreen | Dormant, consent pending | Its first run discloses collection and analysis of installed-app data; SARO did not accept this on the owner's behalf, and its APK is currently omitted to preserve space. |
| Tubi | Compatibility-limited | Current official build refuses to run without Google Play Services. |
| Pluto TV | Compatibility-limited | Current Android TV build remains on its splash screen on this device. |
| Plex, Crunchyroll, ESPN, Sling | Launcher support only | Installation and full device verification are pending. |

## Credential Persistence

Streaming credentials live inside each streaming app, not in this project.
A normal tablet reboot or an in-place APK update should preserve that app data
and therefore usually preserve sign-in state. Clearing app data, uninstalling
the app, factory-resetting the tablet, or a manufacturer update that wipes user data
will remove it.

For storage maintenance, Android's host-only `pm uninstall -k` operation can
remove an APK while retaining its private data for a later same-signer
reinstall. That behavior was verified on this exact firmware with a disposable
marker and then with Firefox, Kindle, Netflix, and Audible. It is not a backup,
is not available through normal Settings uninstall, and will not survive a data
wipe.

The project configuration backup contains launcher choices, order, visibility,
installed package choices, ride-overlay and TV-remote positions, and SARO game progress. It will
not export passwords, DRM keys, cookies, or protected streaming authentication
tokens. After a wipe, services may need to be signed in again even when the rest
of the setup is restored.

Cold-boot testing preserved the signed-in Hulu, Max, Prime Video, Apple TV, and
Peacock catalogs plus Kindle's existing Amazon session. Prime, Apple TV, and
Peacock also resumed entitled protected playback after reboot. That is evidence
for this tablet state, not a promise that a provider will never expire or revoke
its own tokens.

In-place SARO 0.5.0 and 0.5.1 updates preserved active rides and foreground
provider tasks. The 0.5.1 migration retained Prime Video's fifth launcher slot
while replacing the incompatible TV package with the mobile package. The TV APK
was later removed after the migration and recovery bundle were verified. The
0.5.2 update preserved launcher state and exposed the transient Accessibility
rebind state directly on Home before returning to fresh telemetry. A subsequent
real reboot autonomously restored SARO Home, fresh telemetry, the exact exported
configuration, and Hulu's signed-in catalog before a one-tap local ride passed.
The 0.5.3 update canonicalized the failed Max TV package to the working mobile
package without changing app order or other launcher choices. A subsequent full
reboot preserved SARO Home, accessibility, fresh direct telemetry, the canonical
configuration, and Max's signed-in mobile catalog.

The exact 0.6.2 update preserved the canonical launcher configuration and core
ride path. A boot-ID-confirmed reboot restored SARO Home and fresh telemetry
with only `SARO Ride Overlay` enabled; the separate `SARO TV Remote` stayed off
by default. Streaming sign-in state remains owned by each provider app.

SARO 0.6.3 canonicalizes the old Paramount+ TV package to official mobile
`com.cbs.app`, while retaining the TV package as an automatic fallback when the
mobile package is absent. The in-place update preserved the same Android
framework process, SARO Home, fresh sensors, volume zero, and exactly the ride
accessibility service. A schema-v2 export migrated order, desired/installed
state, and last-app references to the mobile package.

SARO 0.6.4 installs the phone/tablet delivery variants of Peacock and MGM+
without clearing either package's data. The Peacock update uses Android's
same-signer downgrade support because its mobile flavor has a lower numeric
version code than its TV flavor. Recovery now detects that exact case and adds
`-d` without uninstalling. The TV Remote resolves Leanback launchers before it
retrieves any provider window root, is hidden for mobile variants, and remains
disabled in the canonical configuration.

SARO 0.6.5 replaces Peacock's fragile content-first Home/PiP transition with a
compact accessibility-overlay control armed only by an explicit SARO media
launch. `START RIDE` begins a local sensor-backed ride in place, `X` dismisses
the control, dragging persists its position, and returning to SARO disarms it.
The arm is process-memory-only and expires after four hours. A full reboot
preserved SARO Home, the narrow original-app-only accessibility service, fresh
sensor access, Peacock authentication, the owner-selected profile, and
protected playback with the complete ride strip.

SARO 0.6.6 removes unnecessary brand references from copy, UI labels, public
paths, command names, app-store profile names, and newly exported configuration.
Fresh setups use `device-setup/`, `saro-control`, `saro-setup.json`, and the
`subscriptionPromptAutomation` key. Imports still accept the legacy filenames
and key, and Android-facing package IDs, firmware metadata, deep links, and the
existing signer remain unchanged so in-place updates and recovery continue to
work. The live update preserved Home, accessibility, saved launcher order, and
ready sensors. Locally signed helper and checkpoint fingerprints remain in the
private recovery bundle rather than the public tree.

## Repository Layout

- `device-setup/ride-starter/`: Android helper, launcher, direct sensor client,
  ride session state, stats overlay, and six sensor-driven games.
- `device-setup/window-agent/`: legacy tablet-side window helper retained for
  older PiP and window-management experiments.
- `device-setup/saro-control`: host-side ADB setup, deployment, diagnostics,
  and legacy launch commands.
- `tools/aurora-profiles/`: device profile used by the patched app store.
- `tools/aurora-store/`: reproducible compatibility patch and build notes.
- `tools/termux/`: pinned, unsupported Codex-on-Termux reinstall helper.
- `tools/security/` and `docs/security/`: reproducible APK audit tooling,
  exact evidence inventories, and package-by-package security review.
- `docs/reviews/`: two independent reviews and the implemented disposition of
  every release-blocking recommendation.
- `docs/config-schema-v2.md`, `docs/recovery.md`, and
  `docs/release-signing.md`: configuration, recovery, and signer-continuity
  contracts.
- `AGENTS.md` and `ai-contributors.md`: mandatory device-safety workflow and
  hardware-specific contributor rules.
- `docs/privacy-audit.md` and `docs/public-release.md`: disclosure audit and the
  mandatory history-free publication process.
- `docs/storage.md`: measured tablet storage use, safe reclamation decisions,
  and the current Zwift-compatible storage profile.
- `docs/zwift.md`: BLE sensor-bridge design, usage, verified boundary, and
  phone-relay limitation.
- `ZWIFT_HANDOFF.md`: exact stopping point and resume checklist for the shelved
  phone-side Companion validation.
- `docs/direct-sensor-live-test.md`: owner-pedaled direct telemetry and local
  ride-control evidence.
- `docs/sensor-recovery-live-test.md`: on-device health, Accessibility recovery,
  and post-reconnect telemetry evidence.
- `docs/streaming-live-test.md`: protected playback and in-video app-drawer
  evidence.
- `context.md`, `plan.md`, and `NEXT_STEPS.md`: architecture context, roadmap,
  and engineering handoff.

## Quick Start

Prerequisites are ADB, a compatible Android SDK/build-tools installation, and a
JDK. Before the first device-changing command, read
[`docs/recovery.md`](docs/recovery.md), create a private read-only baseline, and
preserve the original Home rollback record outside Git. From a clone of this
repository:

```sh
cd device-setup
./saro-control check
BACKUP_ROOT="$HOME/SARO-Backups/$(date +%Y%m%d-%H%M%S)"
./saro-control capture-original-state "$BACKUP_ROOT/prechange-baseline"
./saro-control verify-original-state "$BACKUP_ROOT/prechange-baseline"
./saro-control bootstrap-tablet
./saro-control backup "$BACKUP_ROOT/post-bootstrap-checkpoint" --with-apks
./saro-control verify-backup "$BACKUP_ROOT/post-bootstrap-checkpoint"
./saro-control audit-live "$BACKUP_ROOT/post-bootstrap-checkpoint"
```

`bootstrap-tablet` is a setup/development command. Once the helper and its
accessibility service are installed and configured, rides and streaming should
run entirely on the bike. See `device-setup/README.md` for the current workflow
and known limitations. It records and validates the prior Home before its first
device mutation. A complete original-system image is not available through
ordinary non-root ADB; the baseline is evidence for careful rollback, while the
later APK/config checkpoint recovers the configured SARO installation.
Create the shown checkpoint before installing or updating other apps so it also
retains eligible pre-existing third-party APK/split bytes. Create a second,
separately named checkpoint after the owner reaches the desired setup.

Before publishing this project, read
[`docs/public-release.md`](docs/public-release.md). The working repository and
its history must remain private; only an audited, history-free tracked-tree
snapshot is suitable for a new public repository.

On the bike, the center **P** in the bottom navigation bar is Android's Home
control. Because SARO is selected as the default Home app, tapping that button
opens SARO from the original login screen or any streaming app. SARO's
**BIKE HOME** control returns to the original interface.

The provider remote is a legacy, opt-in fallback. Its row appears in **MANAGE
APPS** only when a supported installed package exposes an Android TV Leanback
launcher, or when the service is already enabled so it can be turned off. It
has no gesture capability or network permission. The current touch-native
Peacock, Paramount+, and MGM+ builds do not need it.

`backup --with-apks` creates an atomic, local, ignored recovery directory. Its
manifests record package versions, signing identity, hardware compatibility,
APK and expansion-file sizes, and SHA-256 hashes, which are checked before
restore. It intentionally excludes manufacturer-owned service packages and
cannot back up app-private logins or Termux's private packages. `verify-backup` is
offline and never installs anything. Read
[`docs/recovery.md`](docs/recovery.md) before a restore.
Use `tools/termux/install-codex.sh` to recreate the unsupported Codex
experiment after a destructive wipe.

Read [`docs/security/package-security-audit.md`](docs/security/package-security-audit.md)
before installing third-party packages. It explains the old platform-patch risk,
the per-installation app-store build, Termux's shared upstream test key, AirScreen's
privacy boundary, and the limits of signature/provenance checks as malware
evidence.

## Public Images

Screenshots are intentionally excluded from the public release because device
captures may contain manufacturer branding, account details, viewing history,
or other owner-specific information. The generated game sprite sheet is the
only public raster image and is pinned by hash with documented provenance.

## License

Released under the [MIT License](LICENSE).
