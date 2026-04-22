package io.github.scala_tessella.editor.models

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.dcel.structure.FaceId
import io.github.scala_tessella.editor.utils.geo.Point
import io.github.scala_tessella.editor.utils.{ColorRGB, SettingsStorage}
import io.github.scala_tessella.editor.models.EditorConfig

import org.scalajs.dom

/** EditorState object contains all the state variables for the editor. The state is organized into logical
  * groups for better maintainability.
  */
object EditorState:
  object FileState:
    /** Current file name, None if no file is open */
    val currentFileName: Var[Option[String]] = Var(None)

  /** Tool-state aggregate — single source of truth for editor mode, active tool, and palette selection. See
    * `ToolState` in EditorData.scala. Per ADR-002 (spike), reads go through
    * `toolState.signal.map(_.field).distinct`; writes through `toolState.update(_.copy(field = …))` (or
    * replace the whole value for atomic restores).
    */
  val toolState: Var[ToolState] = Var(ToolState.initial)

  /** View-state aggregate — canvas view transform, node-label toggle, and symmetry-overlay flags with their
    * caches. See `ViewState` in EditorData.scala. Per ADR-002, reads go through
    * `viewState.signal.map(_.field).distinct`; writes through `viewState.update(_.copy(field = …))`.
    */
  val viewState: Var[ViewState] = Var(ViewState.initial)

  object ThemeState:
    /** Theme preference: None means follow the system, Some(Theme.Light/Dark) is user override */
    val userThemePreference: Var[Option[Theme]] = Var(None)

    /** System theme, updated from EditorApp on mount (default is light). */
    val systemTheme: Var[Theme] = Var(Theme.Light)

    /** Effective theme: user's preference or system */
    val effectiveTheme: Signal[Theme] =
      userThemePreference.signal.combineWith(systemTheme.signal).map:
        case (Some(user), _) => user
        case (None, sys)     => sys

    /** Theme-aware overlay preview stroke color */
    val overlayPreviewStrokeColor: Signal[String] =
      effectiveTheme.map:
        case Theme.Light => "#222222" // dark gray for light mode
        case Theme.Dark  => "#ffffff" // white for dark mode

  /** Tessellation-state aggregate — single source of truth for the current tiling and selection sets. See
    * `TessellationState` in EditorData.scala. Per ADR-002, reads go through
    * `tessellationState.signal.map(_.field).distinct`; writes through
    * `tessellationState.update(_.copy(field = …))` (or replace the whole value for atomic restores).
    */
  val tessellationState: Var[TessellationState] = Var(TessellationState.initial)

  object ColorState:
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

    /** Temporary settings picker color (used by Settings popup UI) */
    val tempSettingsPickerColor: Var[ColorRGB] = Var(defaultStartFillColor.now())

    /** Map of polygon tags to their colors */
    val polygonColors: Var[Map[FaceId, ColorRGB]] = Var(Map.empty)

    /** Whether the color picker is visible */
    val showColorPicker: Var[Boolean] = Var(false)

    /** Temporary color being edited in the color picker */
    val tempColor: Var[ColorRGB] = Var(fillColor.now())

  object UIState:
    /** Whether the editor is currently processing an operation */
    val isProcessing: Var[Boolean] = Var(false)

    /** Optional loading message to show while processing */
    val loadingMessage: Var[Option[String]] = Var(None)

    /** Whether the user is currently dragging */
    val isDragging: Var[Boolean] = Var(false)

    /** Starting point of a drag operation */
    val dragStart: Var[Option[Point]] = Var(None)

    /** Reference to the canvas DOM element */
    val canvasElementRef: Var[Option[dom.Element]] = Var(None)

    /** Whether the mobile menu is open */
    val isMenuOpen: Var[Boolean] = Var(false)

  /** Popup-state aggregate — 6 modal-popup visibility flags. See `PopupState` in EditorData.scala. Per
    * ADR-002, reads via `popupState.signal.map(_.field).distinct`; writes via
    * `popupState.update(_.copy(field = …))`.
    */
  val popupState: Var[PopupState] = Var(PopupState.initial)

  object ErrorState:
    /** Current error message, if any */
    val errorMessage: Var[Option[String]] = Var(None)

    /** Details of a failed polygon placement, if any */
    val failedPlacement: Var[Option[FailedPolygonPlacement]] = Var(None)

    /** Details of a failed polygon deletion, if any */
    val failedDeletion: Var[Option[FailedPolygonDeletion]] = Var(None)

  object PreviewState:
    /** Hover preview of a polygon placement along a perimeter edge, if any */
    val previewPlacement: Var[Option[FailedPolygonPlacement]] = Var(None)

  object AnimationState:
    /** Fan animation overlay, if any */
    val fanAnimation: Var[Option[FanAnimation]] = Var(None)

    /** Doubling animation overlay, if any */
    val doublingAnimation: Var[Option[DoublingAnimation]] = Var(None)

    /** Mirror animation overlay, if any */
    val mirrorAnimation: Var[Option[MirrorAnimation]] = Var(None)

  /** Measurement-state aggregate — ruler/eraser/inserter tool state. See `MeasurementState` in
    * EditorData.scala. Per ADR-002, reads go through `measurementState.signal.map(_.field).distinct`; writes
    * through `measurementState.update(_.copy(field = …))`.
    */
  val measurementState: Var[MeasurementState] = Var(MeasurementState.initial)

  object IrregularState:
    val initialShape: Vector[AngleDegree] = Vector(60, 120, 60, 120).map(AngleDegree(_))

    /** Latest irregular polygon shape chosen (always shown in the slot) */
    val recentIrregularPolygon: Var[Option[Vector[AngleDegree]]] = Var(Some(initialShape))

    /** Whether the irregular polygon is currently selected in the palette */
    val isIrregularSelected: Var[Boolean] = Var(false)

    /** Currently selected irregular polygon (derived from the two states above) */
    val selectedIrregularPolygon: Signal[Option[Vector[AngleDegree]]] =
      isIrregularSelected.signal
        .combineWith(recentIrregularPolygon.signal)
        .map: (isSel, recent) =>
          if isSel then recent else None

  object DerivedState:
    /** True when there is no active tiling. */
    val isTilingEmptySignal: Signal[Boolean] =
      tessellationState.signal.map(_.currentTiling.isEmpty).distinct

    /** True when a file name is set for the current document. */
    val hasFileNameSignal: Signal[Boolean] =
      FileState.currentFileName.signal.map(_.isDefined)

    /** True when at least one perimeter edge or polygon is selected. */
    val hasSelectionSignal: Signal[Boolean] =
      tessellationState.signal
        .map(t => t.selectedTilingPolygons.nonEmpty || t.selectedPerimeterEdges.nonEmpty)
        .distinct

    /** True when current tiling is non-empty. */
    val hasTilingSignal: Signal[Boolean] =
      isTilingEmptySignal.map(!_)

    /** True when save to current file is possible. */
    val canSaveCurrentFileSignal: Signal[Boolean] =
      hasFileNameSignal.combineWith(isTilingEmptySignal).map(_ && !_)

    /** True when no operation is currently running. */
    val isIdleSignal: Signal[Boolean] =
      UIState.isProcessing.signal.map(!_)

    /** True when tiling-dependent mutating actions should be enabled. */
    val canMutateTilingSignal: Signal[Boolean] =
      hasTilingSignal.combineWith(isIdleSignal).map(_ && _)

    /** True when save-to-current-file action should be enabled. */
    val canSaveCurrentFileWhenIdleSignal: Signal[Boolean] =
      canSaveCurrentFileSignal.combineWith(isIdleSignal).map(_ && _)

    /** True when deselection can run (there is a selection and editor is idle). */
    val canDeselectAllSignal: Signal[Boolean] =
      hasSelectionSignal.combineWith(isIdleSignal).map(_ && _)

    /** Checks if Inserter tool is active */
    val isInserterActive: Signal[Boolean] =
      toolState.signal.map(_.activeTool.contains(Tool.Inserter)).distinct

    /** Selected face for insertion, derived from highlighted polygon id */
    val selectedFaceForInsertion: Signal[Option[FaceId]] =
      measurementState.signal.map(_.highlightedPolygonId).distinct

  export FileState.*
  export ThemeState.*
  export ColorState.*
  export UIState.*
  export ErrorState.*
  export PreviewState.*
  export AnimationState.*
  export IrregularState.*
  export DerivedState.*
