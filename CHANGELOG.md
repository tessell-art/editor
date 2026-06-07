# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Releases prior to `v0.4.2` are documented in the
[GitHub Releases page](https://github.com/tessell-art/editor/releases)
and in the git history.

## [Unreleased]

## [0.6.1] - 2026-06-07

### Changed
- Android `applicationId` renamed `io.github.scala_tessella.editor` →
  `art.tessell.editor` (reverse-DNS of the owned `tessell.art` domain) before
  F-Droid publication, so the app's permanent identity matches the project
  domain. The Android shell's Scala package moved to match; the web frontend
  package is unchanged. (`v0.6.0` shipped the previous id.)

## [0.6.0] - 2026-06-07

### Added
- **Android app** (`android/`) — a Scala 3 WebView shell packaging the editor
  for F-Droid. Serves the Vite bundle from app assets via
  `WebViewAssetLoader`, exports SVG/DOT to the system Downloads collection,
  routes external links to the system browser, and shows a clear "update your
  WebView" message on engines too old to run the bundle. Includes release
  signing, a reproducible `gradle.lockfile`, and a tag-triggered `android.yml`
  workflow that builds and attaches the APK.

### Changed
- Vite `build.target` pinned to `es2020`, making the supported browser-engine
  floor explicit and stable across Vite upgrades.

## [0.5.0] - 2026-06-07

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
- New **Uniformity** category in the template gallery, alongside Regular,
  Semi-regular and Aperiodic. It offers six k-uniform tilings (2-uniform
  through 7-uniform) loaded from `public/templates/uniformity/`.

### Removed
- **Edit ▸ Double (to infinite)** and **Edit ▸ Fan** — superseded by Add Copy
  (Translate covers doubling, Rotate covers fanning). Removed from the Edit
  menu, the native desktop menu, the keyboard shortcuts (the `D` accelerator is
  gone), and the in-app guide. Mirror is unaffected.

### Fixed
- The info toasts announcing computed **uniformity classes** and **rotational /
  reflectional** symmetry classes now stay until the user closes them, instead of
  auto-dismissing after 4s — matching error-toast behaviour, so the summary can be
  read at leisure. Other info/warning toasts still auto-dismiss.
- **Show Uniformity** dots and the **symmetry axes** (rotational / reflectional) no
  longer vanish when zooming out below 50%. They were gated by the same level-of-detail
  threshold (`lodMinScale = 0.5`) as the node labels, but they describe the tiling's
  global structure, which is most useful when zoomed out — and showing uniformity makes
  the polygon fills transparent, so culling the dots blanked the canvas entirely. These
  overlays are now exempt from the LOD threshold and stay visible at any zoom; node
  labels still auto-hide below 50%.
- **Add Copy** tools now respond to touch. The drag gesture was wired only into
  the mouse handler, so on touch a press did nothing and a drag panned the
  canvas. Touch now grabs the skeleton (one finger to drag, two-finger
  pinch/zoom still works) by sharing a single dispatch with the mouse path.

### Changed
- **Anchor markers** (vertex / edge-midpoint / face-centroid) are now harmonised
  across every tool. Shape encodes the anchor *type* — vertex = solid
  disc, mid-edge = ellipse aligned to its edge, centroid = hollow ring — and
  colour/size encodes the *role* — idle, active (snapped/picked), measurement
  start/end. Previously the shape was always a circle and colour meant different
  things in different tools (type in Add Copy, but a flat orange in Eraser and
  Measurement). Eraser and Measurement points are now type-aware, and the
  palette/radii live in one place (`MarkerStyle` / `AnchorMarker`) instead of
  being hardcoded per renderer.
- **Measurement start/end** no longer rely on a green/red pair (indistinguishable
  for red-green colour-blindness). They now use the colour-blind-safe
  blue↔vermillion pair, and the measurement line carries an arrowhead pointing
  start → end so the order is legible without colour.
- **Measurement** now discovers points by proximity to the pointer, like the
  Eraser, instead of the two-step "click a polygon, then click one of its
  points" flow (ADR-013 amendment). Hovering (or, on touch, tapping near) a
  vertex / edge-midpoint / face-centre reveals and snaps to it directly,
  halving the clicks needed per measurement. The shared
  `EraserProximityQuery` is renamed `ProximityQuery` since it now serves both
  tools. Fan and AddPolygon ▸ Inside keep their polygon-first flow.
- **Add Copy** tools now reveal anchors by proximity. Instead of lighting up
  every candidate at once, only the anchors near the cursor are shown, across
  all four tools (Translate / Rotate / Reflect / Glide reflect). While dragging,
  the picked anchors stay highlighted using the Measurement tool's start/end
  colours: Translate and Reflect / Glide show the "from" anchor in blue and the
  snapped "to" anchor in vermillion, while Rotate shows just the picked pivot.
- **Add Copy ▸ Translate** additionally draws a segment with a mid-arrow and a
  length label between the source and target to show the translation's
  direction and distance, and its dashed skeleton snaps to the latched target
  so the preview matches the copy that will actually be welded.
- **Add Copy** now hides anchors and snaps that provably cannot weld (ADR-015).
  A fast, sound contact-compatibility check (the copy's vertex figure must
  overlay the original's with only coincidences) prunes incompatible Translate
  targets and Reflect / Glide axis ends from snapping and from the proximity
  dots, hides Rotate centres that have no weldable angle (including interior
  edge-midpoints whose two faces aren't point-symmetric), restricts the Rotate
  snap to weldable angles, and prunes Reflect / Glide axes that would cut a face
  at an edge-midpoint. The check is a necessary condition only — it never hides
  something that could weld, and `maybeAdd…Copy` remains the final authority.
- The in-app **Guide** now documents the four Add Copy tools (Translate /
  Rotate / Reflect / Glide reflect) in a new section, and its Measurement entry
  was rewritten for the proximity flow (no polygon-first step; blue start /
  vermillion end with a direction arrow). English and Spanish both updated.
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

[Unreleased]: https://github.com/tessell-art/editor/compare/v0.6.1...HEAD
[0.6.1]: https://github.com/tessell-art/editor/compare/v0.6.0...v0.6.1
[0.6.0]: https://github.com/tessell-art/editor/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/tessell-art/editor/compare/v0.4.2...v0.5.0
[0.4.2]: https://github.com/tessell-art/editor/compare/v0.4.0...v0.4.2
