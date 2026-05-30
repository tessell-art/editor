# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Releases prior to `v0.4.2` are documented in the
[GitHub Releases page](https://github.com/tessell-art/editor/releases)
and in the git history.

## [Unreleased]

### Added
- Eraser tool now shows deletable points (vertices, edge midpoints, face
  centers) within a screen-space proximity radius of the pointer, instead
  of requiring the pointer to first land on a face. Radius is wider for
  touch (50 viewBox units) than for mouse (30) to accommodate finger
  imprecision.
- Tap-to-delete on touch: tapping near a deletable point with the eraser
  active removes it without needing a drag gesture.

### Changed
- Eraser cursor now carries a red crosshair pinned to its working tip, with
  the cursor hotspot at the crosshair centre, so the aimed pixel is exactly
  the deletion point that gets removed. This replaces the previous cursor
  whose hotspot sat at an unmarked corner, making precise point selection
  hard.
- Eraser no longer surfaces clickable points via face hover
  (`setupFaceClickablePoints`); detection is driven by
  `EraserProximityQuery` over the whole tiling.
- Client-pixel → SVG viewBox coordinate conversion now uses the SVG
  element's `getScreenCTM()`, which correctly handles
  `preserveAspectRatio` letterboxing, CSS sizing, and any other
  transforms between client space and the viewBox. The previous
  bounding-rect math is replaced everywhere via a single helper in
  `EraserProximityQuery.clientToSvg`.

## [0.4.2] - 2026-05-23

### Added
- Apache License 2.0 — full text in `LICENSE` at the repo root.
- License notice, copyright line, and source-code link in the About popup
  (visible in both the web app and the Tauri desktop bundles).
- Desktop bundles are now attached to each GitHub Release as downloadable
  assets — `.deb` and `.AppImage` for Linux, `.dmg` for macOS, `.msi` and
  `.exe` for Windows. Previously the bundles were only retrievable from
  the per-run artifacts page of the GitHub Actions workflow, which
  expires after 90 days.

### Changed
- Build depends on the published `dcel` 0.1.0 (previously consumed via a
  vendored SNAPSHOT artifact under `lib-repo/`).
- CI: bumped `cloudflare/wrangler-action` from `v3` to `v4`; the obsolete
  `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24` workaround was removed from the
  Pages deploy workflow, clearing the Node 20 deprecation warning on
  every deploy run.

[Unreleased]: https://github.com/tessell-art/editor/compare/v0.4.2...HEAD
[0.4.2]: https://github.com/tessell-art/editor/compare/v0.4.0...v0.4.2
