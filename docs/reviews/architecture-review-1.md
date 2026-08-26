# SARO Independent Android and Security Architecture Review 1

Date: 2026-08-11  
Reviewed revision: `4a51378`  
Target: `PLTN-RB1VO-2` / `RB1VO`, Android 11 (API 30), ARM64, 1920x1080 at 240 dpi, security patch `2022-10-05`

## Findings

### High: Ride accounting is coupled to a cancelable overlay-render loop

Evidence: every accessibility event calls `requestUpdateSoon()`
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/RideStarterAccessibilityService.java:78`),
which removes the existing poll callback and schedules a new one 150 ms later
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:131`).
The service subscribes to `typeWindowContentChanged` from many media and utility
packages (`device-setup/ride-starter/res/xml/accessibility_service.xml:2` and
`device-setup/ride-starter/res/xml/accessibility_service.xml:9`). A package that
emits events more frequently than the debounce interval can indefinitely defer
the poll. More importantly, the poll returns before reading or integrating a
local ride whenever the hub or Games is foreground
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:136`),
while output and distance are integrated only later in that method
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:233`).

Impact: cadence can remain live at the Binder layer while the display and saved
totals freeze. Pedaling while choosing another app or playing any game is omitted
from output and distance. On an event-heavy streaming UI, the same omission can
happen without the rider returning to SARO. This violates the long-ride and game
integration claims.

Remediation: create one monotonic ride-engine heartbeat independent of rendering
and accessibility events. It must sample/integrate while the overlay is hidden,
including in Games and the hub. Accessibility events may request a render, but
must never cancel the accounting heartbeat. Persist totals from that engine and
make overlay, hub, and Games read-only consumers of one snapshot.

### High: The private sensor client can remain stale forever and is registered twice during Games

Evidence: `snapshot()` marks samples stale after five seconds
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/DirectSensorClient.java:129`),
but `bind()` refuses to reconnect while `connected` remains true
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/DirectSensorClient.java:137`).
A sensor-error callback records text but neither disconnects nor schedules a
retry (`device-setup/ride-starter/src/com/pelotonhack/ridestarter/DirectSensorClient.java:208`).
Registration failure and null binding schedule another bind without first
unbinding the accepted connection
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/DirectSensorClient.java:43`
and `device-setup/ride-starter/src/com/pelotonhack/ridestarter/DirectSensorClient.java:75`).
The accessibility overlay owns one always-started client
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:110`),
and Games creates and starts a second client with the same package identity
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameView.java:66` and
`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameView.java:85`).

Impact: a silent callback failure produces permanent `--` values until the
accessibility service restarts. Repeated failed binds can leak bind references.
The behavior of two callbacks registered under one package name is undocumented;
one client may replace or unregister the other on this vendor service. The raw
Parcel decoder also has no protocol/version or range validation
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/DirectSensorClient.java:225`).

Remediation: make an application-scoped `SensorRepository` own exactly one bind
and fan immutable snapshots out to ride UI and Games. Add a stale-sample watchdog,
bounded exponential backoff, explicit unregister/unbind before rebind, Binder
death handling, expected-value clamps, and protocol fixtures from the tested
firmware build. Surface connection state and the last failure to the UI.

### High: SARO has two incompatible ride models without an explicit state machine

Evidence: the hub's Start Ride action only creates a local preference-backed
timer and launches media (`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MainActivity.java:467`).
It does not call `JustRideLauncher`; that class has no caller in the repository
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/JustRideLauncher.java:18`).
When the local flag is active, it always takes precedence over accessibility
stats from an official manufacturer workout
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:144`).
Pause and End likewise mutate the local session first and do not operate the
manufacturer workout (`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:626`
and `device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:647`).
Documentation nevertheless says the setup uses the official Just Ride path
(`device-setup/README.md:201`).

Impact: a rider can have an official manufacturer workout and a SARO local ride active
at the same time. The overlay then hides the official workout's state, and SARO
Pause/End controls only the local timer. Ending SARO can leave the official
workout running. A SARO ride is also not a manufacturer workout record, despite UI and
documentation using the terms interchangeably.

