package io.github.scala_tessella.editor.models

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.tessella.Geometry.Point
import io.github.scala_tessella.tessella.IncrementalTiling.Strictness
import org.scalajs.dom
import io.github.scala_tessella.dcel.FaceId

/**
 * EditorState object contains all the state variables for the editor.
 * The state is organized into logical groups for better maintainability.
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

  /** Whether the dual tessellation should be shown */
  val showDual: Var[Boolean] = Var(false)

  /** Theme preference: None means follow system, Some("light") or Some("dark") is user override */
  val userThemePreference: Var[Option[String]] = Var(None)

  //
  // TESSELLATION STATE
  //

  /** Current tiling being edited */
  val currentTiling: Var[TilingDCEL] = Var(TilingDCEL.empty)

  /** Set of selected perimeter edge IDs */
  val selectedPerimeterEdges: Var[Set[String]] = Var(Set.empty)

  /** Set of selected tiling polygon IDs */
  val selectedTilingPolygons: Var[Set[FaceId]] = Var(Set.empty)

  //
  // COLOR MANAGEMENT
  //

  /** Current fill color (RGB tuple) */
  val fillColor: Var[(Int, Int, Int)] = Var((76, 175, 80))

  /** Map of polygon tags to their colors */
  val polygonColors: Var[Map[FaceId, (Int, Int, Int)]] = Var(Map.empty)

  /** Whether the color picker is visible */
  val showColorPicker: Var[Boolean] = Var(false)

  /** Temporary color being edited in the color picker */
  val tempColor: Var[(Int, Int, Int)] = Var(fillColor.now())

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
  val measurementAngle: Var[Option[Double]] = Var(None)

  // -------------------------
  // Derived Signals (no Vars)
  // -------------------------

  /** Is Inserter tool active */
  val isInserterActive: Signal[Boolean] =
    activeTool.signal.map(_.contains(Tool.Inserter))

  /** Selected face for insertion, derived from highlighted polygon id */
  val selectedFaceForInsertion: Signal[Option[FaceId]] =
    highlightedPolygonId.signal

  /** System theme as a Signal (uses media query, no Var) */
  private val lightMediaQuery = dom.window.matchMedia("(prefers-color-scheme: light)")
  private val systemThemeBus = new EventBus[String]
  val systemTheme: Signal[String] =
    systemThemeBus.events.startWith(if (lightMediaQuery.matches) "light" else "dark")
  lightMediaQuery.addEventListener("change", (_: dom.Event) =>
    systemThemeBus.writer.onNext(if (lightMediaQuery.matches) "light" else "dark")
  )

  /** Effective theme: user's preference or system */
  val effectiveTheme: Signal[String] =
    userThemePreference.signal.combineWith(systemTheme).map {
      case (Some(user), _) => user
      case (None, sys)     => sys
    }

  /** Theme-aware overlay preview stroke color */
  val overlayPreviewStrokeColor: Signal[String] =
    effectiveTheme.map {
      case "light" => "#222222" // dark grey for light mode
      case _       => "#ffffff" // white for dark mode
    }