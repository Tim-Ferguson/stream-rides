# SARO Configuration Schema v2

SARO exports project-owned settings as JSON. The current schema version is `2`.
Version `1` remains import-compatible; unknown versions fail closed. Import files
larger than 1 MiB are rejected.

## Shape

```json
{
  "schemaVersion": 2,
  "launcher": {
    "appOrder": ["com.example.media"],
    "hiddenPackages": [],
    "installedPackages": ["com.example.media"],
    "desiredPackages": ["com.example.media"],
    "lastPackage": "com.example.media"
  },
  "overlay": {
    "x": 100,
    "y": 20,
    "rideStart": {
      "x": 1554,
      "y": 318
    },
    "subscriptionPromptAutomation": false,
    "zwiftCompanionBridge": false,
    "tvRemote": {
      "x": 1554,
      "y": 318
    }
  },
  "games": {
    "credits": 0,
    "sessions": 0,
    "scores": {},
    "upgrades": {}
  },
  "lastLocalRide": {
    "available": false
  }
}
```

`appOrder` controls launcher ordering. `hiddenPackages` controls visibility.
`installedPackages` records what existed at export time, while
`desiredPackages` is the owner's reinstall intent. `lastPackage` supports the
overlay's return-to-video action. Import replaces these project-owned sets
rather than silently merging stale preferences.

Overlay coordinates are clamped to `-10000..10000`.
`subscriptionPromptAutomation` is an explicit owner preference and defaults to
false. Imports also accept the legacy `pelotonAutomation` key so older backups
remain usable, but new exports never write it. `zwiftCompanionBridge` is also an
explicit owner preference and defaults to false. Restoring it as true starts
SARO's BLE Cycling Power/cadence bridge when the accessibility runtime is
available, so only import a configuration whose behavior you have reviewed.
The optional `rideStart` object records the dragged position of the compact
`START RIDE` control shown after SARO launches media. It is omitted until that
control has been moved.
The optional `tvRemote` object records the dragged position of SARO's
provider-navigation remote. It is omitted until a position has been saved, so
untouched older schema v1/v2 files retain the default position and their prior
round-trip shape.
The `games` object contains credits, session count, per-mode best/last scores,
and upgrade levels.

`lastLocalRide` is a summary only; it does not resume an active ride or create a
manufacturer workout record.

## Transfer Contract

The device writes `saro-setup.json` and `saro-setup.status` atomically in
SARO's external-files directory. Host requests carry a unique request ID. The
host accepts a transfer only after reading a status object with the same request
ID and action and `ok: true`; stale acknowledgements are ignored.

The importer accepts the legacy configuration filename used through SARO 0.6.5.
Recovery bundles may contain either that filename or `saro-setup.json`, but not
both. Fresh exports and recovery bundles use only the SARO filename.

The file never contains passwords, cookies, DRM keys, provider tokens, Android
app-private data, active ride state, signing keys, or device serial numbers.
Provider sign-in must be restored by each provider after destructive data loss.