Remediation: define one explicit session state machine with mutually exclusive
backends such as `NONE`, `SARO_LOCAL`, and `MANUFACTURER_WORKOUT`. Detect and reject or
adopt an already-running official workout. Route metrics and controls through the
selected backend. Label local rides honestly and document whether they appear in
manufacturer workout history.

### High: Recovery hash verification does not constrain what gets installed

Evidence: a missing APK manifest is accepted as success
(`device-setup/saro-control:1265`). The verifier checks rows that are present,
but does not reject extra files or package directories
(`device-setup/saro-control:1272`). Restore then installs every `*.apk` in every
directory, including files absent from the manifest
(`device-setup/saro-control:1295`). The standalone helper APK is installed
without a recorded hash (`device-setup/saro-control:1315` and
`device-setup/saro-control:1321`). Backup may copy an old helper build because
it rebuilds only when no output exists (`device-setup/saro-control:1253`), while
the APK archive also includes the installed helper because only manufacturer-owned
prefixes are excluded (`device-setup/saro-control:1197`).

Impact: adding an unlisted APK to a writable recovery directory bypasses the
documented pre-install verification. A header-only or missing manifest also
allows all directory contents to install. The two helper copies can differ in
code or signer and make restore fail after a partially applied package set.
SHA-256 rows protect against accidental file changes only when the manifest
itself is trusted; they do not authenticate a mutable backup.

Remediation: require a manifest whenever `apks/` exists; validate a bijection
between manifest rows and files; install only paths read from the manifest; and
reject duplicates, unexpected extensions, package-name mismatches, split signer
mismatches, and extra files. Exclude SARO from the third-party loop and pull the
installed helper as the authoritative artifact. Hash the helper, configuration,
and device metadata in one top-level manifest. Preflight everything before the
first install and provide an optional signed-manifest mode for backups crossing
a trust boundary.

### High: Release signing and versioning are state-dependent and cannot support reliable updates

Evidence: every build has `versionCode="1"` and `versionName="0.1.0"`
(`device-setup/ride-starter/AndroidManifest.xml:3`). If an ignored build-directory
keystore is absent, the build silently creates a new debug key with public
passwords (`device-setup/ride-starter/build.sh:83`). The keystore is not part of
the recovery format and is intentionally ignored (`.gitignore:30`).

Impact: rebuilding from a clean clone creates a different signer and cannot
update an existing installation. Android then requires uninstalling SARO, which
destroys its private ride/config/game state. Constant version metadata also
prevents meaningful upgrade/downgrade policy, incident response, and correlation
between source, APK, and live behavior.

Remediation: separate debug and release builds. Require an explicitly configured,
stable external release key for distributable APKs and fail closed when it is
missing. Increment version code for every release, derive version name from a tag,
publish the release certificate digest, generate checksums, and document secure
key backup/rotation. Never make a release artifact depend on a previously ignored
build directory.

### High: Accessibility scope is broader than the implementation needs, and automation can tap the wrong UI

Evidence: the service can retrieve window content and perform gestures
(`device-setup/ride-starter/res/xml/accessibility_service.xml:4`) and subscribes to
streaming, ChatGPT, Kindle, Zoom, Termux, receiver, and browser packages
(`device-setup/ride-starter/res/xml/accessibility_service.xml:9`). Current direct
telemetry only consumes original-app nodes. Banner dismissal scans window trees and
dispatches a guessed coordinate beside matching text
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:166`).
The five-second throttle is set only after a match, so an absent banner is scanned
every poll. Workout controls match substrings, walk up to five ancestors, and
fall back to raw center taps
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:871`
and `device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:965`).

Impact: SARO requests access to sensitive third-party UI content it does not need.
On the 2022-patch Android image, this materially expands the effect of a SARO bug
or compromised update. A future original app text/layout change can make background or
non-workout controls match and receive a gesture. Repeated full-tree traversal can
also add load during protected playback.

