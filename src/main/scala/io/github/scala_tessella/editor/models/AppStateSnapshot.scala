package io.github.scala_tessella.editor.models

import io.github.scala_tessella.tessella.Tiling

// Represents a snapshot of the application state that can be undone
case class AppStateSnapshot(
  tiling: Option[Tiling],
  selectedPolygon: Option[Int],
  selectedPerimeterEdges: Set[String],
  selectedTilingPolygons: Set[String],
  polygonColors: Map[String, (Int, Int, Int)],
  fillColor: (Int, Int, Int),
  editorMode: EditorMode,
  timestamp: Long = System.currentTimeMillis()
)

object AppStateSnapshot:
  // Create a snapshot from current AppState
  def fromCurrentState: AppStateSnapshot =
    AppStateSnapshot(
      tiling = AppState.currentTiling.now(),
      selectedPolygon = AppState.selectedPolygon.now(),
      selectedPerimeterEdges = AppState.selectedPerimeterEdges.now(),
      selectedTilingPolygons = AppState.selectedTilingPolygons.now(),
      polygonColors = AppState.polygonColors.now(),
      fillColor = AppState.fillColor.now(),
      editorMode = AppState.editorMode.now()
    )