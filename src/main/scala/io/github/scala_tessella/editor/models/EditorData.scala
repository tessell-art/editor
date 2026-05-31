package io.github.scala_tessella.editor.models

import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.TilingSymmetry.BoundaryLocation
import io.github.scala_tessella.dcel.geometry.{AngleDegree, RegularPolygon}
import io.github.scala_tessella.dcel.structure.{FaceId, VertexId}
import io.github.scala_tessella.editor.utils.geo.{Point, Radian}
import io.github.scala_tessella.editor.utils.{ColorRGB, FirstRunStorage, SettingsStorage}
import io.github.scala_tessella.ring_seq.RingSeq.isRotationOrReflectionOf
import org.scalajs.dom

type VertexCoord = (id: VertexId, point: Point)

// Case class to represent a failed polygon placement
case class FailedPolygonPlacement(
    edgeIndex: Int,
    angles: Vector[AngleDegree],
    edge: (VertexCoord, VertexCoord),
    tiling: TilingDCEL,
    intoFace: Option[FaceId] = None
)

// Case class to represent a failed polygon deletion
case class FailedPolygonDeletion(faceId: FaceId, polygonNodes: Vector[VertexId])

// Case class to represent a fan animation overlay
case class FanAnimation(
    facePoints: List[(FaceId, String)],
    pivot: Point,
    copies: Int,
    stepAngle: Radian,
    durationMs: Int,
    staggerMs: Int
)

case class DoublingAnimation(
    facePoints: List[(FaceId, String)],
    delta: Point,
    durationMs: Int
)

case class MirrorAnimation(
    facePoints: List[(FaceId, String)],
    axisY: Double,
    durationMs: Int
)

/** In-flight "Add Copy ▸ Translate" drag. While the user drags the dashed skeleton of the whole tiling,
  * `facePoints` (canvas-view polygon strings, snapshot at drag start) are re-rendered translated by `deltaCv`
  * (a free, unsnapped canvas-view offset = current pointer − `grabPointCv`, so the skeleton tracks the cursor
  * 1:1). `sourceVertexId`/`sourcePointCv` is the vertex nearest the press — the eventual translation's
  * `from`. `snapTarget` is the vertex nearest the current candidate (`sourcePointCv + deltaCv`) within the
  * snap radius, highlighted live; on release it becomes the `to` endpoint. Both endpoints resolve to exact
  * `BigPoint` vertex coords so the welded copy can coincide exactly.
  */
case class TranslateCopyDrag(
    facePoints: List[(FaceId, String)],
    sourceVertexId: VertexId,
    sourcePointCv: Point,
    grabPointCv: Point,
    deltaCv: Point,
    snapTarget: Option[(VertexId, Point)]
)

/** In-flight "Add Copy ▸ Rotate" drag. The user pressed near a centre dot — `centerAnchor` identifies it
  * (`Vertex` / `MidPoint` / `Center`) so the exact `BigPoint` pivot is recomputed at commit, while `centerCv`
  * (canvas-view) drives the angle math and the skeleton's `rotate(...)` transform. `candidates` are the snap
  * angles allowed for this centre type (vertex → edge-alignment; midpoint → 180°; symmetric face → 360/k
  * multiples). As the user drags, `appliedDeg` is the live rotation shown (snapped or free) and `snapped` is
  * the candidate within tolerance — the angle welded on release via `maybeAddRotatedCopy`.
  */
case class RotateCopyDrag(
    facePoints: List[(FaceId, String)],
    centerAnchor: Anchor,
    centerCv: Point,
    candidates: List[AngleDegree],
    grabAngle: Radian,
    appliedDeg: Double,
    snapped: Option[AngleDegree]
)

/** In-flight "Add Copy ▸ Reflect" (or **Glide reflect** when `glide`) drag. The mirror axis is the line
  * through two tiling anchors: `axisAnchor` (point A, fixed at press) and the snapped second anchor.
  * `axisACv`/`axisBCv` are the canvas-view endpoints driving the live skeleton transform and the spanning
  * axis guide line (B follows the cursor, snapping to the nearest anchor). On release with a `snapTarget`,
  * the copy is welded via `maybeAddMirroredCopy` (reflect) or `maybeAddGlideReflectedCopy` (glide — reflect
  * across A–B then slide along it by the vector B − A, so for glide the A→B *direction and length* matter,
  * not just the line). Exact `BigPoint`s are recomputed from the two anchors; both isometries are exact
  * rational maps, so a snapped axis yields exact coincidence.
  */