Remediation: restrict accessibility events to the original app only and remove
third-party packages unless a concrete feature requires them. Make prompt automation an
owner-controlled optional feature, separate from the core overlay. Require the
expected activity/window, stable resource ID, visible/enabled/clickable state,
and exact normalized text before acting. Prefer `ACTION_CLICK`; do not use guessed
screen coordinates for unattended actions. Throttle scan attempts whether or not
a match is found. Consider `TYPE_APPLICATION_OVERLAY` with explicit owner consent
for the core overlay so accessibility is not mandatory for local rides.

### High: The legacy window agent exposes shell-privileged UI operations to every local app

Evidence: ADB launches the agent with `app_process` under the shell identity
(`device-setup/saro-control:1118`). It binds an unauthenticated TCP server on
loopback (`device-setup/window-agent/src/com/pelotonhack/windowagent/TabletWindowAgent.java:38`)
and creates an unbounded thread for every accepted socket
(`device-setup/window-agent/src/com/pelotonhack/windowagent/TabletWindowAgent.java:42`).
Any local app can send fixed commands that change global density, inject key/tap
input, move tasks, or pin activities
(`device-setup/window-agent/src/com/pelotonhack/windowagent/TabletWindowAgent.java:85`).
There is no stop command, peer authentication, connection limit, or idle
shutdown. The JAR now has a source build script, but the privileged protocol has
no automated security test.

Impact: a malicious or compromised sideloaded app can drive shell-authorized UI
changes and exhaust the agent with open connections. `overlay-current-compact`
can leave the whole tablet at density 90; tap sequences can hit unrelated controls
after a manufacturer update. Calling the socket "local-only" does not establish an
Android application trust boundary.

Remediation: exclude this agent and its launcher command from public/core builds.
If it must remain for research, require a per-launch random capability delivered
only over ADB, verify peer credentials with an Android local socket, serialize and
rate-limit requests, implement idle/explicit shutdown, restore global state in a
finally path, and provide a reproducible build. A shell helper is not needed by
the current direct overlay architecture.

### High: The documented Aurora release command produces an APK signed by a public test key

Evidence: the compatibility patch selects Aurora's AOSP signing config whenever
private signing properties are absent (`tools/aurora-store/0001-rb1vo-compatibility.patch:5`).
The documented build steps do not create those properties
(`tools/aurora-store/README.md:37`). The warning correctly states that this key
provides no publisher identity (`tools/aurora-store/README.md:60`), but the unsafe
fallback remains the default successful release build.

Impact: anyone with the public AOSP test key can create a same-package, higher-
version update accepted over that Aurora build. Because Aurora is an app installer,
that is a high-value update channel on a device already running many sideloaded
apps.

Remediation: make release builds fail when private signing configuration is
absent. Permit the public test key only in an explicitly named development flavor
that cannot be confused with a distributable artifact. Record the expected
Aurora signer digest and verify it before every in-place update.

### Medium: Sensor loss silently switches Games to synthetic input and records it as progress

Evidence: any stale sensor snapshot enables generated cadence, watts, and
resistance (`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameView.java:144`).
That input still advances the session and records scores, credits, and bests
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameView.java:127`).
`Input.demo` is stored but no runner uses it to suppress persistence
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameSession.java:19`).
This contradicts the contributor rule that simulated fallback must never be
recorded as live telemetry (`ai-contributors.md:21`).

Impact: a five-second callback outage can improve a purported live score and
award upgrades without pedaling. A fully disconnected bike can populate the same
leaderboard and economy as a live session.

Remediation: make Demo an explicit mode selected before play and store its results
separately or not at all. In live mode, pause the game on stale telemetry and
resume only after a stable reconnect. Mark a session ineligible for live records
if any synthetic sample was consumed.

### Medium: Start Ride succeeds even when no overlay or sensor service is available

