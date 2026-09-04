# SARO AI Contributor Guide

This repository contains hardware-specific integration work. Treat device compatibility as an explicit architecture boundary, not an assumption.

## Tested Hardware

| Tablet model | Product/device | Android | API | ABI | Platform | Implementation |
| --- | --- | --- | --- | --- | --- | --- |
| `PLTN-RB1VO-2` | `RB1VO` | 11 | 30 | `arm64-v8a` only | MediaTek `mt8173` | [`device-setup/`](device-setup/) |
| `PLTN-RB1VO` | `RB1VO` | 11 | 30 | `arm64-v8a` only | MediaTek `mt8173` | [`devices/pltn-rb1vo/`](devices/pltn-rb1vo/) |

These values were read from connected tablets with `getprop`. Both tested
models report build fingerprint
`Peloton/RB1VO/RB1VO:11/RO.250111.A/43:user/release-keys` and security patch
level `2022-10-05`.

## Device Change Safety

[`AGENTS.md`](AGENTS.md) defines the mandatory pre-change safety gate. Before
the first mutating ADB command, an agent must identify the authorized target,
record and privately preserve the validated original Home rollback, and collect
a read-only baseline of device/build information, display state, relevant
settings, Accessibility services, and third-party packages. `bootstrap-tablet`
also refuses to begin its first mutation until it has recorded a valid prior
Home component.

Non-root ADB cannot produce a complete factory image or export protected app
data, credentials, DRM keys, or firmware. State that limitation plainly. Keep
the baseline and all recovery archives outside Git because they can contain
device identifiers, package inventories, local signatures, and account-adjacent
state. After setup, create and verify the separate SARO APK/config checkpoint
described in [`docs/recovery.md`](docs/recovery.md).

For removal, prefer `saro-control safe-uninstall`: it restores the validated
prior Home before removing SARO's services and helper. Treat removal of any
other package, app-data clearing, factory reset, or firmware operation as a
separate destructive action requiring the owner's explicit approval.

## Why The Hardware Matters

- The helper targets API 30 and uses an accessibility overlay because it works above unrelated apps without root or the system overlay permission flow.
- Android can retain `enabled_accessibility_services` briefly while the service
  process is not connected after an in-place APK update. Do not infer runtime
  readiness from the secure setting alone. SARO 0.5.2 checks its live service
  instance, reports the distinction on Home, and routes the owner to Android's
  Accessibility screen when recovery is needed.
- The tablet is ARM64-only. App-store spoofing and third-party APK selection must advertise or include `arm64-v8a`; 32-bit-only Android TV profiles are incompatible.
- The manufacturer Android image does not provide a normal certified Google Play environment. The app-store workflow therefore uses a documented Aurora Store compatibility patch. Use an ARM64 phone profile for phone-only apps and the custom ARM64 Android TV profile only for TV packages; the wrong profile can hide otherwise compatible listings.
- Streaming apps vary in their assumptions about Google Play services, DRM certification, touchscreen input, and Android TV launch intents. Package presence is not proof of playback compatibility.
- Prefer phone/tablet packages when the integrated display violates a
  TV build's HDMI or remote-control assumptions. Prime Video is the concrete
  precedent: its TV package rendered a guest catalog but rejected playback,
  while SARO 0.5.1 migrates launcher state to the official mobile package. The
  mobile build subsequently passed protected playback, overlay controls, and
  signed-in playback persistence after reboot.
- Max is a second concrete precedent. Its TV package reached a signed-in catalog
  but crashed while initializing protected playback; official ARM64 mobile Max
  7.8.1.2 passed Widevine playback and rebooted login persistence. SARO 0.5.3
  canonicalizes TV-package configuration to mobile. Resolve launcher activities
  from PackageManager because the mobile activity name differs from the TV one.
- Apple TV shows that an Android TV build can work on this display: 2.5.0 passed
  protected playback and reboot persistence. Its first post-reboot Resume spent
  roughly 25 seconds in Media3 `BUFFERING` before reaching `PLAYING`; allow a
  bounded startup interval before diagnosing a freeze.