case class ReflectCopyDrag(
    facePoints: List[(FaceId, String)],
    axisAnchor: Anchor,
    axisACv: Point,
    axisBCv: Point,
    snapTarget: Option[(Anchor, Point)],
    glide: Boolean = false
)

enum Anchor:

  case Vertex(vertexId: VertexId)
  case Center(faceId: FaceId)
  case MidPoint(startVertexId: VertexId, endVertexId: VertexId)

case class ClickablePoint(point: Point, anchor: Anchor)

// Editor mode enumeration
enum EditorMode:
  case Select, Delete

// Tool enumeration. AddPolygon is the default mode (always one mode is active);
// Inserter has been folded into AddPolygon + AddSubmode.Inside.
enum Tool:
  case AddPolygon, ColorPicker, ShapeAndColorPicker, SelectByColor, Eraser, Measurement, Fan, TranslateCopy,
    RotateCopy, ReflectCopy, GlideReflectCopy

// Sub-mode for AddPolygon. Outside places on perimeter edges; Inside places inside a face
// (formerly the Inserter tool). Meaningful only when activeTool == AddPolygon.
enum AddSubmode:
  case Outside, Inside

/** Tool-state aggregate: the currently active editor mode and tool (always one), the AddPolygon sub-mode, and
  * the number of sides of the polygon selected in the palette. Held as a single `Var[ToolState]` so that
  * cross-field invariants are visible and undo/redo restores the whole aggregate atomically.
  */
case class ToolState(
    editorMode: EditorMode,
    activeTool: Tool,
    addSubmode: AddSubmode,
    selectedPolygon: Option[Int]
):
  /** True when AddPolygon is active in its Inside sub-mode (formerly `Tool.Inserter`). */
  def isAddInside: Boolean = activeTool == Tool.AddPolygon && addSubmode == AddSubmode.Inside

object ToolState:
  val initial: ToolState = ToolState(
    editorMode = EditorMode.Select,
    activeTool = Tool.AddPolygon,
    addSubmode = AddSubmode.Outside,
    selectedPolygon = None
  )

/** Tessellation-state aggregate: the current tiling and the selection sets (perimeter edges and interior
  * polygons). Writes go through `tessellationState.update(_.copy(...))`; undo/redo restore replaces the whole
  * value atomically.
  */
case class TessellationState(
    currentTiling: TilingDCEL,
    selectedPerimeterEdges: Set[String],
    selectedTilingPolygons: Set[FaceId]
)

object TessellationState:
  val initial: TessellationState = TessellationState(
    currentTiling = TilingDCEL.empty,
    selectedPerimeterEdges = Set.empty,
    selectedTilingPolygons = Set.empty
  )

/** View-state aggregate: the canvas view transformation, the node-label toggle, and the symmetry-overlay
  * visibility flags plus their cached computations (uniformity, rotation, reflection). Kept as one aggregate
  * because it's what components subscribe to as a unit for rendering the canvas overlays.
  */
case class ViewState(
    viewTransform: ViewTransform,
    showNodeLabels: Boolean,
    showUniformity: Boolean,
    uniformityMap: Option[Map[VertexId, Int]],
    showRotation: Boolean,
    rotationVertexIds: Option[List[BoundaryLocation]],
    showReflection: Boolean,
    reflectionVertexIds: Option[List[(BoundaryLocation, BoundaryLocation)]],
    showTilingInfo: Boolean
)

object ViewState:
  val initial: ViewState = ViewState(
    viewTransform = ViewTransform(),
    showNodeLabels = false,
    showUniformity = false,
    uniformityMap = None,
    showRotation = false,
    rotationVertexIds = None,
    showReflection = false,
    reflectionVertexIds = None,
    showTilingInfo = false
  )

