# Zwift Sensor Bridge

SARO 0.4.0 can expose direct sensor telemetry as standard Bluetooth
Low Energy Cycling Power and Cycling Speed/Cadence services. This is an
experimental compatibility bridge, not a manufacturer- or Zwift-supported feature.
For the exact current stopping point and resume checklist, see
[`ZWIFT_HANDOFF.md`](../ZWIFT_HANDOFF.md).

## Runtime Path

```text
Affernet Binder
        |
        v
SARO SensorRepository
        |
        v
BLE Cycling Power (0x1818) + CSC (0x1816)
        |
        v
Zwift Companion on a phone
        |
        v
local Wi-Fi relay to Zwift on the bike
```

A computer is not required during a ride, but a phone running Zwift Companion
is required as the Bluetooth central and network relay. Android successfully
starts SARO's peripheral advertisement and GATT server, but the bike's own
Bluetooth scanner cannot discover an advertisement emitted by the same
controller. The direct same-tablet path is therefore unavailable through
public Android BLE APIs.

## Use

1. Put the bike and phone on the same local network and open Zwift Companion on
   the phone.
2. In SARO, open **Manage apps** and turn **Zwift Companion sensor bridge** on.
   Wait for its status to become `ADVERTISING`.
3. Launch Zwift from SARO. On Zwift's pairing screen, choose the phone/Companion
   pairing route described in [Zwift's device-connection guide](https://support.zwift.com/connecting-your-devices-for-cycling-HJIFDrw5S).
4. Select the bike, normally advertised under the tablet Bluetooth name
   `PLTN-RB1VO-2`, as the power source and cadence source.
5. SARO reports `COMPANION CONNECTED` after a GATT connection and
   `COMPANION RECEIVING` after the phone subscribes to either measurement.

Turn the bridge off when it is not needed. If SARO turned Bluetooth on, it
restores Bluetooth to off when the bridge stops. The enabled preference and
Bluetooth ownership marker survive process restarts. The owner-visible enabled
preference is also included in SARO configuration export/import.

## Data Contract

- Power uses the Bluetooth SIG Cycling Power Service (`0x1818`) and
  instantaneous-power characteristic (`0x2A63`).
- The power service also exposes its mandatory feature (`0x2A65`) and sensor
  location (`0x2A5D`) characteristics. It identifies itself as one complete,
  non-distributed whole-bike sensor at the `Other` location.
- Cadence uses the Bluetooth SIG Cycling Speed and Cadence Service (`0x1816`)
  with crank revolutions and 1/1024-second event time in characteristic
  `0x2A5B`.
- Notifications are published once per second after a central subscribes. One
  server-wide queue waits for Android's `onNotificationSent` callback before
  sending the next power or cadence notification.
- sensor samples older than 2.5 seconds are emitted as zero power and no new
  crank revolutions. This stricter bridge threshold prevents a stalled sensor
  service from replaying a high-power sample. SARO never invents live Zwift
  telemetry.
- Resistance is not sent. The tested Bike has manual resistance and SARO does
  not claim FTMS trainer-control support.

The UUIDs and payloads follow the Bluetooth SIG
[Cycling Power Service](https://www.bluetooth.com/specifications/specs/cycling-power-service/)
and
[Cycling Speed and Cadence Service](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/CSCS_v1.0/out/en/index-en.html).
SARO's helper still requests no Internet permission; the phone and Zwift use
their own network connection.

## Verified Boundary

Live tests on the RB1VO bike verified all of the following:

- SARO can turn Bluetooth on after explicit owner opt-in.
- Both GATT services, both measurement characteristics, both feature
  characteristics, the mandatory power sensor-location characteristic, and
  both notification descriptors register and start.
- BLE advertising reports success even though this controller reports no
  multiple-advertisement support.
- SARO removes the GATT services and restores a clean Bluetooth-off baseline.
- If Bluetooth was already on before the bridge started, SARO leaves it on.
- The bridge survives an in-place SARO update and a full bike reboot, with
  accessibility and all nine GATT handles re-registering automatically.
- Power payload, feature and location values, endianness, stale-data handling,
  crank integration, and gap bounding pass host unit tests.
- The bridge preference appears in a live schema-v2 export.
- A separate owner-pedaled local ride verified that the bridge's upstream direct
  source produces changing nonzero cadence, watts, and resistance. This does not
  verify phone subscription or BLE delivery.

An end-to-end Zwift Companion subscription and avatar movement still require a
real phone test. Until that succeeds, this feature is Android-layer verified,
not ride-integrated. The owner shelved that phone-side test on 2026-08-11. The
tablet has 2 GB RAM, below Zwift's current 3 GB Android minimum, and Zwift
produced one Android-not-responding dialog after sitting idle. See
[the storage profile](storage.md) for the separate installation constraints.

The same-tablet loopback and reviewed bridge captures were removed from the
publishable tree under the neutral-layout-only screenshot policy. The measured
results and limitation remain recorded above.
