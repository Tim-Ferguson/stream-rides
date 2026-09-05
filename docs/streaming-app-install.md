# Streaming App Installation Runbook for Agents

This runbook covers a fresh or updated Aurora Store installation and the
streaming applications tested on the `PLTN-RB1VO-2` / `RB1VO` tablet. It is for
agents and operators working on that exact Android 11, API 30, ARM64 target. Do
not apply it to another tablet generation without a separate compatibility
review.

The versions below are the last versions verified by this repository, not a
promise that a provider still offers them or that a newer version will work.
Never obtain an old version from an untrusted APK mirror merely to match this
table. If Aurora offers a different version, record it as unverified and test it
before updating the compatibility claims or recovery checkpoint.

## Mandatory Safety Gate

Before the first device-changing command, follow [`../AGENTS.md`](../AGENTS.md)
and [`recovery.md`](recovery.md). In particular:

1. Select exactly one authorized ADB target and privately record its hardware,
   build fingerprint, ABI, Android version, and current Home component.
2. Explain that ordinary non-root ADB cannot create a complete restorable image
   of app-private credentials, account sessions, DRM material, or manufacturer
   firmware.
3. Create and verify the pre-change baseline.
4. Run the minimal SARO bootstrap and create, verify, and live-audit the
   post-bootstrap checkpoint before installing or updating Aurora or a provider
   package.

From `device-setup/`, the required sequence is:

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

Stop if any step fails. Preserve the baseline, checkpoint, and generated
`.state/previous-home-*` record outside Git. They are rollback evidence and may
contain identifying package and device information; they are not a factory
image and cannot restore streaming sign-in or DRM state.

## Build and Verify Aurora Store

Use the pinned Aurora Store source, tracked compatibility patch, and private
per-installation signing procedure in
[`../tools/aurora-store/README.md`](../tools/aurora-store/README.md). A trusted
release build must fail closed when the private signing configuration is
missing. Do not install or distribute the `.saro.unsafe` public-test-key build.

The expected output in the Aurora checkout is:

```text
app/build/outputs/apk/vanilla/release/app-vanilla-release.apk
```

Before installation, use Android SDK `apksigner` to verify the APK and display
its signing certificate, then compute its SHA-256. Record the APK hash and
signer output in the private owner-controlled backup directory, not in Git:

```sh
apksigner verify --verbose --print-certs \
  /path/to/AuroraStore/app/build/outputs/apk/vanilla/release/app-vanilla-release.apk
shasum -a 256 \
  /path/to/AuroraStore/app/build/outputs/apk/vanilla/release/app-vanilla-release.apk
```

If `com.aurora.store` is already installed, use only an APK signed by that
installation's existing private key. Do not uninstall Aurora to bypass
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`; stop and recover the correct key/build.

## Install the Newly Built Aurora APK

Only after the safety gate and post-bootstrap checkpoint succeed, install the
verified release APK in place:

```sh
adb install -r \
  /path/to/AuroraStore/app/build/outputs/apk/vanilla/release/app-vanilla-release.apk
adb shell pm path com.aurora.store
adb shell dumpsys package com.aurora.store | \
  sed -n '/versionCode=/p;/versionName=/p'
```

`adb install -r` preserves app data for a valid same-signer update. A signer
mismatch, install error, unexpected package ID, or missing package path is a
hard stop. Do not clear package data or uninstall a provider app as an assumed
fix.

Launch **App Store** from SARO. On first use, the owner must make any account,
anonymous-session, consent, and unknown-app-install decisions on the tablet.
Agents must not accept provider terms, enter credentials, or enable unrelated
permissions on the owner's behalf.

## Select the Correct Aurora Profile

The RB1VO tablet can run only `arm64-v8a` code. The profile must advertise ARM64
or Aurora may offer incompatible packages or hide compatible ones.

- For phone/tablet applications, select Aurora's built-in ARM64 phone profile
  `OnePlus8Pro_EEA`.
- For an Android TV-specific package, import and select
  [`../tools/aurora-profiles/saro-rb1vo-android-tv-arm64.properties`](../tools/aurora-profiles/saro-rb1vo-android-tv-arm64.properties).
- Switch back to the phone profile immediately after acquiring a TV-specific
  package. Leaving the TV profile active has caused phone listings to appear
  unsupported or report version zero.

The TV profile can be copied to the tablet for Aurora's import flow from the
repository root:

```sh
adb push tools/aurora-profiles/saro-rb1vo-android-tv-arm64.properties \
  /sdcard/Download/