/** Measurement-state aggregate: ruler/eraser/inserter tool state — start/end points, previous end,
  * highlighted polygon, clickable points, distance and angle results, angle display unit.
  * `MeasurementOperations.clearAll()` resets the first 7 fields (leaves `isAngleShownInRad` alone).
  */
case class MeasurementState(
    measurementStartPoint: Option[ClickablePoint],
    measurementEndPoint: Option[ClickablePoint],
    measurementPreviousEndPoint: Option[Point],
    highlightedPolygonId: Option[FaceId],
    clickablePoints: List[ClickablePoint],
    measurementResult: Option[Double],
    measurementAngle: Option[Radian],
    isAngleShownInRad: Boolean
)

object MeasurementState:
  val initial: MeasurementState = MeasurementState(
    measurementStartPoint = None,
    measurementEndPoint = None,
    measurementPreviousEndPoint = None,
    highlightedPolygonId = None,
    clickablePoints = Nil,
    measurementResult = None,
    measurementAngle = None,
    isAngleShownInRad = true
  )

/** Popup-state aggregate: visibility flags for the app's modal popups. */
case class PopupState(
    showAboutPopup: Boolean,
    showGuidePopup: Boolean,
    showShortcutsPopup: Boolean,
    showIrregularPolygonPopup: Boolean,
    showSettingsPopup: Boolean,
    showSettingsColorPicker: Boolean,
    showTemplateGallery: Boolean,
    showRecentFilesPanel: Boolean,
    showPrintPopup: Boolean,
    showUnsavedConfirm: Boolean
)

object PopupState:
  val initial: PopupState = PopupState(
    showAboutPopup = false,
    showGuidePopup = false,
    showShortcutsPopup = false,
    showIrregularPolygonPopup = false,
    showSettingsPopup = false,
    showSettingsColorPicker = false,
    showTemplateGallery = false,
    showRecentFilesPanel = false,
    showPrintPopup = false,
    showUnsavedConfirm = false
  )

/** Phone-only palette bottom-sheet detent. Meaningful only at phone widths; desktop ignores the value because
  * the palette renders as a fixed left column there.
  */
enum PaletteSheetDetent:
  case Peek, Full

/** A reference to a previously-opened file. v1 only stores entries for templates loaded via the gallery
  * (re-fetchable URLs); user-imported SVGs would need persistent file handles (File System Access API or
  * Tauri) and aren't tracked yet.
  *
  * @param path
  *   URL/relative path that can be re-fetched (e.g. "templates/regular/regular_3-3-3-3-3-3.svg").
  * @param filename
  *   Display name (e.g. the SVG filename).
  * @param lastOpenedAt
  *   Unix epoch milliseconds at the time of the most recent open.
  */
case class RecentFile(path: String, filename: String, lastOpenedAt: Long)

/** User preference for animation behaviour. `Auto` honours the OS-level `prefers-reduced-motion` media query;
  * `On`/`Off` override it explicitly.
  */
enum ReduceMotionPref:
  case Auto, On, Off

/** Paper size for Print-to-PDF. Maps to a CSS `@page { size: ... }` value. */
enum PaperSize:
  case A4, Letter

/** Orientation for Print-to-PDF. Pairs with [[PaperSize]] in the `@page { size: ... }` rule. */
enum Orientation:
  case Portrait, Landscape

/** Non-color settings (boundary-edge width, reduce-motion preference). Color settings live in [[ColorState]]
  * for historical reasons.
  *
  * Each user-editable field has a paired `temp*` companion that the Settings popup writes to as the user
  * drags / toggles. "Apply" promotes temp → saved and persists; "Cancel" discards temps.
  */
case class SettingsState(
    boundaryEdgeWidth: Double,
    polygonEdgeWidth: Double,
    reduceMotion: ReduceMotionPref,
    tempBoundaryEdgeWidth: Double,
    tempPolygonEdgeWidth: Double,
    tempReduceMotion: ReduceMotionPref
)

