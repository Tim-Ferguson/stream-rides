# SARO Package Security and Provenance Audit

Audit date: 2026-08-11 through 2026-08-12

Test device: `PLTN-RB1VO-2` / `RB1VO`, Android 11 / API 30, security patch
`2022-10-05`.

## Result

All 25 publishable archived third-party packages passed Android APK signature
verification.
Fifteen archived base APKs also have a verified Google Play Source Stamp. The
remaining ten have intact signatures but no Source Stamp, which is expected for
some vendor-direct and GitHub builds. The separately archived SARO helper and
locally signed app-store build also pass private recovery verification. Their
certificate and APK fingerprints are intentionally excluded from public
inventories.

The canonical current-state checkpoint contains 20 installed third-party
packages across 45 APK/base/split files. Ten current base APKs have a verified
Google Play Source Stamp. Current mobile Peacock, Paramount+,
MGM+, and Max delivery sets are pinned alongside the protected-playback-verified
Prime package. The public current inventory covers 19 packages and 44 files;
the one omitted row is the locally signed app-store build. The historical
inventory retains the corresponding 25 publishable packages and 61 files so
earlier TV-build artifact evidence is not rewritten.

Research found no disclosed malware or signing-key compromise affecting the
installed versions of the official vendor packages. This is not proof that any
binary is harmless. A valid APK signature proves integrity and signer
continuity, not benign behavior, and an absent public incident is not a clean
malware scan.

Two third-party components still need an explicit operator trust decision:

- Termux 0.119.0-beta.3 is the official GitHub release, but official GitHub APKs
  use a publicly shared test key. Its exact SHA-256 is pinned; never accept an
  update from an arbitrary source merely because its signature matches.
- AirScreen is vendor-signed and Source-Stamped, but its privacy notice permits
  broad collection and analysis of installed-app and network/device data. SARO
  left its consent screen unaccepted. Treat it as opt-in and run it only while
  needed on a trusted LAN.

Aurora Store's earlier public-test-key build was replaced. The current 4.8.4
compatibility build is reproducible from pinned source and the tracked patch,
and is signed with a private operator-controlled key. The key and built APK are
not committed. Key custody remains security-critical because Aurora has package
installation and broad storage access.

SmartTube is deliberately excluded. Its official distribution suffered a
reported supply-chain/signing compromise in late 2025. Official Firefox with
official uBlock Origin is SARO's lower-risk ad-blocking path.

## Evidence Files

- [`installed-apk-inventory.tsv`](installed-apk-inventory.tsv) records exact
  package/version values, base APK SHA-256 values, all applicable signing
  certificates, signature schemes, Source Stamp state, and declared
  permissions for the 25 publishable archived third-party packages.
- [`current-apk-inventory.tsv`](current-apk-inventory.tsv) records the same
  fields for 19 official/upstream packages from the 20-package canonical
  recovery checkpoint after the Max, Paramount+, Peacock, and MGM+ mobile
  migrations. The local app-store build is excluded.
- [`downloaded-artifact-inventory.tsv`](downloaded-artifact-inventory.tsv)
  records all 41 ignored local download artifacts. It expands every XAPK and
  records all 134 container, APK, split, and OBB rows without committing
  proprietary binaries.
- The ignored current-state recovery bundle records and verifies 45 APK and
  split hashes across the 20 currently installed third-party packages. Its top
  manifest pins the SARO 0.6.6 helper, configuration, and original Home
  component. The bundle uses only the neutral `saro-setup.json` filename and
  passes offline and live verification. Local hashes and its private name are
  intentionally omitted here.
  It omits Max TV, Prime TV, Paramount+ TV, YouTube TV, and FLauncher APKs.
  Archived optional
  packages and the Zwift installer OBB remain in older local bundles and the
  downloaded-artifact inventory.
- [`audit-apks.sh`](../../tools/security/audit-apks.sh) and
  [`audit-downloads.sh`](../../tools/security/audit-downloads.sh) regenerate the
  two tracked inventories with Android SDK `aapt` and `apksigner`.

