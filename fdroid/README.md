# F-Droid packaging (ADR-005 Phase 6)

`art.tessell.editor.yml` is an **in-repo mirror** of the build-metadata recipe
that lives in [`fdroiddata`][fdroiddata] at `metadata/art.tessell.editor.yml`.
Keep the two in sync; only the fdroiddata copy is consumed by F-Droid.

**Status:** submitted as fdroiddata [MR !39962][mr], build green on F-Droid CI,
in maintainer review.

## How the build works

The app is a Scala 3 WebView shell (`android/`) over the Vite `dist/` bundle, so
the F-Droid build chains **three toolchains**: sbt + Node/Vite → Gradle. The
recipe choices below are load-bearing — most were required by review:

- **`subdir: android/app`, no `output:`.** The app is a *nested* Gradle module.
  Gradle is invoked from `android/app` and walks **up** to `android/settings.gradle`,
  so the root project is `android/` (hence `copyWebDist`'s `${rootDir}/../dist`
  resolves to the repo-root `dist/`). The APK then lands at `<subdir>/build/outputs`,
  which is exactly where F-Droid looks — so no `output:` override is needed.
  (Verify locally: `cd android/app && JAVA_HOME=<jdk17> ../gradlew projects` →
  `BUILD SUCCESSFUL`, with reports written under `android/build/`.)
- **Node from Debian forky**, not the nodesource `curl … | bash` installer
  (F-Droid disallows piping remote scripts to a shell):
  ```yaml
  - echo "deb https://deb.debian.org/debian forky main" > /etc/apt/sources.list.d/forky.list
  - apt-get update
  - apt-get install -y -t forky nodejs npm
  ```
- **sbt from GitHub releases**, not the scalasbt apt repo — download + checksum +
  unpack to `/usr/local`:
  ```yaml
  - curl -Lo sbt.tgz https://github.com/sbt/sbt/releases/download/v1.12.9/sbt-1.12.9.tgz
  - echo "<sha256>  sbt.tgz" | sha256sum -c -
  - tar xzf sbt.tgz --strip-components=1 -C /usr/local/
  ```
  The sbt version tracks `project/build.properties`; **recompute the sha256 when
  it changes** (`curl -L … && sha256sum …`).
- **`prebuild` is a YAML array** (items are joined with `&&`, so the working
  directory persists). `cd ../..` because `prebuild` runs in `android/app` and the
  npm project is at the repo root:
  ```yaml
  prebuild:
    - cd ../..
    - npm ci
    - npm run build
  ```
- **`scandelete`** paths are relative to the **build root (repo root)**, not
  `subdir` — they clean the web/sbt build intermediates so the scanner doesn't 403.
- **`commit:` is a full 40-char SHA**, not a tag (F-Droid requirement). It must
  point at the commit that contains the upstream Fastlane metadata (below).
- **`AutoName: Tessella` must stay** in the recipe — `fdroid checkupdates`
  auto-derives it from the manifest `android:label` and fails if it's missing.

## Localized metadata lives upstream (Fastlane)

Title, summary, description, icon, screenshots and changelogs are kept in **this
repo** under `fastlane/metadata/android/en-US/`:

```
fastlane/metadata/android/en-US/
  title.txt              short_description.txt   full_description.txt
  images/icon.png        images/phoneScreenshots/1.png … 5.png
  changelogs/<versionCode>.txt
```

F-Droid pulls these from the source checkout **at `commit:`**, so they must be
present in that commit. Do **not** put summary/description/screenshots in
fdroiddata — only the recipe (`.yml`) and `AutoName` live there.

## Updating for the next release

1. `npm version X.Y.Z --no-git-tag-version` (propagates via `sync-version.mjs`),
   commit, push tag `vX.Y.Z` (also runs `.github/workflows/android.yml`).
2. Add `fastlane/metadata/android/en-US/changelogs/<newVersionCode>.txt`
   (versionCode = `major*10000 + minor*100 + patch`).
3. F-Droid auto-update is on (`AutoUpdateMode: Version`,
   `UpdateCheckMode: Tags ^v[0-9.]+$`), so the bot opens the new build entry from
   the tag — no manual recipe edit unless the build env changes.
4. If sbt (`project/build.properties`) or the Node suite changes, update the
   `sudo:` block (and the sbt sha256) in the fdroiddata recipe and this mirror.

## Reference recipes

- `metadata/com.nospeak.app.yml` — same `subdir: android/app` nested-module +
  Debian-suite Node pattern.
- `metadata/ltd.evilcorp.atox.yml` — sbt-from-GitHub + array build steps.

## Notes / non-blockers

- **JDK:** AGP 8.13 needs JDK 17 locally (the machine default `java` is a JRE 21);
  the F-Droid build server's JDK builds fine.
- **Bundled UI5 "72" font** (`public/ui5-assets/fonts/72-*.woff2`): Apache-2.0,
  verified via the REUSE report for `SAP/theming-base-content`.
- **No trackers / no INTERNET permission** — passes Exodus cleanly.
- F-Droid signs published APKs with **its own** key; the Phase-5 keystore signs
  only the GitHub-release APK (see [`android/RELEASING.md`](../android/RELEASING.md)).
- `rewritemeta` long-line folding is ruamel-version-sensitive; match CI's diff
  byte-for-byte. `check-jsonschema` (via pipx) replicates the CI schema gate.

[fdroiddata]: https://gitlab.com/fdroid/fdroiddata
[mr]: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/39962
