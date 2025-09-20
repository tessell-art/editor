package io.github.scala_tessella.editor.models

import io.github.scala_tessella.dcel.BigDecimalGeometry.AngleDegree
import io.github.scala_tessella.dcel.{FaceId, TilingDCEL}

// Represents a snapshot of the application state that can be undone
case class AppStateSnapshot(
   tiling: TilingDCEL,
   selectedPolygon: Option[Int],
   selectedPerimeterEdges: Set[String],
   selectedTilingPolygons: Set[FaceId],
   polygonColors: Map[FaceId, (Int, Int, Int)],
   fillColor: (Int, Int, Int),
   editorMode: EditorMode,
   timestamp: Long = System.currentTimeMillis(),
   recentIrregularPolygon: Option[Vector[AngleDegree]],
   isIrregularSelected: Boolean
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
      editorMode = EditorState.editorMode.now(),
      recentIrregularPolygon = EditorState.recentIrregularPolygon.now(),
      isIrregularSelected = EditorState.isIrregularSelected.now()
    )