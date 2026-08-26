# SARO Release Signing

Android permits an in-place update only when the new APK has a compatible
signer. Losing a private signer therefore turns future upgrades into an
uninstall/reinstall operation, which clears SARO data and can disrupt Home and
accessibility configuration.

## Helper Builds

`device-setup/ride-starter/build.sh` defaults to a local debug build. Its ignored
debug keystore is suitable only for development on one checkout. A release build
fails closed unless all required private-key variables are provided:

```sh
export SARO_KEYSTORE=/secure/path/saro-release.keystore
export SARO_KEY_ALIAS=saro
export SARO_KEYSTORE_PASSWORD='...'
export SARO_KEY_PASSWORD='...'
device-setup/ride-starter/build-release.sh
```

`SARO_KEY_PASSWORD` may be omitted when it matches the keystore password. The
result is `build/ride-starter-release.apk`; the build verifies its signature and
writes a SHA-256 sidecar. Never place passwords in shell history, committed
files, CI logs, or screenshots.

Before installing an update, compare its signer with the installed package. Use
`adb install -r` only for a reviewed same-package, same-signer artifact. Keep an
encrypted offline copy of the keystore, alias, passwords, signer fingerprint,
and a known-good signed APK.

## Aurora Store

The compatibility patch under `tools/aurora-store/` also fails closed for a
normal release without private signing. `create-owner-signing.sh` creates
ignored per-installation material and refuses to overwrite it. The explicit public-test-key
mode changes the package ID, version suffix, and label so it cannot masquerade
as or update the trusted `com.aurora.store` build.

Each owner should generate a separate Aurora key. Never publish a private key,
`signing.properties`, built third-party APK, or password. Rebuild only from the
pinned upstream commit and tracked patch, then record the resulting APK and
signer hashes in the local recovery bundle.

Run `tools/verify-release.sh` before publishing source. It checks repository
hygiene, permissions, retired implementation references, tests, APK metadata,
signatures, and personal-identifier leakage.
