# F-Droid submission (ADR-005 Phase 6)

`art.tessell.editor.yml` is the **draft** build-metadata recipe destined for
[`fdroiddata`][fdroiddata]. It's kept here for in-repo review before being
copied into a `fdroiddata` fork at `metadata/art.tessell.editor.yml`.

## Prerequisite — a tag carrying the final applicationId

The app id is **`art.tessell.editor`** (reverse of the owned domain
`tessell.art`). `v0.6.0` shipped the earlier id
`io.github.scala_tessella.editor`, so the recipe targets **`v0.6.1`** — the
first tag with the renamed id. Before submitting:

1. Merge the rename to `main` (editor **and** docs repos — see ADR-011 split).
2. The version is already bumped to 0.6.1 in-tree (`sync-version.mjs`); commit it.
3. Push tag `v0.6.1` (also runs `.github/workflows/android.yml`).

Then `commit: v0.6.1` resolves to a tree whose APK has applicationId
`art.tessell.editor`.

## Submitting

```bash
# fork + clone https://gitlab.com/fdroid/fdroiddata
cp art.tessell.editor.yml fdroiddata/metadata/art.tessell.editor.yml
cd fdroiddata
fdroid readmeta && fdroid lint art.tessell.editor
fdroid build -v -l art.tessell.editor    # full build on a clean VM
# then open a merge request
```

F-Droid signs published APKs with its **own** key; our keystore (Phase 5) only
signs the GitHub-release APK.

## Validation checklist / known risks

The 3-toolchain build (sbt + Node + Gradle) is unusual for F-Droid, so expect
1–3 review rounds. Confirm each with `fdroid build -l` before the MR:

- [ ] **JDK 17.** AGP 8.13 requires it (locally the default `java` was a JRE 21
      — see ADR-005). Verify the F-Droid build server's default JDK is 17, or
      add a Gradle Java toolchain / select it explicitly.
- [ ] **`sudo` tool install.** The Node.js (nodesource) + sbt (apt repo + key)
      steps may need adjusting to the F-Droid base image; pin versions for
      reproducibility. Node ≥ 20.19 / 22 is required by Vite 8.
- [ ] **`subdir: android`** is the Gradle root (holds `gradlew` +
      `settings.gradle`); `gradle: [yes]` runs `assembleRelease` there, building
      the single `:app` module. Confirm F-Droid locates the wrapper.
- [x] **Bundled UI5 "72" font** (`public/ui5-assets/fonts/72-*.woff2`):
      **Apache-2.0 — not a blocker.** Verified via the REUSE report for
      `SAP/theming-base-content` (all files Apache-2.0, no font-specific or
      proprietary licence): <https://api.reuse.software/info/github.com/SAP/theming-base-content>.
- [ ] **Reproducibility.** `android/app/gradle.lockfile` pins deps; the build
      should be deterministic. Reproducible-builds opt-in (publishing the
      dev-signed APK) is a later, optional step.
- [ ] **No trackers / cleartext.** App has no INTERNET permission and no
      trackers — should pass Exodus cleanly.

[fdroiddata]: https://gitlab.com/fdroid/fdroiddata
