package io.github.scala_tessella.editor.models

import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.TilingSymmetry.BoundaryLocation
import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.dcel.structure.{FaceId, VertexId}
import io.github.scala_tessella.editor.utils.geo.{Point, Radian}
import io.github.scala_tessella.editor.utils.{ColorRGB, SettingsStorage}
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

enum Anchor:

  case Vertex(vertexId: VertexId)
  case Center(faceId: FaceId)
  case MidPoint(startVertexId: VertexId, endVertexId: VertexId)

case class ClickablePoint(point: Point, anchor: Anchor)

// Editor mode enumeration
enum EditorMode:
  case Select, Delete

// Tool enumeration
enum Tool:
  case ColorPicker, ShapeAndColorPicker, SelectByColor, Eraser, Inserter, Measurement, Fan

/** Tool-state aggregate: the currently active editor mode, tool (if any), and the number of sides of the
  * polygon selected in the palette. Held as a single `Var[ToolState]` so that cross-field invariants are
  * visible and undo/redo restores the whole aggregate atomically.
  *
  * ADR-002 spike target: demonstrates the Option B state-container approach.
  */
case class ToolState(
    editorMode: EditorMode,
    activeTool: Option[Tool],
    selectedPolygon: Option[Int]
)

object ToolState:
  val initial: ToolState = ToolState(
    editorMode = EditorMode.Select,
    activeTool = None,
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
    reflectionVertexIds: Option[List[(BoundaryLocation, BoundaryLocation)]]
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
    reflectionVertexIds = None
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

/** Popup-state aggregate: 6 boolean visibility flags for the app's modal popups (About, Guide, Shortcuts,
  * IrregularPolygon, Settings, SettingsColorPicker).
  */
case class PopupState(
    showAboutPopup: Boolean,
    showGuidePopup: Boolean,
    showShortcutsPopup: Boolean,
    showIrregularPolygonPopup: Boolean,
    showSettingsPopup: Boolean,
    showSettingsColorPicker: Boolean
)

object PopupState:
  val initial: PopupState = PopupState(
    showAboutPopup = false,
    showGuidePopup = false,
    showShortcutsPopup = false,
    showIrregularPolygonPopup = false,
    showSettingsPopup = false,
    showSettingsColorPicker = false
  )

/** UI-state aggregate: transient UI flags — processing/loading guards, drag state, the canvas DOM element
  * ref, and the mobile menu toggle.
  */
case class UIState(
    isProcessing: Boolean,
    loadingMessage: Option[String],
    isDragging: Boolean,
    dragStart: Option[Point],
    canvasElementRef: Option[dom.Element],
    isMenuOpen: Boolean
)

object UIState:
  val initial: UIState = UIState(
    isProcessing = false,
    loadingMessage = None,
    isDragging = false,
    dragStart = None,
    canvasElementRef = None,
    isMenuOpen = false
  )

/** Color-state aggregate: persisted preferences (default fill, perimeter edge), live working fill color, the
  * three settings-popup temp colors, the per-polygon color map, the color picker visibility flag, and the
  * color-picker's working value.
  */
case class ColorState(
    defaultStartFillColor: ColorRGB,
    perimeterEdgeColor: ColorRGB,
    fillColor: ColorRGB,
    tempDefaultFillColor: ColorRGB,
    tempPerimeterEdgeColor: ColorRGB,
    tempSettingsPickerColor: ColorRGB,
    polygonColors: Map[FaceId, ColorRGB],
    showColorPicker: Boolean,
    tempColor: ColorRGB
)

object ColorState:
  /** Loads persisted preferences from `SettingsStorage`; falls back to `EditorConfig` defaults. `def` (not
    * `val`) so tests can rebuild a fresh baseline after mutating storage.
    */
  def initial: ColorState =
    val defaultFill = SettingsStorage.loadDefaultStartFillColor().getOrElse(EditorConfig.defaultPolygonColor)
    val perimeter   = SettingsStorage.loadPerimeterEdgeColor().getOrElse(EditorConfig.defaultPerimeterEdgeColor)
    ColorState(
      defaultStartFillColor = defaultFill,
      perimeterEdgeColor = perimeter,
      fillColor = defaultFill,
      tempDefaultFillColor = defaultFill,
      tempPerimeterEdgeColor = perimeter,
      tempSettingsPickerColor = defaultFill,
      polygonColors = Map.empty,
      showColorPicker = false,
      tempColor = defaultFill
    )

/** File-state aggregate: currently only the open-file name. Kept as an aggregate to be consistent with the
  * ADR-002 pattern across all state groups.
  */
case class FileState(currentFileName: Option[String])

object FileState:
  val initial: FileState = FileState(currentFileName = None)

/** Preview-state aggregate: hover preview of a polygon placement along a perimeter edge. */
case class PreviewState(previewPlacement: Option[FailedPolygonPlacement])

object PreviewState:
  val initial: PreviewState = PreviewState(previewPlacement = None)

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

/** Irregular-polygon-palette-state aggregate: the latest irregular shape entered and whether it's currently
  * selected. The derived `selectedIrregularPolygon` signal lives on `EditorState`.
  */
case class IrregularState(
    recentIrregularPolygon: Option[Vector[AngleDegree]],
    isIrregularSelected: Boolean
)

object IrregularState:
  val initialShape: Vector[AngleDegree] = Vector(60, 120, 60, 120).map(AngleDegree(_))
  val initial: IrregularState           = IrregularState(
    recentIrregularPolygon = Some(initialShape),
    isIrregularSelected = false
  )
