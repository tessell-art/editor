package io.github.scala_tessella.editor.models

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.dcel.structure.{FaceId, VertexId}
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.editor.utils.geo.{Point, Radian}
import io.github.scala_tessella.editor.utils.{ColorRGB, SettingsStorage}
import io.github.scala_tessella.editor.models.EditorConfig
import io.github.scala_tessella.dcel.TilingSymmetry.BoundaryLocation

import org.scalajs.dom

import scala.scalajs.js

/** EditorState object contains all the state variables for the editor. The state is organized into logical
  * groups for better maintainability.
  */
object EditorState:
  //
  // FILE MANAGEMENT
  //

  /** Current file name, None if no file is open */
  val currentFileName: Var[Option[String]] = Var(None)

  //
  // EDITOR MODE AND TOOLS
  //

  /** Current editor mode (Select or Delete) */
  val editorMode: Var[EditorMode] = Var(EditorMode.Select)

  /** Currently active tool, if any */
  val activeTool: Var[Option[Tool]] = Var(None)

  /** Selected polygon from the palette (number of sides) */
  val selectedPolygon: Var[Option[Int]] = Var[Option[Int]](None)

  //
  // VIEW STATE
  //

  /** View transformation (scale, rotation, pan) */
  val viewTransform: Var[ViewTransform] = Var(ViewTransform())

  /** Whether node labels should be shown */
  val showNodeLabels: Var[Boolean] = Var(false)

  /** Whether uniformity should be shown */
  val showUniformity: Var[Boolean] = Var(false)

  val uniformityMap: Var[Option[Map[VertexId, Int]]] = Var(None)

  /** Whether rotational symmetry should be shown */
  val showRotation: Var[Boolean] = Var(false)

  val rotationVertexIds: Var[Option[List[BoundaryLocation]]] = Var(None)

  /** Whether reflectional symmetry should be shown */
  val showReflection: Var[Boolean] = Var(false)

  val reflectionVertexIds: Var[Option[List[(BoundaryLocation, BoundaryLocation)]]] = Var(None)

  /** Theme preference: None means follow the system, Some("light") or Some("dark") is user override */
  val userThemePreference: Var[Option[String]] = Var(None)

  //
  // TESSELLATION STATE
  //

  /** Current tiling being edited */
  val currentTiling: Var[TilingDCEL] = Var(TilingDCEL.empty)

  /** Set of selected perimeter-edge IDs */
  val selectedPerimeterEdges: Var[Set[String]] = Var(Set.empty)

  /** Set of selected tiling polygon IDs */
  val selectedTilingPolygons: Var[Set[FaceId]] = Var(Set.empty)

  //
  // COLOR MANAGEMENT
  //

  /** Default fill color for new tilings (can be customized in settings) */
  val defaultStartFillColor: Var[ColorRGB] =
    Var(SettingsStorage.loadDefaultStartFillColor().getOrElse(EditorConfig.defaultPolygonColor))

  /** Perimeter edge color for the editor canvas (can be customized in settings) */
  val perimeterEdgeColor: Var[ColorRGB] =
    Var(SettingsStorage.loadPerimeterEdgeColor().getOrElse(EditorConfig.defaultPerimeterEdgeColor))

  /** Current fill color (RGB tuple) */
  val fillColor: Var[ColorRGB] = Var(defaultStartFillColor.now())

  /** Temporary settings default fill color (used by Settings popup UI) */
  val tempDefaultFillColor: Var[ColorRGB] = Var(defaultStartFillColor.now())

  /** Temporary settings perimeter edge color (used by Settings popup UI) */
  val tempPerimeterEdgeColor: Var[ColorRGB] = Var(perimeterEdgeColor.now())

  /** Map of polygon tags to their colors */
  val polygonColors: Var[Map[FaceId, ColorRGB]] = Var(Map.empty)

  /** Whether the color picker is visible */
  val showColorPicker: Var[Boolean] = Var(false)

  /** Temporary color being edited in the color picker */
  val tempColor: Var[ColorRGB] = Var(fillColor.now())

  //
  // UI STATE
  //

  /** Whether the editor is currently processing an operation */
  val isProcessing: Var[Boolean] = Var(false)

  /** Whether the user is currently dragging */
  val isDragging: Var[Boolean] = Var(false)

  /** Starting point of a drag operation */
  val dragStart: Var[Option[Point]] = Var(None)

  /** Reference to the canvas DOM element */
  val canvasElementRef: Var[Option[dom.Element]] = Var(None)

  //
  // POPUP DIALOGS
  //

  /** Whether the about popup is visible */
  val showAboutPopup: Var[Boolean] = Var(false)

  /** Whether the guide popup is visible */
  val showGuidePopup: Var[Boolean] = Var(false)

  /** Whether the shortcuts popup is visible */
  val showShortcutsPopup: Var[Boolean] = Var(false)

  /** Whether the irregular polygon popup is visible */
  val showIrregularPolygonPopup: Var[Boolean] = Var(false)

  /** Whether the settings popup is visible */
  val showSettingsPopup: Var[Boolean] = Var(false)

  //
  // ERROR HANDLING
  //

  /** Current error message, if any */
  val errorMessage: Var[Option[String]] = Var(None)

  /** Details of a failed polygon placement, if any */
  val failedPlacement: Var[Option[FailedPolygonPlacement]] = Var(None)

  /** Details of a failed polygon deletion, if any */
  val failedDeletion: Var[Option[FailedPolygonDeletion]] = Var(None)

  //
  // HOVER PREVIEW
  //

  /** Hover preview of a polygon placement along a perimeter edge, if any */
  val previewPlacement: Var[Option[FailedPolygonPlacement]] = Var(None)

  //
  // MEASUREMENT TOOL
  //

  /** Starting point for measurement */
  val measurementStartPoint: Var[Option[ClickablePoint]] = Var(None)

  /** Ending point for measurement */
  val measurementEndPoint: Var[Option[ClickablePoint]] = Var(None)

  /** Previous ending point for measurement (for angle calculation) */
  val measurementPreviousEndPoint: Var[Option[Point]] = Var(None)

  /** ID of the highlighted polygon during measurement/insertion */
  val highlightedPolygonId: Var[Option[FaceId]] = Var(None)

  /** List of clickable points for the measurement/eraser tools */
  val clickablePoints: Var[List[ClickablePoint]] = Var(List.empty)

  /** Measurement result (distance) */
  val measurementResult: Var[Option[Double]] = Var(None)

  /** Measurement result (angle) */
  val measurementAngle: Var[Option[Radian]] = Var(None)

  /** Whether the angle measurement result is shown in radians or degrees */
  val isAngleShownInRad: Var[Boolean] = Var(true)

  //
  // IRREGULAR POLYGON SELECTION
  //

  val initialShape: Vector[AngleDegree] = Vector(60, 120, 60, 120).map(AngleDegree(_))

  /** Latest irregular polygon shape chosen (always shown in the slot) */
  val recentIrregularPolygon: Var[Option[Vector[AngleDegree]]] = Var(Some(initialShape))

  /** Whether the irregular polygon is currently selected in the palette */
  val isIrregularSelected: Var[Boolean] = Var(false)

  /** Currently selected irregular polygon (derived from the two states above) */
  val selectedIrregularPolygon: Signal[Option[Vector[AngleDegree]]] =
    isIrregularSelected.signal
      .combineWith(recentIrregularPolygon.signal)
      .map { (isSel, recent) =>

        if isSel then recent else None
      }

  // -------------------------
  // Derived Signals (no Vars)
  // -------------------------

  /** Checks if Inserter tool is active */
  val isInserterActive: Signal[Boolean] =
    activeTool.signal.map(_.contains(Tool.Inserter))

  /** Selected face for insertion, derived from highlighted polygon id */
  val selectedFaceForInsertion: Signal[Option[FaceId]] =
    highlightedPolygonId.signal

  /** System theme as a Signal (uses a media query, no Var) */
  private val systemThemeBus      = new EventBus[String]
  val systemTheme: Signal[String] =
    if js.typeOf(js.Dynamic.global.window) != "undefined" then
      // Browser environment
      val lightMediaQuery = dom.window.matchMedia("(prefers-color-scheme: light)")
      val signal          = systemThemeBus.events.startWith(if lightMediaQuery.matches then "light" else "dark")
      lightMediaQuery.addEventListener(
        "change",
        (_: dom.Event) =>
          systemThemeBus.writer.onNext(if lightMediaQuery.matches then "light" else "dark")
      )
      signal
    else
      // Test/Node.js environment - default to light theme
      systemThemeBus.events.startWith("light")

  /** Effective theme: user's preference or system */
  val effectiveTheme: Signal[String] =
    userThemePreference.signal.combineWith(systemTheme).map {
      case (Some(user), _) => user
      case (None, sys)     => sys
    }

  /** Theme-aware overlay preview stroke color */
  val overlayPreviewStrokeColor: Signal[String] =
    effectiveTheme.map {
      case "light" => "#222222" // dark gray for light mode
      case _       => "#ffffff" // white for dark mode
    }
