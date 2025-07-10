package io.github.scala_tessella.editor.operations

import OperationGuard.ifNotProcessing
import io.github.scala_tessella.editor.models.{Anchor, ClickablePoint, EditorMode, EditorState, Tool}

import io.github.scala_tessella.tessella.Geometry.{LineSegment, Point}
import io.github.scala_tessella.tessella.Topology.{Node as TilingNode, NodeOrdering}

object SelectionOperations:

  private def polygonId(nodes: Seq[TilingNode]): String =
    val polyTag = nodes.sorted(using NodeOrdering).map(_.toString).mkString("-")
    s"tiling-poly-$polyTag"

  def handlePointClickForMeasurement(point: ClickablePoint): Unit =
    ifNotProcessing:
      val startOpt = EditorState.measurementStartPoint.now()
      val endOpt = EditorState.measurementEndPoint.now()

      if startOpt.isEmpty then
        // No start point, so set this as the start point
        EditorState.measurementStartPoint.set(Some(point))
        EditorState.measurementResult.set(None)
      else if startOpt.contains(point) then
        // Clicked on the start point, clear both start and end points
        EditorState.measurementStartPoint.set(None)
        EditorState.measurementEndPoint.set(None)
        EditorState.measurementResult.set(None)
      else if endOpt.contains(point) then
        // Clicked on the end point, clear only the end point
        EditorState.measurementEndPoint.set(None)
        EditorState.measurementResult.set(None)
      else
        // Start point is set, so set this as the end point
        val start = startOpt.get // Safe due to the first check
        EditorState.measurementEndPoint.set(Some(point))
        val distance = point.point.distanceTo(start.point)
        EditorState.measurementResult.set(Some(distance))

  def clearAllSelections(): Unit =
    ifNotProcessing:
      EditorState.selectedTilingPolygons.set(Set.empty)
      EditorState.selectedPerimeterEdges.set(Set.empty)

  def selectAllPolygons(): Unit =
    ifNotProcessing:
      val tiling = EditorState.currentTiling.now()
      if !tiling.isEmpty then
        val allPolygonIds = tiling.orientedPolygons.map(polygonId).toSet
        EditorState.selectedTilingPolygons.set(allPolygonIds)
        EditorState.selectedPerimeterEdges.set(Set.empty)

  def selectPolygonsBySides(sides: Int): Unit =
    ifNotProcessing:
      val tiling = EditorState.currentTiling.now()
      if !tiling.isEmpty then
        val polygonIdsToAdd = tiling.orientedPolygons.collect {
          case nodes if nodes.length == sides => polygonId(nodes)
        }.toSet
        EditorState.selectedTilingPolygons.set(polygonIdsToAdd)
//        EditorState.selectedTilingPolygons.update(_ ++ polygonIdsToAdd)

  def selectPolygonsByColor(polygonId: String): Unit =
    ifNotProcessing:
      val polyTag = if polygonId.startsWith("tiling-poly-") then polygonId.substring("tiling-poly-".length) else polygonId
      EditorState.polygonColors.now().get(polyTag).foreach { color =>
        val polygonIdsToAdd = EditorState.polygonColors.now().collect {
          case (tag, c) if c == color => s"tiling-poly-$tag"
        }.toSet
        EditorState.selectedTilingPolygons.set(polygonIdsToAdd)
      }
      EditorState.activeTool.set(None) // Deactivate after picking

  def toggleTilingPolygonSelection(polygonId: String): Unit =
    ifNotProcessing:
      EditorState.selectedTilingPolygons.update { selected =>
        if selected.contains(polygonId) then selected - polygonId
        else selected + polygonId
      }

  def togglePerimeterEdgeSelection(edgeId: String): Unit =
    ifNotProcessing:
      EditorState.selectedPerimeterEdges.update { selected =>
        if selected.contains(edgeId) then selected - edgeId
        else selected + edgeId
      }

  def handleTilingPolygonClick(polygonId: String): Unit =
    EditorState.activeTool.now() match
      case Some(Tool.ColorPicker) =>
        val polyTag = if polygonId.startsWith("tiling-poly-") then polygonId.substring("tiling-poly-".length) else polygonId
        EditorState.polygonColors.now().get(polyTag).foreach { color =>
          EditorState.fillColor.set(color)
          EditorState.activeTool.set(None) // Deactivate after picking
        }
      case Some(Tool.ShapeAndColorPicker) =>
        val polyTag = if polygonId.startsWith("tiling-poly-") then polygonId.substring("tiling-poly-".length) else polygonId
        EditorState.polygonColors.now().get(polyTag).foreach { color =>
          EditorState.fillColor.set(color)
          val sides = polyTag.count(_ == '-') + 1
          EditorState.selectedPolygon.set(Some(sides))
          EditorState.activeTool.set(None) // Deactivate after picking
        }
      case Some(Tool.SelectByColor) =>
        selectPolygonsByColor(polygonId)
      case Some(Tool.Measurement) =>
        handlePolygonClickForMeasurement(polygonId)
      case _ =>
        EditorState.editorMode.now() match
          case EditorMode.Select =>
            toggleTilingPolygonSelection(polygonId)
          case EditorMode.Delete =>
            TessellationOperations.attemptPolygonDeletion(polygonId)

  private def handlePolygonClickForMeasurement(polygonId: String): Unit =
    EditorState.currentTiling.now() match
      case tiling if !tiling.isEmpty =>
        val polyTag = polygonId.stripPrefix("tiling-poly-")

        tiling.orientedPolygons.find { nodes =>
          val tag = nodes.sorted(using NodeOrdering).map(_.toString).mkString("-")
          tag == polyTag
        } match
          case Some(polygonNodes) =>
            EditorState.highlightedPolygonId.set(Some(polygonId))

            val coords = tiling.coordinates
            val vertices = polygonNodes.map(coords).map(_.toPoint)

            val vertexPoints = polygonNodes.zip(vertices).map { case (node, point) =>
              ClickablePoint(point, Anchor.Vertex(node))
            }

            val centerX = vertices.map(_.x).sum / vertices.size
            val centerY = vertices.map(_.y).sum / vertices.size
            val centerPoint = ClickablePoint(Point(centerX, centerY), Anchor.Center)

            val edges = polygonNodes.zip(polygonNodes.tail :+ polygonNodes.head)
            val midPoints = edges.map { case (node1, node2) =>
              val p1 = coords(node1).toPoint
              val p2 = coords(node2).toPoint
              ClickablePoint(LineSegment(p1, p2).midPoint, Anchor.MidPoint)
            }

            EditorState.clickablePoints.set(centerPoint :: vertexPoints.toList ++ midPoints.toList)

          case None => ()
      case _ => ()

  def handlePerimeterEdgeClick(edgeId: String, edgeIndex: Int): Unit =
    ifNotProcessing:
      (EditorState.currentTiling.now(), EditorState.selectedPolygon.now()) match
        case (tiling, Some(_)) if !tiling.isEmpty => TessellationOperations.attemptPolygonAddition(edgeId, edgeIndex)
        case (_, None)                            => togglePerimeterEdgeSelection(edgeId)
        case (_, _)                               => ErrorOperations.showError("No tiling available to grow")