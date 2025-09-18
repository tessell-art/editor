package io.github.scala_tessella.editor

import munit.{AfterEach, BeforeEach, FunSuite}
import io.github.scala_tessella.editor.models._
import io.github.scala_tessella.dcel.TilingDCEL

// Mixin for test suites to get robust state isolation.
// Usage: class MySpec extends FunSuite with EditorStateFixture
trait EditorStateFixture:
  self: FunSuite =>

    private var saved: Option[AppStateSnapshot] = None

    override def beforeEach(context: BeforeEach): Unit =
      // Snapshot the state that represents the "app model"
      saved = Some(AppStateSnapshot.fromCurrentState)

      // Ensure a clean baseline for each test (transient UI/processing flags)
      EditorState.isProcessing.set(false)
      EditorState.isDragging.set(false)
      EditorState.dragStart.set(None)

      // Reset popups
      EditorState.showAboutPopup.set(false)
      EditorState.showGuidePopup.set(false)
      EditorState.showShortcutsPopup.set(false)

      // Clear errors and failed ops
      EditorState.errorMessage.set(None)
      EditorState.failedPlacement.set(None)
      EditorState.failedDeletion.set(None)

      // Clear preview and measurement-related state
      EditorState.previewPlacement.set(None)
      EditorState.clickablePoints.set(Nil)
      EditorState.measurementStartPoint.set(None)
      EditorState.measurementEndPoint.set(None)
      EditorState.measurementPreviousEndPoint.set(None)
      EditorState.highlightedPolygonId.set(None)
      EditorState.measurementResult.set(None)
      EditorState.measurementAngle.set(None)

      // Reset selection & tiling to a known base
      EditorState.currentTiling.set(TilingDCEL.empty)
      EditorState.selectedPolygon.set(None)
      EditorState.selectedPerimeterEdges.set(Set.empty)
      EditorState.selectedTilingPolygons.set(Set.empty)
      EditorState.polygonColors.set(Map.empty)

      // Reset view and toggles
      EditorState.viewTransform.set(ViewTransform())
      EditorState.showNodeLabels.set(false)
      EditorState.showDual.set(false)

    override def afterEach(context: AfterEach): Unit =
      // Restore the structural snapshot
      saved.foreach { s =>
        EditorState.currentTiling.set(s.tiling)
        EditorState.selectedPolygon.set(s.selectedPolygon)
        EditorState.selectedPerimeterEdges.set(s.selectedPerimeterEdges)
        EditorState.selectedTilingPolygons.set(s.selectedTilingPolygons)
        EditorState.polygonColors.set(s.polygonColors)
        EditorState.fillColor.set(s.fillColor)
        EditorState.editorMode.set(s.editorMode)
      }

      // And reset the ephemeral / transient state again (to avoid leaks even if no snapshot)
      EditorState.isProcessing.set(false)
      EditorState.isDragging.set(false)
      EditorState.dragStart.set(None)

      EditorState.errorMessage.set(None)
      EditorState.failedPlacement.set(None)
      EditorState.failedDeletion.set(None)

      EditorState.previewPlacement.set(None)
      EditorState.clickablePoints.set(Nil)
      EditorState.measurementStartPoint.set(None)
      EditorState.measurementEndPoint.set(None)
      EditorState.measurementPreviousEndPoint.set(None)
      EditorState.highlightedPolygonId.set(None)
      EditorState.measurementResult.set(None)
      EditorState.measurementAngle.set(None)

      EditorState.showNodeLabels.set(false)
      EditorState.showDual.set(false)

      saved = None
