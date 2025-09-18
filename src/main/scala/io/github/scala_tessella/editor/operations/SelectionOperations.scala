package io.github.scala_tessella.editor.operations

import OperationGuard.ifNotProcessing
import io.github.scala_tessella.editor.models.{Anchor, ClickablePoint, EditorMode, EditorState, Tool}
import io.github.scala_tessella.editor.utils.TessellationGeometry.toPoint
import io.github.scala_tessella.dcel.FaceId
import io.github.scala_tessella.ring_seq.RingSeq.slidingO
import io.github.scala_tessella.tessella.Geometry.{LineSegment, Point}

object SelectionOperations:

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
      if !tiling.isEmpty then
        val allPolygonIds = tiling.innerFaces.map(_.id).toSet
        EditorState.selectedTilingPolygons.set(allPolygonIds)
        EditorState.selectedPerimeterEdges.set(Set.empty)

  def selectPolygonsBySides(sides: Int): Unit =
    ifNotProcessing:
      val tiling = EditorState.currentTiling.now()
      if !tiling.isEmpty then
        val polygonIdsToAdd = tiling.innerFaces.collect {
          // @todo two traversals, one for the number of sides, one for the angles
          case face if face.halfEdges.toOption.get.size == sides && face.hasEqualAngles => face.id
        }.toSet
        EditorState.selectedTilingPolygons.set(polygonIdsToAdd)

  def selectPolygonsByColor(faceId: FaceId): Unit =
    ifNotProcessing:
      EditorState.polygonColors.now().get(faceId).foreach { color =>
        val polygonIdsToAdd = EditorState.polygonColors.now().collect {
          case (tag, c) if c == color => tag
        }.toSet
        EditorState.selectedTilingPolygons.set(polygonIdsToAdd)
      }
      EditorState.activeTool.set(None) // Deactivate after picking

  def toggleTilingPolygonSelection(faceId: FaceId): Unit =
    ifNotProcessing:
      EditorState.selectedTilingPolygons.update { selected =>
        if selected.contains(faceId) then selected - faceId
        else selected + faceId
      }

  def togglePerimeterEdgeSelection(edgeId: String): Unit =
    ifNotProcessing:
      EditorState.selectedPerimeterEdges.update { selected =>
        if selected.contains(edgeId) then selected - edgeId
        else selected + edgeId
      }

  def handleTilingPolygonClick(faceId: FaceId): Unit =
    EditorState.activeTool.now() match
      case Some(Tool.ColorPicker) =>
        EditorState.polygonColors.now().get(faceId).foreach { color =>
          EditorState.fillColor.set(color)
          EditorState.activeTool.set(None) // Deactivate after picking
        }
      case Some(Tool.ShapeAndColorPicker) =>
        val tiling = EditorState.currentTiling.now()
        EditorState.polygonColors.now().get(faceId).foreach { color =>
          EditorState.fillColor.set(color)
          val face = tiling.findInnerFace(faceId).toOption.get
          if face.hasEqualAngles then
            val sides = face.halfEdges.toOption.get.size
            EditorState.selectedPolygon.set(Some(sides))
          else
            EditorState.selectedPolygon.set(None)
          EditorState.activeTool.set(None) // Deactivate after picking
        }
      case Some(Tool.SelectByColor) =>
        selectPolygonsByColor(faceId)
      case Some(Tool.Measurement) =>
        setupFaceClickablePoints(faceId)
      case Some(Tool.Eraser) =>
        setupFaceClickablePoints(faceId)
      case Some(Tool.Inserter) =>
        setupFaceClickablePoints(faceId, edgesOnly = true)
      case _ =>
        EditorState.editorMode.now() match
          case EditorMode.Select =>
            toggleTilingPolygonSelection(faceId)
          case EditorMode.Delete =>
            TessellationOperations.attemptFaceDeletion(faceId)

  private def setupFaceClickablePoints(faceId: FaceId, edgesOnly: Boolean = false): Unit =
    EditorState.currentTiling.now() match
      case tiling if !tiling.isEmpty =>

        tiling.innerFaces.find { face =>
          val tag = face.id
          tag == faceId
        } match
          case Some(face) =>
            EditorState.highlightedPolygonId.set(Some(faceId))

            if edgesOnly then
              EditorState.clickablePoints.set(Nil)
            else
              val coords = tiling.coordinates
              val vs = face.getVertices.toOption.get
              val vertices = vs.map(_.coords).map(_.toPoint)
              val vertexIdsAndPoints = vs.map(_.id).zip(vertices)
              val edges = vertexIdsAndPoints.toVector.slidingO(2).toList
              //            val edges = polygonNodes.zip(polygonNodes.tail :+ polygonNodes.head)
              val midPoints = edges.map { edge =>
                val p1 = edge(0)._2
                val p2 = edge(1)._2
                ClickablePoint(LineSegment(p1, p2).midPoint, Anchor.MidPoint(edge(0)._1, edge(1)._1))
              }

              val vertexPoints = vertexIdsAndPoints.map { case (vertexId, point) =>
                ClickablePoint(point, Anchor.Vertex(vertexId))
              }
              val centerX = vertices.map(_.x).sum / vertices.size
              val centerY = vertices.map(_.y).sum / vertices.size
              val centerPoint = ClickablePoint(Point(centerX, centerY), Anchor.Center(face.id))

              EditorState.clickablePoints.set(centerPoint :: vertexPoints ++ midPoints)

          case None => ()
      case _ => ()

  def handlePerimeterEdgeClick(edgeId: String, edgeIndex: Int): Unit =
    ifNotProcessing:
      (EditorState.currentTiling.now(), EditorState.selectedPolygon.now()) match
        case (tiling, Some(_)) if !tiling.isEmpty => TessellationOperations.attemptPolygonAddition(edgeId, edgeIndex)
        case (_, None)                            => togglePerimeterEdgeSelection(edgeId)
        case (_, _)                               => ErrorOperations.showError("No tiling available to grow")
