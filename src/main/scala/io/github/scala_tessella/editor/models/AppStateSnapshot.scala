package io.github.scala_tessella.editor.models

import io.github.scala_tessella.dcel.TilingDCEL

// Represents a snapshot of the application state that can be undone
case class AppStateSnapshot(
  tiling: TilingDCEL,
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
      tiling = EditorState.currentTiling.now(),
      selectedPolygon = EditorState.selectedPolygon.now(),
      selectedPerimeterEdges = EditorState.selectedPerimeterEdges.now(),
      selectedTilingPolygons = EditorState.selectedTilingPolygons.now(),
      polygonColors = EditorState.polygonColors.now(),
      fillColor = EditorState.fillColor.now(),
      editorMode = EditorState.editorMode.now()
    )