Evidence: the hub starts local state, ignores the boolean returned by
`refreshOverlay()`, and immediately launches media
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MainActivity.java:467`).
No UI checks whether accessibility is enabled, the service instance exists, the
Affernet bind succeeded, or samples are fresh.

Impact: after an accessibility reset or firmware change, the rider sees a normal
"started" flow but receives no overlay and may accumulate zero derived totals.
The only diagnostic is hidden in logcat.

Remediation: expose service and sensor readiness as observable state in the hub.
Preflight before starting, offer an explicit timer-only degraded mode, and provide
a direct path to the relevant Android settings. Keep ride-start state pending
until the overlay acknowledges attachment.

### Medium: MPH, distance, and calories are estimates presented as measured values

Evidence: local speed is an arbitrary clamped square-root function of watts
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:274`),
distance integrates that estimate, and calories are assigned exactly the same
numeric value as mechanical kilojoules
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:256`).
The overlay labels are unqualified `MPH`, `MI`, and `CAL`
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:63`).

Impact: these values can look authoritative while differing materially from the
manufacturer workout. They have no rider mass, calibration, efficiency, or resistance
model, and nonzero combined telemetry remains an acknowledged pending test
(`device-setup/README.md:209`).

Remediation: consume vendor-provided fields if the protocol exposes them. Until
validated, label values `EST MPH`, `EST MI`, and `EST CAL`, document formulas and
error bounds, and add golden tests against official Just Ride samples across a
range of cadence/resistance/output values.

### Medium: Ride persistence uses unbounded wall-clock deltas and has no stale-session policy

Evidence: session start and elapsed calculations use `System.currentTimeMillis()`
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/RideSessionStore.java:22`
and `device-setup/ride-starter/src/com/pelotonhack/ridestarter/RideSessionStore.java:40`).
An active flag survives indefinitely, and transition writes use asynchronous
`apply()` (`device-setup/ride-starter/src/com/pelotonhack/ridestarter/RideSessionStore.java:28`).

Impact: NTP/manual clock movement can shorten or inflate a ride. A tablet left
off or unused for days can resume a multi-day active session at Home. A process
or power loss immediately after Start/Pause/End can lose the transition.

Remediation: use `elapsedRealtime()` within a boot, persist bounded checkpoints
with boot identity and wall time only for cross-boot recovery, and require user
confirmation before resuming an implausibly old session. Commit lifecycle
transitions synchronously; keep high-frequency totals asynchronous.

### Medium: Configuration export does not restore the setup it claims to describe

Evidence: `installedPackages` is exported
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/ConfigStore.java:38`)
but ignored on import (`device-setup/ride-starter/src/com/pelotonhack/ridestarter/ConfigStore.java:95`).
Only currently installed apps are persisted in order
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MediaLauncher.java:274`),
so uninstalling an app loses its intended position. Launcher, overlay, and game
state are committed separately, making import non-atomic
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/ConfigStore.java:95`).
Missing game score fields merge with old values instead of producing an exact
restore (`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameProgressStore.java:123`).
Documentation additionally claims enabled services, launcher behavior, and setup
metadata that the JSON does not contain (`context.md:152`).

Impact: a post-wipe import cannot tell the owner which selected apps to reinstall
or faithfully reproduce prior order. A failed late import can leave a mixture of
old and new settings while the UI reports only a generic exception.

Remediation: publish a JSON schema with replace-versus-merge semantics. Store the
full catalog order and an explicit desired-package set independent of installation
state. Parse and validate into an immutable model, then commit all project state
as one transaction or staged migration. Add file-size/depth limits and an import
result that identifies missing packages without launching installs silently.

### Medium: Making SARO Home has no documented or automated fail-safe

