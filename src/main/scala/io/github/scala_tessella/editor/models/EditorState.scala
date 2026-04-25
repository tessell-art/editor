package io.github.scala_tessella.editor.models

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.dcel.structure.FaceId

/** EditorState: the single entry point for every application Var.
  *
  * Per ADR-002, each logical state group is a `Var[<SomeState>]` whose case class lives in
  * `EditorData.scala`. Derived signals (cross-aggregate or single-field distinct views) live inside the
  * `DerivedState` object below.
  *
  * Pattern:
  *   - read a field: `EditorState.fooState.signal.map(_.bar).distinct`
  *   - read a field snapshot: `EditorState.fooState.now().bar`
  *   - write a field: `EditorState.fooState.update(_.copy(bar = …))`
  *   - atomic reset: `EditorState.fooState.set(FooState.initial)`
  */
object EditorState:

  val toolState: Var[ToolState]                 = Var(ToolState.initial)
  val viewState: Var[ViewState]                 = Var(ViewState.initial)
  val tessellationState: Var[TessellationState] = Var(TessellationState.initial)
  val colorState: Var[ColorState]               = Var(ColorState.initial)
  val uiState: Var[UIState]                     = Var(UIState.initial)
  val popupState: Var[PopupState]               = Var(PopupState.initial)
  val measurementState: Var[MeasurementState]   = Var(MeasurementState.initial)
  val fileState: Var[FileState]                 = Var(FileState.initial)
  val previewState: Var[PreviewState]           = Var(PreviewState.initial)
  val themeState: Var[ThemeState]               = Var(ThemeState.initial)
  val errorState: Var[ErrorState]               = Var(ErrorState.initial)
  val animationState: Var[AnimationState]       = Var(AnimationState.initial)
  val irregularState: Var[IrregularState]       = Var(IrregularState.initial)

  /** Effective theme: user's preference or the detected system theme. */
  val effectiveTheme: Signal[Theme] =
    themeState.signal.map(s => s.userThemePreference.getOrElse(s.systemTheme)).distinct

  /** Theme-aware overlay preview stroke color. */
  val overlayPreviewStrokeColor: Signal[String] =
    effectiveTheme.map:
      case Theme.Light => "#222222" // dark gray for light mode
      case Theme.Dark  => "#ffffff" // white for dark mode

  /** Currently selected irregular polygon (shape + "is selected?" combined). */
  val selectedIrregularPolygon: Signal[Option[Vector[AngleDegree]]] =
    irregularState.signal.map(_.selectedShape).distinct

  object DerivedState:
    /** True when there is no active tiling. */
    val isTilingEmptySignal: Signal[Boolean] =
      tessellationState.signal.map(_.currentTiling.isEmpty).distinct

    /** True when a file name is set for the current document. */
    val hasFileNameSignal: Signal[Boolean] =
      fileState.signal.map(_.currentFileName.isDefined).distinct

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
      uiState.signal.map(!_.isProcessing).distinct

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

  export DerivedState.*
