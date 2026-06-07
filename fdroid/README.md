# F-Droid submission (ADR-005 Phase 6)

`io.github.scala_tessella.editor.yml` is the **draft** build-metadata recipe
destined for [`fdroiddata`][fdroiddata]. It is kept here so it can be reviewed
and iterated in-repo before being copied into a `fdroiddata` fork at
`metadata/io.github.scala_tessella.editor.yml`.

## Prerequisite — the build must reference a tag that contains `android/`

The `android/` module is **not** in any released tag yet (it post-dates
`v0.5.0`). The `Builds:` entry therefore targets **`v0.6.0`**. Before
submitting:

1. Merge the `android-fdroid-packaging` work to `main` (both the editor and
   docs repos — see ADR-011 split).
2. Bump the version: `npm version 0.6.0` (Gradle derives versionCode 600).
3. Push the tag `v0.6.0` (this also runs `.github/workflows/android.yml`).

Then the F-Droid `commit: v0.6.0` resolves to a tree that includes `android/`.

## Submitting

```bash
# fork + clone https://gitlab.com/fdroid/fdroiddata
cp io.github.scala_tessella.editor.yml \
   fdroiddata/metadata/io.github.scala_tessella.editor.yml
cd fdroiddata
fdroid readmeta && fdroid lint io.github.scala_tessella.editor
fdroid build -v -l io.github.scala_tessella.editor   # full build on a clean VM
# then open a merge request
```

F-Droid signs published APKs with **its own** key; our keystore (Phase 5) only
signs the GitHub-release APK.

## Validation checklist / known risks

The 3-toolchain build (sbt + Node + Gradle) is unusual for F-Droid, so expect
1–3 review rounds. Confirm each of these with `fdroid build -l` before the MR:

- [ ] **JDK 17.** AGP 8.13 requires it (locally the default `java` was a JRE 21
      — see ADR-005). Verify the F-Droid build server's default JDK is 17, or
      add a Gradle Java toolchain / select it explicitly.
- [ ] **`sudo` tool install.** The Node.js (nodesource) + sbt (apt repo + key)
      steps may need adjusting to the F-Droid base image; pin versions for
      reproducibility. Node ≥ 20.19 / 22 is required by Vite 8.
- [ ] **`subdir: android`** is the Gradle root (holds `gradlew` +
      `settings.gradle`); `gradle: [yes]` runs `assembleRelease` there, building
      the single `:app` module. Confirm F-Droid locates the wrapper.
- [ ] **Bundled UI5 "72" font** (`public/ui5-assets/fonts/72-*.woff2`, pulled
      into the APK). Verify its licence is F-Droid-acceptable; if not, replace
      with a libre font or drop it. **Most likely review blocker.**
- [ ] **Reproducibility.** `android/app/gradle.lockfile` pins deps; the build
      should be deterministic. Reproducible-builds opt-in (publishing the
      dev-signed APK) is a later, optional step.
- [ ] **No trackers / cleartext.** App has no INTERNET permission and no
      trackers — should pass Exodus cleanly.

[fdroiddata]: https://gitlab.com/fdroid/fdroiddata
