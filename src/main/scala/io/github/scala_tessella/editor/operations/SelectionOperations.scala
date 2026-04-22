package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.structure.FaceId
import io.github.scala_tessella.editor.models.{Anchor, ClickablePoint, EditorMode, EditorState, Tool}
import io.github.scala_tessella.editor.operations.OperationGuard.ifNotProcessing
import io.github.scala_tessella.editor.utils.Logger
import io.github.scala_tessella.editor.utils.geo.TessellationGeometry.toPoint
import io.github.scala_tessella.editor.utils.geo.{LineSegment, Point, Radian}
import io.github.scala_tessella.ring_seq.RingSeq.{isRotationOrReflectionOf, slidingO}

object SelectionOperations:

  private case class PerimeterClickContext(
      tiling: TilingDCEL,
      selectedPolygon: Option[Int],
      isIrregularSelected: Boolean
  )

  private def withNonEmptyTiling(op: TilingDCEL => Unit): Unit =
    val tiling = EditorState.currentTiling.now()
    if !tiling.isEmpty then op(tiling)

  private def deactivateActiveTool(): Unit =
    EditorState.toolState.update(_.copy(activeTool = None))

  def handlePointClickForMeasurement(point: ClickablePoint): Unit =
    ifNotProcessing:
      val startOpt = EditorState.measurementStartPoint.now()
      val endOpt   = EditorState.measurementEndPoint.now()

      (startOpt, endOpt) match
        case (None, _)                          =>
          // No start point, so set this as the start point.
          EditorState.measurementStartPoint.set(Some(point))
          EditorState.measurementResult.set(None)
        case (Some(start), _) if start == point =>
          // Clicked on the start point, clear both start and end points.
          EditorState.measurementStartPoint.set(None)
          EditorState.measurementEndPoint.set(None)
          EditorState.measurementPreviousEndPoint.set(None)
          EditorState.measurementResult.set(None)
        case (_, Some(end)) if end == point     =>
          // Clicked on the end point, clear only the end point.
          EditorState.measurementEndPoint.set(None)
          EditorState.measurementPreviousEndPoint.set(None)
          EditorState.measurementResult.set(None)
        case (Some(start), previousEndOpt)      =>
          // Start point is set, so set this as the end point.
          EditorState.measurementPreviousEndPoint.set(previousEndOpt.map(_.point))
          EditorState.measurementEndPoint.set(Some(point))
          val distance   = point.point.distanceTo(start.point)
          EditorState.measurementResult.set(Some(distance))
          val maybeAngle =
            previousEndOpt.map: clickable =>

              val angle1 = LineSegment(start.point, clickable.point).horizontalAngle
              val angle2 = LineSegment(start.point, point.point).horizontalAngle
              val diff   = (angle2 - angle1).normalize.toDouble
              // Use the smaller conjugate angle in [0, PI].
              Math.min(diff, Radian.TAU.toDouble - diff)
          EditorState.measurementAngle.set(maybeAngle.map(Radian(_)))

  def handlePointClickForFan(point: ClickablePoint): Unit =
    ifNotProcessing:
      EditorState.clickablePoints.set(Nil)
      point.anchor match
        case Anchor.Vertex(vertexId) =>
          Logger.debug(s"Fan vertex clicked: $vertexId")
          TessellationOperations.attemptFanning(vertexId)
        case _                       => ()

  def handlePointClickForDeletion(point: ClickablePoint): Unit =
    ifNotProcessing:
      EditorState.clickablePoints.set(Nil)
      point.anchor match
        case Anchor.Vertex(vertexId)                     =>
          TessellationOperations.attemptVertexDeletion(vertexId)
        case Anchor.Center(faceId)                       =>
          TessellationOperations.attemptFaceDeletion(faceId)
        case Anchor.MidPoint(startVertexId, endVertexId) =>
          TessellationOperations.attemptEdgeDeletion(startVertexId, endVertexId)

  def handlePointClickForInsertion(point: ClickablePoint): Unit =
    ifNotProcessing:
      EditorState.clickablePoints.set(Nil)
      point.anchor match
        case Anchor.MidPoint(startVertexId, endVertexId) =>
          TessellationOperations.attemptPolygonInsertion(startVertexId, endVertexId)
        case _                                           => ()

  def clearAllSelections(): Unit =
    ifNotProcessing:
      EditorState.selectedTilingPolygons.set(Set.empty)
      EditorState.selectedPerimeterEdges.set(Set.empty)

  def selectAllPolygons(): Unit =
    ifNotProcessing:
      withNonEmptyTiling: tiling =>

        val allPolygonIds = tiling.innerFaces.map(_.id).toSet
        EditorState.selectedTilingPolygons.set(allPolygonIds)
        EditorState.selectedPerimeterEdges.set(Set.empty)

  def selectPolygonsBySides(sides: Int): Unit =
    ifNotProcessing:
      withNonEmptyTiling: tiling =>

        val polygonIdsToAdd =
          tiling.innerFaces
            .collect:
              // @todo two traversals, one for the number of sides, one for the angles
              case face
                  if face.halfEdges.toOption.exists(_.size == sides) &&
                    face.hasEqualAngles.toOption.contains(true) =>
                face.id
            .toSet
        EditorState.selectedTilingPolygons.set(polygonIdsToAdd)

  def selectPolygonsByShape(angles: Vector[AngleDegree]): Unit =
    ifNotProcessing:
      withNonEmptyTiling: tiling =>

        val polygonIdsToAdd =
          tiling.innerFaces
            .collect:
              // @todo two traversals, one for the number of sides, one for the angles
              case face
                  if face.halfEdges.toOption.exists(_.size == angles.size) &&
                    face.angles.toOption.exists(_.isRotationOrReflectionOf(angles)) =>
                face.id
            .toSet
        EditorState.selectedTilingPolygons.set(polygonIdsToAdd)

  def selectPolygonsByColor(faceId: FaceId): Unit =
    ifNotProcessing:
      val colors = EditorState.polygonColors.now()
      colors.get(faceId).foreach: color =>

        val polygonIdsToAdd = colors.collect {
          case (tag, c) if c == color => tag
        }.toSet
        EditorState.selectedTilingPolygons.set(polygonIdsToAdd)
      deactivateActiveTool()

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
    val tools = EditorState.toolState.now()
    tools.activeTool match
      case Some(Tool.ColorPicker)         =>
        val colors = EditorState.polygonColors.now()
        colors.get(faceId).foreach: color =>

          EditorState.fillColor.set(color)
          deactivateActiveTool()
      case Some(Tool.ShapeAndColorPicker) =>
        val tiling     = EditorState.currentTiling.now()
        val maybeFace  = tiling.findInnerFace(faceId).toOption
        val maybeColor = EditorState.polygonColors.now().get(faceId)
        (for color <- maybeColor; face <- maybeFace yield (color, face)) match
          case Some((color, face)) =>
            EditorState.fillColor.set(color)
            if face.hasEqualAngles.toOption.contains(true) then
              face.halfEdges.toOption.foreach: edges =>

                val sides = edges.size
                EditorState.isIrregularSelected.set(false)
                EditorState.toolState.update(_.copy(selectedPolygon = Some(sides)))
            else
              face.angles.toOption.foreach: angles =>

                EditorState.recentIrregularPolygon.set(Some(angles.toVector)) // remember latest irregular
                EditorState.toolState.update(_.copy(selectedPolygon = None))
                EditorState.isIrregularSelected.set(true) // select irregular
            deactivateActiveTool()
          case None                =>
            ()
      case Some(Tool.SelectByColor)       =>
        selectPolygonsByColor(faceId)
      case Some(Tool.Measurement)         =>
        setupFaceClickablePoints(faceId)
      case Some(Tool.Fan)                 =>
        setupFaceClickablePoints(faceId, boundaryVerticesOnly = true)
      case Some(Tool.Eraser)              =>
        setupFaceClickablePoints(faceId)
      case Some(Tool.Inserter)            =>
        setupFaceClickablePoints(faceId, edgesOnly = true)
      case _                              =>
        tools.editorMode match
          case EditorMode.Select =>
            toggleTilingPolygonSelection(faceId)
          case EditorMode.Delete =>
            TessellationOperations.attemptFaceDeletion(faceId)

  private def setupFaceClickablePoints(
      faceId: FaceId,
      edgesOnly: Boolean = false,
      boundaryVerticesOnly: Boolean = false
  ): Unit =
    withNonEmptyTiling: tiling =>
      tiling.findInnerFace(faceId).toOption match
        case Some(face) =>
          EditorState.highlightedPolygonId.set(Some(faceId))

          if edgesOnly then
            EditorState.clickablePoints.set(Nil)
          else
            tiling.findInnerFaceVertices(face.id).toOption.foreach: faceVertices =>

              val vertices           = faceVertices.map(_.coords).map(_.toPoint)
              val vertexIdsAndPoints = faceVertices.map(_.id).zip(vertices)
              val edges              = vertexIdsAndPoints.toVector.slidingO(2).toList
              val midPoints          =
                edges.map: edge =>

                  val p1 = edge(0)._2
                  val p2 = edge(1)._2
                  ClickablePoint(LineSegment(p1, p2).midPoint, Anchor.MidPoint(edge(0)._1, edge(1)._1))

              val vertexPoints =
                vertexIdsAndPoints.map: (vertexId, point) =>
                  ClickablePoint(point, Anchor.Vertex(vertexId))
              val centerX      = vertices.map(_.x).sum / vertices.size
              val centerY      = vertices.map(_.y).sum / vertices.size
              val centerPoint  = ClickablePoint(Point(centerX, centerY), Anchor.Center(face.id))

              if boundaryVerticesOnly then
                val boundaryVertexIds    = tiling.boundaryVertices.toOption.get.map(_.id).toSet
                val vertexBoundaryPoints =
                  vertexIdsAndPoints
                    .filter: (vertexId, _) =>
                      boundaryVertexIds.contains(vertexId)
                    .map: (vertexId, point) =>
                      ClickablePoint(point, Anchor.Vertex(vertexId))
                EditorState.clickablePoints.set(vertexBoundaryPoints)
              else
                EditorState.clickablePoints.set(centerPoint :: vertexPoints ++ midPoints)

        case None => ()

  def handlePerimeterEdgeClick(edgeId: String, edgeIndex: Int): Unit =
    ifNotProcessing:
      val context = PerimeterClickContext(
        tiling = EditorState.currentTiling.now(),
        selectedPolygon = EditorState.toolState.now().selectedPolygon,
        isIrregularSelected = EditorState.isIrregularSelected.now()
      )
      context match
        case PerimeterClickContext(_, None, false)                  => togglePerimeterEdgeSelection(edgeId)
        case PerimeterClickContext(tiling, _, _) if !tiling.isEmpty =>
          TessellationOperations.attemptPolygonAddition(edgeId, edgeIndex)
        case _                                                      =>
          ErrorOperations.showError("No tiling available to grow")