- The display is 1920x1080 and is used as a large landscape touchscreen. Changes must preserve large touch targets, stable overlay geometry, and readable type at the configured device density.
- Receiver apps can potentially provide AirPlay, Cast emulation, or DLNA, but Android 11 and DRM-secure video surfaces limit Miracast and protected-content mirroring.
- On Android 11, a streaming activity may enter native PiP or voluntarily finish
  its player when another Activity opens. SARO 0.5.0 therefore keeps `APPS` in a
  second non-focusable accessibility-overlay window. Do not change that control
  back to an Activity launch: Netflix live testing proved the same
  `PlayerActivity` remains resumed with the drawer open. The explicit
  `SARO HOME` route still uses API 30's
  `android:activity.disallowEnterPictureInPictureWhileLaunching` option, and
  `MainActivity` uses `moveTaskToBack(true)` for return. Revalidate both paths
  for every additional Android/hardware implementation.
- Peacock's protected player can ANR on this Android 11 build while losing
  focus into native PiP. SARO 0.6.5 arms a compact, non-focusable `START RIDE`
  accessibility overlay after an explicit media launch instead. It starts the
  local ride without an Activity transition, is draggable and dismissible, and
  never retrieves provider nodes. Preserve its ephemeral, time-bounded arm and
  revalidate provider touch-through plus secure playback on other hardware.
- Peacock, Paramount+, and MGM+ now use verified touch-native mobile delivery
  variants. Their earlier Android TV builds required D-pad semantics. SARO
  0.6.4 retains the separately owner-enabled TV Remote only as a legacy
  fallback. Preserve the service split, `canPerformGestures=false`, package and
  window-state event scope, and default-off recovery behavior. Resolve an
  installed Leanback launcher before retrieving any provider root; a shared
  package ID alone does not prove the TV flavor is installed.
- Paramount+ has a distinct phone/tablet package, `com.cbs.app`. Version 16.18.0
  passed touch focus, keyboard, and SARO launch tests on this hardware, so SARO
  0.6.3 prefers it while preserving `com.cbs.ott` as fallback. Keep form-factor
  package aliases centralized and migrate saved package references instead of
  exposing duplicate launcher entries.
- Peacock and MGM+ use one package ID for multiple Play delivery variants.
  Peacock mobile 7.8.10 has a lower numeric versionCode than TV 7.6.100 despite
  being the newer phone flavor. Exact recovery therefore uses same-package
  `install-multiple -r -d` only when the verified archive version is below the
  installed version, and never uninstalls merely to change flavor. Preserve
  signer and per-split verification before that operation.
- The MediaTek Bluetooth controller can host a BLE GATT peripheral even though
  Android reports no multiple-advertisement support. It cannot discover its own
  advertisement, so Zwift integration uses a phone running Zwift Companion as
  the Bluetooth central and local-network relay. Do not claim direct
  same-tablet pairing without new hardware-specific evidence.
- The live-verified bridge exposes complete Cycling Power and crank-only CSC
  services, serializes notifications through `onNotificationSent`, preserves
  owner-on and owner-off Bluetooth baselines, and automatically re-registers
  after an in-place update or cold boot. The upstream direct sensor path is
  owner-pedaled and physically verified. A real Companion subscription remains
  required before claiming full Zwift support and is currently shelved.
- This tablet has 2 GB RAM. Current Zwift lists 3 GB as its Android minimum;
  treat launch success as experimental and retain observed ANR evidence.
- Games use the direct Affernet callback rather than sampled screen values. Any simulated fallback must be conspicuously labeled and must never be recorded as live telemetry.
- SARO Local Ride is an independent session. It must never be represented as an
  official manufacturer workout, control manufacturer workout state, or claim
  manufacturer-history synchronization. Speed, distance, and calories remain visibly
  estimated; cadence, watts, and resistance are direct callback values.