The inventories are the authoritative source for full hashes. Short signer
identifiers below are included only to make the review readable.

## Package Review

| Package | Reviewed version | Signer prefix / stamp | Decision | Package-specific assessment |
|---|---|---|---|---|
| `com.peacocktv.peacockandroid` | 7.8.10 | `e5efd56fa3246e48` / yes | Accept, protected playback verified | Official [Peacock Play package](https://play.google.com/store/apps/details?id=com.peacocktv.peacockandroid). Exact mobile base SHA-256 is `a390c9b54b700109a014daa2880d8f0f0e8c97d6ebc8d54a267d27fa4d3658f1`; all four selected APKs have valid v3 signatures and verified Google Play Source Stamps. Direct touch, advisory Play-services dismissal, protected playback, secure AVC decoding, SARO strip coexistence, and login/profile persistence after reboot pass. It declares overlay, coarse-location, biometric, and advertising capabilities, but live app-op checks show no active overlay grant. |
| `com.hulu.plus` | 6.32.0 | `cfbdf8dcd9646924` / no | Accept, update-sensitive | Official [Hulu phone/tablet package](https://play.google.com/store/apps/details?id=com.hulu.plus); exact installed base matches the retained download. No compromise found for this package. It declares precise and coarse location. |
| `com.openai.chatgpt` | 1.2026.216 | `b24f4bfbb3cf293f` / yes | Accept, owner opt-in | OpenAI documents this as the [official Android package](https://help.openai.com/en/articles/7920239-is-chatgpt-available-on-android). The installed build postdates OpenAI's [2026 TanStack incident response](https://openai.com/index/our-response-to-the-tanstack-npm-supply-chain-attack/), which reported no malicious published app and a signing-key rotation. Camera, contacts, location, and microphone are declared but not currently granted. |
| `com.google.android.youtube.tvunplugged` | 3.83.01 | `e1fdaf1b3246fb59` / yes | Removed, archive only | Official [YouTube TV package](https://play.google.com/store/apps/details?id=com.google.android.youtube.tvunplugged). Google lists this hardware family among [supported devices](https://support.google.com/youtubetv/answer/7129767), but sideloading and location verification can still block use. The owner skipped account testing, so the package was removed with keep-data semantics and omitted from the current recovery checkpoint. |
| `us.zoom.videomeetings` | 7.1.6 | `60b75724b34686e5` / no | Accept, update-sensitive | Official [Zoom Play package](https://play.google.com/store/apps/details?id=us.zoom.videomeetings). It is newer than Zoom's 7.0.4 Android security threshold in [ZSB-26010](https://www.zoom.com/en/trust/security-bulletin/zsb-26010/). It declares many sensitive capabilities; none of its dangerous runtime permissions are currently granted. |
| `me.efesser.flauncher` | 0.18.0 | `0dfffb6265da7a33` / yes | Removed, archive only | Official [FLauncher package](https://play.google.com/store/apps/details?id=me.efesser.flauncher) with no known compromise. The APK is removed from the bike with keep-data semantics after SARO Home and original-launcher recovery passed; only uninstall metadata remains. |
| `com.aurora.store` | 4.8.4 patched | private local / no | Accept, operator-key custody required | Built locally from pinned [Aurora Store source](https://gitlab.com/AuroraOSS/AuroraStore) with the tracked compatibility patch and a per-installation private signer. Its public inventory row and local fingerprints are intentionally omitted. Storage, all-files, package-install, and all-package visibility access make key custody and source review critical. |
| `com.wbd.hbomax` | 7.8.1.4 | `619cbb027b715560` / yes | Removed, archive only | Official [Warner package](https://play.google.com/store/apps/details?id=com.wbd.hbomax). Landing, Sign In, and catalog worked, but playback reproducibly crashed in `PlayerSDKPlayerManager` even with SARO idle. This is compatibility evidence, not malware evidence. Its APK was removed with keep-data semantics and remains only in historical local archives. |
| `com.amazon.amazonvideo.livingroom` | 6.24.4 | `2f19adeb284eb36f` / yes | Removed, archive only | Official [Prime Video Android TV package](https://play.google.com/store/apps/details?id=com.amazon.amazonvideo.livingroom). It reached a guest catalog, but starting offered content produced an HDMI-connection error before media playback. Its APK was removed with keep-data semantics after mobile Prime migration and recovery verification; only uninstall metadata remains on the bike, and exact APKs remain in historical ignored archives. |
| `com.amazon.avod.thirdpartyclient` | 3.0.466.2047 | `2f19adeb284eb36f` / no | Accept, protected playback verified | Official [Prime Video phone/tablet package](https://play.google.com/store/apps/details?id=com.amazon.avod.thirdpartyclient). The exact Aurora-delivered APK SHA-256 is `4f04bf217150887ec6bb503ff11f400894bb2dec311831118213d9f9802ea5ae`; it is ARM64 and shares Amazon's signer with Kindle and TV Prime. Signed-in protected playback, secure hardware decoding, DRM, advancing audio, SARO overlay operation, and login/playback persistence after reboot passed. Location, accounts, phone-state, and microphone runtime permissions remain denied. Public advisory research found no package-specific compromise, which is not proof of safety. |
| `com.pelotonhack.ridestarter` | 0.6.6 | private local / no | Trust reviewed repository build | SARO's locally built helper, archived separately from third-party apps. Each operator build has a distinct local signature, so its exact hash and certificate are not published. It adds no permission. This release neutralizes public copy, repository paths, command names, new configuration filenames, and the exported prompt-automation key while retaining legacy import aliases. The process-memory-only media ride control uses the already enabled original-app-scoped accessibility service, does not traverse provider trees, and can be dragged or dismissed. Banner automation validates each node as manufacturer-owned under one scan-wide 256-node/25-ms budget. Its optional provider service remains separate, default-off, gesture-disabled, event-scoped, and flavor-gated. |
| `com.wbd.stream` | 7.8.1.2 | `619cbb027b715560` / yes | Accept, protected playback verified | Current official [Warner package](https://play.google.com/store/apps/details?id=com.wbd.stream), not an impersonator. Exact ARM64 base SHA-256 is `2d3de020b42c3d4e31ef0cc753cc7678cb8cfc13f59188a35e313325cb005643`; all four installed splits are pinned in the current inventory. It shares Warner's signer with the TV package and has a verified Google Play Source Stamp. Signed-in Widevine playback, secure hardware decoding, audio, SARO overlay operation, and login persistence across reboot passed. Declared location permissions remain denied. |
| `com.zwift.zwiftgame` | 1.119.0 (164079) | `58956ae1b271546a` / no | Accept, experimental compatibility | Official [Zwift Play package](https://play.google.com/store/apps/details?id=com.zwift.zwiftgame). The retained base has a valid v3 signature under `CN=zwift coder`; its exact APK and OBB hashes are recorded in the inventories. Public incident and [NVD](https://nvd.nist.gov/vuln/search/results?query=Zwift&search_type=all) research found no disclosed compromise specific to this package, which is not proof of safety. Location and storage permissions are currently denied. The observed idle ANR and below-minimum RAM are reliability issues, not malware evidence. |
| `org.xbmc.kodi` | 21.3 | `f517b44b5db5e62a` / no | Accept with constraints | Exact base matches the retained [official Kodi ARM64 download](https://kodi.tv/download/android/). Avoid unofficial add-on repositories and disable UPnP/AirPlay services when unused; Kodi's broad file/network access increases the impact of an unsafe add-on. |
| `com.netflix.mediaclient` | 9.40.0 build 7 | two Netflix certs / no | Accept, update-sensitive | Official [Netflix package](https://play.google.com/store/apps/details?id=com.netflix.mediaclient). Its v3.1 signer lineage contains separate certificate paths for SDK 24-32 and SDK 33+, both captured in the inventory. The known FlixOnline malware used unrelated package `com.fab.wflixonline`, not this package. Protected playback passed on this target build, but Netflix may change uncertified-device or HD policy in a future release. |
| `org.mozilla.firefox` | 153.0.3 | `a78b62a5165b4494` / no | Recommended if updated | Official [Mozilla package](https://play.google.com/store/apps/details?id=org.mozilla.firefox). Keep current against [Mozilla Android advisories](https://www.mozilla.org/en-US/security/advisories/). Official [uBlock Origin for Android](https://addons.mozilla.org/en-US/android/addon/ublock-origin/) is installed. Firefox currently has location permission granted by default; revoke it unless a site needs it. |
| `com.termux` | 0.119.0-beta.3 | `b6da01480eefd5fb` / no | Conditional, pinned only | Exact base matches the retained [official Termux GitHub release](https://github.com/termux/termux-app/releases/tag/v0.119.0-beta.3). This is newer than the old private-file exposure fix, but the upstream GitHub channel's publicly shared test key makes hash/source pinning essential. Its shell history and Codex tokens are sensitive. |
| `com.tubitv` | 10.31.5000 | `aac5ab52f6b789dc` / yes | Accept, compatibility-limited | Official [Tubi package](https://play.google.com/store/apps/details?id=com.tubitv). No official compromise found. Third parties have distributed unrelated malware impersonating Tubi and other streaming brands, reinforcing package/signer checks. Current device compatibility remains blocked. |
| `tv.pluto.android` | 5.66.0 | `29946c49261522d6` / yes | Accept, compatibility-limited | Official [Pluto TV package](https://play.google.com/store/apps/details?id=tv.pluto.android). TeaBot campaigns used fake Pluto apps; reporting explicitly separated those from the legitimate developer. This build currently stalls at splash on the bike. |
| `com.google.android.youtube.tv` | 5.30.320 | `21199d112cece428` / yes | Accept | Official [YouTube for Android TV package](https://play.google.com/store/apps/details?id=com.google.android.youtube.tv). No official signer compromise found. Prefer this over modified ad-free YouTube clients. |
| `com.audible.application` | 26.30.05 | `57901d5c9f6744f2` / no | Accept, update-sensitive | Official [Audible package](https://play.google.com/store/apps/details?id=com.audible.application). The historical [CVE-2019-11554](https://nvd.nist.gov/vuln/detail/CVE-2019-11554) affected versions through 2.34.0; this 26.x build is far newer. Camera, microphone, storage, and phone-state permissions are declared but not currently granted. |
| `com.ionitech.airscreen` | 2.15.1 | `022735b12fbf7271` / yes | Opt-in only | Official [AirScreen package](https://play.google.com/store/apps/details?id=com.ionitech.airscreen), with no package-specific compromise found. Its [privacy policy](https://airscreen.app/privacypolicy) and onboarding describe broad diagnostics/device/network collection. Applicability of the 2025 AirBorne third-party AirPlay receiver risk is unknown. No sensitive runtime permission is currently granted. |
| `com.epix.epix.now` | 237.1.2026237011 | signer lineage / yes | Accept, login ready; paid playback not tested | Official [MGM+ package](https://play.google.com/store/apps/details?id=com.epix.epix.now); the legacy EPIX package ID is intentional. Exact mobile base SHA-256 is `a69bcfdb5830f45a4c3c00b55d975cf7fa7d58b7f70d9485507b88f51d6e5097`. Its v3.1 lineage includes the prior EPIX signer for API 30 and Google's newer signer; all selected APKs have verified Source Stamps. Direct touch launch, Log In, email focus, and keyboard pass. No active overlay app-op was found. |
| `com.apple.atve.androidtv.appletv` | 2.5.0 | `771d8674d3d9837c` / yes | Accept, protected playback verified | Official [Apple TV package](https://play.google.com/store/apps/details?id=com.apple.atve.androidtv.appletv). Signed-in protected playback, three DRM sessions, secure hardware decoding, advancing audio, SARO overlay operation, and login/playback persistence after reboot passed. No current Android compromise found. The older CVE-2020-27940 was reported for the separate Fire OS app. |
| `com.cbs.ott` | 16.18.0 | `6623d6eff75750d5` / yes | Removed, archive only | Official [Paramount+ Android TV package](https://play.google.com/store/apps/details?id=com.cbs.ott). SARO's prototype focused Sign In and opened the login-method chooser; no method was selected. The package was removed with keep-data semantics after touch-native `com.cbs.app` passed and is omitted from the current recovery checkpoint. No official compromise found; commercial measurement/advertising telemetry remains. |
| `com.cbs.app` | 16.18.0 | `a7520085336e5005` / yes | Accept, login ready; paid playback not tested | Official [Paramount+ phone/tablet package](https://play.google.com/store/apps/details?id=com.cbs.app). Exact base SHA-256 is `2c2ae1d48738cabcdd0048186b57625f3a5677d9218f51c91f2dc1a1bbe26c49`; all three selected APKs have valid v3 signatures under CBS Interactive Mobile and verified Google Play Source Stamps. Direct touch, native Sign In, empty-field focus, on-screen keyboard invocation, and SARO launch pass. Coarse location is declared but denied. Current public-incident research found no package-specific signing-key or malware disclosure, which is not proof of safety. |
| `com.disney.disneyplus` | 26.12.1 | `b4d251e979ea974d` / yes | Accept, protected playback verified | Official [Disney+ package](https://play.google.com/store/apps/details?id=com.disney.disneyplus). Installed base and selected splits match the retained XAPK members. Signed-in playback passed with active DRM, secure hardware AVC decoding, advancing audio, and the SARO ride overlay. No official-package compromise found. |
| `com.amazon.kindle` | 8.153.0.100 | `2f19adeb284eb36f` / no | Accept, update-sensitive | Official [Kindle package](https://play.google.com/store/apps/details?id=com.amazon.kindle), signed with the same Amazon certificate as Prime Video. The historical [CVE-2014-3908](https://nvd.nist.gov/vuln/detail/CVE-2014-3908) affected versions before 4.5; this 8.x build is far newer. Sensitive declared permissions are not currently granted. |

## Permission Review

Declared permissions are capabilities an app may request, not proof that the
capability is active. A live API-30 `dumpsys package` check found:

- No dangerous runtime grants for ChatGPT, Zoom, AirScreen, Audible, Kindle,
  mobile Prime Video, Max mobile, Paramount+ mobile, Termux, Kodi, Hulu, or Zwift. Kodi separately has
  Android's all-files special access, which is not reported as a dangerous
  runtime grant.
- Aurora has read/write external-storage grants. These support its installer
  role but increase the consequence of a compromised local build. Live app-op
  checks also show Aurora's all-files and package-install special access active.
- Firefox has coarse/fine location granted by default. SARO does not need this;
  revoke it unless browser geolocation is wanted.
- Zoom declares camera, microphone, contacts, calendar, phone, overlay, storage,
  and package-install capabilities. Meeting testing must grant only camera and
  microphone when the owner chooses; the other permissions are not required by
  SARO.
- ChatGPT declares camera, microphone, contacts, and location. Voice testing
  should grant microphone only when the owner accepts the app's terms and login.
- Zoom, AirScreen, Termux, Peacock mobile, MGM+ mobile, and SARO have no active
  `SYSTEM_ALERT_WINDOW` app-op.
  SARO displays its strip through the enabled accessibility service's dedicated
  accessibility-overlay window type.
- SARO 0.4.0 adds Android 11's normal `BLUETOOTH` and `BLUETOOTH_ADMIN`
  permissions to host its owner-enabled Cycling Power/cadence GATT peripheral.
  It still requests neither Internet nor location permission.
- SARO 0.6.2 through 0.6.6 separate the original-app-only, gesture-capable ride service
  from the optional `SARO TV Remote`. The latter observes only window-state events for
  Peacock, Paramount+, and MGM+, retrieves their interactive nodes, and
  declares `canPerformGestures=false`. Android's package filter limits event
  delivery but is not assumed to authorize all window access; each command also
  validates the top foreground application package and exact window. Version
  0.6.4 additionally verifies an installed Leanback launcher before obtaining a
  root, preventing mobile provider login windows from being inspected. The
  service is disabled in the canonical state.
  Version 0.6.5's in-place ride-start control is rendered by the ride service,
  but it does not inspect provider nodes or dispatch provider gestures; its arm
  is ephemeral, launch-scoped, dismissible, and time-bounded.
  SARO 0.6.6 keeps that behavior while neutralizing the user-facing Home action,
  settings copy, and exported configuration names. The ride service's optional
  subscription-banner automation validates an original-app root before traversal
  and enforces both node-count and elapsed-time
  budgets, because Android's accessibility package filter does not itself
  restrict `getWindows()` results.

Android special access and network behavior are not fully represented by the
runtime-permission list. AirScreen, Kodi, Termux, Aurora, the accessibility
service, and SARO's overlay deserve greater scrutiny because of their roles even
when runtime permission output is empty.

## System-Level Risk

The bike's October 2022 Android security patch is the largest unresolved
security exposure. Modern apps can be current while the OS remains vulnerable
to years of platform issues. Keep the bike on a trusted, isolated network,
avoid general-purpose browsing or unknown files, and do not treat it as a place
for valuable secrets.

The manufacturer Android image is not a normal certified Google Play device. Play Integrity,
Widevine, HDCP, device allowlists, accessibility detection, overlays, screen
capture, and ad blocking can all cause login or protected-playback failure. A
freeze or black video surface is not by itself evidence of malware.

## Malware-Scan Limits

No ClamAV, YARA, MobSF, or commercial antivirus engine was available locally.
No APK was uploaded to VirusTotal or another third party because that would
redistribute proprietary binaries and could expose unpublished hashes without
an explicit operator decision. This audit therefore establishes:

1. byte-for-byte inventory and recovery integrity;
2. APK signature validity and signer continuity;
3. Google Source Stamp state where present;
4. official package/source provenance;
5. declared permission exposure and selected live grants; and
6. researched public malware, signing, vulnerability, and privacy reports.

It does not establish that every code path is benign. SARO does not publish a
binary bundle. Before accepting an APK for local installation, use a licensed
scanner or hash-only reputation service when policy and licensing allow it, and
record the engine versions, scan time, and exact SHA-256. Continue publishing
source, patches, and official-package hashes rather than redistributing
third-party APKs.

## Required Follow-Up

1. Preserve private SARO and Aurora signing keys in a secure offline backup;
   never replace Aurora with a same-package public-test-key build.
2. Keep Aurora, Firefox, Zoom, ChatGPT, and all streaming packages current.
3. Revoke Firefox location unless needed.
4. Leave AirScreen consent unaccepted unless the owner accepts its data use;
   stop its receiver service after casting.
5. Never update Termux from an untrusted URL. Match the pinned official release
   hash and do not mix GitHub and F-Droid plugin/signing ecosystems.
6. Keep `com.wbd.stream` as the supported Max variant and retain the
   playback-crashing `com.wbd.hbomax` only in historical local archives. Repeat
   protected-playback testing before accepting future provider updates.
7. Keep SmartTube and modified streaming clients excluded.
8. Regenerate both inventories whenever any APK changes and review the diff
   before installation or release.
9. Keep `SARO TV Remote` disabled unless its three supported TV packages are in
   use. Revalidate provider navigation and protected playback after provider
   updates because accessibility node contracts can change without notice.
