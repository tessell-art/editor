package io.github.scala_tessella.editor.models

import io.github.scala_tessella.editor.models.EditorState.*
import io.github.scala_tessella.editor.operations.*
import io.github.scala_tessella.editor.utils.UndoManager

import io.github.scala_tessella.tessella.Tiling
import io.github.scala_tessella.tessella.Topology.{Edge, Node => TilingNode}

// Case class to represent a failed polygon placement
case class FailedPolygonPlacement(edgeIndex: Int, polygonSides: Int, edge: Edge, tiling: Tiling)

// Case class to represent a failed polygon deletion
case class FailedPolygonDeletion(polygonId: String, polygonNodes: Vector[TilingNode], tiling: Tiling)

// Editor mode enumeration
enum EditorMode:
  case Select, Delete

object AppState:
  // Expose state for components to read
  export EditorState.*

  // Simple UI operations
  def toggleEditorMode(): Unit =
    if !isProcessing.now() then
      editorMode.update {
        case EditorMode.Select => EditorMode.Delete
        case EditorMode.Delete => EditorMode.Select
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

  def undo(): Unit =
    if !isProcessing.now() then
      UndoManager.undo()