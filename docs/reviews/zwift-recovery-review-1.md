# Zwift Bridge and Recovery Review 1

Date: 2026-08-11

Scope: SARO 0.4.0 BLE Cycling Power/CSC bridge, Bluetooth lifecycle, recovery
journal, and OBB restore path.

## Findings

1. High: GATT notifications were sent back-to-back without waiting for
   `onNotificationSent`, so cadence or later-central notifications could be
   dropped.
2. High: Cycling Power omitted mandatory Sensor Location (`0x2A5D`).
3. High: a completed restore journal could suppress APK and OBB restoration
   after a later wipe on the same serial.
4. Medium: callbacks from an older rapid OFF/ON generation could mutate a new
   GATT server.
5. Medium: Bluetooth ownership was cleared when `disable()` was accepted rather
   than after observing `STATE_OFF`.
6. Medium: the general five-second sensor freshness window could replay stale
   high power for too long in Zwift.
7. Medium: OBB restore lacked capacity preflight and wrote directly to the final
   filename.
8. Medium: recovery and Zwift documentation overstated what the code verified.

The payload byte order, crank rollover, stale zero behavior, configuration
wiring, OBB identity, and manifest validation were otherwise coherent. The
review was read-only; payload tests, 49 host tests, shell syntax, and offline
bundle verification passed at the reviewed point.
