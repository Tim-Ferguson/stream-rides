# SARO Tablet Storage Profile

The tested bike exposes a 9.4 GB `/data` filesystem. Zwift installation is the
dominant storage constraint, not cached bike media.

## Measured Findings

| Item | Measured use | Decision |
| --- | ---: | --- |
| original app private data | about 20 MB | Keep intact |
| original app cache | under 1 MB | Too small to matter |
| bike external files | about 24 KB | No cached rides or videos found |
| Zwift base APK | 45,268,623 bytes | Keep installed |
| Zwift installer OBB | 1,756,583,202 bytes | Keep in the local recovery bundle, not on the bike after expansion |
| Zwift expanded resources | 3,512,305,801 bytes, 22,291 files | Required for runtime |
| Codex temporary plugin clone | 119 MB | Removed; no credentials or configuration were deleted |
| Termux npm download cache | about 124 MB | Cleared; installed packages and credentials were retained |

Zwift 1.119.0 hard-codes a 3,780,741,257-byte free-space check before
expansion. After expansion, its ordinary launcher still checks for the OBB
before checking its completion cookie. SARO 0.4.0 therefore opens the exported
`ZwiftMainActivity` directly. The normal package launch remains a fallback for a
future Zwift version that changes this behavior.

## Current Bike Profile

Firefox, Kindle, Audible, and Netflix were reinstalled from exact same-signer
backups after a temporary keep-data removal. Kindle and Netflix visibly retained
their account state. The working `com.wbd.stream` Max mobile package is installed
and retained its signed-in catalog across reboot. Zoom, AirScreen, the
playback-crashing `com.wbd.hbomax` TV build, the playback-incompatible Prime TV
build, and obsolete FLauncher remain APK-dormant with private data retained.
Their exact APKs are in historical ignored recovery bundles; SARO's Manage Apps
screen can open the app-store listing when enough space is available.

The final measured free space after SARO 0.4.0 installation and app cold starts
was about 1.1 GB. Do not reclaim space by clearing original app package data,
deleting unknown vendor assets, or removing Zwift's expanded private resources.
After later mobile Prime Video and SARO 0.5.2 testing, then removal of the Prime
TV APK, `/data` reported about 927 MB free. FLauncher and Prime TV had no
installed APK at that checkpoint. Installing Max mobile and then removing Max
TV left about 913 MB free at the SARO 0.5.3 canonical checkpoint.
The final 0.6.6 live audit reported 644,084 KiB free, below SARO's 768 MiB
maintenance warning threshold. An earlier pass reported 724,028 KiB; Android's
app/cache activity accounts for continuing variance. No private app data,
original-app assets, or Zwift resources were removed to raise that number.

## Recovery Boundary

`pm uninstall -k` retention is a device-local maintenance state, not portable
backup. A factory reset, user-data wipe, ordinary uninstall, or firmware update
that clears `/data` destroys it. The local recovery bundle stores exact APKs and
the hash-pinned Zwift OBB, but provider credentials and Zwift's expanded private
data still cannot be exported.
