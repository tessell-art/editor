package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.models.{EditorState, EditorMode}

object SelectionOperations:

  def clearAllSelections(): Unit =
    if !EditorState.isProcessing.now() then
      EditorState.selectedTilingPolygons.set(Set.empty)
      EditorState.selectedPerimeterEdges.set(Set.empty)

  def toggleTilingPolygonSelection(polygonId: String): Unit =
    if !EditorState.isProcessing.now() then
      EditorState.selectedTilingPolygons.update { selected =>
        if selected.contains(polygonId) then selected - polygonId
        else selected + polygonId
      }

  def togglePerimeterEdgeSelection(edgeId: String): Unit =
    if !EditorState.isProcessing.now() then
      EditorState.selectedPerimeterEdges.update { selected =>
        if selected.contains(edgeId) then selected - edgeId
        else selected + edgeId
      }

  def handleTilingPolygonClick(polygonId: String): Unit =
    if !EditorState.isProcessing.now() then
      EditorState.editorMode.now() match
        case EditorMode.Select => toggleTilingPolygonSelection(polygonId)
        case EditorMode.Delete => TessellationOperations.attemptPolygonDeletion(polygonId)

  def handlePerimeterEdgeClick(edgeId: String, edgeIndex: Int): Unit =
    if !EditorState.isProcessing.now() then
      (EditorState.currentTiling.now(), EditorState.selectedPolygon.now()) match
        case (tiling, Some(_)) if !tiling.isEmpty => TessellationOperations.attemptPolygonGrowth(edgeId, edgeIndex)
        case (_, None)                            => togglePerimeterEdgeSelection(edgeId)
        case (_, _)                               => ErrorOperations.showError("No tiling available to grow")