object SettingsState:
  /** Loads the persisted preferences from `SettingsStorage`; falls back to `EditorConfig` defaults. */
  def initial: SettingsState =
    val width        = SettingsStorage.loadBoundaryEdgeWidth().getOrElse(EditorConfig.defaultBoundaryEdgeWidth)
    val polygonWidth =
      SettingsStorage.loadPolygonEdgeWidth().getOrElse(EditorConfig.defaultPolygonEdgeWidth)
    val rm           = SettingsStorage.loadReduceMotion().getOrElse(ReduceMotionPref.Auto)
    SettingsState(
      boundaryEdgeWidth = width,
      polygonEdgeWidth = polygonWidth,
      reduceMotion = rm,
      tempBoundaryEdgeWidth = width,
      tempPolygonEdgeWidth = polygonWidth,
      tempReduceMotion = rm
    )

/** UI-state aggregate: transient UI flags — processing/loading guards, drag state, the canvas DOM element
  * ref, the mobile menu toggle, the phone palette-sheet detent, and the first-run welcome overlay flag.
  *
  * `isPaletteDragActive` is the drag-from-palette gesture flag (Phase 5.6), distinct from `isDragging` which
  * is the canvas pan drag. When set, edge hover-previews stand down so the drag's snap-driven preview owns
  * the dotted wireframe.
  */
case class UIState(
    isProcessing: Boolean,
    loadingMessage: Option[String],
    isDragging: Boolean,
    dragStart: Option[Point],
    canvasElementRef: Option[dom.Element],
    isMenuOpen: Boolean,
    paletteSheetDetent: PaletteSheetDetent,
    showFirstRunOverlay: Boolean,
    isPaletteDragActive: Boolean
)

object UIState:
  /** `def` (not `val`) so `showFirstRunOverlay` can read [[FirstRunStorage]] at app startup; subsequent
    * resets via tests / dev rebuild get a fresh read of the persisted flag.
    */
  def initial: UIState = UIState(
    isProcessing = false,
    loadingMessage = None,
    isDragging = false,
    dragStart = None,
    canvasElementRef = None,
    isMenuOpen = false,
    paletteSheetDetent = PaletteSheetDetent.Peek,
    showFirstRunOverlay = !FirstRunStorage.hasSeenFirstRun,
    isPaletteDragActive = false
  )

/** Where the color picker was opened from. Drives the popup title — `FillSelected` shows "Pick color and fill
  * selected" (the on-canvas Fill swatch button's intent), `Default` keeps the generic "Select Color" used by
  * Edit → Fill Color and the palette's fill-all-shape actions.
  */
enum ColorPickerContext:
  case Default, FillSelected

/** Color-state aggregate: persisted preferences (default fill, perimeter edge), live working fill color, the
  * three settings-popup temp colors, the per-polygon color map, the color picker visibility flag, the
  * color-picker's working value, and the open-context that drives the picker's title.
  */
case class ColorState(
    defaultStartFillColor: ColorRGB,
    perimeterEdgeColor: ColorRGB,
    polygonEdgeColor: ColorRGB,
    fillColor: ColorRGB,
    tempDefaultFillColor: ColorRGB,
    tempPerimeterEdgeColor: ColorRGB,
    tempPolygonEdgeColor: ColorRGB,
    tempSettingsPickerColor: ColorRGB,
    polygonColors: Map[FaceId, ColorRGB],
    showColorPicker: Boolean,
    tempColor: ColorRGB,
    colorPickerContext: ColorPickerContext
)

object ColorState:
  /** Loads persisted preferences from `SettingsStorage`; falls back to `EditorConfig` defaults. `def` (not
    * `val`) so tests can rebuild a fresh baseline after mutating storage.
    */
  def initial: ColorState =
    val defaultFill = SettingsStorage.loadDefaultStartFillColor().getOrElse(EditorConfig.defaultPolygonColor)
    val perimeter   = SettingsStorage.loadPerimeterEdgeColor().getOrElse(EditorConfig.defaultPerimeterEdgeColor)
    val polygonEdge =
      SettingsStorage.loadPolygonEdgeColor().getOrElse(EditorConfig.defaultPolygonEdgeColor)
    ColorState(
      defaultStartFillColor = defaultFill,
      perimeterEdgeColor = perimeter,
      polygonEdgeColor = polygonEdge,
      fillColor = defaultFill,
      tempDefaultFillColor = defaultFill,
      tempPerimeterEdgeColor = perimeter,
      tempPolygonEdgeColor = polygonEdge,
      tempSettingsPickerColor = defaultFill,
      polygonColors = Map.empty,
      showColorPicker = false,
      tempColor = defaultFill,
      colorPickerContext = ColorPickerContext.Default
    )

