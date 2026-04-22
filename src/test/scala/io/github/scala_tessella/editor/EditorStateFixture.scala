package io.github.scala_tessella.editor

import io.github.scala_tessella.editor.models.*
import munit.FunSuite

// Mixin for test suites to get robust state isolation.
// Usage: class MySpec extends FunSuite with EditorStateFixture
trait EditorStateFixture:
  self: FunSuite =>

  private var saved: Option[AppStateSnapshot] = None

  private def resetTransientState(): Unit =
    // Each aggregate reset is a single atomic update (one signal emission).
    EditorState.uiState.set(UIState.initial)
    EditorState.popupState.set(PopupState.initial)
    EditorState.measurementState.set(MeasurementState.initial)
    EditorState.tessellationState.set(TessellationState.initial)
    EditorState.colorState.set(ColorState.initial)
    EditorState.viewState.set(ViewState.initial)
    EditorState.errorState.set(ErrorState.initial)
    EditorState.previewState.set(PreviewState.initial)
    EditorState.animationState.set(AnimationState.initial)
    EditorState.irregularState.set(IrregularState.initial)
    EditorState.fileState.set(FileState.initial)
    EditorState.themeState.update(_.copy(userThemePreference = None))
    EditorState.toolState.update(_.copy(selectedPolygon = None, activeTool = None))

  override def beforeEach(context: BeforeEach): Unit =
    // Snapshot the state that represents the "app model"
    saved = Some(AppStateSnapshot.fromCurrentState)

    resetTransientState()

  override def afterEach(context: AfterEach): Unit =
    // Restore the structural snapshot
    saved.foreach { s =>

      EditorState.tessellationState.set(
        TessellationState(
          currentTiling = s.tiling,
          selectedPerimeterEdges = s.selectedPerimeterEdges,
          selectedTilingPolygons = s.selectedTilingPolygons
        )
      )
      EditorState.colorState.update(_.copy(polygonColors = s.polygonColors, fillColor = s.fillColor))
      EditorState.toolState.set(
        ToolState(
          editorMode = s.editorMode,
          activeTool = s.activeTool,
          selectedPolygon = s.selectedPolygon
        )
      )
      EditorState.irregularState.set(
        IrregularState(
          recentIrregularPolygon = s.recentIrregularPolygon,
          isIrregularSelected = s.isIrregularSelected
        )
      )
    }

    // And reset the ephemeral / transient state again (to avoid leaks even if no snapshot)
    resetTransientState()

    saved = None
