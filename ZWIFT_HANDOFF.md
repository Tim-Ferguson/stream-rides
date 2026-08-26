# Zwift Handoff

Last updated: 2026-08-11

**Status: shelved at the owner's request. Do not resume Companion testing until
the owner explicitly asks to continue.**

This document records the exact stopping point for SARO's experimental Zwift
sensor bridge. Start here when resuming the work. The full design and BLE data
contract remain in [`docs/zwift.md`](docs/zwift.md).

## Current Result

SARO 0.4.0 is installed on the target RB1VO exercise bike running Android 11.
Zwift 1.119.0 launches from SARO with the ride-stat overlay visible. SARO can
advertise the bike's direct watts and cadence as standard BLE Cycling Power and
Cycling Speed/Cadence services.

The intended runtime path is:

```text
Affernet sensors
        |
        v
SARO direct SensorRepository
        |
        v
BLE Cycling Power + CSC advertisement
        |
        v
Zwift Companion on a phone
        |
        v
local Wi-Fi relay to Zwift on the bike
```

A computer is not required during a ride. A phone running Zwift Companion is
required because the tablet's Bluetooth controller could not discover its own
BLE peripheral advertisement through Android's public APIs.

The implementation baseline and exact locally signed APK remain recorded in
the private engineering repository and recovery bundle. Their identifiers are
intentionally omitted from the history-free public release.

## Verified On The Bike

- Zwift launches from SARO after its one-time resource expansion.
- The SARO overlay remains visible over Zwift.
- The owner-controlled bridge toggle appears under **Manage apps**.
- Enabling the bridge turns Bluetooth on when needed and reaches
  `ADVERTISING`.
- Both GATT services and all nine expected handles register:
  Cycling Power measurement, CCCD, feature, and mandatory sensor location;
  CSC measurement, CCCD, and feature; plus both services.
- One server-wide queue waits for Android's notification-completion callback
  before sending another power or cadence notification.
- An in-place SARO update while advertising preserves Bluetooth and restarts
  the complete bridge.
- The bridge and accessibility runtime return after a full tablet reboot.
- Turning the bridge off removes its GATT services.
- If SARO turned Bluetooth on, it restores Bluetooth to off afterward.
- If the owner already had Bluetooth on, SARO leaves it on afterward.
- The bridge preference is included in schema-v2 configuration export/import.
- Payload encoding, stale-sample handling, crank integration, and rollover pass
  host tests.
- The complete release gate passes all 57 host tests from a clean checkout.
- A separate owner-pedaled local ride verified the bridge's upstream direct
  telemetry source with changing nonzero cadence, watts, and resistance.

## Not Yet Verified

The Android peripheral layer and upstream physical telemetry are complete, but
the end-to-end Zwift path has not yet been certified. These shelved checks
require owner interaction:

1. Subscribe to SARO's BLE services from a real phone running Zwift Companion.
2. Confirm the SARO bridge advances from `ADVERTISING` to
   `COMPANION CONNECTED`, then `COMPANION RECEIVING`.
3. Confirm Zwift displays nonzero power and cadence while the bike is pedaled.
4. Confirm that the Zwift avatar moves and remains responsive for a useful test
   interval.
5. Confirm the bridge shuts down cleanly after that real Companion connection
   and restores the owner's prior Bluetooth state.

Resistance is visible in SARO but is intentionally not transmitted to Zwift.
This bike has manual resistance, and SARO does not implement or claim FTMS
trainer control.

## Physical Prerequisite Complete

On 2026-08-11, an owner-pedaled Local Ride produced `CAD 51`, `WATTS 40`, and
`RES 37` together. A second sample changed to `CAD 15`, `WATTS 11`, and
`RES 55`. After pedaling stopped, cadence and watts returned to zero while
resistance remained live. Pause/resume, the five-second guarded end flow,
overlay removal, and the persisted ride summary also passed. See
[`docs/direct-sensor-live-test.md`](docs/direct-sensor-live-test.md).

## Remaining Owner Work When Resumed

The final Zwift test needs a phone:

1. Install or open Zwift Companion on the phone and sign into the same Zwift
   account used on the bike.
2. Put the phone and bike on the same local network and enable Bluetooth on
   the phone.
3. In SARO, open **Manage apps** and turn **Zwift Companion sensor bridge** on.
4. Wait for the bridge status to become `ADVERTISING`.
5. Launch Zwift from SARO and select its phone/Companion pairing route.
6. Select the bike, normally advertised as `PLTN-RB1VO-2`, as both the power
   source and cadence source.
7. Pedal for at least 30 seconds with moderate resistance.
8. Compare Zwift's power and cadence with the SARO overlay and confirm avatar
   movement.
9. Record successful pairing and moving-avatar results without publishing
   account, device-address, or workout-history screenshots.
10. Turn the bridge off and confirm Bluetooth returns to its prior state.

Keep any diagnostic captures outside the public repository. Do not commit an
account name, email address, device address, or other private information.

## Resume Checklist

From the repository root:

```sh
git status --short --branch
git pull --ff-only
/opt/homebrew/bin/adb get-state
cd device-setup
./saro-control check
./saro-control status
```

Before changing bridge code, run the existing tests:

```sh
tools/tests/test-ride-accumulator.sh
tools/tests/test-host-tools.sh
tools/verify-release.sh
```

If code changes are required, deploy through the existing helper workflow:

```sh
cd device-setup
./saro-control deploy-helper
```

Useful bridge diagnostics during a test:

```sh
/opt/homebrew/bin/adb logcat -c
/opt/homebrew/bin/adb logcat -s ZwiftBleBridge
```

Sanitize any captured logs before committing them because Bluetooth callbacks
can contain device addresses.

## Expected Status Sequence

```text
OFF
STARTING
ADVERTISING
COMPANION CONNECTED
COMPANION RECEIVING
```

`ADVERTISING` proves only that Android started the BLE peripheral. It does not
prove that Companion subscribed. `COMPANION RECEIVING` is the first tablet-side
evidence that the phone enabled at least one measurement notification. Avatar
movement is still the final end-to-end proof.

## Failure Triage

- If SARO's own cadence, watts, or resistance remain blank or zero while
  pedaling, debug the Affernet sensor path before changing BLE code.
- If the bridge remains at `STARTING`, inspect `ZwiftBleBridge` logs and Android
  Bluetooth state.
- If it reaches `ADVERTISING` but the phone finds nothing, verify Companion's
  Bluetooth/nearby-device permission, phone Bluetooth, and that Companion is
  active.
- If it reaches `COMPANION CONNECTED` but not `COMPANION RECEIVING`, inspect the
  Companion pairing selection and GATT subscription logs.
- If values appear in Zwift but the avatar does not move, capture the pairing
  screen and Zwift state before changing payload encoding.
- If Zwift freezes, record whether it happened during pairing, loading, or
  riding. The tablet has 2 GB RAM, below Zwift's documented 3 GB Android
  minimum, and one idle Android-not-responding dialog was previously observed.

Do not replace the current bridge architecture solely because same-tablet BLE
scanning fails. That limitation was reproduced independently; the phone relay
is the intentional path.

## Current Device State At Handoff

- USB ADB connection: available when last checked.
- SARO bridge preference: off.
- Android Bluetooth: off.
- Android media volume: `0`.
- Active SARO Local Ride: none; the physical test ended and saved its summary.
- Owner-local recovery bundle: `local-backups/saro-20260811-final` where still
  present; it is intentionally ignored by Git.

No additional bike changes are required until the owner is ready for the
phone-based Companion test and explicitly unshelves this work.
