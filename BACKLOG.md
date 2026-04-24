# Tessella Editor — Improvement Backlog

Prioritized list of maintainability and correctness improvements identified by a
static analysis pass (scalex) on 2026-04-22.

Priorities:
- **P1** — architectural; affects many files and future velocity.
- **P2** — code quality / correctness; localized but high value.
- **P3** — polish; low risk, easy wins.

Each P1 item is flagged for promotion to an ADR (Architecture Decision Record)
under `docs/adr/` before implementation.

---

## P1 — Architectural

### P1#1 — Break the `models` ↔ `operations` circular dependency
**ADR:** [ADR-001 — Package layering](docs/adr/001-package-layering.md)
(Accepted — both phases done 2026-04-22; enforced via sbt `checkLayering`
task hooked into `Compile / compile`).

`models` depends on `operations` (`AppState` imports `TessellationOperations`,
`ColorOperations`, `SelectionOperations`, `ViewOperations`, `ErrorOperations`)
and `operations` depends back on `models`. Classic bidirectional coupling.

Proposed target layering (to be ratified in ADR-001):
1. `models` — pure data + `Var[…]` declarations. No imports from `operations`,
   `components`, or `interactions`.
2. `operations` — pure-ish functions `(State, Event) → Effect`, allowed to read
   and write `models`.
3. `components` / `interactions` — UI + event handlers, wire Laminar `Signal`s
   and `Observer`s to `operations`.
4. `AppState` (if retained) — thin façade over `operations`, owned by the UI
   layer, not by `models`.

**Related Laminar idioms** (for ADR-001):
- Operations should expose `Observer[Event]` where reasonable so components can
  wire them with `-->`, instead of calling `AppState.foo()` imperatively from
  event handlers.

### P1#2 — Redesign the `EditorState` god-object ✅
**ADR:** [ADR-002 — State container design](docs/adr/002-state-container.md)
(**Accepted + fully implemented 2026-04-22**.) All 13 stateful
aggregates migrated: `ToolState`, `TessellationState`, `ViewState`,
`MeasurementState`, `PopupState`, `UIState`, `ColorState`, `FileState`,
`PreviewState`, `ThemeState`, `ErrorState`, `AnimationState`,
`IrregularState`. `DerivedState` keeps pure derivations as designed.

`EditorState.scala`: from ~280 lines (14 nested `object`s + flat
exports) to **98 lines** (13 one-line `Var` declarations + derived
signals + `DerivedState`).

Follow-up: P3#15 (`AppStateSnapshot` / `UndoManager` consolidation)
now unblocked.

`EditorState.scala` currently exposes **66 `Var[…]`** across 14 nested `object`s
(`FileState`, `ToolState`, …, `DerivedState`), all re-exported flat via
`export FileState.* / ToolState.* / …`, and it is imported by **48 files**.
`AppState` *also* re-exports `EditorState.*`, so callers reach the same `Var`
via two paths.

