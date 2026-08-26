# Direct Sensor Live Test

- Test date: 2026-08-11
- Target: RB1VO exercise bike, Android 11
- SARO: 0.4.0

## Scope

This owner-assisted test verified SARO's direct Affernet telemetry and local
ride controls with real physical pedaling. It did not test the Zwift Companion
bridge. The bridge remained off, and Zwift appeared only because it happened to
be SARO's previously selected background app.

## Procedure And Results

1. Ended a stale six-hour development ride and confirmed a clean
   **START LOCAL RIDE** state.
2. Started a fresh SARO Local Ride without using a manufacturer workout.
3. Captured the stationary baseline at `00:26`:
   `CAD 0`, `WATTS 0`, `RES 4`, `0.00 mi`, and `0 kJ`.
4. Pedaled at moderate resistance and captured a live sample at `01:27`:
   `CAD 51`, `WATTS 40`, `RES 37`, estimated `14.2 mph`, `0.10 mi`, and
   `1 kJ`.
5. Increased resistance and changed cadence. The second sample at `02:36`
   showed `CAD 15`, `WATTS 11`, and `RES 55`, proving the values were changing
   independently rather than retaining one sample.
6. Stopped pedaling. At `03:57`, cadence and watts had returned to zero while
   resistance remained live at `55`; distance and energy remained at their
   accumulated values.
7. Paused at `05:18`, waited more than five seconds, and confirmed the timer
   remained exactly `05:18` with the control changed to **RESUME**.
8. Resumed and confirmed the timer advanced with the control restored to
   **PAUSE**.
9. Confirmed that one isolated **END** tap did not terminate the ride after its
   five-second guard expired.
10. Issued two deliberate **END** taps within the guard window. The overlay was
    removed and SARO persisted `LAST LOCAL 13:36 | 3 KJ | 0.44 MI`.

No matching `RideSensor`, `StatsOverlay`, or `AndroidRuntime` error was present
in the isolated readable Android main log after the test.

## Evidence

The original live-device screenshots were reviewed during development and then
removed from the publishable tree because they contained attributable ride
telemetry and history. The neutral current strip layout is retained in the
README; the measurements above are the text record of this physical test.

## Conclusion

The direct tablet-side sensor path is physically verified. Cadence, watts, and
resistance update together during real pedaling; cadence and watts return to
zero after pedaling stops; derived metrics accumulate; pause/resume works; and
the guarded end flow saves a summary and removes the overlay.

This closes the previously pending nonzero active-pedaling validation. It does
not certify BLE subscription, Zwift Companion, or Zwift avatar movement; those
tests are shelved until the owner explicitly resumes them.
