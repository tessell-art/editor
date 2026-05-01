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

### P1#1 — `models` ↔ `operations` cycle ✅
[ADR-001](docs/adr/001-package-layering.md) (Accepted 2026-04-22). Four-layer
graph (`components/interactions → AppState → operations → models → utils`)
enforced by sbt `checkLayering` on every compile.

### P1#2 — `EditorState` god-object redesign ✅
[ADR-002](docs/adr/002-state-container.md) (Accepted 2026-04-22). All 13
stateful aggregates migrated; `EditorState.scala` shrunk from ~280 lines
(14 nested objects) to 98 lines. `DerivedState` keeps pure derivations.

### P1#16 — Test strategy: Laminar-in-JSDOM + Playwright ✅
[ADR-003](docs/adr/003-test-strategy.md) (Accepted 2026-04-22). Tier 1
scaffolding (`LaminarTestSupport`) + first 4 mount specs landed; Tier 2
Playwright smoke suite under `e2e/` with 5 scenarios + Scala test hooks
(`@JSExportTopLevel` via `window.__tessellaTestHooks__`). CI integration
pending; remaining popup mount specs and touch-emulation visual snapshots
opportunistic.

### P1#21 — Desktop packaging via Tauri 2 ✅
[ADR-008](docs/adr/008-desktop-packaging-tauri.md) (Accepted 2026-04-24).
End-to-end Tauri shell under `desktop/src-tauri/` produces per-OS installers
(Linux .deb/.AppImage, Windows .msi/.exe, macOS .dmg/.app). Shared shortcut
table `MenuShortcuts.scala` ↔ `menu_shortcuts.rs` enforced by
`checkMenuShortcutsParity`. CI matrix in `.github/workflows/desktop.yml`.
Remaining: Flathub / winget / Homebrew Cask submissions (separate backlog
entries once binaries stabilize).

### P1#3 — Reactive-first discipline ✅
[`docs/laminar-conventions.md`](docs/laminar-conventions.md) (2026-04-22).
`.now()` calls dropped 110 → 98; remainder are legitimate event-boundary
reads. Treated as ongoing maintenance under the conventions doc.

---

## P2 — Code quality & correctness

### P2#4 — `SvgImporter` returns `Either` ✅
2026-04-22. 4-step `Either[String, _]` pipeline; main-sources `throw new`
count 4 → 0.