Evidence: deployment and restore unconditionally assign SARO as Home
(`device-setup/saro-control:1145` and `device-setup/saro-control:1332`).
There is no command to restore the original Home component. The in-app original
Home button launches the original app but does not change the default
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MediaLauncher.java:228`).

Impact: a bad SARO update or startup crash can create a Home crash loop and make
normal tablet recovery depend on ADB, precisely when the normal workflow is meant
to be computer-independent.

Remediation: record the prior Home component during bootstrap, add `restore-home`
and `uninstall-saro-safely` commands, document raw ADB recovery commands near the
risk warning, and preflight-launch the new APK before changing Home. Consider a
long-press or settings action that lets the owner reselect Home on-device.

### Medium: The helper's Max support conflicts with the host's dual-package support

Evidence: the launcher catalog includes only `com.wbd.hbomax`
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MediaLauncher.java:27`).
The manifest queries and accessibility package list also omit `com.wbd.stream`
(`device-setup/ride-starter/AndroidManifest.xml:12` and
`device-setup/ride-starter/res/xml/accessibility_service.xml:9`). The host tooling
supports both packages but always chooses `com.wbd.hbomax` when both are installed
(`device-setup/saro-control:717`).

Impact: the tablet-only launcher cannot select or remember the alternate Max
package even though the repository installs/supports it elsewhere. Keeping both
installed does not provide a fallback for the reported freeze.

Remediation: model variants explicitly, detect both, let the owner select one,
and store that choice in config. Do not infer obsolescence or region from package
name alone. Hide the unused variant after successful playback validation.

### Medium: Overlay geometry violates the project's touch-target requirement at the tested density

Evidence: overlay buttons are fixed at 84x52 raw pixels
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:557`).
The reviewed bike reports 240 dpi, making the target approximately 56x34.7 dp.
The project requires all controls to be at least 48 dp
(`plan.md:51`). Text, padding, height, and drag slop are also mostly raw pixels,
and width can become negative on displays narrower than 520 px
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:441`).

Impact: controls are harder to hit while riding and do not scale predictably with
density, font scale, display override, or future tablet hardware.

Remediation: define dimensions in dp/sp, enforce a 48x48 dp minimum hit rectangle,
use `WindowMetrics` plus system insets, and clamp width before constructing layout
params. Add screenshot and touch-bound assertions at physical 1920x1080/240 dpi,
font scales 1.0 and 1.3, and any supported display override.

### Medium: Legacy accessibility parsing can combine unrelated windows and retain stale workout data

Evidence: when no local session exists, SARO recursively collects every original-app
node from every accessibility window into shared token arrays
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:296`).
Any time-shaped token can become elapsed time
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:363`).
Values are retained for two minutes, and caches are not cleared when the overlay
detaches or a workout changes
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:998`).
`WindowManager.addView`, `removeView`, and `updateViewLayout` lifecycle failures
are not caught (`device-setup/ride-starter/src/com/pelotonhack/ridestarter/StatsOverlayManager.java:421`).

Impact: a background bike dialog/home screen can be mixed with workout nodes,
and a new workout can briefly show old values. A service/window race can terminate
the accessibility service and remove the core overlay until Android restarts it.

Remediation: isolate the active `FreestyleWorkoutActivity` window, parse one
versioned node model, require a coherent minimum field set, and reset all caches
on workout identity/elapsed rollback/detach. Guard window operations and rebuild
after `BadTokenException` or `IllegalArgumentException`.

### Medium: Exported helper components permit unauthorized state-changing actions

Evidence: `OverlayCurrentActivity` and `ConfigTransferActivity` are exported
without a caller permission (`device-setup/ride-starter/AndroidManifest.xml:78`
and `device-setup/ride-starter/AndroidManifest.xml:85`). Explicitly starting the
first creates a ride and opens media
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/OverlayCurrentActivity.java:9`).
The second treats every action other than exact Import as Export and can also
apply stored configuration (`device-setup/ride-starter/src/com/pelotonhack/ridestarter/ConfigTransferActivity.java:11`).
The APK also requests `INTERNET` despite containing no network client
(`device-setup/ride-starter/AndroidManifest.xml:10`). Several ride-launch classes
and the command service are otherwise unreachable dead surface.

Impact: any installed app can start a SARO ride, switch the foreground app, or
trigger config import/export. The broad permission/dead-component set makes the
small helper harder to audit and increases the consequences of future changes.

