package io.github.scala_tessella.editor.models

import io.github.scala_tessella.editor.models.EditorState.*
import io.github.scala_tessella.editor.operations.*
import io.github.scala_tessella.editor.utils.UndoManager

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.tessella.IncrementalTiling
import io.github.scala_tessella.tessella.IncrementalTiling.Strictness
import io.github.scala_tessella.tessella.Topology.{Edge, Node => TilingNode}

// Case class to represent a failed polygon placement
case class FailedPolygonPlacement(edgeIndex: Int, polygonSides: Int, edge: Edge, tiling: IncrementalTiling)

// Case class to represent a failed polygon deletion
case class FailedPolygonDeletion(polygonId: String, polygonNodes: Vector[TilingNode])

// Editor mode enumeration
enum EditorMode:
  case Select, Delete

object AppState:
  // Expose state for components to read
  export EditorState.*

  // Expose undo/redo capabilities for UI components
  val canUndo: Signal[Boolean] = UndoManager.canUndo.signal
  val canRedo: Signal[Boolean] = UndoManager.canRedo.signal

  // Simple UI operations
  def toggleEditorMode(): Unit =
    if !isProcessing.now() then
      editorMode.update {
        case EditorMode.Select => EditorMode.Delete
        case EditorMode.Delete => EditorMode.Select
      }

  def toggleStrictness(): Unit =
    if !isProcessing.now() then
      strictness.update {
        case Strictness.STRICT   => Strictness.TOUCHING
        case Strictness.TOUCHING => Strictness.CROSSING
        case Strictness.CROSSING => Strictness.STRICT
      }

  def toggleNodeLabels(): Unit =
    if !isProcessing.now() then
      showNodeLabels.update(!_)

  def isTilingEmpty: Boolean = currentTiling.now().isEmpty

  // Delegate to operation objects
  def selectPolygon(sides: Int): Unit =
    TessellationOperations.selectPolygon(sides)

  def clearTiling(): Unit =
    TessellationOperations.clearTiling()
    EditorState.currentFileName.set(None)

  def handleTilingPolygonClick(polygonId: String): Unit =
    SelectionOperations.handleTilingPolygonClick(polygonId)

  def handlePerimeterEdgeClick(edgeId: String, edgeIndex: Int): Unit =
    SelectionOperations.handlePerimeterEdgeClick(edgeId, edgeIndex)

  def applyColorToSelectedPolygons(color: (Int, Int, Int)): Unit =
    ColorOperations.applyColorToSelectedPolygons(color)

  def getOrAssignPolygonColor(polyTag: String): (Int, Int, Int) =
    ColorOperations.getOrAssignPolygonColor(polyTag)

  def showError(message: String, placement: Option[FailedPolygonPlacement] = None, deletion: Option[FailedPolygonDeletion] = None): Unit =
    ErrorOperations.showError(message, placement, deletion)

  def clearError(): Unit =
    ErrorOperations.clearError()

  def deleteSelectedElements(): Unit =
    if !isProcessing.now() then
      if selectedTilingPolygons.now().nonEmpty then
        ErrorOperations.showError("Tessellation polygon deletion not supported yet")

  def selectPolygonsBySides(sides: Int): Unit =
    if !isProcessing.now() && !isTilingEmpty then
      UndoManager.saveState()
      SelectionOperations.selectPolygonsBySides(sides)

  def selectAll(): Unit =
    if !isProcessing.now() && !isTilingEmpty then
      UndoManager.saveState()
      SelectionOperations.selectAllPolygons()

  def deselectAll(): Unit =
    if !isProcessing.now() && (selectedTilingPolygons.now().nonEmpty || selectedPerimeterEdges.now().nonEmpty) then
      UndoManager.saveState()
      SelectionOperations.clearAllSelections()

  def undo(): Unit =
    if !isProcessing.now() then
      UndoManager.undo()

  def redo(): Unit =
    if !isProcessing.now() then
      UndoManager.redo()

  def fitTilingToCanvas(): Unit =
    val tiling = EditorState.currentTiling.now()
    if tiling.isEmpty then return

    val coords = tiling.coordinates.values.map(p => (p.x * 50, p.y * 50))
    if coords.isEmpty then return

    EditorState.canvasElementRef.now().foreach { canvasElement =>
      val canvasRect = canvasElement.getBoundingClientRect()
      val canvasWidth = canvasRect.width
      val canvasHeight = canvasRect.height
      val currentTransform = EditorState.viewTransform.now()

      if canvasWidth > 0 && canvasHeight > 0 then
        val rad = currentTransform.rotationDegrees * Math.PI / 180
        val cosRad = Math.cos(rad)
        val sinRad = Math.sin(rad)

        val rotatedCoords = coords.map { case (x, y) =>
          (x * cosRad - y * sinRad, x * sinRad + y * cosRad)
        }

        val minX = rotatedCoords.map(_._1).min
        val maxX = rotatedCoords.map(_._1).max
        val minY = rotatedCoords.map(_._2).min
        val maxY = rotatedCoords.map(_._2).max

        val tilingWidth = maxX - minX
        val tilingHeight = maxY - minY

        if tilingWidth > 0 || tilingHeight > 0 then
          val padding = 40.0

          val safeTilingWidth = if tilingWidth <= 0 then 1 else tilingWidth
          val safeTilingHeight = if tilingHeight <= 0 then 1 else tilingHeight

          val scaleX = (canvasWidth - padding * 2) / safeTilingWidth
          val scaleY = (canvasHeight - padding * 2) / safeTilingHeight
          val newScale = Math.min(scaleX, scaleY)

          val tilingCenterX = (minX + maxX) / 2.0
          val tilingCenterY = (minY + maxY) / 2.0

          val tilingCenterOnCanvasX = tilingCenterX + 400
          val tilingCenterOnCanvasY = tilingCenterY + 300

          val newPanX = canvasWidth / 2.0 - tilingCenterOnCanvasX * newScale
          val newPanY = canvasHeight / 2.0 - tilingCenterOnCanvasY * newScale

          EditorState.viewTransform.set(
            currentTransform.copy(
              scale = newScale,
              panX = newPanX,
              panY = newPanY
            )
          )
    }
