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

### P1#2 — Redesign the `EditorState` god-object
**ADR:** [ADR-002 — State container design](docs/adr/002-state-container.md)
(Accepted — Option B. 3 of 14 aggregates migrated 2026-04-22:
`ToolState`, `TessellationState`, `ViewState`. Remaining 11 pending,
one PR each.)

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

### P1#3 — Reactive-first discipline: eliminate accidental `.now()` reads
**ADR:** no — document as a **coding convention** in
`docs/laminar-conventions.md` (referenced by ADR-002).

`.now()` is called **110 times across 9 files**. Two existing comments
(`KeyboardEventHandler.scala:27`, `EditorCanvasComponent.scala:99`) already
document avoiding `.now()`; the discipline should be propagated. Example hot
spots:
- `TouchEventHandler.scala:108-112` — five `.now()` reads in a row, perfect
  candidate for `signal.withCurrentValueOf(…).foreach`.
- `AppStateSnapshot.fromCurrentState` — ten `.now()` reads to produce a
  snapshot; collapses to `state.now()` if P1#2 option B is chosen.

**Convention to codify:**
- `.now()` is allowed only when *snapshotting at an event boundary*
  (e.g. handling a mousedown, persisting to storage). Anywhere inside a
  `.foreach`/`.map` on a signal, use `.withCurrentValueOf` or `combineWith`.
- Rendering: prefer `child <-- signal`, `children <-- signal`, `--> observer`
  over imperative `.now()` in component bodies.

---

## P2 — Code quality & correctness