Consequences today:
- Zero encapsulation — every consumer can poke any `Var`.
- `AppStateSnapshot` has to be hand-maintained against the flat set (see P2#5).
- Tests cannot be isolated because the state is truly global — every spec has
  to reset the world in `beforeEach`.

Options to evaluate in ADR-002:
- **A.** Keep nested `object`s but stop the second-level `export` chain; pick
  one access path (either `EditorState.foo` or `AppState.foo`, not both) and
  make the other `private[models]`.
- **B.** Collapse to a single `Var[EditorState]` case class; derive all signals
  with `.signal.map(_.foo)`. `AppStateSnapshot` becomes the state type itself —
  undo/redo gets automatic structural equality.
- **C.** Group by aggregate (e.g. `ViewModel`, `ToolModel`, `ThemeModel`), each
  with its own `Var[T]`. Middle ground between A and B.

**Related Laminar idioms** (for ADR-002):
- One `Var` per *independent* source of truth. Derived values are `Signal`s
  (already done correctly in `DerivedState`).
- Top-level `Var`s own themselves; subscribers attached via element bindings
  (`-->`, `child <--`) clean up automatically. Any `signal.foreach { … }` living
  outside an element needs an explicit `Owner`.

### P1#16 — Test strategy: Laminar-in-JSDOM + Playwright (no Selenium)
**ADR:** [ADR-003 — Test strategy](docs/adr/003-test-strategy.md)
(Accepted — Tier 1 scaffolding landed 2026-04-22; Tier 2 deferred).

**Tier 1 landed 2026-04-22** — mount/unmount helper plus first two specs:

- `LaminarTestSupport` trait
  (`src/test/.../components/LaminarTestSupport.scala`, ~60 lines):
  `mount(Element)`, `querySelector` / `querySelectorAll` scoped to
  the container, `clickOn(selector)`. Auto-cleans the container and
  unmounts the `RootNode` in `afterEach`. Extends `FunSuite` (not
  `self: FunSuite =>`) so `super.afterEach` can chain into the next
  trait. Mix *last* after `EditorStateFixture` so unmount runs before
  state restore.
- `AboutPopupSpec` (4 tests) — first mount spec. Static content +
  `PopupCommons` close wiring (close button, overlay, content
  stopPropagation).
- `IrregularPolygonPopupSpec` (8 tests) — second mount spec, picked to
  smoke-test the helper against `child.maybe <--` state-driven content,
  state-mutating button observers with `e.stopPropagation()` inside the
  observer body, and reactive DOM updates mid-test.
- `ErrorMessageComponentSpec` (4 tests, landed 2026-04-22) — covers the
  `child.maybe <-- signal.map(_.map(...))` empty/non-empty branch
  switch, close-button → `ErrorOperations.clearError()`, and reactive
  message-text updates mid-test.
- `UndoComponentSpec` extended (4 mount tests on top of the existing
  6 pure-helper tests, landed 2026-04-22) — covers the `disabled <--`
  wiring driven by `UndoManager.canUndo` × `EditorState.uiState
  .isProcessing`, including the "isProcessing forces both buttons
  disabled" interaction that the pure helpers can't see.

**Tier 2 first cut landed 2026-04-22** — Playwright smoke suite under
`e2e/` (sibling project, own `package.json`/`node_modules`):

- `e2e/playwright.config.ts` — auto-starts `vite dev` from the parent
  directory, reuses an existing dev server outside CI, Chromium-only.
- `e2e/tests/smoke.spec.ts` — five scenarios: app boots and renders the
  palette + canvas; clicking the hexagon palette button creates a
  tiling; `Ctrl+Z` undoes it; Help → About... opens the popup and the
  close button dismisses it; **SVG export → Clear Tiling → re-import
  round-trip preserves shape** (added 2026-04-22 — exercises
  `window.prompt` interception via `page.on('dialog')`, download
  capture via `page.waitForEvent('download')`, and file-input
  feeding via `page.waitForEvent('filechooser')` + `setFiles`).
- `e2e/README.md` — first-time setup, run instructions, what's
  deliberately not yet covered (touch gestures, visual regression),
  and a CI-integration recipe.
- `.gitignore` updated for Playwright artifacts (`test-results/`,
  `playwright-report/`, `.playwright-cache/`).

**CI integration is pending** — the GitHub Actions workflow at
`.github/workflows/build.yml` only runs `sbt test` and `npm run build`.
Wiring Playwright in adds a ~150 MB browser download per run; recipe
in `e2e/README.md` covers the `npx playwright install --with-deps
chromium` step. Could live in a separate workflow gated on `main`
pushes if PR build time becomes a concern.

Open follow-ups (not urgent — opportunistic):
1. Mount specs for the remaining popups (`GuidePopup`,
   `ShortcutsPopup`, `SettingsPopup`) plus
   `ColorPickerPopupComponent`. `UndoComponent` and
   `ErrorMessageComponent` landed 2026-04-22 — the helper has now
   handled four distinct component shapes (static popup, state-driven
   popup, conditional `child.maybe`, signal-driven `disabled`)
   without needing changes.
2. Tier 2 expansion: ~~SVG export-import round-trip~~ ✅ landed
   2026-04-22; remaining — `TouchEventHandler` via Playwright's
   touch-emulation device, visual snapshots for the canvas at known
   scenes.
3. Wire Tier 2 into CI.
4. **Language for e2e tests** — see
   [ADR-004](docs/adr/004-e2e-test-language.md) (Accepted 2026-04-22).
   Path 2 landed: TS tests + `@JSExportTopLevel` Scala test hooks for
   domain-native assertions. First implementation:
   - `src/main/scala/io/github/scala_tessella/editor/TestHooks.scala` —
     four observation hooks (`tilingPolygonCount`, `isTilingEmpty`,
     `currentFillColor`, plus `firstFaceVertexCount` added 2026-04-22
     for the SVG round-trip shape check) installed on
     `window.__tessellaTestHooks__` from `Editor()` startup via
     `js.Dynamic` (manual install rather than `@JSExportTopLevel`
     because the build is `ModuleKind.ESModule`; the latter would
     produce a named ES module export, not a window global).
   - `e2e/tests/fixtures/hooks.ts` — `Window` augmentation + typed
     `hooks.*` (one-shot reads) and `expectHook.*` (Playwright
     `expect.poll` wrapper) helpers.
   - `e2e/tests/smoke.spec.ts` — the hexagon-creates-tiling test
     rewritten to use `isTilingEmpty` + `tilingPolygonCount` hooks
     instead of `polygon.tiling-polygon` selectors. The other three
     tests stay selector-based for contrast.

   Production-bundle guard explicitly **not** added (ADR-004
   §Consequences mitigation A — accept). Revisit if bundle size ever
   becomes a real metric.

Static analysis on 2026-04-22 reports 217 tests across 27 suites, but the
distribution is uneven:

- **Strong**: `utils/geo` (incl. two property specs), `models`, most
  `operations` (`OperationRunner`, `UndoManager`, `ColorOperations`,
  `ErrorOperations`, `SelectionOperations`, `ViewOperations`),
  `SvgExporter` (30 tests), `SvgImporter` (3 round-trip + failure paths).
- **Zero direct coverage**: ~19 UI components (`EditorCanvasComponent`,
  `MenuBarComponent`, every `Tessellation*Renderer`, `PolygonPaletteComponent`,
  `CanvasControlComponent`, `UndoComponent`, `ErrorMessageComponent`,
  `ColorPickerPopupComponent`, all 5 popups), 5 ops
  (`DeletionOperations`, `SymmetryOperations`, `MeasurementOperations`,
  `SettingsOperations`, `OperationGuard`), `TouchEventHandler`,
  `FileDownloader`, `TemplateLoader`, several utils (`PolygonNameGenerator`,
  `SvgDsl`, `Logger`, `PolygonSvg`).
- **Partial**: `KeyboardEventHandler` (only pure helpers — `handleKeyDown`
  wiring untested), `MouseEventHandler` (2 of 8 methods).

Adding the missing UI coverage is a strategy choice with build-pipeline and
CI impact. Three options surveyed in ADR-003: Selenium (rejected — heavy,
flaky, wrong shape for a Scala.js client app), JSDOM-only (rejected —
can't validate real canvas/SVG rendering or pointer/touch gestures),
**Laminar-in-JSDOM + Playwright smoke** (chosen).

The existing `JSDOMNodeJSEnv` (already in `build.sbt:68`) plus the
`EditorStateFixture` snapshot/restore pattern are the right base. Mount
Laminar components into the JSDOM document via `com.raquo.laminar.api.L.render`
and assert on the rendered DOM. Reserve Playwright for a ~10–15-test smoke
suite (canvas rendering, real input events, visual regression) pointed at
`vite dev`.

### P1#21 — Desktop packaging: Tauri 2 shell for Linux / Windows / macOS
**ADR:** [ADR-008 — Desktop packaging via Tauri](docs/adr/008-desktop-packaging-tauri.md)
(Accepted 2026-04-24).

**Pre-work landed 2026-04-24** (shared with ADR-005 / ADR-007):
- `base: './'` in `vite.config.js`.
- `public/site.webmanifest` `start_url` / `scope` → `./`.
- Runtime CDN audit: UI5 Web Components pulled 24 woff2 + 1 CLDR JSON from
  jsdelivr. Replaced with a build-time URL rewrite (`ui5LocalAssets`
  Vite plugin, version-agnostic regex) pointing at `public/ui5-assets/`.
  Per on-demand vendoring, only the one woff2 actually observed to
  fetch (`72-Regular.woff2`, from opening the color picker) is vendored;
  the other 23 rewritten paths will 404 locally when/if exercised,
  giving a clear signal of what to add. CLDR URL rewritten but the
  file not vendored; same fallback pattern.

**Slice 1 landed 2026-04-24** — shared shortcut table (migration step 6):
- `src/main/scala/.../models/MenuShortcuts.scala` — `MenuAction` enum
  (10 shortcut-bearing actions), `Shortcut` case class with structured
  `primary`/`shift`/`alt` fields, `label` emitting
  `[Shift+][Ctrl+][Alt+]key` (Shift-first ordering preserves the
  pre-extraction `"Shift+Ctrl+Z"` redo label verbatim).
- `MenuBarComponent.scala` — 10 hard-coded shortcut strings replaced
  with `MenuShortcuts.labelOf(MenuAction.X)`.
- `MenuShortcutsSpec` — locks in label format across modifier
  combinations and asserts every `MenuAction` has a binding, so
  slice 2 can trust the table when generating Rust accelerators.

**Slice 2 landed 2026-04-24** — Tauri shell end-to-end (migration
steps 4, 5, 7, 9):
- `desktop/src-tauri/` — hand-scaffolded Tauri 2 project (Cargo.toml,
  build.rs, tauri.conf.json with `withGlobalTauri: true`,
  `capabilities/default.json`, `src/{main,lib,menu,menu_shortcuts}.rs`).
  `cargo tauri dev` spawns Vite, waits for localhost:5173, opens a
  native window with a populated File/Edit/View/Help menu bar.
- `menu.rs` — 4 submenus mirrored from `MenuBarComponent`, plus
  macOS app menu (shadowing rather than `let mut` to avoid unused-mut
  warnings on non-macOS builds), predefined Cut/Copy/Paste in Edit.
  Menu clicks emit a `"menu"` event with the item id as payload.
- `menu_shortcuts.rs` — Rust mirror of `MenuShortcuts.scala`,
  `Shortcut::accelerator()` renders Tauri's `CmdOrCtrl+…` format that
  remaps to ⌘ on macOS automatically.
- `platform/desktop/DesktopMenuBridge.scala` — listens to `"menu"`
  events when `window.__TAURI__` is defined, dispatches each id to
  the same `AppState` / `EditorState` entry point the DOM menu uses.
  No-op on web. Wired from `Editor @main` startup alongside
  `TestHooks.install()`.
- Compound-action extraction: `AppState.newTiling()`,
  `ViewOperations.{zoomIn,zoomOut,resetView}()` so the DOM menu and
  the desktop bridge call one action path each, not copies.
- `build.sbt` `checkMenuShortcutsParity` — runs on every compile
  (like `checkLayering`); reads both `MenuShortcuts.scala` and
  `menu_shortcuts.rs`, fails the build on either missing or orphan
  names. JVM-side so it sidesteps Scala.js regex/ES-version
  constraints.
- `.gitignore` — `/desktop/src-tauri/target/` and `/gen/schemas/`.

Toolchain notes learned during slice 2 (Ubuntu 24.04):
- Rust **1.85+** needed for `tauri-cli 2.10.x` — one transitive dep
  requires edition 2024. `rustup update stable` lands it.
- `libwebkit2gtk-4.1-dev` alone does not pull all headers on Noble.
  Full list needed: `libwebkit2gtk-4.1-dev libgtk-3-dev build-essential
  curl wget file libxdo-dev libssl-dev libayatana-appindicator3-dev
  librsvg2-dev libjavascriptcoregtk-4.1-dev libsoup-3.0-dev`.
- `beforeDevCommand` CWD is the **parent of `src-tauri/`**, not
  `src-tauri/` itself — our layout puts that at `desktop/`, so
  `npm --prefix .. run dev` (not `../..`). `frontendDist` is still
  relative to `tauri.conf.json` so `../../dist` there remains correct.
- `tauri::generate_context!` validates icon paths **at compile time**;
  declared icons must exist. `cargo tauri icon <source.png>` fills
  `icons/` with every referenced size/format in one shot.

**Step 8 decided against 2026-04-24** — OS-level file associations for
`.svg` / `.dot` rejected. `SvgImporter` only handles Tessella-authored
SVG (DCEL metadata required); `.dot` has no importer. Associating the
extensions at the OS level would hijack generic SVG double-clicks and
surface an error to users who had no reason to expect Tessella. The
intra-app `File → Load SVG...` stays the only entry point. Revisit
only if Tessella gains a format it uniquely owns. See ADR-008
migration path §8 for the full rationale.

**Slice 3 — remaining ADR-008 migration path** (not landed):
- Step 10 — CI (`.github/workflows/desktop.yml`) matrix over
  ubuntu/macos/windows. Tracked separately, not blocking.
- Step 11 — Flathub / winget / Homebrew Cask submissions. Each is its
  own backlog entry once the binary is stable (unsigned acceptable
  initially per ADR).

Extends the "Vite `dist/` is the portable artifact, shells are thin
wrappers" pattern from ADR-005 (Android) and ADR-007 (iOS) to the three
mainstream desktop OSes. A Tauri 2 shell under `desktop/src-tauri/`
produces per-OS installers (AppImage + .deb, .msi + .exe, .dmg + .app)
that load the same bundle Cloudflare Pages already serves.

**Sequencing**: all three packaging ADRs share the `base: './'` +
webmanifest pre-work. Landing it once unblocks 005 / 007 / 008 in any
order.

**Desktop-specific menu work** (the substantive new surface this ADR
introduces on top of the pattern):
- Shared shortcut table (`MenuShortcuts.scala` + `menu_shortcuts.rs`)
  so the DOM menu and the native Tauri menu can't drift. Scala half
  landed in slice 1 (2026-04-24) with `MenuShortcutsSpec` locking the
  label format; Rust half + assertion test land with slice 2.
- `menu.rs` as a static mirror of the four `menuItem(...)` blocks;
  clicks emit `"menu:<file.id>"` events.
- `DesktopMenuBridge.scala` (new, in a `platform/desktop/` package)
  subscribes when `window.__TAURI__ != null` and dispatches to the
  existing `AppState` / `EditorState` methods — no exposed JS commands,
  preserves the "no JS bridge" posture from ADR-005.
- macOS: native menu is mandatory (OS shows it whether we populate it
  or not). Windows / Linux: native menu is additive; in-DOM menu stays
  as primary affordance.
- **Out of scope for v1**: mirroring enabled/disabled signals (e.g.
  `canSaveCurrentFileWhenIdleSignal`) to native menu item state.
  Action-side guards no-op silently until a user reports confusion.

**Signing / notarization is follow-up, not blocking**: unsigned builds
ship first (AppImage self-signed with the project GPG key; Windows
`.exe` through SmartScreen; macOS `.app` via right-click-Open). Revisit
per-platform when user feedback warrants it; macOS notarization is
bundled with the same $99/yr Apple Developer decision ADR-007 defers.

**Distribution channels are separate backlog entries** once the binary
is stable: Flathub (Linux, analogous to F-Droid in ADR-005), winget
(Windows), Homebrew Cask (macOS).

Risks called out in ADR-008 §Consequences:
- WebKitGTK on Linux diverges from WebKit-on-macOS — Web Components
  coverage is the most likely failure surface. Electron is the kept-in-
  reserve fallback.
- Menu drift between the two declaration sites (mitigated by shared
  shortcut table + assertion test but not eliminated).
- Two-track testing: Playwright covers web Chromium + WebKit; a
  `tauri-driver` smoke test per OS would close the desktop-shell gap.
  Not required for the initial ship.

### P1#3 — Reactive-first discipline: eliminate accidental `.now()` reads ✅
**Convention doc:** [`docs/laminar-conventions.md`](docs/laminar-conventions.md)
(written 2026-04-22).

Covers:
- `.now()` vs `.signal` decision rule (event-boundary snapshot vs reactive
  subscription).
- Writes: `.set(Initial)` / `.update(_.copy(…))` / `.update(s => …)`.
- Post-ADR-002 helper patterns (lens-lambda, signal+observer pair, by-name
  close action).
- Observer-wiring preference over callbacks.
- Pitfalls hit during migration (`_ + X` underscore-lambda,
  `onClick.compose(...).map(...)` compile error, name shadowing, unused
  `Var.writer` / `Var.zoom`).

`.now()` went from 110 → 98 calls after ADR-002 rollout, but the raw count
isn't the metric — the remaining ~98 are mostly legitimate event-boundary
reads (operations triggered by user actions, snapshot construction, guard
reads in `OperationGuard`/`UndoManager`). The two deliberate `.now()`
avoidances (`KeyboardEventHandler:28`, `EditorCanvasComponent:99`) remain
the referenced models for reactive-first patterns.

Ongoing: when touching a file that calls `.now()` inside a
`signal.foreach`/`.map`, apply the reactive rewrite. Not a tracked
backlog item — normal maintenance under the conventions doc.

---

## P2 — Code quality & correctness

### P2#4 — `SvgImporter` still throws instead of returning `Either` ✅
Resolved 2026-04-22. All four `throw new Exception(…)` sites removed.
`SvgImporter` is now a 4-step `Either[String, _]` pipeline:

```
parseSvg          :: String             => Either[String, dom.Document]
findTessellaMetadata :: dom.Document   => Either[String, dom.Element]
parseTiling       :: String             => Either[String, TilingDCEL]
readPolygonFillsStrict :: (Document, Int) => Either[String, List[ColorRGB]]
```

The top-level `importTilingFromSVG` is a for-comprehension that
short-circuits on the first `Left`. On success, side effects are applied
via `loadTilingIntoEditor`; on failure, `showImportError` produces a
toast. `Try` now only wraps the one DOM call that could genuinely throw
(`new DOMParser()` + `parseFromString`).

Main-sources `throw new` count: 4 → **0**.

### P2#5 — `UndoManager` / `AppStateSnapshot` drift risk ✅
Resolved 2026-04-22 (under P3#15). `AppStateSnapshot` now has 5 fields —
three aggregate case classes (`TessellationState`, `ToolState`,
`IrregularState`) plus two cherry-picked `ColorState` fields
(`polygonColors`, `fillColor`). Structural `==` on the nested case
classes replaces `isStateEquivalent`. Adding a field to an aggregate
case class now automatically propagates into undo/redo.

### P2#6 — Split `TessellationOperations.scala` ✅
Resolved 2026-04-22. Split from 511 lines into 4 files, all below 300
lines, each with module-level scaladoc:

| File | Lines | Contents |
|---|---|---|
| `TessellationOperations.scala` | 91 | Lifecycle: `selectPolygon`, `clearTiling`, `selectIrregularInPalette`, `initializeWithIrregularIfEmpty`; shared `toCoords` extension; `clearStaleAfterMutation()` helper |
| `DeletionOperations.scala` | 38 | `attemptFaceDeletion`, `attemptVertexDeletion`, `attemptEdgeDeletion` |
| `PlacementOperations.scala` | 172 | `PolygonPlacementKind` + helpers, `attemptPolygonAddition`, `attemptPolygonInsertion`, `findFaceContainingEdge` |
| `TransformOperations.scala` | 273 | `FanContext`, `DoublingContext`, per-op private helpers; `attemptFanning`, `attemptDoubling`, `attemptMirroring` |

**Naming note:** renamed the new file for fan/double/mirror to
`TransformOperations` (not `SymmetryOps`) to avoid colliding with the
existing `SymmetryOperations` (which handles overlay *visibility*).
Both are documented in their module scaladoc so the distinction is
discoverable.

**Private helper rename:** `clearSymmetryAndPerimeterSelectionOnSuccess`
(awkward, used across 4 ops methods) → public
`TessellationOperations.clearStaleAfterMutation()` — shorter name,
captures intent ("after a mutation, clear stale overlays + selection").
Callers in sibling ops files use this shared helper.

Also closes **P3#9** (module-level scaladoc on `TessellationOperations`)
as a side effect — all four files now carry the `attempt…` naming
convention documented at the module level.

Callers updated: `AppState`, `SelectionOperations`, `TessellationEdgeRenderer`, `TessellationOperationsSpec`.

### P2#7a — Resolve `utils → AppState` layering inversion ✅
Resolved under ADR-001 Phase 2 on 2026-04-22. `utils/UndoManager`,
`utils/file/SvgImporter`, and `utils/file/SvgExporter` now call specific
`*Operations` directly instead of going through `AppState`. Enforced by
the `checkLayering` sbt task.

### P2#17 — Close `operations` and `interactions` coverage gaps ✅
Resolved 2026-04-22. Six new specs, two deepened. Suite totals went
from 208 tests / 27 suites to **248 tests / 33 suites** (+40 tests).

New specs (all under `EditorStateFixture`):

| Spec | Tests | Notes |
|---|---|---|
| `DeletionOperationsSpec` | 5 | empty-tiling + unknown-id failure paths for face/vertex/edge; success-path overlay-clear |
| `SymmetryOperationsSpec` | 6 | `clearOverlays` atomic reset; sync hide / sync show-from-cache for `toggleShowUniformity`; async compute path for `toggleShowRotation`; empty-tiling no-op for `toggleShowReflection`; `ifNotProcessing` gate |
| `MeasurementOperationsSpec` | 3 | `clearAll` resets the 7 per-measurement fields; preserves `isAngleShownInRad`; idempotent on initial state |
| `SettingsOperationsSpec` | 4 | `applySettings` writes state + persists; `resetFillColorToDefault`; `refreshSettingsTempValues`; round-trip through a rebuilt `ColorState.initial` |
| `OperationGuardSpec` | 4 | `ifNotProcessing(boolean)`, the EditorState-reading variant, and `gate(EventStream)` (drops events while processing) |
| `PlacementOperationsSpec` | 5 | success-path tiling growth for `attemptPolygonAddition` (palette colour inherited, undo recorded); invalid edge index; silent no-op when no shape selected; silent no-op when both regular and irregular selected; empty-tiling error for `attemptPolygonInsertion` |

Deepened specs:

| Spec | Was | Now | Added |
|---|---|---|---|
| `MouseEventHandlerSpec` | 2 | 9 | `handleMouseDown/Move/Up` (drag pan + no-op when not dragging); `handleWheel` zoom-in/zoom-out using a JSDOM-created canvas ref; `handleWheel` no-op when no canvas registered |
| `KeyboardEventHandlerSpec` | 3 | 9 | `handleKeyDown` for Escape (clears selections), undo/redo via primary+z and primary+shift+Z (asserts tiling restored + redo stack), `<input>` target ignored, unmapped key no-op, 'd' on empty tiling no-op |

Untested adjustments:
- `MeasurementOperations` had no `distance`/`angle` helpers as the
  original backlog implied — those live in geometry. Spec scoped to
  `clearAll` only, with `MeasurementState.initial` round-trip as the
  third test. Property test for distance symmetry rolled into P2#18
  (geometry-layer), where it actually belongs.
- `SymmetryOperations.displaySizeInfo` writes via
  `ErrorOperations.info`, which deliberately does **not** set
  `errorMessage`, so the spec asserts the visibility-flag flip and
  cache state instead of a user-facing message.

Test-event construction pattern for the JSDOM dispatch:
```scala
val init = js.Dynamic
  .literal(key = "z", ctrlKey = true, metaKey = true, shiftKey = false)
  .asInstanceOf[dom.KeyboardEventInit]
new dom.KeyboardEvent("keydown", init)
```
This is reusable for any future component-level test that needs a
synthetic `KeyboardEvent`/`MouseEvent`/`WheelEvent` and avoids needing
to mock anything.

Async tests use the same `Promise[Unit]` + `setTimeout(200)` pattern
already established by `TessellationOperationsSpec`, factored into a
local `afterAsync` helper in each new spec.

Followups:
- Several specs use a duplicated `afterAsync` helper. Worth extracting
  into a shared trait alongside `EditorStateFixture` if it appears in
  one more place.
- Symmetry compute tests are timing-sensitive (200ms wait for the
  50ms-debounced compute). Flake risk is low but not zero — revisit
  if CI ever flakes.

### P2#18 — Property-based specs for `Radian` and `PolygonPlacementGeometry` ✅
Resolved 2026-04-22. Two new property specs (13 properties total).

`RadianPropertySpec` (8 properties) — covers the opaque-`Radian`
contract:
- `normalize` maps any angle into `[0, TAU)`.
- `normalize` is idempotent.
- `normalize` is invariant under shifts of `k * TAU` for any integer k.
- `normalizeDelta` maps any angle into `(-π, π]`.
- Addition is commutative modulo TAU.
- `Radian.fromDegrees(d).toDegrees ≈ d` within scaled ε. The original
  backlog wording mentioned `toBigDecimal` round-trip; that helper lives
  on `AngleDegree` (dcel library), not `Radian`. The degree round-trip
  is the equivalent contract here.
- `normalizeDeltaAngle` returns the smallest signed rotation
  (`|delta| ≤ π`).
- `(r * n) / n == r` for non-zero `n`.

`PolygonPlacementPropertySpec` (5 properties) — covers
`PolygonPlacementGeometry.computeWireframePoints` for regular polygons
attached to a perimeter edge of `TilingBuilders.square`:
- Returns exactly `sides` vertices.
- All vertices lie on a common circle around their centroid (equal radii
  within ε) — i.e. the result is inscribed in a circle.
- Consecutive vertex distances are equal (uniform side length).
- Side length equals `tilingEdgeLen × canvasScale` within ε (proves the
  canvas-coord scaling is applied correctly).
- Returns `Vector.empty` for a degenerate zero-length edge.

Generators range over `sides ∈ [3, 12]` and `edgeIndex ∈ [0, 3]` (the
square's four perimeter edges), so each property exercises 100 random
combinations.

The "distance symmetry" property mentioned earlier (P2#17 scope note)
isn't included — `MeasurementOperations` exposes no `distance` helper;
the symmetric `Point#distanceTo` from `utils.geo` already has implicit
coverage via every other geometry test. Not worth a one-line property.

Suite totals: **261 tests / 35 suites** (was 248 / 33).

### P2#7 — `ErrorOperations.messageTimeoutId: private var Option[Int]` race ✅
Resolved 2026-04-22. The actual race was not just the tracked 10s
message-timeout but the **untracked 3s overlay-timeout**: rapid
consecutive errors had their overlay cleared prematurely by the
previous error's dangling timer.

**Fix:** introduced a `private final class SingleTimeout` helper
encapsulating the cancel+schedule pattern in one place. Two instances
(`messageTimeout` / `overlayTimeout`) replace the single
`private var messageTimeoutId` and now correctly cancel **both**
pending timers on each `showError` call.

Side cleanups while in the file:
- Three sequential `errorState.update(_.copy(x = …))` calls (set
  message + placement + deletion) consolidated into one atomic update.
- Same for the clear-overlays timeout callback and for `clearError`.
- `clearError` now cancels both timeouts (was only cancelling the
  message timeout before).

The remaining `private var` is encapsulated inside `SingleTimeout`,
tagged `// scalafix:ok` — ready for P3#11 (re-enabling the scalafix
`var` rule).

---

## P3 — Polish & maintainability

### P3#19 — Test-code hygiene cleanup ✅
Resolved 2026-04-22.

- Placeholder test `"anf"` at `ViewOperationsSpec.scala:31` renamed to
  `"AngleDegree(90).toBigRadian ≈ π/2"`.
- `SvgExporterSpec`: two commented-out test blocks (dead references to
  removed `showDual` flag) deleted; over-sliced single-`assert(contains)`
  tests consolidated by target helper — one test per `(helper, state)`
  pair. Specifically:
  - `generatePolygonsXml` structure + points merged into one test (was 3);
    the two colour-pipeline tests kept separate because they exercise
    different `EditorState.colorState`.
  - `generateLabelsXml` styling + per-vertex text merged (was 2);
    positioning and empty-input cases kept separate.
  - `generateMetadataXml` RDF structure + source/license + coordinates
    merged (was 3); empty-tiling case kept separate.
  - `generateSvgContent` envelope + dimensions + background merged (was
    3); the `showNodeLabels` on/off pair merged into one toggle test
    (was 2); other state-dependent cases kept separate.
  - Added a `defaultSvgContent(tiling = squareTiling)` helper to
    eliminate the repeated 5-arg call. Net: 30 → 21 tests, same coverage.
- `SettingsStorageSpec`: removed `isLocalStorageAvailable` +
  `assume(…)` silent-skip. `JSDOMNodeJSEnv` always provides
  `localStorage`; if it ever stops, `clearKeys()` will throw in
  `beforeEach` and the test will fail loudly.
- `AppStateSpec`: 7 sequential `measurementState.update(_.copy(…))` calls
  in `clearMeasurements` test collapsed into one multi-field `copy`;
  same for the 5 `colorState.update` calls in
  `refreshSettingsTempValues`.

Suite totals went from 217 tests across 27 suites to **208 across 27** —
the 9-test delta is the `SvgExporterSpec` consolidation.

### P3#20 — Extract pure-function helpers from UI components (ongoing)
`TessellationCursorStyles` is the model: cursor logic lives as a pure
function, tested directly by `TessellationCursorStylesSpec`. Replicate
the pattern across renderers and popups so ~80% of component logic is
testable without mounting. Each extraction is a small refactor; do them
opportunistically when touching a component for other reasons. Lowers
the surface area that ADR-003's Laminar-in-JSDOM work has to cover.

**First batch landed 2026-04-22** — 3 specs, 20 tests:

| Component | Extracted | Tests |
|---|---|---|
| `GridRenderer` | `strokeWidthForScale(scale: Double): String` — clamp(1/scale, 0.1, 2.0) | `GridRendererSpec` (3) |
| `UndoComponent` | `undoTitle` / `redoTitle (canUndo/canRedo, preview)` — tooltip mapper | `UndoComponentSpec` (6) |
| `PolygonPaletteComponent` | `validateSides` (3-fallback + clamp [3,100]), `polygonTooltip`, `irregularPolygonLabel`, `polygonButtonClasses` | `PolygonPaletteComponentSpec` (11) |

All helpers are `private[components]` — accessible to tests in the same
package but not exported publicly. The existing Signal-based
`polygonButtonClass` now delegates to the pure `polygonButtonClasses`,
so the Laminar wiring and the testable core share one source of truth.

**Remaining candidates (not urgent, track when touching each file):**

- `MenuBarComponent` — dropdown-entry label/disabled-rule mapping (~25 entries).
- `CanvasControlComponent` — `labelsButtonText(showLabels: Boolean)`, `labelsButtonTitle(showLabels: Boolean)` — trivial but uniform.
- `ErrorMessageComponent` — already minimal; probably not worth extracting.
- Popup labels & class composition: `SettingsPopup`, `IrregularPolygonPopup`, `GuidePopup`, `AboutPopup`, `ShortcutsPopup`.
- `TessellationEdgeRenderer` / `TessellationPolygonRenderer` / overlay/measurement renderers — SVG attribute strings derived from state; good pure-function candidates but each one is modest in isolation.

Suite totals: **281 tests / 38 suites** (was 261 / 35).

### P3#8 — Pin `dcel 0.1.0-SNAPSHOT`
`build.sbt:43`. SNAPSHOT deps hurt reproducibility of CI builds and of old tags
checked out later. Pin to a release or milestone.

### P3#9 — Add module-level scaladoc to `TessellationOperations` ✅
Resolved 2026-04-22 as a side effect of P2#6. The split yielded 4
focused ops files, each with module-level scaladoc explaining scope
and the `attempt…` naming convention (`Unit`-returning; errors
surfaced via `ErrorOperations` rather than thrown).

### P3#10 — README "Architecture" section
One paragraph pointing at `EditorState` / `AppState` / `operations` /
`interactions` layering (the future state per ADR-001/002) would short-circuit
most onboarding confusion.

### P3#11 — Re-enable `var` in `.scalafix.conf` `DisableSyntax`
Only 2 `private var`s exist in main sources
(`ErrorOperations.messageTimeoutId`, `Logger.minLevel`). Re-enable the rule
with `// scalafix:ok` on those two (or eliminate them per P2#7) to prevent
regression.

### P3#12 — Replace `for (_ <- 0 until MAX_UNDO_DEPTH)` in UndoManager ✅
Resolved 2026-04-22 (under P3#15). The two-stack ping-pong replaced
with `while undoStack.size > MAX_UNDO_DEPTH do undoStack.remove(undoStack.size - 1)`.

### P3#13 — Promote remaining `() => AppState.foo()` callbacks to `Observer[Unit]` ✅
Resolved 2026-04-22 — with a different approach than originally proposed.

The original suggestion was `Observer[Unit]` wrappers on `AppState` for
each action. Evaluating: that would require ~15 `val xObserver =
Observer(_ => x())` definitions, each of which is more ceremony than the
`() => x()` it replaces. Same semantics either way — purely a syntactic
question.

**Actual fix:** `MenuBarComponent.dropdownLink*` helpers now take
`action: => Unit` (by-name) instead of `action: () => Unit`. Call sites
drop the `() =>` prefix entirely:

```scala
// Before
dropdownLink("Clear Tiling", () => AppState.clearTiling())

// After
dropdownLink("Clear Tiling", AppState.clearTiling())
```

24 call sites cleaned up in one file. Three multi-line action blocks
(`New`, `Zoom In`, `Zoom Out`) wrapped in explicit `{ … }` blocks for
clarity. The `--> observer` idiom is still preferred for direct stream
bindings (as documented in `docs/laminar-conventions.md` §4) —
`UndoComponent` continues to use `--> AppState.undoObserver` since the
observer there is wired with `withCurrentValueOf(isProcessing)` and
genuinely benefits from the reactive form.

### P3#15 — Consolidate `AppStateSnapshot` fields into aggregate case classes ✅
Resolved 2026-04-22. What landed:

- `AppStateSnapshot` went from **10 flat fields + timestamp** to **5
  fields**: `tessellation: TessellationState`, `tools: ToolState`,
  `irregular: IrregularState`, `polygonColors: Map[FaceId, ColorRGB]`,
  `fillColor: ColorRGB`. (The last two cherry-picked because the rest
  of `ColorState` is UI preference / picker state, not app model.)
- `timestamp` deleted entirely (was never read anywhere).
- `UndoManager.isStateEquivalent` (9-line hand comparison) deleted;
  structural `==` on the case class does the same job.
- `UndoManager.restoreState` went from ~10 lines across 4 aggregates
  (plus a now-removed `ColorState.*` export reliance) to 4 lines: one
  `.set` per full aggregate + one `.update` for the two cherry-picked
  `ColorState` fields. Undo now emits 4 signal events per restore
  instead of 10.
- `AppStateSnapshot.fromCurrentState` went from 10 `.now()` calls to 4
  (one per aggregate). Adding a field inside any of the three
  aggregate case classes now automatically propagates into the snapshot
  and restore.
- `EditorStateFixture.afterEach` rewritten to 4 atomic aggregate
  restores (mirrors `UndoManager.restoreState`).
- Side effect: P2#5 (drift risk) and P3#12 (MAX_UNDO_DEPTH loop)
  closed too.

### P3#14 — Relocate `UndoManager` out of `utils/` ✅
Resolved 2026-04-22. `UndoManager` moved from
`utils/UndoManager.scala` to `operations/UndoManager.scala` (package
changed to `io.github.scala_tessella.editor.operations`). The
corresponding `UndoManagerSpec.scala` moved from `test/.../utils/` to
`test/.../operations/` to keep test and production together.

All 10 importers updated (mix of bare imports and `{…, UndoManager}`
compound imports). `utils/` no longer contains any operation-style
orchestrator. One pre-existing `utils → operations` edge remains
(`utils/file/SvgImporter` importing `UndoManager`) — that's the
separate "utils/file/ is really I/O operations" issue, not in scope.

---

## Recommended order of attack

ADRs 001 and 002 plus the original P2 wave are done. Open items:

1. ~~**P3#19** (test-code hygiene)~~ ✅ done 2026-04-22.
2. ~~**P2#17** (operations + interactions coverage gaps)~~ ✅ done 2026-04-22.
3. ~~**P2#18** (property specs for `Radian`, `PolygonPlacementGeometry`)~~ ✅ done 2026-04-22.
4. ~~**ADR-003** (P1#16 — test strategy)~~ ✅ Accepted 2026-04-22; scaffolding
   (`LaminarTestSupport` + first mount spec) landed same day.
5. **P3#20** (extract pure-function helpers from components) — first batch
   done 2026-04-22; continue opportunistically when touching each file.
6. **Implementation of ADR-003 Tier 1** — first batch (helper + 2 popup
   specs) landed 2026-04-22; continue opportunistically with the remaining
   popups and small components, reusing `LaminarTestSupport`.
7. **Implementation of ADR-003 Tier 2** — first cut (4-test smoke suite +
   scaffold) landed 2026-04-22; remaining work is SVG round-trip, touch
   emulation, visual snapshots, and CI integration.
8. Remaining P3 items (P3#8 dcel pin, P3#10 README architecture section,
   P3#11 scalafix `var` rule).

---

## ADR workflow

ADRs live under `docs/adr/`:

- [`000-template.md`](docs/adr/000-template.md) — template for new ADRs.
- [`001-package-layering.md`](docs/adr/001-package-layering.md) — Accepted.
- [`002-state-container.md`](docs/adr/002-state-container.md) — Accepted.
- [`003-test-strategy.md`](docs/adr/003-test-strategy.md) — Accepted.
- [`004-e2e-test-language.md`](docs/adr/004-e2e-test-language.md) — Accepted.
- [`005-android-packaging-fdroid.md`](docs/adr/005-android-packaging-fdroid.md) — Proposed.
- [`006-scalajs-wasm-backend.md`](docs/adr/006-scalajs-wasm-backend.md) — Accepted.
- [`007-ios-packaging-pwa-first.md`](docs/adr/007-ios-packaging-pwa-first.md) — Proposed.
- [`008-desktop-packaging-tauri.md`](docs/adr/008-desktop-packaging-tauri.md) — Accepted.

Each ADR follows the standard template: **Status** (Proposed / Accepted /
Superseded), **Context**, **Decision**, **Consequences**, **Alternatives
considered**, **References**. Update the status line in the ADR when the
decision is made; do not rewrite history.
