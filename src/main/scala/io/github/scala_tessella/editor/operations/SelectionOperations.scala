package io.github.scala_tessella.editor.operations

import OperationGuard.ifNotProcessing
import io.github.scala_tessella.editor.models.{Anchor, ClickablePoint, EditorMode, EditorState, Tool}

import io.github.scala_tessella.dcel.BigDecimalGeometry.BigPoint
import io.github.scala_tessella.ring_seq.RingSeq.slidingO
import io.github.scala_tessella.tessella.Geometry.{LineSegment, Point}
import io.github.scala_tessella.tessella.Topology.{NodeOrdering, Node as TilingNode}

object SelectionOperations:

  private def polygonId(nodes: Seq[TilingNode]): String =
    val polyTag = nodes.sorted(using NodeOrdering).map(_.toString).mkString("-")
    s"tiling-poly-$polyTag"

  // Extract polygon tag from polygon ID
  private def extractPolyTag(polygonId: String): String =
    if polygonId.startsWith("tiling-poly-") then polygonId.substring("tiling-poly-".length) else polygonId

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
        EditorState.measurementPreviousEndPoint.set(None)
        EditorState.measurementResult.set(None)
      else if endOpt.contains(point) then
        // Clicked on the end point, clear only the end point
        EditorState.measurementEndPoint.set(None)
        EditorState.measurementPreviousEndPoint.set(None)
        EditorState.measurementResult.set(None)
      else
        // Start point is set, so set this as the end point
        val start = startOpt.get // Safe due to the first check
        // If there was an endpoint before this click, save its position.
        EditorState.measurementPreviousEndPoint.set(endOpt.map(_.point))
        EditorState.measurementEndPoint.set(Some(point))
        val distance = point.point.distanceTo(start.point)
        EditorState.measurementResult.set(Some(distance))
        val maybeAngle: Option[Double] = endOpt.map(clickable =>
          val angle1 = LineSegment(start.point, clickable.point).horizontalAngle
          val angle2 = LineSegment(start.point, point.point).horizontalAngle
          val diffRad: Double = (angle2 - angle1).toDouble
          // Normalize the difference to be within the [0, 2*PI) range
          val TAU = 2 * Math.PI
          val normalizedAngle = (diffRad % TAU + TAU) % TAU
          // The angle is the smaller of the two conjugate angles, which is in [0, PI]
          Math.min(normalizedAngle, TAU - normalizedAngle)
        )
        EditorState.measurementAngle.set(maybeAngle)

  def handlePointClickForDeletion(point: ClickablePoint): Unit =
    ifNotProcessing:
      EditorState.clickablePoints.set(Nil)
      point.anchor match
        case Anchor.Vertex(vertexId) =>
          TessellationOperations.attemptVertexDeletion(vertexId)
        case Anchor.Center(faceId) =>
          TessellationOperations.attemptFaceDeletion(faceId)
        case Anchor.MidPoint(startVertexId, endVertexId) =>
          TessellationOperations.attemptEdgeDeletion(startVertexId, endVertexId)

  def handlePointClickForInsertion(point: ClickablePoint): Unit =
    ifNotProcessing:
      EditorState.clickablePoints.set(Nil)
      point.anchor match
        case Anchor.MidPoint(startVertexId, endVertexId) =>
          TessellationOperations.attemptPolygonInsertion(startVertexId, endVertexId)
        case _ => ()

  def clearAllSelections(): Unit =
    ifNotProcessing:
      EditorState.selectedTilingPolygons.set(Set.empty)
      EditorState.selectedPerimeterEdges.set(Set.empty)

  def selectAllPolygons(): Unit =
    ifNotProcessing:
      val tiling = EditorState.currentTiling.now()
//      if !tiling.isEmpty then
//        val allPolygonIds = tiling.orientedPolygons.map(polygonId).toSet
//        EditorState.selectedTilingPolygons.set(allPolygonIds)
//        EditorState.selectedPerimeterEdges.set(Set.empty)

  def selectPolygonsBySides(sides: Int): Unit =
    ifNotProcessing:
      val tiling = EditorState.currentTiling.now()
//      if !tiling.isEmpty then
//        val polygonIdsToAdd = tiling.orientedPolygons.collect {
//          case nodes if nodes.length == sides => polygonId(nodes)
//        }.toSet
//        EditorState.selectedTilingPolygons.set(polygonIdsToAdd)

  def selectPolygonsByColor(polygonId: String): Unit =
    ifNotProcessing:
      val polyTag = extractPolyTag(polygonId)
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
        val polyTag = extractPolyTag(polygonId)
        EditorState.polygonColors.now().get(polyTag).foreach { color =>
          EditorState.fillColor.set(color)
          EditorState.activeTool.set(None) // Deactivate after picking
        }
      case Some(Tool.ShapeAndColorPicker) =>
        val polyTag = extractPolyTag(polygonId)
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
      case Some(Tool.Eraser) =>
        handlePolygonClickForMeasurement(polygonId)
      case Some(Tool.Inserter) =>
        handlePolygonClickForMeasurement(polygonId, edgesOnly = true)
      case _ =>
        EditorState.editorMode.now() match
          case EditorMode.Select =>
            toggleTilingPolygonSelection(polygonId)
          case EditorMode.Delete =>
            TessellationOperations.attemptPolygonDeletion(polygonId)

  extension (bigPoint: BigPoint)

    def toPoint: Point =
      Point(bigPoint.x.toDouble, bigPoint.y.toDouble)

  private def handlePolygonClickForMeasurement(polygonId: String, edgesOnly: Boolean = false): Unit =
    EditorState.currentTiling.now() match
      case tiling if !tiling.isEmpty =>
        val polyTag = extractPolyTag(polygonId)

        tiling.innerFaces.find { face =>
          val tag = face.id.value
          tag == polyTag
        } match
          case Some(face) =>
            EditorState.highlightedPolygonId.set(Some(polygonId))

            val coords = tiling.coordinates
            val vs = face.getVertices.toOption.get
            val vertices = vs.map(_.coords).map(_.toPoint)
            val vertexIdsAndPoints = vs.map(_.id).zip(vertices)
            val vertexPoints = vertexIdsAndPoints.map { case (vertexId, point) =>
              ClickablePoint(point, Anchor.Vertex(vertexId))
            }

            val centerX = vertices.map(_.x).sum / vertices.size
            val centerY = vertices.map(_.y).sum / vertices.size
            val centerPoint = ClickablePoint(Point(centerX, centerY), Anchor.Center(face.id))

            val edges = vertexIdsAndPoints.toVector.slidingO(2).toList
//            val edges = polygonNodes.zip(polygonNodes.tail :+ polygonNodes.head)
            val midPoints = edges.map { edge =>
              val p1 = edge(0)._2
              val p2 = edge(1)._2
              ClickablePoint(LineSegment(p1, p2).midPoint, Anchor.MidPoint(edge(0)._1, edge(1)._1))
            }

            EditorState.clickablePoints.set(
              if edgesOnly then midPoints
              else centerPoint :: vertexPoints ++ midPoints
            )

          case None => ()
      case _ => ()

  def handlePerimeterEdgeClick(edgeId: String, edgeIndex: Int): Unit =
    ifNotProcessing:
      (EditorState.currentTiling.now(), EditorState.selectedPolygon.now()) match
        case (tiling, Some(_)) if !tiling.isEmpty => TessellationOperations.attemptPolygonAddition(edgeId, edgeIndex)
        case (_, None)                            => togglePerimeterEdgeSelection(edgeId)
        case (_, _)                               => ErrorOperations.showError("No tiling available to grow")
