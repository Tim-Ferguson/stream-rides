# SARO Independent Android and Security Architecture Review 2

Date: 2026-08-11

Reviewed revision: `4a51378`

First report reviewed: `docs/reviews/architecture-review-1.md`

Target: `PLTN-RB1VO-2` / `RB1VO`, Android 11 (API 30), ARM64, 1920x1080 at 240 dpi, security patch `2022-10-05`

## Executive Verdict

The first review is materially correct. Its central conclusion stands: SARO is a
promising hardware-specific prototype, but the current revision is not ready for
a public binary release or for claims of reliable ride accounting and complete
recovery.

I confirmed 22 findings substantially as written, confirmed three with narrowed
impact or severity, and found no fully false finding. The most important
qualifications are:

- The legacy window agent is a high-impact vulnerability only while an owner has
  explicitly started it. It is not installed as a boot-persistent core service,
  so its present repository severity is Medium, while removing it from the public
  workflow remains release-blocking.
- Backward wall-clock movement freezes elapsed advancement until the clock catches
  up; it does not subtract already accumulated base time. `SharedPreferences.apply()`
  creates abrupt-power-loss exposure, not routine process-death loss in every case.
- `ConfigTransferActivity` operates only on SARO's fixed app-specific file. It
  cannot be directed to an arbitrary path through Intent extras, but another app
  can still trigger import/export and alter SARO state.
- The overlay's negative-width case does not occur on the tested 1920x1080 panel,
  but its 52 px control height is only 34.7 dp at 240 dpi and therefore fails the
  project's own 48 dp requirement on the actual bike.

There are also four important omissions in the first report:

1. Hub pause/resume and End have additional accounting races that can discard or
   misattribute totals even without an accessibility event storm.
2. A ride reported as "saved" has no history, display, getter, or export path.
3. The recovery bundle cannot reproduce several setup-defining app states such as
   the Firefox extension, Kodi receiver settings, Aurora profile, runtime grants,
   or Termux contents.
4. The security inventory records signer digests but does not authenticate most
   of them against a trusted vendor baseline; it also incorrectly describes the
   Aurora AOSP public test key as a local debug identity.

## Finding-by-Finding Verdicts

### F01 - Ride accounting is coupled to the overlay loop

**Verdict: Confirmed, High, release blocker.**

Every accessibility event requests a debounced update
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/RideStarterAccessibilityService.java:86`).
That request removes the existing heartbeat before posting a replacement
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:132`).
The poll exits before `readLocalStats()` whenever the hub or Games is foreground
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:137`),
whereas output and distance are integrated only in `readLocalStats()`
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:247`).
The event-storm starvation claim is valid, and the deterministic hub/Games loss
is even stronger evidence than the starvation hypothesis.

### F02 - Sensor ownership, stale callbacks, and duplicate Games binding

**Verdict: Confirmed, High, release blocker.**