- Keep full-screen features such as Games in a separate Android task. The Home
  activity is `singleTask` so pressing Home deterministically returns to the
  hub instead of an internal child screen.
- Generated visual assets must be committed as local runtime resources with their creation method recorded. The current game sprite sheet is a transparent PNG in `device-setup/ride-starter/res/drawable-nodpi/`.

## Adding Hardware Support

Do not modify the `RB1VO` implementation and claim broad exercise-bike
compatibility from a single-device test.

For a different tablet generation, model, Android release, CPU ABI, or sensor-service contract:

1. Put hardware-specific code, patches, commands, and fixtures in `devices/<product-or-model>/`.
2. Keep reusable, device-independent tooling in a clearly named shared directory.
3. Add the new device to the tested-hardware table in this file and link its implementation directory.
4. Document the exact model, product/device values, Android release, API level, ABI list, build fingerprint, display geometry, sensor contract, and tested device software version.
5. Include reproducible device-local verification notes and identify untested behavior explicitly.
6. Submit hardware support as a separate pull request from unrelated refactors.

If the current `RB1VO` implementation is later moved under `devices/rb1vo/`, update every link and command in this repository and change the table above in the same pull request.

## Compatibility Identifiers

Do not rename `com.pelotonhack.ridestarter` during an ordinary update: Android
would treat a new application ID as a different app and would not carry Home,
accessibility, preferences, or signer-bound update state across automatically.
Likewise, `com.peloton.activity`, `com.onepeloton.affernetservice`, the
`peloton://activation/justride` deep link, and the exact tested build fingerprint
identify firmware interfaces rather than project copy. The legacy
`peloton-setup.json`, `peloton-setup.status`, `pelotonAutomation`, and
`PELOTON_CONTROL_STATE_DIR` values remain accepted only for upgrades and old
recovery bundles. New output uses SARO names.

## Pull Request Expectations

- Preserve the standalone runtime: workouts, telemetry, controls, and app switching must not require a connected computer after installation.
- Do not commit streaming credentials, device identifiers, third-party APKs, private signing keys, or developer-specific absolute paths.
- Keep app installs in-place when possible so provider-owned login state survives normal updates and reboots.
- Treat uninstall, factory reset, and firmware wipes as destructive to app-private credentials. Configuration exports may include launcher preferences and package choices, but never claim to back up protected sign-in or DRM tokens.
- Add focused tests or live-device verification proportional to the change.
  Do not publish screenshots. Keep interface, account, telemetry, login, and
  transient diagnostic captures outside the history-free public snapshot.
- Update this guide whenever support boundaries, paths, or verified hardware change.
- Record every distributable or installed APK's source URL, package/version,
  cryptographic hash, signing identity, relevant permissions, and known security
  concerns. An app launching is not evidence that its provenance or behavior is safe.
- Recovery archives must exclude manufacturer-owned service packages and verify the
  recorded SHA-256 manifest before installing any archived split.
- Accessibility changes require separate threat review for event scope, window
  retrieval, gesture capability, foreground validation, target lifetime, and
  compiled manifest/XML association. Keep optional provider inspection off by
  default and require the device owner's on-bike opt-in.
- Treat accessibility event package filters as event-delivery filters, not
  `getWindows()` authorization. Validate each retrieved root package before
  traversal, validate every descendant, and retain one scan-wide node-count
  plus elapsed-time budget. Keep the pure policy tests in the release gate.
- Preserve fail-closed release signing, correlated configuration
  acknowledgements, atomic backup publication, exact manifest/file bijection,
  device compatibility preflight, live journal-state revalidation, stale OBB
  staging cleanup, capacity checks, and atomic expansion-file publication.
- Run `tools/verify-release.sh` before publishing and update the package audit
  and both independent review records when architecture or APK inventory changes.
- Keep BLE payloads standards-based, emit zero/no new revolution for stale
  sensor samples, and never describe manual resistance as controllable FTMS.
