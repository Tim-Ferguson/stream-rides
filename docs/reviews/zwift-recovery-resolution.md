# Zwift Bridge and Recovery Resolution

Date: 2026-08-11

All code-level findings from both Zwift/recovery reviews are resolved:

- one server-wide notification is outstanding at a time and completion advances
  the bounded latest-value queue;
- the complete Cycling Power service includes Sensor Location and declares one
  non-distributed whole-bike sensor;
- every GATT callback is generation-scoped and request handling is serialized on
  the service handler;
- SARO waits for Bluetooth `STATE_OFF` before clearing ownership, using an
  application-scoped watcher that survives accessibility-service destruction;
- Zwift uses a 2.5-second freshness threshold;
- journals are resume hints only, with live APK, OBB, accessibility, config, and
  Home revalidation;
- stale `.saro-part` files are removed before capacity checks; OBBs are checked
  again after APK installation and immediately before transfer, verified in
  staging, atomically renamed, and verified at the final path.

Verification passed with payload compilation/tests, 57 fake-device
recovery/security tests, and live RB1VO checks for all nine GATT handles,
one-shot Bluetooth enable, both ownership baselines, in-place package update,
cold reboot, automatic accessibility/GATT recovery, and clean shutdown. The
bike was returned to Bluetooth off and media volume zero.

Deferred: a real phone must subscribe through Zwift Companion while someone
pedals, demonstrating alternating power/cadence delivery and avatar movement.