Freshness expires after five seconds
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/DirectSensorClient.java:133`),
but `bind()` returns while `connected` is true
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/DirectSensorClient.java:139`).
The sensor-error callback only assigns a string
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/DirectSensorClient.java:213`).
The overlay owns one client
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:115`),
and Games creates and starts another
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameView.java:69`,
`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameView.java:88`).
Whether the vendor service keys callbacks by binder or package remains
unproven, so replacement/unregister impact is a live-test question rather than a
demonstrated failure. The duplicate ownership and permanent silent-stale path are
demonstrated defects regardless.

### F03 - Two incompatible ride models

**Verdict: Confirmed, High, release blocker.**

The hub starts only `RideSessionStore`
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MainActivity.java:468`).
`JustRideLauncher.launch()` exists
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/JustRideLauncher.java:18`)
but has no caller. Local state takes precedence
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:144`),
and local pause/end never control an official workout
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:628`,
`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:660`).
This conflicts with the claim that SARO uses the official Just Ride path
(`device-setup/README.md:202`). The public UX must either be explicitly local or
implement a tested, mutually exclusive official-workout adapter.

### F04 - Recovery hashes do not constrain the install set

**Verdict: Confirmed, High, release blocker.**

A missing APK manifest succeeds
(`device-setup/saro-control:1270`). Present rows are checked
(`device-setup/saro-control:1272`), but restore installs directory glob results
rather than manifest paths (`device-setup/saro-control:1300`,
`device-setup/saro-control:1304`). The standalone helper and configuration are
not covered by that manifest (`device-setup/saro-control:1315`,
`device-setup/saro-control:1321`). Backup also archives installed SARO in the
third-party set because only manufacturer prefixes are excluded
(`device-setup/saro-control:1197`), then separately copies a potentially stale
build output (`device-setup/saro-control:1253`). The first report's bijection,
single-authoritative-helper, and top-level-manifest remedies are necessary.

### F05 - Release signing and versioning are state-dependent

**Verdict: Confirmed, High, release blocker.**

The manifest remains at version code 1/version 0.1.0
(`device-setup/ride-starter/AndroidManifest.xml:3`). A clean build silently creates
a new debug key (`device-setup/ride-starter/build.sh:83`) using public passwords
(`device-setup/ride-starter/build.sh:87`), and the entire build/signing directory is
ignored (`.gitignore:31`). The password is not itself the key compromise; the
blocker is that every clean clone gets a different signer while the build has no
separate fail-closed release mode.

### F06 - Accessibility authority is broader than required

**Verdict: Confirmed, High, release blocker.**

The service retrieves interactive windows and can inject gestures
(`device-setup/ride-starter/res/xml/accessibility_service.xml:4`,
`device-setup/ride-starter/res/xml/accessibility_service.xml:5`). It subscribes to
many sensitive third-party apps
(`device-setup/ride-starter/res/xml/accessibility_service.xml:9`) even though local
telemetry comes from Binder. Banner dismissal computes and taps a coordinate
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:195`,
`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:201`),
and text controls allow substring matches
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:973`).
Restricting event packages to the original app does not prevent an already attached
accessibility overlay from remaining above media apps. Banner/control injection
should be separately enabled, activity-gated, and exact-match first.

### F07 - Legacy shell window agent

**Verdict: Confirmed with narrowed severity: Medium in the repository, High while running; release blocker to quarantine.**

ADB launches the agent under `app_process`
(`device-setup/saro-control:1118`). It listens on loopback
(`device-setup/window-agent/src/com/pelotonhack/windowagent/TabletWindowAgent.java:41`),
spawns one thread per accepted socket
(`device-setup/window-agent/src/com/pelotonhack/windowagent/TabletWindowAgent.java:43`),
and authenticates no peer before accepting shell-powered commands
(`device-setup/window-agent/src/com/pelotonhack/windowagent/TabletWindowAgent.java:85`).
The socket has a 120-second read timeout
(`device-setup/window-agent/src/com/pelotonhack/windowagent/TabletWindowAgent.java:66`),
which bounds each idle connection but not connection count. The agent is started
only by an explicit host command and has no boot registration, so the first
report overstated always-on exposure. It should still be removed from the core
public path or replaced with a capability-authenticated research tool.

### F08 - Aurora release defaults to a public test key

**Verdict: Confirmed, Critical, release blocker.**

The patch selects the upstream AOSP signing config when private properties are
absent (`tools/aurora-store/0001-rb1vo-compatibility.patch:9`). The documented
command builds that release directly
(`tools/aurora-store/README.md:48`), while the warning admits that the key is
public (`tools/aurora-store/README.md:62`). Any holder of that public key can
produce an accepted higher-version update to this privileged installer. A
distribution build must fail without a private key, and the public-key build
must use a visibly separate development application ID.

### F09 - Synthetic game input records live progress

**Verdict: Confirmed, Medium, release blocker if Games ship enabled.**

Any stale sample automatically activates generated input
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameView.java:146`).
The finished session is always recorded
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameView.java:131`), and
`Input.demo` is merely stored
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameSession.java:23`).
`recordResult()` has no eligibility argument
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameProgressStore.java:52`).
This directly violates `ai-contributors.md:21`.

### F10 - Start Ride does not preflight the runtime

**Verdict: Confirmed, Medium, release blocker.**

The hub creates active local state
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MainActivity.java:468`),
ignores the boolean result of `refreshOverlay()`
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MainActivity.java:469`),
and launches media (`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MainActivity.java:470`).
The refresh API explicitly returns false without the service
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/RideStarterAccessibilityService.java:47`).
There is also no freshness acknowledgement from the sensor client. The flow can
therefore claim success while only the wall-clock timer is active.