### P2#4 — `SvgImporter` still throws instead of returning `Either`
`src/main/scala/io/github/scala_tessella/editor/utils/file/SvgImporter.scala:62, :71, :111, :120`
use `throw new Exception(...)` despite commit `bd13119` ("Switch to safe method
returning Either"). The outer `Try { … }.recover` catches them, but
control-flow-through-exceptions is inconsistent with the `Either` style
`TilingSVG.fromMetadata` already returns. Refactor to a single
`Either[String, Tiling]` pipeline; wrap only the raw DOM APIs in `Try`.

### P2#5 — `UndoManager` / `AppStateSnapshot` drift risk
Three hand-maintained lists must stay in sync or undo silently loses state:
- `AppStateSnapshot` case class — 10 fields (`AppStateSnapshot.scala:9`).
- `AppStateSnapshot.fromCurrentState` — 10 `.now()` calls.
- `UndoManager.isStateEquivalent` — 9 field comparisons (`timestamp` excluded).
- `UndoManager.restoreState` — 10 `.set` calls.

Any new `Var` added to `EditorState` is silently dropped by undo. Fixes:
- Make `AppStateSnapshot` the *only* source of truth with structural equality;
  drop `timestamp` or override `equals`.
- Better: once ADR-002 lands option B, a snapshot = the whole `EditorState`
  case class value; all three hand-written lists disappear.

Separately, the `MAX_UNDO_DEPTH` trimming in `UndoManager.scala:40-47`
manually ping-pongs between two stacks. Replace with `ArrayDeque.removeLast()`
or `undoStack.takeRight(MAX_UNDO_DEPTH)`.

### P2#6 — Split `TessellationOperations.scala` (20 KB, ~470 lines, 30+ methods)
Largest file in the project. Contains
`attemptFaceDeletion / attemptVertexDeletion / attemptEdgeDeletion /
attemptFanning / attemptDoubling / attemptMirroring / attemptPolygonAddition /
attemptPolygonInsertion` plus private data classes. Suggested split:
- `DeletionOps` — face / vertex / edge deletion.
- `PlacementOps` — addition / insertion.
- `SymmetryOps` — fanning / doubling / mirroring (+ their `*Context` classes).

Also lacks a module-level scaladoc explaining the `attempt…` naming (silent
failure vs. error-producing contract).

### P2#7a — Resolve `utils → AppState` layering inversion ✅
Resolved under ADR-001 Phase 2 on 2026-04-22. `utils/UndoManager`,
`utils/file/SvgImporter`, and `utils/file/SvgExporter` now call specific
`*Operations` directly instead of going through `AppState`. Enforced by
the `checkLayering` sbt task.

### P2#7 — `ErrorOperations.messageTimeoutId: private var Option[Int]` race
On rapid consecutive errors, the timeout-id tracking has a small race between
`clearTimeout` and `setTimeout`. Replace with `Var[Option[Int]]` (or let the
observer pattern manage the cancellation) — also improves testability.

---

## P3 — Polish & maintainability

### P3#8 — Pin `dcel 0.1.0-SNAPSHOT`
`build.sbt:43`. SNAPSHOT deps hurt reproducibility of CI builds and of old tags
checked out later. Pin to a release or milestone.

### P3#9 — Add module-level scaladoc to `TessellationOperations`
The largest object has none. Landing with P2#6 is natural.

### P3#10 — README "Architecture" section
One paragraph pointing at `EditorState` / `AppState` / `operations` /
`interactions` layering (the future state per ADR-001/002) would short-circuit
most onboarding confusion.

### P3#11 — Re-enable `var` in `.scalafix.conf` `DisableSyntax`
Only 2 `private var`s exist in main sources
(`ErrorOperations.messageTimeoutId`, `Logger.minLevel`). Re-enable the rule
with `// scalafix:ok` on those two (or eliminate them per P2#7) to prevent
regression.

### P3#12 — Replace `for (_ <- 0 until MAX_UNDO_DEPTH)` in UndoManager
Cosmetic; likely resolved as a side effect of P2#5.

### P3#13 — Promote remaining `() => AppState.foo()` callbacks to `Observer[Unit]`
ADR-001 Phase 2 deferred the mass observer-wiring pass. The existing
`--> AppState.undoObserver` / `redoObserver` pattern is the target idiom;
`MenuBarComponent.scala` still uses `() => AppState.toggleShowUniformity()`
etc. as callbacks. Cosmetic but aligns the code with Laminar idiom —
do opportunistically when touching these files for other reasons.

### P3#15 — Consolidate `AppStateSnapshot` fields into aggregate case classes
Currently `AppStateSnapshot` flattens the state into 10 top-level fields
(`editorMode`, `activeTool`, `selectedPolygon`, …). Once ADR-002's Option B
rollout is complete, each aggregate should move into `AppStateSnapshot` as a
case-class-typed field (e.g. `toolState: ToolState`). Benefits:
- Adding a new `Var` to an aggregate automatically appears in the snapshot —
  no hand-maintained list.
- `UndoManager.isStateEquivalent` and `UndoManager.restoreState` both
  collapse to the shape `snapshot.aggregate`.
- Resolves BACKLOG P2#5 (snapshot drift risk) structurally, not by
  convention.

Do opportunistically as each aggregate migrates.

### P3#14 — Relocate `UndoManager` out of `utils/`
`utils/UndoManager.scala` orchestrates state transitions and reads/writes
many `EditorState` fields — conceptually an *operation*, not a utility.
Relocating it to `operations/` would make the layering purely top-down
(utils would contain only stateless helpers). Mechanical move; no
functional change.

---

## Recommended order of attack

1. **ADR-001** (P1#1 — package layering). Small, unblocks the rest.
2. **ADR-002** (P1#2 — state container). Largest blast radius; must precede #3.
3. **P1#3** (`.now()` convention) — document, then apply opportunistically as
   the two ADRs roll out.
4. **P2#5** — falls out almost for free if ADR-002 picks option B.
5. **P2#4** — independent, can be done in parallel.
6. **P2#6** — independent, any time.
7. Everything in P3.

Together, items 1–4 should eliminate roughly half of the `.now()` call sites
without a dedicated sweep.

---

## ADR workflow

ADRs live under `docs/adr/`:

- [`000-template.md`](docs/adr/000-template.md) — template for new ADRs.
- [`001-package-layering.md`](docs/adr/001-package-layering.md) — Proposed.
- [`002-state-container.md`](docs/adr/002-state-container.md) — Proposed.

Each ADR follows the standard template: **Status** (Proposed / Accepted /
Superseded), **Context**, **Decision**, **Consequences**, **Alternatives
considered**, **References**. Update the status line in the ADR when the
decision is made; do not rewrite history.
