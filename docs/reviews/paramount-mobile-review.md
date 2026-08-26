# Paramount+ Mobile Migration Review

Date: 2026-08-12

Scope: SARO 0.6.3 package aliases, launcher resolution, icon selection,
manifest visibility, tests, and release assertions.

## Independent Findings

The independent reviewer found one release-blocking regression: the first
shared alias implementation offered the known-incompatible Max and Prime Video
TV packages as runtime fallbacks. It also requested an exact 0.6.3 helper hash,
a package-level security assessment for `com.cbs.app`, and stronger migration
coverage.

## Resolution

- **Fixed:** `launchCandidates` now falls back only from Paramount+ mobile to
  Paramount+ TV. Max TV and Prime TV remain migration-only aliases and cannot
  reappear when their mobile packages are absent. A focused unit test and a
  release-gate rejection assert this boundary.
- **Fixed:** the reviewed helper was rebuilt reproducibly and its exact local
  bytes are pinned in the private recovery bundle.
- **Fixed:** the security audit now records the Paramount+ mobile base and
  splits, CBS Interactive Mobile signer, Play Source Stamp, permissions,
  provenance, and package-level decision.
- **Verified live:** both Paramount+ packages were installed. Selecting the
  existing SARO tile resumed `com.cbs.app`, proving mobile preference. A
  schema-v2 export canonicalized saved order, desired/installed state, and last
  app from `com.cbs.ott` to `com.cbs.app`. The in-place reviewed update retained
  the same `system_server` PID and exactly the ride accessibility service.

No privacy or accessibility-scope regression was found. The provider remote
remains limited to the TV package and disabled by default.