### F11 - Estimated fields are presented as measured

**Verdict: Confirmed, Medium, documentation/UI blocker.**

The labels are unqualified (`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:64`).
Speed is a clamped square-root estimate
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:278`),
and local calories equal mechanical kilojoules numerically
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:261`).
The latter is a common rough cycling convention, not automatically absurd, but it
is still unvalidated for this device and rider. `EST` labels and documented
formulas are feasible now; accuracy claims require owner pedaling data.

### F12 - Wall-clock persistence and stale sessions

**Verdict: Confirmed with narrowed impact, Medium.**

Ride time is based on `System.currentTimeMillis()`
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/RideSessionStore.java:44`)
and adds only a nonnegative delta
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/RideSessionStore.java:48`).
A backward jump therefore freezes rather than subtracts elapsed time; a forward
jump inflates it. Active state has no age or boot policy, and transition writes
use `apply()` (`device-setup/ride-starter/src/com/pelotonhack/ridestarter/RideSessionStore.java:36`,
`device-setup/ride-starter/src/com/pelotonhack/ridestarter/RideSessionStore.java:85`).
The abrupt-power-loss risk is real, but normal same-process reads see the in-memory
preference update immediately.

### F13 - Configuration export is not an exact setup restore

**Verdict: Confirmed, Medium, release blocker for the recovery claim.**

`installedPackages` is exported
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/ConfigStore.java:41`) but
not passed to import
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/ConfigStore.java:95`).
Order export includes only currently installed apps
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MediaLauncher.java:276`).
Launcher state commits before overlay and Games
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/ConfigStore.java:96`,
`device-setup/ride-starter/src/com/pelotonhack/ridestarter/ConfigStore.java:112`,
`device-setup/ride-starter/src/com/pelotonhack/ridestarter/ConfigStore.java:114`).
Missing score objects leave old values intact
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameProgressStore.java:135`).
The current format therefore mixes replace and merge behavior and cannot produce
an exact post-wipe desired state.

### F14 - No Home fail-safe

**Verdict: Confirmed, Medium, release blocker.**

Deployment and restore assign SARO as Home
(`device-setup/saro-control:1145`, `device-setup/saro-control:1332`) without
recording the previous component or health-checking SARO. The in-app original
app button starts the original UI only
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MediaLauncher.java:228`).
The separate Games task and `MainActivity.singleTask` are positive and explain
the verified normal Home behavior (`device-setup/ride-starter/AndroidManifest.xml:64`,
`device-setup/ride-starter/AndroidManifest.xml:98`), but they do not recover from a
crashing Home APK.

### F15 - Max package variants are inconsistent

**Verdict: Confirmed, Medium.**

The tablet catalog includes only `com.wbd.hbomax`
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MediaLauncher.java:31`),
and the query list likewise omits `com.wbd.stream`
(`device-setup/ride-starter/AndroidManifest.xml:18`). Host tooling prefers
`com.wbd.hbomax` whenever both are present
(`device-setup/saro-control:722`). The first review correctly avoided calling
`com.wbd.stream` obsolete; the repository's package audit also identifies both
as current Warner packages (`docs/security/package-security-audit.md:69`,
`docs/security/package-security-audit.md:72`). Variant choice belongs in config
and must be decided by protected-playback testing.

### F16 - Overlay geometry and touch targets

**Verdict: Confirmed, Medium, release blocker for touch usability.**

Overlay controls are exactly 84x52 px
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:575`).
At 240 dpi, their height is 34.7 dp, below `plan.md:51`. Text sizes and padding
also use raw pixels
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:501`,
`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:509`).
The width formula can become negative
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:444`),
but not on the tested 1920 px panel. The actual-bike touch-height failure alone
confirms the finding.

### F17 - Legacy accessibility parsing mixes windows and stale data

**Verdict: Confirmed, Medium.**

The nonlocal path collects all window roots into shared arrays
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:301`,
`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:309`).
Any time token can be adopted
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:377`),
and cached values survive for two minutes
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:1005`).
`addView`, `removeView`, and `updateViewLayout` remain unguarded
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:429`,
`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:437`,
`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:612`).
This path affects official manufacturer workout scraping, not normal local direct-Binder
telemetry, which narrows but does not remove the risk.

