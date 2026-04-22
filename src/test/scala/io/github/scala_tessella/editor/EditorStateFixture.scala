package io.github.scala_tessella.editor

import io.github.scala_tessella.editor.models.*
import munit.FunSuite

// Mixin for test suites to get robust state isolation.
// Usage: class MySpec extends FunSuite with EditorStateFixture
trait EditorStateFixture:
  self: FunSuite =>

  private var saved: Option[AppStateSnapshot] = None

  private def resetTransientState(): Unit =
    // Ensure a clean baseline for each test (transient UI/processing flags)
    EditorState.isProcessing.set(false)
    EditorState.isDragging.set(false)
    EditorState.dragStart.set(None)

    // Reset popups
    EditorState.popupState.set(PopupState.initial)
    EditorState.showColorPicker.set(false) // ColorState field, not PopupState

    // Clear errors and failed ops
    EditorState.errorMessage.set(None)
    EditorState.failedPlacement.set(None)
    EditorState.failedDeletion.set(None)

    // Clear preview and measurement-related state
    EditorState.previewPlacement.set(None)
    EditorState.fanAnimation.set(None)
    EditorState.doublingAnimation.set(None)
    EditorState.mirrorAnimation.set(None)
    EditorState.measurementState.set(MeasurementState.initial)

    // Reset selection & tiling to a known base
    EditorState.tessellationState.set(TessellationState.initial)
    EditorState.polygonColors.set(Map.empty)
    EditorState.toolState.update(_.copy(selectedPolygon = None, activeTool = None))
    EditorState.isIrregularSelected.set(false)
    EditorState.recentIrregularPolygon.set(Some(EditorState.initialShape))

    // Reset view and toggles
    EditorState.viewState.set(ViewState.initial)
    EditorState.currentFileName.set(None)
    EditorState.canvasElementRef.set(None)
    EditorState.userThemePreference.set(None)
    EditorState.isMenuOpen.set(false)
    EditorState.tempColor.set(EditorState.fillColor.now())
    EditorState.defaultStartFillColor.set(EditorConfig.defaultPolygonColor)
    EditorState.perimeterEdgeColor.set(EditorConfig.defaultPerimeterEdgeColor)
    EditorState.tempDefaultFillColor.set(EditorConfig.defaultPolygonColor)
    EditorState.tempPerimeterEdgeColor.set(EditorConfig.defaultPerimeterEdgeColor)
    EditorState.tempSettingsPickerColor.set(EditorConfig.defaultPolygonColor)
    EditorState.loadingMessage.set(None)

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
      EditorState.polygonColors.set(s.polygonColors)
      EditorState.fillColor.set(s.fillColor)
      EditorState.toolState.set(
        ToolState(
          editorMode = s.editorMode,
          activeTool = s.activeTool,
          selectedPolygon = s.selectedPolygon
        )
      )
      EditorState.recentIrregularPolygon.set(s.recentIrregularPolygon)
      EditorState.isIrregularSelected.set(s.isIrregularSelected)
    }

    // And reset the ephemeral / transient state again (to avoid leaks even if no snapshot)
    resetTransientState()

    saved = None
