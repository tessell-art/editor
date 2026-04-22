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
**ADR:** [ADR-003 — Test strategy](docs/adr/003-test-strategy.md) (Proposed).

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

### P2#17 — Close `operations` and `interactions` coverage gaps
Depends on ADR-003 only for the *style* of UI tests; the items below are
plain MUnit + `EditorStateFixture` and can start immediately.

Untested `operations` (each is small, all read/write `EditorState`):
- `DeletionOperations` — `attemptFaceDeletion`, `attemptVertexDeletion`,
  `attemptEdgeDeletion`. Cover empty-tiling no-op, valid deletion, and
  failure → `ErrorOperations` path (mirrors the `attemptPolygonAddition`
  test in `TessellationOperationsSpec`).
- `SymmetryOperations` — six toggle/overlay methods; assert the relevant
  `ViewState` / overlay flag flips and that `displaySizeInfo` renders the
  expected message on a known tiling.
- `MeasurementOperations` — distance + angle between two `ClickablePoint`s,
  `clearAll` reset. Property test for `distance(a, b) == distance(b, a)`.
- `SettingsOperations` — `applySettings` / `resetFillColorToDefault` round-trip
  with `SettingsStorage` (already exercised under JSDOM in
  `SettingsStorageSpec`).
- `OperationGuard` — exercise each guard predicate against representative
  states.

Partial coverage to deepen:
- `KeyboardEventHandler.handleKeyDown` — dispatch a synthetic
  `KeyboardEvent` against the JSDOM document and assert the bound
  operation ran (undo, zoom, rotation). The pure helpers are already
  covered.
- `MouseEventHandler` — `handleWheel`, `handleMouseDown`/`Move`/`Up`,
  `getCanvasRelativePosition`. Same JSDOM event-dispatch pattern.
- `PlacementOperations` — currently only the empty-tiling early-return
  branches. Add success-path tests for both `attemptPolygonAddition` and
  `attemptPolygonInsertion` on a non-empty tiling.

### P2#18 — Property-based specs for `Radian` and `PolygonPlacementGeometry`
Two property specs exist (`GeometryPropertySpec`, `ViewOperationsPropertySpec`);
both pay off well. Add:
- `RadianPropertySpec` — normalisation idempotence, addition commutative
  mod 2π, `toBigDecimal` round-trip within ε.
- `PolygonPlacementPropertySpec` — placed regular polygon vertices lie on
  the expected circle, edge length invariants.

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

### P3#19 — Test-code hygiene cleanup
Independent, low-risk, no ADR dependency:
- Rename placeholder test `"anf"` at
  `src/test/.../operations/ViewOperationsSpec.scala:31` to something
  descriptive (e.g. `"AngleDegree(90).toBigRadian ≈ π/2"`).
- Delete the commented-out test block in `SvgExporterSpec.scala:116+`.
- Consolidate `SvgExporterSpec`: the ~15 single-`assert(contains(...))`
  tests run on the same generated XML. Group into 3–4 tests with stronger
  assertions, or convert to a single snapshot file. Today one upstream
  change cascades into ~15 red tests for the same root cause.
- `SettingsStorageSpec`: replace the `assume(isLocalStorageAvailable, …)`
  silent-skip with `fail` if `JSDOMNodeJSEnv` ever stops providing
  `localStorage` — current behaviour passes invisibly.
- `AppStateSpec.refreshSettingsTempValues`: collapse the 7 sequential
  `_.copy(…)` updates into one.

### P3#20 — Extract pure-function helpers from UI components
`TessellationCursorStyles` is the model: cursor logic lives as a pure
function, tested directly by `TessellationCursorStylesSpec` (4 tests, no
DOM, no Laminar). Replicate the pattern across renderers and popups so
~80% of component logic is testable without mounting. Each extraction is
a small refactor; do them opportunistically when touching a component for
other reasons. Lowers the surface area that ADR-003's Laminar-in-JSDOM
work has to cover.

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

1. **P3#19** (test-code hygiene). Independent, no ADR dependency, makes the
   suite cheaper to grow before doing it. Quick.
2. **P2#17** (operations + interactions coverage gaps). Plain MUnit +
   `EditorStateFixture` — no new tooling needed. Closes most of the
   currently-uncovered code.
3. **P2#18** (property specs for `Radian`, `PolygonPlacementGeometry`).
   Independent, small.
4. **ADR-003** (P1#16 — test strategy). Decide and accept *before* writing
   the first Laminar-in-JSDOM mount test or wiring up Playwright.
5. **P3#20** (extract pure-function helpers from components). Apply
   opportunistically when touching a component for other reasons.
6. **Implementation of ADR-003 tier 1** — Laminar-in-JSDOM mount tests for
   popups and components, helped along by P3#20.
7. **Implementation of ADR-003 tier 2** — small Playwright smoke suite.
   Last, deliberately.
8. Remaining P3 items (P3#8 dcel pin, P3#10 README architecture section,
   P3#11 scalafix `var` rule).

---

## ADR workflow

ADRs live under `docs/adr/`:

- [`000-template.md`](docs/adr/000-template.md) — template for new ADRs.
- [`001-package-layering.md`](docs/adr/001-package-layering.md) — Accepted.
- [`002-state-container.md`](docs/adr/002-state-container.md) — Accepted.
- [`003-test-strategy.md`](docs/adr/003-test-strategy.md) — Proposed.

Each ADR follows the standard template: **Status** (Proposed / Accepted /
Superseded), **Context**, **Decision**, **Consequences**, **Alternatives
considered**, **References**. Update the status line in the ADR when the
decision is made; do not rewrite history.