### F18 - Exported host bridge activities

**Verdict: Confirmed with narrowed impact, Medium, release blocker.**

Both bridge activities are exported without a caller permission
(`device-setup/ride-starter/AndroidManifest.xml:81`,
`device-setup/ride-starter/AndroidManifest.xml:88`). `OverlayCurrentActivity`
starts local ride state and foreground media
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/OverlayCurrentActivity.java:11`,
`device-setup/ride-starter/src/com/pelotonhack/ridestarter/OverlayCurrentActivity.java:13`).
`ConfigTransferActivity` treats every non-Import action as Export
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/ConfigTransferActivity.java:16`,
`device-setup/ride-starter/src/com/pelotonhack/ridestarter/ConfigTransferActivity.java:20`).
The config activity uses one fixed app-specific path, so arbitrary-file access is
not present. Unauthorized state changes and foreground disruption are present.
The unused `INTERNET` permission remains at `device-setup/ride-starter/AndroidManifest.xml:10`.

### F19 - Brittle host global-state operations

**Verdict: Confirmed, Medium for development tooling; most legacy paths are not core runtime.**

ADB defaults to a Homebrew path but is environment-overridable
(`device-setup/saro-control:4`). No serial-selection abstraction exists.
Legacy PiP changes density before later failure points
(`device-setup/saro-control:665`), banner dismissal uses fixed coordinates
(`device-setup/saro-control:464`), and Just Ride fallback uses fixed taps
(`device-setup/saro-control:486`). Screenshot capture leaves the remote file and
writes beside the script (`device-setup/saro-control:1080`,
`device-setup/saro-control:1088`). These defects should not block a source-only
research release if clearly quarantined, but they block presenting the host tool
as safe, portable recovery automation.

### F20 - Game timing is frame-rate dependent

**Verdict: Confirmed, Medium, release blocker if Games ship enabled.**

`GameView` clamps observed delta to 80 ms
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameView.java:119`), and
`GameSession` clamps it again
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameSession.java:70`).
At less than 12.5 fps, elapsed game time and physics both run slow. Deterministic
trace replay with a monotonic session deadline is required before persistent
scores are meaningful.

### F21 - Public build and license hygiene

**Verdict: Confirmed, Medium; legal scope should be stated cautiously.**

There is no test or CI tree, and the shell build is the only helper build path
(`device-setup/ride-starter/build.sh:1`). The root MIT notice broadly calls the
repository "the Software" (`LICENSE:6`), while the Aurora profile declares
`GPL-3.0-or-later`
(`tools/aurora-profiles/saro-rb1vo-android-tv-arm64.properties:4`) and the repository
carries a patch against GPL upstream.
The contributor guide requires generated-asset method records
(`ai-contributors.md:25`), but no prompt/model/generation record accompanies the
sprite. The exact derivative-work conclusion is legal advice, not a code-review
fact; the engineering remedy is still a `THIRD_PARTY_NOTICES` file and explicit
per-path licensing/exclusions for provider screenshots and upstream-derived work.

### F22 - Installed APK audit trusts manifest metadata

**Verdict: Confirmed, upgraded to High because the security report calls the output authoritative; release blocker.**

`audit-apks.sh` copies package, version, path, and hash from the recovery manifest
(`tools/security/audit-apks.sh:37`). It verifies the selected base signature
(`tools/security/audit-apks.sh:47`) but does not independently run `aapt` for
package/version and writes the supplied hash unchanged
(`tools/security/audit-apks.sh:79`). It selects only rows named `base.apk`
(`tools/security/audit-apks.sh:38`). This is inconsistent with the claim that the
tracked inventories are authoritative full-hash evidence
(`docs/security/package-security-audit.md:55`). Every APK/split must be measured,
matched to one package/set/signer, and compared with rather than copied from the
recovery manifest.

### F23 - Recovery lacks compatibility preflight and journaled semantics

**Verdict: Confirmed, High for the recovery promise, release blocker.**

