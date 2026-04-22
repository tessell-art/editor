package io.github.scala_tessella.editor.models

import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.dcel.structure.FaceId
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.editor.utils.ColorRGB

// Represents a snapshot of the application state that can be undone
case class AppStateSnapshot(
    tiling: TilingDCEL,
    selectedPolygon: Option[Int],
    selectedPerimeterEdges: Set[String],
    selectedTilingPolygons: Set[FaceId],
    polygonColors: Map[FaceId, ColorRGB],
    fillColor: ColorRGB,
    editorMode: EditorMode,
    activeTool: Option[Tool],
    timestamp: Long = System.currentTimeMillis(),
    recentIrregularPolygon: Option[Vector[AngleDegree]],
    isIrregularSelected: Boolean
)

object AppStateSnapshot:
  // Create a snapshot from the current AppState
  def fromCurrentState: AppStateSnapshot =
    val tools = EditorState.toolState.now()
    AppStateSnapshot(
      tiling = EditorState.tessellationState.now().currentTiling,
      selectedPolygon = tools.selectedPolygon,
      selectedPerimeterEdges = EditorState.tessellationState.now().selectedPerimeterEdges,
      selectedTilingPolygons = EditorState.tessellationState.now().selectedTilingPolygons,
      polygonColors = EditorState.colorState.now().polygonColors,
      fillColor = EditorState.colorState.now().fillColor,
      editorMode = tools.editorMode,
      activeTool = tools.activeTool,
      recentIrregularPolygon = EditorState.recentIrregularPolygon.now(),
      isIrregularSelected = EditorState.isIrregularSelected.now()
    )