/** File-state aggregate: the open-file name and the last-saved tiling snapshot used for the dirty-tracker.
  * `lastSavedTiling = None` means "no save / load has happened yet" — in that baseline an empty tiling is
  * considered clean and any non-empty one is dirty. After Save / Load / template, the field holds the tiling
  * at that moment so subsequent mutations show as dirty.
  */
case class FileState(
    currentFileName: Option[String],
    lastSavedTiling: Option[TilingDCEL]
)

object FileState:
  val initial: FileState = FileState(currentFileName = None, lastSavedTiling = None)

/** Snap-hint overlay for the drag-from-palette gesture: a halo + chevron drawn on the edge that will become
  * the commit target on release. The chevron points in the *growth direction* (outward for Outside addition,
  * into the snapped face for Inside) so the user sees both *which* edge and *which side* is active before
  * committing. All points and vectors are in canvas-view coords.
  */
case class PaletteSnapHint(
    edgeStart: Point,
    edgeEnd: Point,
    growthNormal: Point
)

/** Preview-state aggregate. Three concurrent previews:
  *
  *   - `previewPlacement`: the *would-be* commit. In hover mode (desktop click-to-place) this is also what
  *     the user sees — a dotted polygon glued to the edge. In drag-from-palette mode it stays latched to the
  *     snapped edge to be picked up by the release handler, but is NOT rendered (the ghost takes over the
  *     visible role).
  *   - `paletteGhost`: a free-floating dotted polygon that tracks the pointer during a drag-from-palette
  *     gesture. Vertices are stored in canvas-view coordinates (post `tilingPointToCanvasView`, pre
  *     `contentGroup` transform). Only set while the gesture is in flight.
  *   - `paletteSnapHint`: halo + directional chevron on the edge currently latched as the commit target.
  *     Cleared whenever the drag has no commit target (off-canvas / empty tiling).
  *   - `previewIsValid`: when a `previewPlacement` is set, drives the visual: a valid placement paints the
  *     normal theme-aware dotted preview; an invalid one (rejected by the cheap angle-at-endpoints check in
  *     `PlacementValidation`) paints a red dotted outline and dims the chevron, so the user sees that the
  *     target is recognised but won't accept the polygon. Defaults to `true`; only meaningful while a preview
  *     is active.
  */
case class PreviewState(
    previewPlacement: Option[FailedPolygonPlacement],
    paletteGhost: Option[Vector[Point]],
    paletteSnapHint: Option[PaletteSnapHint],
    previewIsValid: Boolean = true,
    translateCopyDrag: Option[TranslateCopyDrag] = None,
    rotateCopyDrag: Option[RotateCopyDrag] = None,
    reflectCopyDrag: Option[ReflectCopyDrag] = None
)

object PreviewState:
  val initial: PreviewState =
    PreviewState(previewPlacement = None, paletteGhost = None, paletteSnapHint = None)

/** Theme-state aggregate: user's theme preference (`None` means follow the system) and the detected system
  * theme. Derived signals (`effectiveTheme`, `overlayPreviewStrokeColor`) live on `EditorState` next to the
  * aggregate `Var`.
  */
case class ThemeState(
    userThemePreference: Option[Theme],
    systemTheme: Theme
)

object ThemeState:
  val initial: ThemeState = ThemeState(
    userThemePreference = None,
    systemTheme = Theme.Light
  )

/** Error-state aggregate: the active error message plus optional "what went wrong" payloads for a failed
  * polygon placement or deletion (used by the canvas to render visual feedback).
  */
case class ErrorState(
    errorMessage: Option[String],
    failedPlacement: Option[FailedPolygonPlacement],
    failedDeletion: Option[FailedPolygonDeletion]
)

object ErrorState:
  val initial: ErrorState = ErrorState(
    errorMessage = None,
    failedPlacement = None,
    failedDeletion = None
  )