Backup writes device identity
(`device-setup/saro-control:1227`), but restore begins installs without reading
it (`device-setup/saro-control:1320`). Helper/config validation is only a file
existence check (`device-setup/saro-control:1315`), and config import reports
success after a fixed sleep (`device-setup/saro-control:1182`). Android package
installation cannot be made globally atomic, but a full preflight, idempotent
journal, and explicit partial-failure report are feasible and required.

### F24 - Compile-time app catalog and no force-close workflow

**Verdict: Confirmed, Low, architectural follow-up.**

The catalog is a static Java array
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MediaLauncher.java:27`),
and launch only reorders the task
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MediaLauncher.java:194`).
A normal app cannot force-stop peers, so Home/Recents switching is the honest
public behavior. A versioned bundled catalog is useful extensibility work but is
not a core release blocker.

### F25 - Games lack per-control Android accessibility semantics

**Verdict: Confirmed, Low, architectural follow-up unless accessible Games are a release requirement.**

The entire custom View has one content description
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameView.java:82`). All
controls share one `onTouchEvent()` and rectangle hit testing
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameView.java:411`,
`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameView.java:434`).
Native controls around the game canvas are preferable to a custom virtual-node
provider for maintainability and testability.

## New Findings

### N01 - Hub controls create additional accounting loss and pause-boundary errors

**Severity: High, release blocker.**

Hub pause calls only `RideSessionStore.togglePause()`
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MainActivity.java:133`).
It does not persist in-memory totals or reset the integration clock, unlike the
overlay pause path
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:629`,
`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:631`).
Because accounting is suspended while the hub is foreground
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:137`),
a quick resume can integrate paused wall time, while a longer visit drops the
whole delta under the five-second guard
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:246`).

Hub End writes stored totals before asking the overlay to refresh
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MainActivity.java:485`,
`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MainActivity.java:486`).
The overlay saves only every two seconds
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:251`)
and resets in-memory tracking as soon as it observes inactive state
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:151`).
The last unsaved interval can therefore be lost. One serialized ride engine must
own pause/end and final checkpointing.

### N02 - "Ride saved" data is not accessible as a saved ride

**Severity: High product-semantics blocker.**

End writes `LAST_DURATION_MS`, `LAST_OUTPUT_KJ`, and `LAST_DISTANCE_MI`
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/RideSessionStore.java:82`),
but no method reads those keys. Neither the hub nor config export includes ride
history (`device-setup/ride-starter/src/com/pelotonhack/ridestarter/ConfigStore.java:54`).
Nevertheless, both end paths tell the rider that the ride was saved
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MainActivity.java:488`,
`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:666`).
Either implement a durable, visible local ride history/export or change the copy
to "Ride ended; summary not retained". This remains separate from whether an
official manufacturer workout is recorded.

### N03 - Recovery does not reproduce important utility setup

**Severity: Medium, release blocker for a "restore my setup" claim.**

Backup contains SARO config, device metadata, one helper APK, and optional APK
files (`device-setup/saro-control:1250`, `device-setup/saro-control:1260`).
SARO config contains only launcher, overlay, and Games objects
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/ConfigStore.java:54`).
It does not capture the selected Aurora spoof profile, Firefox/uBlock setup,
Kodi receiver configuration, Android runtime/special grants, AirScreen consent,
or Termux packages/Codex state. Provider credentials and DRM tokens should remain
excluded, but nonsecret reproducible setup steps need a declarative post-restore
plan with per-step verification. Claims must distinguish reboot persistence from
destructive-wipe recovery.

### N04 - Aurora trust is mislabeled in the package audit

**Severity: Critical documentation and supply-chain blocker.**

The package audit calls Aurora a locally patched build signed with a "local
Android debug key" (`docs/security/package-security-audit.md:23`) and describes it
as conditional local trust (`docs/security/package-security-audit.md:68`). The
tracked signer DN is the generic Android identity
(`docs/security/installed-apk-inventory.tsv:8`), and the build patch explicitly
selects the publicly known AOSP key
(`tools/aurora-store/0001-rb1vo-compatibility.patch:10`). This is not a private
local identity. The audit must state that update authenticity is absent and that
the installed installer is replaceable by anyone holding the public test key.

### N05 - Games also miss the 48 dp touch requirement

**Severity: Medium, release blocker for touch-first Games.**