Remediation: make non-launcher components non-exported, or guard the ADB bridge
with an explicit shell-only/capability check. Reject unknown actions. Remove
unused permissions, services, activities, and tap-sequence code; add them back
only with tests and a documented caller contract.

### Medium: Host tooling retains brittle global-state operations without rollback

Evidence: ADB defaults to a Homebrew-specific absolute path
(`device-setup/saro-control:4`) and has no serial-selection handling. Legacy PiP
sets global display density to 90 before several failure points and has no trap
to restore it (`device-setup/saro-control:662`). Just Ride and banner helpers use
hard-coded screen taps (`device-setup/saro-control:458` and
`device-setup/saro-control:468`). The macOS `.command` wrappers do not enable
`errexit` and print "Done" even if `saro-control` fails, for example
`device-setup/Start Just Ride.command:1`. Screenshot capture leaves the remote PNG
behind and writes locally outside the documented `screenshots/` evidence path
(`device-setup/saro-control:1078`).

Impact: setup is less portable than documented, multiple attached Android devices
are unsafe, legacy failures can leave the bike at an unusable density, and GUI
wrappers can report false success.

Remediation: resolve `adb` from `PATH`, support `ANDROID_SERIAL`/`-s`, preflight a
single target, and wrap every global override in recorded-state restoration.
Retire coordinate automation where semantic nodes exist. Make wrappers propagate
exit status. Pull screenshots directly to `screenshots/`, remove remote captures,
and add a redaction checklist.

### Medium: Game timing is frame-rate dependent on the older tablet

