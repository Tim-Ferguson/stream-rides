# Sensor Recovery Live Test

- Test date: 2026-08-11
- Target: RB1VO exercise bike, Android 11
- SARO: 0.5.2 (`versionCode` 9)

## Scope

This test verified that the Home screen reports overlay and direct-sensor
readiness without ADB diagnostics, offers the correct tablet-side recovery
action, and returns to fresh telemetry after Accessibility reconnects. The test
did not start a new ride or replace the owner's existing last-ride summary.

## Procedure And Results

1. Installed SARO 0.5.2 as a same-signer in-place update. Android retained the
   enabled-service setting, but the Accessibility service was briefly not
   connected. Home reported **OVERLAY OFF** and displayed **ENABLE OVERLAY**.
2. Allowed Android's service manager to reconnect. Home changed to
   **SENSORS READY** and showed the current stationary sample:
   `CAD 0 | 0 W | RES 55`.
3. Temporarily replaced the enabled Accessibility component list with a
   non-existent test component. Home returned to **OVERLAY OFF** without
   starting an invalid local ride.
4. Tapped **ENABLE OVERLAY** on the touchscreen. Android resumed
   `Settings$AccessibilitySettingsActivity`, proving that recovery does not
   require a computer.
5. Restored SARO's exact Accessibility component and global Accessibility
   state, then returned Home. Both the SARO process and Affernet service were
   running, and Home returned to **SENSORS READY** with `CAD 0`, `0 W`, and
   `RES 55`.

The Accessibility setting was restored exactly after the controlled test.
Media volume remained zero and no ride was active.

## Standalone Start Regression

After the recovery cycle, SARO exported its configuration from the touchscreen
and started a new local ride with one tap from Home. The ride resumed the
existing signed-in Hulu task and displayed time, estimated speed and distance,
cadence, watts, resistance, energy, calories, and all four ride controls. The
in-video app drawer opened without replacing Hulu's resumed activity, and its
**SARO HOME** action returned to the active ride screen.

The Home confirmation dialog ended the temporary ride. Importing the pre-test
configuration from **Manage apps** restored the prior
`LAST LOCAL 12:59 | 0 KJ | 0.00 MI` summary. No host process participated in
the ride, overlay, app switcher, end flow, or configuration round-trip; ADB was
used only to inject equivalent touch coordinates and collect evidence.

## Behavior

Home distinguishes five states: overlay disabled, sensor service connecting,
waiting for the first sample, stale sample, and ready. A stale or waiting state
exposes **RECONNECT**, which drops and recreates SARO's Affernet Binder binding.
Pressing **START LOCAL RIDE** while telemetry is unavailable requests the same
reconnect and refuses to create a ride until a fresh sample arrives.

This reconnect can repair SARO's Binder registration. It cannot repair malformed
packets or a fault inside Affernet serial implementation; those remain
firmware/service conditions outside SARO's process.

## Evidence

The original recovery screenshots were removed from the publishable tree. They
showed transient device and provider state rather than current interface
layouts; the test sequence and observed states remain recorded above.

The state evaluator has host-side coverage for every status, and the Android
helper compiled and installed successfully before this live test.