At the target's scale factor 1.0, the Games EXIT control is 60 px tall
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameView.java:176`) and
the in-game pause control is 58 px tall
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameView.java:275`). At
240 dpi these are 40 dp and 38.7 dp. The first review correctly caught overlay
controls but omitted the same failure in Games. Hit bounds should be at least 72
physical pixels on this 240 dpi device even if the visual treatment is smaller.

### N06 - APK provenance conclusions are not fully reproducible from the audit tools

**Severity: High, release blocker for provenance claims.**

The installed audit records signer values but has no authenticated expected-signer
input (`tools/security/audit-apks.sh:43`). A valid self-consistent signature and a
vendor-looking DN do not establish vendor identity. The download audit measures
each XAPK member independently
(`tools/security/audit-downloads.sh:104`) but never rejects mixed packages,
signers, versions, duplicate base APKs, or missing required ABI/config splits.
Its output schema also has no source URL or retrieval timestamp
(`tools/security/audit-downloads.sh:90`).

Google Play Source Stamp evidence materially improves provenance where present,
but ten installed packages have no stamp
(`docs/security/package-security-audit.md:12`). For each accepted unstamped
package, the repository needs either an authenticated official-release hash or
signer baseline, or an explicit "origin not cryptographically established"
decision. The existing malware-scan limitations are otherwise appropriately
cautious (`docs/security/package-security-audit.md:129`).

## Ordered Remediation Plan

### Release-blocking work feasible now

1. **Freeze public binary claims and choose the ride contract.** Default to an
   honestly labeled `SARO Local Ride` unless the owner explicitly requires an
   official manufacturer workout history entry. Disable the unused official parser/control
   adapter by default and stop calling local completion "saved" until local
   history exists.
2. **Build one process-wide `SensorRepository` and `RideEngine`.** Exactly one
   repository owns Binder registration, death/stale recovery, immutable samples,
   bounds validation, and status. A monotonic engine owns session state,
   integration, pause/end serialization, checkpointing, stale policy, and final
   summary. Hub, overlay, and Games consume snapshots only.
3. **Make Games honest and deterministic.** Demo must be explicitly selected;
   live mode pauses on sensor loss and any demo-contaminated session is ineligible
   for live records/rewards. Use monotonic deadlines and fixed-step physics.
4. **Reduce privilege.** Limit accessibility events to the original app, make gesture
   automation separately consented and activity/resource gated, throttle all
   scans, and guard window lifecycle errors. Protect exported bridge activities
   with a permission held by ADB shell but not ordinary apps, reject unknown
   actions, and remove unused components/permissions.
5. **Establish real release identity.** Add separate debug/release modes, monotonic
   versions, fail-closed external signing configuration, published certificate
   digest/checksums, and documented secure key backup. Make Aurora release fail
   without a private key and give public-test-key development builds a distinct
   application ID.
6. **Make recovery preflight exact.** Require a top-level manifest; hash config,
   helper, metadata, every base/split, and recovery scripts; enforce a manifest/file
   bijection; verify package/version/signer/split consistency; install only listed
   paths; compare hardware/API/ABI; and write an idempotent restore journal. Pull
   installed SARO as the one authoritative helper artifact or build and identify
   it once, never both.
7. **Fix audit tooling and claims.** Independently hash and inspect every file,
   compare against rather than echo manifest values, validate XAPK set coherence,
   pin source URL/time and trusted signer or official hash, and correct Aurora's
   trust description. Treat reputation scanning as separate evidence.
8. **Meet touch and Home recovery requirements.** Convert overlay dimensions to
   dp/sp, enforce 48 dp hit bounds in overlay and Games, account for insets/font
   scale, record the previous Home, health-check SARO before assignment, and add
   `restore-home`/safe-uninstall plus raw ADB recovery documentation.
9. **Add automated gates.** Start with pure JVM tests for ride/sensor/config/game
   logic, fake-ADB tests for recovery, manifest/security assertions, clean-source
   builds, certificate/checksum checks, and screenshot/touch-bound tests. Add
   `THIRD_PARTY_NOTICES` and generated-asset provenance before tagging a release.

### Architectural follow-up after the blockers