```

In Aurora, open its spoof/device-profile settings, import the file from
Downloads, and confirm that the selected profile reports ARM64 before
installing a TV package.

## Last-Verified Streaming Packages

Search in Aurora by exact package ID, verify the displayed publisher and
package, and install one service at a time. Unless marked **TV**, use the ARM64
phone profile. These values come from the repository's current verified APK
inventory.

| Service | Profile | Package | Last verified version | Evidence and cautions |
| --- | --- | --- | --- | --- |
| Netflix | Phone | `com.netflix.mediaclient` | `9.40.0 build 7` (`63705`) | Protected playback verified. Certification policy may change. |
| Hulu | Phone | `com.hulu.plus` | `6.32.0+17694659-google` | Protected playback and retained sign-in verified. |
| Disney+ | Phone | `com.disney.disneyplus` | `26.12.1+rc1-2026.07.15` | Protected playback, secure decoding, audio, and overlay verified. |
| Max | Phone | `com.wbd.stream` | `7.8.1.2` | Protected playback verified. Do not substitute TV package `com.wbd.hbomax`; tested TV `7.8.1.4` crashed during playback. |
| Prime Video | Phone | `com.amazon.avod.thirdpartyclient` | `3.0.466.2047` | Protected playback verified. Do not substitute TV package `com.amazon.amazonvideo.livingroom`; tested TV `6.24.4` failed its HDMI check. |
| Apple TV | **TV** | `com.apple.atve.androidtv.appletv` | `2.5.0` | Protected playback verified. Allow a bounded startup interval after reboot. |
| Peacock | Phone | `com.peacocktv.peacockandroid` | `7.8.10` | Protected playback and reboot persistence verified. This mobile build has a lower numeric version code than tested TV `7.6.100`; never uninstall to change flavor. |
| Paramount+ | Phone | `com.cbs.app` | `16.18.0` | Touch and login flow verified; paid protected playback remains untested. Avoid TV fallback `com.cbs.ott` for a fresh setup. |
| MGM+ | Phone | `com.epix.epix.now` | `237.1.2026237011` | Touch and login flow verified; paid protected playback remains untested. The legacy-looking package ID is official. |
| YouTube | **TV** | `com.google.android.youtube.tv` | `5.30.320` | Launch verified. |

Do not install these as part of a standard streaming setup:

- YouTube TV was only launch-tested without an account and is omitted from the
  current recovery checkpoint.
- Tubi requires Google Play Services on this tablet.
- Pluto TV remained stuck on its splash screen.
- Plex, Crunchyroll, ESPN, and Sling have launcher support but no completed
  device verification.
- Max TV, Prime Video TV, and Paramount+ TV are historical fallbacks or known
  playback failures; prefer the mobile packages listed above.

See [`security/current-apk-inventory.tsv`](security/current-apk-inventory.tsv)
for the exact version codes, APK hashes, signers, splits, Source Stamp results,
and declared permissions. See
[`security/package-security-audit.md`](security/package-security-audit.md) and
[`streaming-live-test.md`](streaming-live-test.md) before changing a package or
claiming compatibility.

## Verify Each Installation

After each install, confirm that Android reports the intended package and
version. Replace the sample package ID for each provider:

```sh
adb shell pm path com.netflix.mediaclient
adb shell dumpsys package com.netflix.mediaclient | \
  sed -n '/versionCode=/p;/versionName=/p'
```

Then launch the app from SARO and record the result using the repository's
evidence levels:

- **Installed** means only that PackageManager reports the package.
- **Launch verified** means native UI was reached.
- **Login flow verified** does not prove paid playback.
- **Protected playback verified** requires the device evidence described in
  [`streaming-live-test.md`](streaming-live-test.md); package presence or a
  visible catalog is insufficient.

The owner performs all provider sign-ins. Do not capture or publish screens
containing credentials, account details, profiles, viewing history, or protected
content. Grant only permissions needed for a feature the owner chooses.

## Preserve the Configured State

After the owner finishes installing, signing in, arranging SARO's app list, and
testing the desired services, create a separately named checkpoint outside Git:

```sh
./saro-control backup "$BACKUP_ROOT/configured-streaming-checkpoint" --with-apks
./saro-control verify-backup "$BACKUP_ROOT/configured-streaming-checkpoint"
./saro-control audit-live "$BACKUP_ROOT/configured-streaming-checkpoint"
```

Keep the original baseline, post-bootstrap checkpoint, and configured-streaming
checkpoint as separate directories. The final checkpoint preserves eligible APK
and split bytes and SARO configuration, but it still cannot export provider
credentials, DRM keys, cookies, or other protected app-private data.