### P2#5 — `UndoManager` / `AppStateSnapshot` drift risk ✅
2026-04-22 (under P3#15). Structural `==` on aggregate case classes replaces
hand-rolled `isStateEquivalent`.

### P2#6 — Split `TessellationOperations.scala` ✅
2026-04-22. 511 lines → 4 files (`TessellationOperations`,
`DeletionOperations`, `PlacementOperations`, `TransformOperations`), each
with module-level scaladoc.

### P2#7 — `ErrorOperations` timer race ✅
2026-04-22. `private final class SingleTimeout` encapsulates cancel+schedule;
two instances (`messageTimeout` / `overlayTimeout`) replace the dangling
`messageTimeoutId`. Atomic state updates throughout.

### P2#7a — `utils → AppState` layering inversion ✅
2026-04-22. Resolved under ADR-001 Phase 2; enforced by `checkLayering`.

### P2#17 — `operations` + `interactions` coverage gaps ✅
2026-04-22. 6 new specs + 2 deepened (`MouseEventHandlerSpec` 2→9,
`KeyboardEventHandlerSpec` 3→9). Suite: 208 → 248 tests.

### P2#18 — Property-based specs for `Radian` and placement geometry ✅
2026-04-22. `RadianPropertySpec` (8 properties) +
`PolygonPlacementPropertySpec` (5 properties). Suite: 248 → 261 tests.

---

## P3 — Polish & maintainability

### P3#8 — Pin `dcel 0.1.0-SNAPSHOT`
`build.sbt:58`. SNAPSHOT deps hurt CI reproducibility and old-tag rebuilds.
Pin to a release once dcel publishes one. Vendored Ivy artifacts under
`lib-repo/` are the current workaround.

### P3#9 — Module scaladoc on `TessellationOperations` ✅
2026-04-22 as a side effect of P2#6.

### P3#10 — README "Architecture" section ✅
2026-05-01. README.md gained an "Architecture" section between
"Technology Stack" and "Getting Started" — one paragraph + 5-layer
dependency diagram, cross-linking ADR-001, ADR-002, and
`docs/laminar-conventions.md`.

### P3#11 — Re-enable `var` in `.scalafix.conf` `DisableSyntax` — won't fix
Investigated 2026-05-01. The `DisableSyntax.var` keyword rule flags every
`var` token regardless of scope. Actual count is 13 in main sources (e.g.
`PolygonSvg` point-in-polygon scan, `TessellationAnimationRenderer` RAF
lifecycle, `PopupCommons` focus trap, `I18n` interpolation walker,
`TransformOperations` `fitDelayed` flags) and 10 in tests — local-scope vars
that are legitimate algorithm choices, not regression risks. The two
`private var`s originally cited (`ErrorOperations.SingleTimeout.currentId`,
`Logger.minLevel`) are the only ones matching the regression-risk profile.
Mechanical enforcement would require a custom Scalafix rule filtering by
member level — investment exceeds the risk. Treat as code-review convention
in `docs/laminar-conventions.md` if it becomes an issue.

### P3#12 — Replace `for (_ <- 0 until MAX_UNDO_DEPTH)` ✅
2026-04-22 (under P3#15). Replaced with bounded `while` loop.

### P3#13 — Promote `() => AppState.foo()` callbacks ✅
2026-04-22. Took the by-name route instead of `Observer[Unit]` wrappers:
`MenuBarComponent.dropdownLink*` now takes `action: => Unit`. 24 call sites
cleaned up. `--> observer` form retained where genuinely beneficial
(`UndoComponent`).

### P3#14 — Relocate `UndoManager` out of `utils/` ✅
2026-04-22. Moved to `operations/`; tests followed.

### P3#15 — Consolidate `AppStateSnapshot` into aggregate case classes ✅
2026-04-22. 10 flat fields + timestamp → 5 fields (3 aggregates +
2 cherry-picked `ColorState` fields). Adding fields to an aggregate now
auto-propagates. Closed P2#5 and P3#12 as side effects.

### P3#19 — Test-code hygiene ✅
2026-04-22. Placeholder rename, dead test-block removal,
`SvgExporterSpec` consolidation (30 → 21 tests, same coverage), atomic
`update(_.copy(…))` collapses.

### P3#20 — Extract pure-function helpers from UI components (ongoing)
`TessellationCursorStyles` is the model: pure logic tested directly. First
batch landed 2026-04-22 (3 specs / 20 tests for `GridRenderer`,
`UndoComponent`, `PolygonPaletteComponent`). Suite: 261 → 281 tests.

Remaining candidates (do opportunistically when touching each file):
`MenuBarComponent` dropdown rules, `CanvasControlComponent` label helpers,
popup labels (`SettingsPopup`, `IrregularPolygonPopup`, `GuidePopup`,
`AboutPopup`, `ShortcutsPopup`), and the `Tessellation*Renderer` SVG
attribute strings.

---

## Recommended order of attack

Open items only:

1. **P3#20** — continue opportunistic helper extraction.
2. **ADR-003 Tier 1** — remaining popup mount specs (`GuidePopup`,
   `ShortcutsPopup`, `SettingsPopup`, `ColorPickerPopupComponent`).
3. **ADR-003 Tier 2** — touch emulation, visual snapshots, CI integration.
4. **P3#8** — pin `dcel` once an upstream release is available.
5. **ADR-008 follow-ups** — Flathub / winget / Homebrew Cask submissions.

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