1. Implement an official manufacturer workout backend only after the private Affernet
   and workout-control contracts are captured as versioned fixtures. Keep it an
   adapter behind the same explicit session state machine.
2. Move the app catalog to signed/versioned bundled data with explicit package
   variants and desired-state config. Continue to describe app "close" as task
   switching unless a narrowly scoped privileged design is accepted.
3. Add durable local ride history and export using a transactional store. Define
   retention, schema migration, deletion, and estimated-versus-measured fields.
4. Replace custom-canvas menus and controls with native Views/Compose while
   retaining Canvas for gameplay, or provide complete virtual accessibility nodes.
5. Decide whether recovery archives need authenticity across cloud/untrusted
   transfer. If yes, add a detached owner signature; hashes alone cover accidental
   corruption only.
6. Convert optional utility setup into declarative, idempotent steps where Android
   permits it. Keep credentials, DRM material, owner consent, and sensitive tokens
   explicitly out of scope.

### Owner-only live tests

1. Pedal through a calibrated cadence/resistance range and compare cadence, watts,
   resistance, output, speed, distance, and calories with official Just Ride.
2. Force Affernet callback silence, service restart, Binder death, SARO process
   death, accessibility disable/reenable, and low-memory pressure. Confirm one
   registration, bounded reconnect, no stale values, and no double callback.
3. Exercise simultaneous official and local ride attempts; verify deterministic
   rejection/adoption and correct Pause/End routing.
4. Run an event-heavy media UI while riding, spend time in the hub and every Game,
   and verify monotonic totals with no missing or paused-time integration.
5. Validate physical touch targets, drag edges, system bars, font scale 1.0/1.3,
   the "still working out" prompt, subscription banner, and negative lookalike
   controls on the exact firmware build.
6. Test both current Warner packages while signed in, including protected playback,
   overlay/accessibility enabled and disabled, then retain one configured variant.
7. Test clean boot, abrupt power loss, implausibly old sessions, wall-clock jumps,
   and immediate Start/Pause/End power loss.
8. Perform a destructive restore only with owner approval and a known recovery
   path. Verify refusal on a tampered archive and incompatible device before the
   first install, then verify all declared nonsecret utility setup steps.
9. Verify the Home escape path with SARO intentionally disabled/crashing and USB
   debugging both available and unavailable.

## Acceptance Tests Per Release Blocker

### RB1 - Ride engine and explicit session model

- A fake-clock test proves totals advance through hub/Games/hidden-overlay states
  and through 10+ accessibility events per second.
- Pause excludes exactly the paused sensor interval; resume includes only later
  samples; hub and overlay controls produce identical state transitions.
- End performs a final synchronous checkpoint before state becomes inactive and
  exposes the resulting summary/history.
- Local and official backends cannot both be active; every control is routed to
  the selected backend and UI copy names it.

### RB2 - Sensor ownership and failure recovery

- Instrumentation shows exactly one register and one matching unregister for the
  process while switching hub, overlay, Games, and media.
- Stale callback, sensor error, null binding, service disconnect, and Binder death
  each produce bounded backoff and eventual fresh state without leaking binds.
- Truncated/out-of-range Parcel fixtures cannot crash the process or publish a
  fresh sample; accepted cadence/watts/resistance ranges are documented.

### RB3 - Games integrity and timing

- Live mode cannot start until fresh telemetry is stable and pauses visibly on
  dropout; no synthetic frame can affect live score, best, credits, or upgrades.
- Demo results are separate or nonpersistent.
- The same timestamped sensor trace produces equivalent results at 60, 30, 15,
  and 8 fps and across pause/resume.

### RB4 - Accessibility and exported-component boundary

- Manifest inspection shows only required packages/events/permissions and no
  unprotected state-changing exported component.
- A hostile test APK cannot start a ride, switch media, import/export config, or
  invoke original-app gestures; ADB shell can invoke only the documented bridge.
- Captured original-app node fixtures include positive prompts and negative lookalikes;
  only exact activity/resource/text matches act, and coordinate fallback is never
  unattended.

### RB5 - Release and Aurora signing

- Two clean clones using the configured release key produce APKs with the same
  published certificate digest; a release build without that key fails.
