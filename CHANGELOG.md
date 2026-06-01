# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Releases prior to `v0.4.2` are documented in the
[GitHub Releases page](https://github.com/tessell-art/editor/releases)
and in the git history.

## [Unreleased]

### Added
- **Edit ▸ Add Copy** submenu — four direct-manipulation tools that grow the
  tiling by welding on a copy of itself under a plane isometry (dcel's
  `maybeAdd…Copy`). Each drags a dashed skeleton preview, snaps to exact tiling
  anchors, names itself in the top-left mode chip, and runs the full validation
  pipeline (a rejected copy surfaces a toast and leaves the tiling unchanged):
  - **Translate** — drag the skeleton; both endpoints snap to vertices, so the
    slide vector is exact.
  - **Rotate** — press a centre (any vertex, edge midpoint, or the centre of a
    rotationally-symmetric face) and drag around it; the angle snaps to the
    values that can weld for that centre (vertex → edge-alignment angles;
    edge midpoint → 180°; face centre → multiples of 360/k).
  - **Reflect** — press one anchor, drag to a second; the mirror axis is the
    line through the two, shown as a spanning guide with a live reflected
    preview.
  - **Glide reflect** — like Reflect, but the copy is also slid along the axis
    by the drag vector (so the press→release direction and length matter).
  - The welded copy now **inherits the fill colors** of the source polygons
    instead of coming out in the flat default fill. Colors are matched
    geometrically (each result face's centroid mapped back through the isometry),
    so it is robust to the weld renumbering faces. Where the copy superimposes
    the existing tiling, the **original face's color is kept** (the copy only
    colors genuinely new area).
- **Add Copy** is now reachable directly from the canvas tool strip: a new
  button after Measurement opens a chevron flyout with the four operations
  (Translate / Rotate / Reflect / Glide reflect), highlighted while any copy
  mode is active.
- Keyboard shortcuts for Add Copy: **`T`** (Translate), **`R`** (Rotate),
  **`Y`** (Reflect). Glide reflect stays keyless. Shown in the Edit ▸ Add Copy
  submenu, the toolbar flyout, and the Keyboard Shortcuts popup.
- Eraser tool now shows deletable points (vertices, edge midpoints, face
  centers) within a screen-space proximity radius of the pointer, instead
  of requiring the pointer to first land on a face. Radius is wider for
  touch (50 viewBox units) than for mouse (30) to accommodate finger
  imprecision.
- Tap-to-delete on touch: tapping near a deletable point with the eraser
  active removes it without needing a drag gesture.

### Removed
- **Edit ▸ Double (to infinite)** and **Edit ▸ Fan** — superseded by Add Copy
  (Translate covers doubling, Rotate covers fanning). Removed from the Edit
  menu, the native desktop menu, the keyboard shortcuts (the `D` accelerator is
  gone), and the in-app guide. Mirror is unaffected.

### Fixed
- **Add Copy** tools now respond to touch. The drag gesture was wired only into
  the mouse handler, so on touch a press did nothing and a drag panned the
  canvas. Touch now grabs the skeleton (one finger to drag, two-finger
  pinch/zoom still works) by sharing a single dispatch with the mouse path.

### Changed
- `Esc` now exits **Measurement** and **Eraser** modes (clearing the clickable-point
  overlay and returning to Add Polygon), matching how it already exits the Add Copy
  modes.
- Canvas rotation shortcuts moved to the gaming-standard **`Q`** (rotate left) /
  **`E`** (rotate right). Previously `E` rotated left and `R` rotated right;
  `R` is now free (earmarked for an Add Copy shortcut).
- Build depends on the published `dcel` 0.1.2 (previously 0.1.0), which adds
  the grow-by-isometry API (`maybeAddTranslatedCopy` / `maybeAddRotatedCopy` /
  `maybeAddMirroredCopy` / `maybeAddGlideReflectedCopy`) used by Add Copy.
- Error toasts now clip to a mobile-friendly preview (~6 lines / 280
  characters) with an ellipsis, keeping the full text in the toast's hover
  tooltip, and the toast body scrolls internally past 40vh. Previously a long
  message grew the bottom-anchored toast upward until its close button slid off
  the top of the screen.
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
