# SARO 0.5.2 Full-Reboot Live Test

Date: 2026-08-11

## Scope

- Hardware: `PLTN-RB1VO-2` / `RB1VO`
- Android: 11 / API 30
- Helper: locally signed SARO 0.5.2; exact bytes remain in private recovery
- Starting state: SARO Home, no active ride, no accessibility-overlay window

ADB requested the reboot, injected touchscreen events, inspected Android state,
and captured evidence. It did not launch SARO, start a service, restore a setting,
or participate in the ride runtime. This was a complete Android software reboot,
not a power-removal test.

## Pre-Reboot State

The bike had been up for 6 hours and 4 minutes. SARO was the default Android
Home, its Accessibility service was enabled, and Home showed fresh stationary
telemetry: `CAD 0 | 0 W | RES 55`. The exported configuration was retained for
an exact post-reboot comparison.

No type-2032 accessibility overlay was active.

## Autonomous Recovery

ADB reconnected about 15 seconds after the reboot request. Android reported
`sys.boot_completed=1` at the next check, approximately 30 seconds after the
request. Without an `am start` command, Android resumed:

```text
com.pelotonhack.ridestarter/.MainActivity
```

The first post-boot frame already showed `SENSORS READY`,
`CAD 0 | 0 W | RES 55`, `START LOCAL RIDE`, and the saved
`LAST LOCAL 12:59 | 0 KJ | 0.00 MI` summary. The exact SARO Accessibility
component remained enabled, SARO remained the default Home, and the
configuration hash was unchanged.

The original reboot screenshot was removed from the publishable tree under the
neutral-layout-only screenshot policy.

## Provider and Ride Regression

The visible Hulu tile opened `com.hulu.BottomNavActivity` directly into the
signed-in catalog. Returning with the tablet Home key restored SARO. One tap on
`START LOCAL RIDE` then reopened Hulu with the complete accessibility overlay:
elapsed time, estimated speed and distance, cadence, watts, resistance, energy,
estimated calories, Apps, Pause, and End.

Android reported one live type-2032 overlay while Hulu remained the resumed
Activity. Pause changed the control to Resume and froze elapsed time. The
five-second End guard rejected a delayed second tap as designed; two deliberate
taps one second apart ended the ride and removed the overlay. Android then
reported zero type-2032 windows.

The short test summary was removed through SARO's own touchscreen Import action.
Home returned to the saved 12:59 summary, and the configuration again matched
the pre-reboot bytes exactly.

## Package Persistence

After reboot, official mobile Prime Video
`com.amazon.avod.thirdpartyclient` still had an installed APK path. The removed,
playback-incompatible Prime TV package
`com.amazon.amazonvideo.livingroom` still had no APK path. Reboot therefore did
not undo the pruned current-state package selection.

## Result

PASS. The finalized SARO 0.5.2 state recovered from a real Android reboot and
started a complete local ride over a retained signed-in provider without any
host-side runtime component.