- Every public release has an increased version code and source/tag/checksum map.
- In-place upgrade preserves SARO config/history; debug or public-AOSP-key APKs
  cannot overwrite the release package.
- Aurora's distributable signer is private and pinned; the AOSP-key development
  flavor has a different package ID and visible label.

### RB6 - Recovery trust and completeness

- Missing/header-only manifests, extra APKs, missing splits, duplicate paths,
  traversal, malformed hashes, package mismatch, signer mismatch, helper/config
  tampering, and device mismatch all fail before the first install.
- Restore installs only manifest paths and emits a machine-readable journal that
  can resume safely after failure.
- A clean-device rehearsal reproduces every explicitly supported nonsecret setting
  and lists unsupported provider login/DRM/consent state before execution.
- Export/import returns structured acknowledgement; a failed Activity import
  cannot be printed as success by the host.

### RB7 - Audit correctness and package provenance

- Mutating one byte in any base or split changes the measured hash and fails a
  manifest comparison.
- Mixed-package, mixed-version, mixed-signer, duplicate-base, missing-ABI, and
  unsafe-path XAPKs are rejected rather than merely listed.
- Every accepted package row records retrieval URL/time, measured hashes, signer
  lineage, Source Stamp state, and an authenticated expected signer/hash or an
  explicit unresolved-origin decision.
- Security documentation no longer equates signature validity with vendor identity
  or malware cleanliness and accurately labels both public-test-key channels.

### RB8 - Touch sizing and Home recovery

- Automated bounds assert at least 48x48 dp hit areas for every overlay and Game
  control at 1920x1080/240 dpi and supported font scales.
- Screenshots and physical taps verify no overlap, clipped stats, off-screen drag,
  or control displacement across media, prompts, and system bars.
- Bootstrap records the old Home and launches/health-checks SARO before changing
  default Home; restore/uninstall commands restore that exact component.
- A documented raw ADB path and an owner-tested on-device escape path recover from
  a broken SARO Home.

### RB9 - Legacy privileged tooling

- A normal bootstrap and reboot leave TCP port 47631 closed and no window-agent
  process running.
- Public/core commands do not start the agent or alter global density.
- If retained under an explicit research profile, peer authentication,
  connection/rate limits, idle shutdown, explicit stop, and guaranteed density
  restoration are tested.

## Residual Risks That Must Remain Documented

- The target OS security patch is from 2022. Current apps do not remediate
  platform vulnerabilities; network isolation and low-value accounts remain
  prudent.
- Affernet is a private, hardware/firmware-specific protocol with no compatibility
  guarantee. A manufacturer update can break telemetry or control semantics.
- Accessibility overlays, gesture automation, sideloading, Aurora, ad blocking,
  and an uncertified Play environment can trigger app integrity, DRM, or account
  restrictions.
- Widevine/HDCP/device allowlists can produce SD video, black surfaces, login
  refusal, or freezes independently of SARO correctness.
- App-private credentials, cookies, DRM keys, owner consent, and most protected
  app state cannot be safely restored after uninstall/factory reset/firmware wipe.
- Hashes prove bytes, signatures prove control of a signing key, and Source Stamp
  proves a distribution stamp where valid. None alone proves benign behavior.
- Third-party APK versions and public security research age quickly. Package audit
  evidence must be regenerated and reviewed for every release.
- Global Home replacement can always require ADB recovery if both SARO and the
  on-device selector are unusable.
- Estimated speed/distance/calories remain estimates until validated against
  official values and documented with error bounds.
- Protected-playback, camera/microphone, voice, casting, subscription-banner, and
  nonzero telemetry tests require the owner's accounts, physical pedaling, or
  consent and cannot be certified by source review.

## Review Scope and Verification

This was a source and architecture review of revision `4a51378` plus the complete
first report. No implementation file or live device state was changed. The review
traced all 25 first-report findings to current source, inspected the helper
manifest/build, ride/sensor/overlay/game/config paths, host recovery tooling,
window agent, Aurora patch, package audit tools, and tracked security inventories.

No automated test suite exists to validate the failure modes above. The existing
screenshots and previous live notes demonstrate important happy paths, including
normal Home return after Games and cold-boot persistence, but they are not
repeatable acceptance tests for accounting, security boundaries, destructive
recovery, or signer trust.
