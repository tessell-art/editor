package art.tessell.editor

import art.tessell.editor.models.{
  AnimationState, AppStateSnapshot, ColorState, EditorState, ErrorState, FileState, IrregularState,
  MeasurementState, PopupState, PreviewState, TessellationState, ToolState, UIState, ViewState
}
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
    EditorState.irregularState.set(IrregularState.empty)
    EditorState.fileState.set(FileState.initial)
    EditorState.themeState.update(_.copy(userThemePreference = None))
    EditorState.toolState.set(ToolState.initial)

  override def beforeEach(context: BeforeEach): Unit =
    // Snapshot the state that represents the "app model"
    saved = Some(AppStateSnapshot.fromCurrentState)

    resetTransientState()

  override def afterEach(context: AfterEach): Unit =
    // Restore the structural snapshot atomically — one `.set`/`.update` per aggregate.
    saved.foreach { s =>

      EditorState.tessellationState.set(s.tessellation)
      EditorState.toolState.set(s.tools)
      EditorState.irregularState.set(s.irregular)
      EditorState.colorState.update(_.copy(polygonColors = s.polygonColors, fillColor = s.fillColor))
    }

    // And reset the ephemeral / transient state again (to avoid leaks even if no snapshot)
    resetTransientState()

    saved = None
