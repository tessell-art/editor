package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.models.{EditorMode, EditorState}

import io.github.scala_tessella.tessella.Topology.NodeOrdering

object SelectionOperations:

  def clearAllSelections(): Unit =
    if !EditorState.isProcessing.now() then
      EditorState.selectedTilingPolygons.set(Set.empty)
      EditorState.selectedPerimeterEdges.set(Set.empty)

  def selectAllPolygons(): Unit =
    if !EditorState.isProcessing.now() then
      val tiling = EditorState.currentTiling.now()
      if !tiling.isEmpty then
        val allPolygonIds = tiling.orientedPolygons.map { nodes =>
          val polyTag = nodes.sorted(NodeOrdering).map(_.toString).mkString("-")
          s"tiling-poly-$polyTag"
        }.toSet
        EditorState.selectedTilingPolygons.set(allPolygonIds)
        EditorState.selectedPerimeterEdges.set(Set.empty)

  def selectPolygonsBySides(sides: Int): Unit =
    if !EditorState.isProcessing.now() then
      val tiling = EditorState.currentTiling.now()
      if !tiling.isEmpty then
        val polygonIdsToAdd = tiling.orientedPolygons.collect {
          case nodes if nodes.length == sides =>
            val polyTag = nodes.sorted(NodeOrdering).map(_.toString).mkString("-")
            s"tiling-poly-$polyTag"
        }.toSet
        EditorState.selectedTilingPolygons.set(polygonIdsToAdd)
//        EditorState.selectedTilingPolygons.update(_ ++ polygonIdsToAdd)

  def selectPolygonsByColor(polygonId: String): Unit =
    if !EditorState.isProcessing.now() then
      val polyTag = if polygonId.startsWith("tiling-poly-") then polygonId.substring("tiling-poly-".length) else polygonId
      EditorState.polygonColors.now().get(polyTag).foreach { color =>
        val polygonIdsToAdd = EditorState.polygonColors.now().collect {
          case (tag, c) if c == color => s"tiling-poly-$tag"
        }.toSet
        EditorState.selectedTilingPolygons.set(polygonIdsToAdd)
      }
      EditorState.isColorSelectorActive.set(false) // Deactivate after picking

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
      if (EditorState.isEyedropperActive.now()) {
        val polyTag = if polygonId.startsWith("tiling-poly-") then polygonId.substring("tiling-poly-".length) else polygonId
        EditorState.polygonColors.now().get(polyTag).foreach { color =>
          EditorState.fillColor.set(color)
          EditorState.isEyedropperActive.set(false) // Deactivate after picking
        }
      } else if (EditorState.isColorSelectorActive.now()) {
        selectPolygonsByColor(polygonId)
      } else{
        EditorState.editorMode.now() match
          case EditorMode.Select => toggleTilingPolygonSelection(polygonId)
          case EditorMode.Delete => TessellationOperations.attemptPolygonDeletion(polygonId)
      }

  def handlePerimeterEdgeClick(edgeId: String, edgeIndex: Int): Unit =
    if !EditorState.isProcessing.now() then
      (EditorState.currentTiling.now(), EditorState.selectedPolygon.now()) match
        case (tiling, Some(_)) if !tiling.isEmpty => TessellationOperations.attemptPolygonAddition(edgeId, edgeIndex)
        case (_, None)                            => togglePerimeterEdgeSelection(edgeId)
        case (_, _)                               => ErrorOperations.showError("No tiling available to grow")