Evidence: both `GameView` and `GameSession` clamp every frame delta to 80 ms
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameView.java:116` and
`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameSession.java:68`).

Impact: below 12.5 frames per second, a nominal 45/60/75-second game takes longer
in wall time and awards a different score for the same sensor trace. The old
MediaTek/API-30 target is exactly where long frames are plausible.

Remediation: use elapsed monotonic time for session duration and a bounded
fixed-step accumulator for physics. Cap catch-up work without slowing the game
clock. Add deterministic trace-replay tests at 60, 30, 15, and 8 fps.

### Medium: Public build and license hygiene is incomplete

Evidence: there is no CI, Android lint configuration, test source set, dependency
lock, or release pipeline. The helper's shell build emits a Java 8
bootstrap warning and compiles deprecated APIs without lint. The root applies an
MIT license generally (`LICENSE:1`), while the committed spoof profile identifies
itself as GPL-3.0-or-later (`tools/aurora-profiles/saro-rb1vo-android-tv-arm64.properties:1`)
and the repository includes patches derived from GPL upstream code. The generated
sprite sheet has no durable generation/provenance note despite the contributor
rule (`ai-contributors.md:25`). Provider UI screenshots also contain third-party
marks/content not owned by SARO.

Impact: a release cannot yet demonstrate source-to-APK reproducibility, regression
quality, or an accurate license boundary. The blanket MIT statement can be read
as licensing files and imagery the project does not own.

Remediation: add CI for clean-source build, lint, unit tests, manifest inspection,
and release checksum/certificate verification. Add `THIRD_PARTY_NOTICES` and
per-path license guidance that preserves GPL and excludes third-party screenshot
content from the MIT grant. Record sprite generation method, prompt/model/version,
post-processing, and confirmation that no third-party source asset was copied.

### Medium: The APK audit helper reports a manifest hash rather than independently measuring the file

Evidence: `audit-apks.sh` reads `base_hash` from the recovery manifest
(`tools/security/audit-apks.sh:37`), runs signature inspection on the file, and
writes the previously supplied hash without recomputing or comparing it
(`tools/security/audit-apks.sh:45` and `tools/security/audit-apks.sh:79`). It also
selects one `base.apk` per package and does not verify every split in that report.

Impact: running the audit helper directly against a stale or modified archive can
produce a TSV where the hash column does not describe the inspected base file.
That weakens the evidentiary value of the durable inventory even though Android
would reject mismatched split signers at install time.

Remediation: compute SHA-256 for every inspected file, fail on manifest mismatch,
run `apksigner verify` on every split, and emit both container and split inventory
from one command. This finding concerns audit-tool correctness only; downloaded
APK malware/provenance conclusions were explicitly outside this review.

### Medium: Recovery does not preflight device compatibility or provide transaction semantics

Evidence: backup records model/API/ABI/security-patch metadata
(`device-setup/saro-control:1227`), but restore never reads it before installing
packages (`device-setup/saro-control:1308`). Package installation begins before
helper/config validation is complete, and config import reports success after a
fixed sleep without receiving an Activity result
(`device-setup/saro-control:1170`).

Impact: an RB1VO archive can be applied to an incompatible tablet or firmware,
and any mid-restore error leaves a partially changed device with no machine-
readable resume report. Config import can fail in the Activity while the host
prints success.

Remediation: compare model, product, API, ABI, helper schema, package set, hashes,
and signer continuity before installation. Require an explicit override for a
hardware mismatch. Stage a restore journal, make each step idempotent, and return
structured completion/error status from config import rather than sleeping.

### Low: The app catalog is a compile-time list and there is no real close-app workflow

Evidence: all supported apps are hard-coded in one array
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MediaLauncher.java:27`).
Launch uses reorder-to-front but the hub offers no close/stop action
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/MediaLauncher.java:185`).
The product objective explicitly includes opening, closing, and switching apps
(`context.md:9`).

Impact: adding a service requires rebuilding SARO, and several heavy streaming
tasks can remain resident on memory-constrained hardware. A normal third-party
app cannot safely force-stop another package, so the current goal is not actually
implementable as phrased without privileged shell support.

Remediation: move catalog metadata and variants to a versioned bundled JSON model
that config can extend safely. Clarify that Home/Recents switches rather than
force-stops apps, expose Android Recents ergonomically, and avoid promising a
close operation unless a narrowly scoped privileged mechanism is accepted.

### Low: Custom-canvas Games expose almost no Android accessibility semantics

Evidence: `GameView` has one content description for the entire surface
(`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameView.java:79`),
while all mode cards, buttons, status, and results are canvas text and rectangle
hit tests (`device-setup/ride-starter/src/com/pelotonhack/ridestarter/GameView.java:411`).

Impact: TalkBack, switch access, keyboard focus, and automated UI tests cannot
discover or operate individual controls. This is also a testability limitation.

Remediation: use native Views/Compose for controls around the canvas, or implement
a virtual accessibility node provider with labels, bounds, actions, and focus.

## Open Questions

1. Is a normal SARO session intentionally an offline/local workout, or must it
   create and save an official manufacturer workout? The UX and documentation must
   choose one definition.
2. What is the exact Affernet AIDL/Parcel contract for build
   `RO.250111.A/43`, and does the service support multiple callbacks from one
   package? Which fields are vendor speed/distance/calories, if any?
3. What error bounds are acceptable for estimated speed, distance, and calories,
   and are those estimates allowed in exported ride records?
4. Should cancelled-subscription dismissal and original-app control automation be
   enabled by default, or separately consented to because they inject gestures?
5. Is the recovery threat model accidental corruption only, or must archives
   remain trustworthy after cloud transfer/untrusted storage? A plain mutable hash
   manifest answers only the former.
6. Who owns and backs up the SARO release signing key, and what is the update and
   key-compromise response plan?
7. Which Max package is correct for each supported account/device region, and can
   the unused variant be removed after protected-playback testing?
8. What on-device escape path is acceptable if SARO becomes the broken default
   Home app while USB debugging is unavailable?
9. Should live ride totals continue while Games is foreground? The current product
   language implies yes, but implementation stops accounting.
10. Are demo-game scores intentionally comparable with live scores? Current policy
    says no while current storage says yes.

## Architecture Assessment

Direct Affernet telemetry plus an Android accessibility overlay is a practical
way to remove PiP and computer dependencies on this API-30 bike. The package is
small, has no runtime third-party library dependency, keeps credentials in vendor
apps, and persists only project-owned state. The launcher, overlay, and game
experience are demonstrably integrated on the target hardware.

The current implementation is not release-grade because telemetry ownership,
ride lifecycle, accounting, overlay rendering, and privileged accessibility
automation are coupled inside one long-lived service. The next architecture
should have one sensor repository, one explicit ride engine/state machine, and
separate consumers for overlay, hub, and Games. Original-app UI automation should be
an optional adapter with a smaller trust boundary. Legacy shell/PiP code should
be quarantined outside the normal install path.

Recovery has the right high-level boundaries: project config is separate from
provider credentials, app-private logins are not extracted, and APK splits are
locally archived. Its install-set verification, signing identity, device
preflight, and failure recovery need to be made deterministic before it can be
called safe or reliable.

## Test Gaps

No unit, instrumentation, UI, shell, or CI test suite exists in the reviewed
repository. The checked-in screenshots are useful evidence of individual manual
states, but they are not repeatable assertions and do not cover the failure modes
above.

Required JVM tests:

- Ride timing across pause/resume, process death, reboot, wall-clock jumps, stale
  session expiry, and immediate transition persistence.
- Output/distance integration across hub visits, Games, overlay hide/show, event
  storms, stale samples, and reconnects.
- Affernet Parcel fixtures for valid, truncated, out-of-range, changed-version,
  error, Binder-death, null-binding, and duplicate-subscriber behavior.
- Config exact round-trip, malformed/truncated/oversized JSON, unknown schema,
  missing packages, replace/merge semantics, and atomic failure.
- Game trace replay, sensor dropout, demo eligibility, frame-rate independence,
  rewards, upgrade overflow/bounds, and deterministic scoring.
- Accessibility node matching using captured/sanitized trees for every supported
  subscription-prompt and negative-lookalike cases.

Required host-tool tests with a fake ADB executable:

- Empty/header-only/malformed manifests, extra APKs, missing splits, duplicate
  paths, traversal attempts, wrong hashes, wrong package names, signer changes,
  device mismatch, install failure, and resumable partial restore.
- Existing/missing/stale helper build, helper signer mismatch, multiple connected
  devices, paths containing spaces, command interruption, and density rollback.
- Config export/import Activity failure and explicit success acknowledgement.

Required API-30 device tests:

- Nonzero cadence, watts, and resistance together over a calibrated range, with
  comparison to official Just Ride values.
- Callback silence, Affernet force-stop/restart, accessibility disable/reenable,
  SARO process kill, low-memory kill, and cold boot during active and paused rides.
- A media-generated accessibility event flood while verifying one-second display
  updates and uninterrupted totals.
- Game play during an active ride, proving that ride totals continue and that a
  sensor dropout cannot record a live best.
- Simultaneous manufacturer and SARO ride attempts, proving deterministic
  backend selection and correct Pause/End routing.
- Overlay attachment/removal races, 48 dp hit bounds, drag edges, font scaling,
  density changes, system bars, and touch behavior over every supported app.
- Home crash/recovery and restoration of the original launcher without relying on
  a functioning SARO Activity.
- Clean wipe and restore from a read-only archive, including deliberate tampering
  and an incompatible-device refusal before the first package install.

## Verification Performed

- `ride-starter/build.sh` completed and `apksigner` verified v1, v2, and v3
  signatures. `window-agent/build.sh` also completed. The compiles emitted Java 8
  bootstrap warnings, and the helper compile emitted deprecation notices.
- Bash and zsh syntax checks passed for current shell entry points.
- `git diff --check` passed at review time.
- Live read-only ADB checks confirmed 1920x1080, 240 dpi, API 30, and security
  patch `2022-10-05`.
- `shellcheck` was not installed, so no ShellCheck analysis was performed.
- Downloaded APK malware/provenance was intentionally excluded from this review.
