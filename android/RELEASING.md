# Releasing the Android app

The `android/` module builds a WebView-shell APK over the Vite `dist/` bundle
(see ADR-005). Releases are produced by `.github/workflows/android.yml` on a
`v*.*.*` tag push, which builds, signs (if secrets are set), and attaches the
APK to the GitHub Release.

## Versioning

`package.json#version` is the single source of truth (propagated to other
manifests by `scripts/sync-version.mjs`). `app/build.gradle` reads it at build
time:

- `versionName` = the package.json version (e.g. `0.5.0`)
- `versionCode` = `major*10000 + minor*100 + patch` (e.g. `0.5.0` → `500`),
  monotonic as long as minor/patch stay below 100.

No Android-specific edit is needed when bumping the version — just
`npm version X.Y.Z` then push the tag.

## One-time signing setup

F-Droid signs published APKs with **its own** key, so signing here is only for
the **GitHub-release APK** (direct sideloading) and any reproducible-build
opt-in. Generate a keystore **once** and keep it safe — losing it means a new
app identity and losing update continuity for existing installs.

```bash
keytool -genkeypair -v -keystore tessella-release.jks -alias tessella \
  -keyalg RSA -keysize 2048 -validity 10000
# back this file up somewhere outside the repo
```

### Local signed build

Create `android/keystore.properties` (gitignored):

```properties
storeFile=/absolute/path/to/tessella-release.jks
storePassword=…
keyAlias=tessella
keyPassword=…
```

Then:

```bash
JAVA_HOME=<jdk17> ANDROID_HOME=~/Android/Sdk ./android/build-android.sh Release
```

Without `keystore.properties` the release build still succeeds but is unsigned.

### CI signing (GitHub Actions secrets)

Add these repository secrets so the workflow signs the release APK:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 tessella-release.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `tessella` |
| `KEY_PASSWORD` | key password |

The workflow writes `release.jks` + `keystore.properties` from these; if
`KEYSTORE_BASE64` is unset (forks/PRs) it builds unsigned.

## Cutting a release

```bash
npm version 0.6.0          # bumps package.json + propagates; commit is created
git push && git push --tags
```

The tag triggers `android.yml` → signed APK attached to the GitHub Release.

## Reproducibility

Dependencies are pinned in `app/gradle.lockfile` (dependency locking is enabled
in `build.gradle`). Regenerate after intentional dependency/plugin changes:

```bash
JAVA_HOME=<jdk17> ANDROID_HOME=~/Android/Sdk \
  ./android/gradlew -p android :app:assembleRelease --write-locks
```
