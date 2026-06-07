package art.tessell.editor.models

import art.tessell.editor.i18n.Locale
import art.tessell.editor.utils.{LocaleStorage, RecentFilesStorage}
import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.geometry.{AngleDegree, RegularPolygon}
import io.github.scala_tessella.dcel.structure.FaceId
import spire.compat.numeric // gives `.sum` for Iterable[Rational] via Numeric typeclass
import scala.util.Try

/** EditorState: the single entry point for every application Var.
  *
  * Each logical state group is a `Var[<SomeState>]` whose case class lives in `EditorData.scala`. Derived
  * signals (cross-aggregate or single-field distinct views) live inside the `DerivedState` object below.
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
  val settingsState: Var[SettingsState]         = Var(SettingsState.initial)

  /** Most-recent-first list of recently-opened files. Initialized from localStorage at startup; mutated
    * through [[art.tessell.editor.operations.RecentFilesOperations]] which keeps storage in sync.
    */
  val recentFilesState: Var[List[RecentFile]] = Var(RecentFilesStorage.load())

  /** Currently-selected UI locale. Initialized from localStorage at startup; the top-bar language toggle
    * writes back through [[art.tessell.editor.utils.LocaleStorage]] to keep the preference across reloads.
    */
  val localeState: Var[Locale] = Var(LocaleStorage.load())

  /** Mirrors the OS-level `prefers-reduced-motion` media query. Updated by `Editor.scala` on mount (initial
    * value + a `change` listener) so anything driving off it stays in sync as the user changes their system
    * setting at runtime.
    */
  val osPrefersReducedMotion: Var[Boolean] = Var(false)

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

    /** True when AddPolygon is in its Inside sub-mode (formerly `Tool.Inserter`). */
    val isAddInsideActive: Signal[Boolean] =
      toolState.signal.map(_.isAddInside).distinct

    /** True when the current view scale is at or above the level-of-detail threshold. Renderers gate
      * secondary overlays (node labels, uniformity dots, symmetry axes) on this signal so they disappear when
      * the user zooms far out and re-appear when they zoom back in.
      */
    val isAboveLodThreshold: Signal[Boolean] =
      viewState.signal.map(_.viewTransform.scale).distinct.map(_ >= EditorConfig.lodMinScale).distinct

    /** Number of vertices in the current tiling. Used by the Tiling-info panel. */
    val vertexCountSignal: Signal[Int] =
      tessellationState.signal.map(_.currentTiling.vertices.size).distinct

    /** Number of inner faces (polygons) in the current tiling. */
    val faceCountSignal: Signal[Int] =
      tessellationState.signal.map(_.currentTiling.innerFaces.size).distinct

    /** Number of edges in the current tiling. Each undirected edge is represented by two half-edges, so we
      * divide by two. The `outerFace`'s outer half-edge cycle is included in the count.
      */
    val edgeCountSignal: Signal[Int] =
      tessellationState.signal.map(_.currentTiling.halfEdges.size / 2).distinct

    /** Distinct full-vertex configurations in the current tiling, sorted by descending count. Each entry is
      * `(configString, count)` where `configString` is the canonical "n.n.n…" form (e.g. `3.6.3.6`).
      *
      * Filters applied:
      *   - Only inner vertices whose inner-angle sum is exactly 360° (boundary / partial vertices excluded).
      *   - Configurations that contain a 180° term are excluded (the "vertex" sits on a straight internal
      *     edge — not a real vertex for classification purposes).
      *   - If a vertex's incident faces include any irregular polygon (an interior angle that doesn't map to
      *     a regular n-gon), that vertex is also skipped.
      */
    val fullVertexConfigurationsSignal: Signal[List[(String, Int)]] =
      tessellationState.signal.map(_.currentTiling).distinct.map(computeFullVertexConfigurations)

    /** Selected face for insertion, derived from highlighted polygon id */
    val selectedFaceForInsertion: Signal[Option[FaceId]] =
      measurementState.signal.map(_.highlightedPolygonId).distinct

  export DerivedState.*

  /** Helper for `fullVertexConfigurationsSignal`. Pure: takes a tiling, returns sorted (config, count) pairs.
    */
  private def computeFullVertexConfigurations(tiling: TilingDCEL): List[(String, Int)] =
    if tiling.isEmpty then List.empty
    else
      val r360                  = AngleDegree(360).toRational
      val r180                  = AngleDegree(180)
      val configs: List[String] = tiling.vertices.flatMap: vertex =>
        tiling.getInnerAnglesAtVertex(vertex.id).toOption.flatMap: angles =>

          val sumExact = angles.map(_.toRational).sum
          val has180   = angles.exists(_ == r180)
          if sumExact != r360 || has180 then None
          else
            // Map each angle to its regular-polygon side count. Throws if any angle isn't of a regular
            // polygon — irregular polygons don't have a clean classical configuration string, so we just
            // skip those vertices.
            Try(angles.map(a => RegularPolygon.fromInteriorAngle(a).toSides).toVector).toOption
              .map(canonicalConfig)
      configs.groupBy(identity).toList
        .map((cfg, occurrences) => (cfg, occurrences.size))
        .sortBy { case (_, count) =>
          -count
        }

  /** Canonical "3.6.3.6"-style config string. Picks the lexicographically smallest cyclic rotation so
    * equivalent configurations group together (`3.6.3.6` and `6.3.6.3` collapse into one entry).
    */
  private def canonicalConfig(sides: Vector[Int]): String =
    val rotations = (0 until sides.size).map(i => (sides.drop(i) ++ sides.take(i)).toList)
    rotations.minBy(_.toString).mkString(".")