/** Animation-state aggregate: three mutually-exclusive transient animations. */
case class AnimationState(
    fanAnimation: Option[FanAnimation],
    doublingAnimation: Option[DoublingAnimation],
    mirrorAnimation: Option[MirrorAnimation]
)

object AnimationState:
  val initial: AnimationState = AnimationState(
    fanAnimation = None,
    doublingAnimation = None,
    mirrorAnimation = None
  )

/** Palette shape-queue aggregate: a bounded queue of shapes (regular + irregular, mixed) used for the palette
  * grid (head = most-recent), plus the index of the irregular one currently selected, if any. The legacy
  * field name `recentIrregularPolygons` is retained to avoid churn across tests; it now stores the full
  * palette queue. The derived `selectedIrregularPolygon` signal lives on `EditorState`.
  */
case class IrregularState(
    recentIrregularPolygons: Vector[Vector[AngleDegree]],
    selectedIndex: Option[Int]
):
  /** Whether any irregular polygon is currently selected. */
  def isSelected: Boolean = selectedIndex.isDefined

  /** The currently selected irregular shape, if any. */
  def selectedShape: Option[Vector[AngleDegree]] =
    selectedIndex.flatMap(recentIrregularPolygons.lift)

  /** The most-recent (head) irregular shape, if any. */
  def headOption: Option[Vector[AngleDegree]] = recentIrregularPolygons.headOption

  /** Add a shape at the head of the queue, deduping by rotation/reflection equivalence and bounding to
    * `EditorConfig.paletteShapeQueueSize`. If `selectIt` is true, the new head becomes the selection.
    */
  def withShape(shape: Vector[AngleDegree], selectIt: Boolean): IrregularState =
    val existingIdx  = recentIrregularPolygons.indexWhere(_.isRotationOrReflectionOf(shape))
    val nextList     =
      if existingIdx >= 0 then
        // Move existing entry to head, replacing its angle vector with the latest orientation
        shape +:
          (recentIrregularPolygons.take(existingIdx)
            ++ recentIrregularPolygons.drop(existingIdx + 1))
      else
        (shape +: recentIrregularPolygons).take(EditorConfig.paletteShapeQueueSize)
    val nextSelected = if selectIt then Some(0) else selectedIndex
    copy(recentIrregularPolygons = nextList, selectedIndex = nextSelected)

  /** Replace the currently-selected entry by applying `f`. No-op if no entry is selected or the selected
    * index is out of range.
    */
  def updateSelected(f: Vector[AngleDegree] => Vector[AngleDegree]): IrregularState =
    selectedIndex match
      case Some(i) if i >= 0 && i < recentIrregularPolygons.size =>
        copy(recentIrregularPolygons = recentIrregularPolygons.updated(i, f(recentIrregularPolygons(i))))
      case _                                                     => this

  /** Drop selection. */
  def deselected: IrregularState = copy(selectedIndex = None)

  /** Select the head, if the MRU is non-empty. */
  def selectHead: IrregularState =
    if recentIrregularPolygons.nonEmpty then copy(selectedIndex = Some(0)) else this

  /** Select the entry at `index`, if it is within range. Otherwise no-op. */
  def selectAt(index: Int): IrregularState =
    if index >= 0 && index < recentIrregularPolygons.size then copy(selectedIndex = Some(index))
    else this

object IrregularState:
  /** Reference 60°/120° rhombus, retained as a fixture for tests. */
  val initialShape: Vector[AngleDegree] = Vector(60, 120, 60, 120).map(AngleDegree(_))

  /** Empty queue, no selection. Used by the test fixture and as the basis for `initial`. */
  val empty: IrregularState = IrregularState(
    recentIrregularPolygons = Vector.empty,
    selectedIndex = None
  )

  /** App-startup state: the palette queue is pre-seeded with the regular polygons listed in
    * `EditorConfig.polygonSides` (currently 3, 4, 5, 6, 8, 12), in that order, with no selection.
    */
  val initial: IrregularState =
    val seed = EditorConfig.polygonSides.toVector.map(sides => RegularPolygon(sides).angles)
    IrregularState(recentIrregularPolygons = seed, selectedIndex = None)
