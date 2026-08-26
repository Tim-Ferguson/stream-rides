# Privacy and Secret Audit

Audit date: 2026-08-12

## Public Tree Result

The proposed history-free tree contains no detected personal name, email,
workstation home path, unique tablet serial, password, API token, private key,
keystore, signing properties, APK, recovery bundle, or owner-specific build
fingerprint. As an additional publication boundary, the final export excludes
the entire private screenshot directory. No device capture is a public-release
input, even when it was previously reviewed as neutral.

Official third-party package hashes, signing certificates, package IDs, the
tested hardware model, firmware fingerprint, Android version, and security
patch remain. These are compatibility and provenance data shared by a device or
software class, not identifiers for one physical unit. Locally signed SARO and
app-store fingerprints are intentionally omitted because they correlate to one
operator's private signing identity and cannot be reproduced by another owner.

## Private History

The private Git history contains an author's real Git identity, a unique USB
serial, old absolute workstation paths, an old personal Java namespace, and
historical screenshots. Automated secret scans found no committed credentials
or private keys, but history still must not be published. The release process
exports only the final tracked tree and creates a new repository with one root
commit.

## Ignored Local State

Ignored files contain active signing material and machine-local state,
including private keystores, a password file, an app-store
`signing.properties`, generated debug signing state, downloaded APKs, recovery
bundles, logs, and a nested third-party source checkout. These files are useful
for upgrades and recovery but are never publication inputs. Private signing
files are mode `0600`; generated debug material is not treated as a release
identity.

A secret scanner intentionally finds seven credential records when pointed at
the entire workspace because that scan includes the ignored signing files. This
is expected and is why a workspace copy is prohibited. The same scanner must
report no findings when run against the exported tracked-tree snapshot.

Use `tools/export-public-snapshot.sh`, not a filesystem copy or archive of the
workspace. The exported tree is independently checked by
`tools/audit-public-tree.sh` and pinned Gitleaks before it is published.

## Verification Scope

- Full reachable Git history and unreachable objects were scanned for common
  secrets and personal identifiers.
- The final tree is scanned for identity patterns, unique serial formats,
  email addresses, credential markers, token formats, sensitive filenames,
  owner-build fingerprints, unexpected MIME/extension pairs, unmanifested
  binaries, screenshot files, and screenshot references.
- Every historical screenshot was OCR-scanned before removal; no typed
  credential or active authentication code was found.
- Previously retained neutral images remain private and are excluded from the
  history-free export.

Pattern and malware scans reduce risk but cannot prove that arbitrary data or a
third-party binary is harmless. Review the final root commit and public diff
before every release.
