# SARO Next Engineering Steps

Start from the repository root. All paths below are repository-relative.

## Immediate Work

1. Complete the touch-native provider account gate with the owner present.
   SARO 0.6.4/versionCode 15 and mobile Peacock 7.8.10, Paramount+ 16.18.0,
   and MGM+ 237.1 are installed. Touch launch, sign-in navigation, email focus,
   the on-screen keyboard, complete ride-strip coexistence, and in-ride drawer
   switching pass for all three. Sign in on-device, verify protected playback,
   then reboot and verify login persistence without recording credentials or
   activation codes. Peacock still shows its missing-Play-services advisory;
   one earlier generic-error path self-exited without a crash, while three
   subsequent cold launches all remained stable at the catalog.

2. Complete other provider-authorized checks.
   Hulu, Netflix, Disney+, Max mobile, Prime mobile, and Apple TV protected
   playback pass. YouTube TV account/playback testing was explicitly skipped
   because the owner has no YouTube TV sign-in. Grant Zoom
   camera/microphone only through the owner's Android prompts, and finish
   ChatGPT authentication/voice testing only after the owner accepts its terms.
   Owner-consented YouTube 5.30.320 public playback and SARO overlay controls
   also pass.

3. Reduce optional attack surface after owner validation.
   Max mobile protected playback and rebooted login persistence now pass. Its
   playback-crashing TV package, FLauncher, and the playback-incompatible Prime
   TV package are APK-dormant with keep-data metadata only; SARO Home, the
   recorded bike `restore-home` target, and mobile provider packages replace
   them.

SARO 0.5.2 now reports overlay and sensor readiness on Home, opens Android
Accessibility settings when the overlay is disabled, and exposes a local
Affernet reconnect action for stale or missing samples. Its controlled live
recovery test is recorded in `docs/sensor-recovery-live-test.md`. The finalized
pruned state also passes autonomous Home, sensor, config, Hulu-session, and
tablet-only ride recovery after a real reboot; see
`docs/reboot-052-live-test.md`.

SARO 0.5.3 migrates old Max TV launcher state to official mobile Max 7.8.1.2.
The current private recovery checkpoint contains 20 third-party packages across
45 APK/base/split files, the exact SARO helper and config, and omits Max TV,
Prime TV, Paramount+ TV, YouTube TV, and FLauncher. Its installation-specific
hashes are intentionally not published.

SARO 0.6.2's default-off update, complete ride strip over MGM+, guarded End,
byte-identical config restore, boot-ID-confirmed reboot, SARO Home recovery,
fresh telemetry, and delayed optional-service-off check all pass. Do not repeat
these by enabling the service unattended.

SARO 0.6.4's Peacock/MGM+ mobile migration, flavor-gated remote, in-place update, live
touch checks, and offline recovery verification pass. It has not yet had a
separate reboot test.

## Shelved Work

The owner explicitly shelved the Zwift Companion relay test on 2026-08-11.
Do not resume it until requested. When resumed, follow `ZWIFT_HANDOFF.md` to
pair SARO's Cycling Power/cadence services through Companion and verify avatar
movement. The non-Zwift physical sensor test is complete; see
`docs/direct-sensor-live-test.md`.

The current private tree passes the release gate, exact live checkpoint audit,
pinned Gitleaks scan, strict reviewed-binary/PNG audit, and 25 adversarial
publication tests. The private repository and its history must never be made
public. Follow `docs/public-release.md`: export committed `HEAD` with the
history-free exporter and create one new parentless root commit in a brand-new
public repository. Re-run `tools/verify-release.sh` after any source or recovery
change.

## Resume Checks

With a bike connected over USB:

```sh
cd device-setup
./saro-control check
./saro-control status
```

Inspect the current working tree before editing because package migration work
may already be present:

```sh
git status --short
git diff --check
```

Build the helper only when ready to test the current source:

```sh
cd device-setup/ride-starter
./build.sh
```

The `bootstrap-tablet` command, neutral package, Home assignment, and
accessibility service have passed live deployment and cold-reboot testing.
Verify any recovery bundle offline before considering a restore:

```sh
./saro-control verify-backup ../saro-backup
```

## Verification Checklist

- Hub opens from Android Home after a cold boot.
- Start Ride succeeds without USB connected.
- Overlay shows all metrics and can be dragged without accidental taps.
- Cadence, watts, and resistance update together while pedaling. Passed live on
  2026-08-11.
- Home reports fresh sensor values, refuses a ride when telemetry is stale, and
  provides touchscreen-only Accessibility/sensor recovery. Passed live for the
  Accessibility and ready-state paths on 2026-08-11.
- A fresh 0.5.2 ride starts from Home, resumes Hulu, opens the overlay app
  drawer, returns Home, ends through confirmation, and restores an exported
  configuration without a runtime host. Passed live on 2026-08-11.
- Pause/resume state and elapsed time remain correct.
- End requires confirmation, saves the ride, and removes the overlay.
- Apps can be opened and switched without ending the ride.
- Opening and closing the overlay app drawer leaves the current player Activity
  resumed; choosing an app intentionally switches providers.
- The original bike app remains reachable.
- App order and hidden state survive process death and reboot.
- Existing streaming sessions survive reboot and in-place APK update.
- SARO game progress survives reboot and configuration round-trip.
- Audible, Kindle, ChatGPT, Zoom, Termux/Codex, Kodi, and uBlock remain present.
- Max mobile protected playback, ride controls, launcher migration, and signed-in
  catalog persistence after reboot pass. The canonical recovery bundle omits the
  playback-crashing TV package.
- Mobile Prime Video retains the old fifth launcher slot. Protected playback,
  overlay controls, and signed-in playback persistence after reboot pass. The
  canonical current-state recovery bundle omits the Android TV APK. Do not
  reinstall it unless new evidence overturns the observed HDMI failure.
- Apple TV protected playback, overlay controls, and signed-in playback
  persistence after reboot pass. A brief cold-start buffering state resolved to
  `PLAYING` without intervention.
- Disney+ protected playback, secure decoding/audio, overlay app drawer,
  ride-only pause/resume, guarded End, and exact config restore pass.
- SARO 0.6.2 keeps `SARO TV Remote` disabled through in-place install and cold
  reboot while the original-app-only ride service, Home, sensors, and complete strip
  remain functional. Passed live on 2026-08-12.
- Owner-gated: authenticate mobile Peacock, Paramount+, and MGM+, then verify
  protected playback and rebooted login persistence. Touch and SARO launch pass.
- Shelved: Zwift Companion receives nonzero power/cadence and Zwift moves the
  avatar, or the exact phone-side failure is documented.
- Tubi still reports missing Google Play Services or is reclassified with proof.
- Pluto TV either advances past splash or remains compatibility-limited.
- Exported config restores project-owned settings but contains no credentials.
- A full wipe is documented as requiring streaming sign-in again.

## Do Not Assume

- A streaming app reaching its catalog proves protected playback works.
- A normal reboot and a factory reset have the same persistence behavior.
- Android permits export of another app's private authentication state.
- The legacy PiP/window-agent path is required by direct telemetry.
- Tubi or Pluto is working until playback is demonstrated on the target bike.
