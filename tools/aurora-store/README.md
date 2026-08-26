# Aurora Store compatibility build

This directory contains the source patch used to build Aurora Store for the
RB1VO Android TV ARM64 setup. It does not contain or redistribute an Aurora
Store APK.

## Pinned upstream

- Repository: `https://gitlab.com/AuroraOSS/AuroraStore.git`
- Commit: `78fa933a080b16587624529cf854e5440c7d2e9f`
- Upstream version at that commit: `4.8.4` (`versionCode 76`)

The patch makes four compatibility changes:

1. Uses an item index in `StreamCarousel` keys so duplicate package entries do
   not crash the home carousel.
2. De-duplicates spoof profiles by both `Build.PRODUCT` and `Platforms`, keeping
   the ARM64 RB1VO profile alongside an upstream profile with the same product.
3. Keys device-list rows by both product and platform to avoid duplicate Compose
   keys on the device profile page.
4. Refuses release builds without private signing. An explicit unsafe development
   override uses Aurora Store's documented AOSP test key with a distinct package
   ID, version suffix, and visible label.

## Requirements

- Git
- A 64-bit JDK 21, with `JAVA_HOME` set to that JDK
- Android SDK Platform `android-37.0`
- Android SDK Build Tools `37.0.0`
- Android SDK Command-line Tools and `ANDROID_HOME` set to the SDK root, or an
  Aurora Store `local.properties` file containing `sdk.dir=<android-sdk-path>`
- Network access for the Gradle wrapper and Maven dependencies

The pinned project uses Gradle `9.5.0`, Android Gradle Plugin `9.3.0`, and Kotlin
`2.4.10`; the checked-in Gradle wrapper downloads the required Gradle version.

## Apply and build

Run these commands from a directory containing this repository and the new
Aurora Store checkout as siblings. Replace `<this-repository>` with this
repository's directory name.

```sh
git clone https://gitlab.com/AuroraOSS/AuroraStore.git AuroraStore
cd AuroraStore
git checkout --detach 78fa933a080b16587624529cf854e5440c7d2e9f
git apply ../<this-repository>/tools/aurora-store/0001-rb1vo-compatibility.patch
# From the SARO checkout, create a private per-installation release key once:
# tools/aurora-store/create-owner-signing.sh
./gradlew :app:assembleVanillaRelease
```

The resulting APK is:

```text
app/build/outputs/apk/vanilla/release/app-vanilla-release.apk
```

Import `tools/aurora-profiles/saro-rb1vo-android-tv-arm64.properties` in Aurora
Store and select it as the spoof device profile after installation.

## Signing warning

Without `signing.properties` at the Aurora checkout root, a release task fails. Configure a private
operator-controlled key through Aurora Store's `signing.properties` mechanism and
protect that key outside version control. Android updates must use the same key
as the installed build.

`create-owner-signing.sh` refuses to overwrite existing material. By default it
stores the keystore and password under ignored `local-backups/signing/` and
writes the ignored checkout's root `signing.properties`. Back up those files
securely. Never publish them; loss of the key prevents in-place Aurora updates.

For isolated patch testing only, `-PsaroAllowPublicTestKey=true` enables the
publicly known AOSP key. That unsafe artifact receives the distinct application
ID suffix `.saro.unsafe`, version suffix `-UNSAFE-TEST-KEY`, and visible label
`Aurora Store (UNSAFE)`. It cannot update `com.aurora.store` and must never be
distributed as a trusted package.

The audited tablet runs a locally signed build, and its private recovery
manifest records the exact installed bytes. Public documentation deliberately
omits that local certificate fingerprint and APK hash: each operator should
generate and protect a separate private key rather than reuse another
installation's signer.

Do not commit the built APK, private signing keys, `signing.properties`, Android
SDK files, Gradle caches, or the third-party Aurora Store source checkout to this
repository.
