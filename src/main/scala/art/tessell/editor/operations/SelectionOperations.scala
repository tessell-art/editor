package art.tessell.editor.operations

import io.github.scala_tessella.dcel.geometry.{AngleDegree, RegularPolygon}
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.structure.FaceId
import OperationGuard.ifNotProcessing
import TessellationOperations.toCoords
import art.tessell.editor.models.{
  AddSubmode, Anchor, ClickablePoint, EditorConfig, EditorMode, EditorState, Tool, ToolState
}
import art.tessell.editor.utils.Logger
import art.tessell.editor.utils.geo.{LineSegment, Point, Radian}
import art.tessell.editor.utils.geo.TessellationGeometry.toPoint
import io.github.scala_tessella.ring_seq.RingSeq.{isRotationOrReflectionOf, slidingO}

object SelectionOperations:

  private case class PerimeterClickContext(
      tiling: TilingDCEL,
      selectedPolygon: Option[Int],
      isIrregularSelected: Boolean
  )

  private def withNonEmptyTiling(op: TilingDCEL => Unit): Unit =
    val tiling = EditorState.tessellationState.now().currentTiling
    if !tiling.isEmpty then op(tiling)

  private def deactivateActiveTool(): Unit =
    EditorState.toolState.update(_.copy(activeTool = Tool.AddPolygon, addSubmode = AddSubmode.Outside))

  def handlePointClickForMeasurement(point: ClickablePoint): Unit =
    ifNotProcessing:
      val ms       = EditorState.measurementState.now()
      val startOpt = ms.measurementStartPoint
      val endOpt   = ms.measurementEndPoint

      (startOpt, endOpt) match
        case (None, _)                          =>
          // No start point, so set this as the start point.
          EditorState.measurementState.update(
            _.copy(measurementStartPoint = Some(point), measurementResult = None)
          )
        case (Some(start), _) if start == point =>
          // Clicked on the start point, clear both start and end points.
          EditorState.measurementState.update(
            _.copy(
              measurementStartPoint = None,
              measurementEndPoint = None,
              measurementPreviousEndPoint = None,
              measurementResult = None
            )
          )
        case (_, Some(end)) if end == point     =>
          // Clicked on the end point, clear only the end point.
          EditorState.measurementState.update(
            _.copy(
              measurementEndPoint = None,
              measurementPreviousEndPoint = None,
              measurementResult = None
            )
          )
        case (Some(start), previousEndOpt)      =>
          // Start point is set, so set this as the end point.
          val distance   = point.point.distanceTo(start.point)
          val maybeAngle =
            previousEndOpt.map: clickable =>

              val angle1 = LineSegment(start.point, clickable.point).horizontalAngle
              val angle2 = LineSegment(start.point, point.point).horizontalAngle
              val diff   = (angle2 - angle1).normalize.toDouble
              // Use the smaller conjugate angle in [0, PI].
              Math.min(diff, Radian.TAU.toDouble - diff)
          EditorState.measurementState.update(
            _.copy(
              measurementPreviousEndPoint = previousEndOpt.map(_.point),
              measurementEndPoint = Some(point),
              measurementResult = Some(distance),
              measurementAngle = maybeAngle.map(Radian(_))
            )
          )

  def handlePointClickForFan(point: ClickablePoint): Unit =
    ifNotProcessing:
      EditorState.measurementState.update(_.copy(clickablePoints = Nil))
      point.anchor match
        case Anchor.Vertex(vertexId) =>
          Logger.debug(s"Fan vertex clicked: $vertexId")
          TransformOperations.attemptFanning(vertexId)
        case _                       => ()

  def handlePointClickForDeletion(point: ClickablePoint): Unit =
    ifNotProcessing:
      EditorState.measurementState.update(_.copy(clickablePoints = Nil))
      point.anchor match
        case Anchor.Vertex(vertexId)                     =>
          DeletionOperations.attemptVertexDeletion(vertexId)
        case Anchor.Center(faceId)                       =>
          DeletionOperations.attemptFaceDeletion(faceId)
        case Anchor.MidPoint(startVertexId, endVertexId) =>
          DeletionOperations.attemptEdgeDeletion(startVertexId, endVertexId)

  def handlePointClickForInsertion(point: ClickablePoint): Unit =
    ifNotProcessing:
      EditorState.measurementState.update(_.copy(clickablePoints = Nil))
      point.anchor match
        case Anchor.MidPoint(startVertexId, endVertexId) =>
          PlacementOperations.attemptPolygonInsertion(startVertexId, endVertexId)
        case _                                           => ()

  def clearAllSelections(): Unit =
    ifNotProcessing:
      EditorState.tessellationState.update(_.copy(selectedTilingPolygons = Set.empty))
      EditorState.tessellationState.update(_.copy(selectedPerimeterEdges = Set.empty))

  def selectAllPolygons(): Unit =
    ifNotProcessing:
      withNonEmptyTiling: tiling =>

        val allPolygonIds = tiling.innerFaces.map(_.id).toSet
        EditorState.tessellationState.update(_.copy(selectedTilingPolygons = allPolygonIds))
        EditorState.tessellationState.update(_.copy(selectedPerimeterEdges = Set.empty))

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
        EditorState.tessellationState.update(_.copy(selectedTilingPolygons = polygonIdsToAdd))

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
        EditorState.tessellationState.update(_.copy(selectedTilingPolygons = polygonIdsToAdd))

  def selectPolygonsByColor(faceId: FaceId): Unit =
    ifNotProcessing:
      val colors = EditorState.colorState.now().polygonColors
      colors.get(faceId).foreach: color =>

        UndoManager.pushSelection()
        val polygonIdsToAdd = colors.collect {
          case (tag, c) if c == color => tag
        }.toSet
        EditorState.tessellationState.update(_.copy(selectedTilingPolygons = polygonIdsToAdd))
      deactivateActiveTool()

  def toggleTilingPolygonSelection(faceId: FaceId): Unit =
    ifNotProcessing:
      UndoManager.pushSelection()
      EditorState.tessellationState.update: s =>

        val next =
          if s.selectedTilingPolygons.contains(faceId) then s.selectedTilingPolygons - faceId
          else s.selectedTilingPolygons + faceId
        s.copy(selectedTilingPolygons = next)

  def togglePerimeterEdgeSelection(edgeId: String): Unit =
    ifNotProcessing:
      UndoManager.pushSelection()
      EditorState.tessellationState.update: s =>

        val next =
          if s.selectedPerimeterEdges.contains(edgeId) then s.selectedPerimeterEdges - edgeId
          else s.selectedPerimeterEdges + edgeId
        s.copy(selectedPerimeterEdges = next)

  def handleTilingPolygonClick(faceId: FaceId): Unit =
    val tools = EditorState.toolState.now()
    tools.activeTool match
      case Tool.ColorPicker         =>
        val colors = EditorState.colorState.now().polygonColors
        colors.get(faceId).foreach: color =>

          EditorState.colorState.update(_.copy(fillColor = color))
          deactivateActiveTool()
      case Tool.ShapeAndColorPicker =>
        val tiling     = EditorState.tessellationState.now().currentTiling
        val maybeFace  = tiling.findInnerFace(faceId).toOption
        val maybeColor = EditorState.colorState.now().polygonColors.get(faceId)
        (for color <- maybeColor; face <- maybeFace yield (color, face)) match
          case Some((color, face)) =>
            EditorState.colorState.update(_.copy(fillColor = color))
            if face.hasEqualAngles.toOption.contains(true) then
              face.halfEdges.toOption.foreach: edges =>

                val sides = edges.size
                EditorState.irregularState.update(_.deselected)
                EditorState.toolState.update(_.copy(selectedPolygon = Some(sides)))
                // Non-core regulars also enter the MRU (so they're reachable later without
                // retyping in the custom-n input). selectIt=false leaves the active selection
                // on selectedPolygon; the MRU just remembers this shape.
                if !EditorConfig.polygonSides.contains(sides) then
                  EditorState.irregularState.update(
                    _.withShape(RegularPolygon(sides).angles, selectIt = false)
                  )
            else
              face.angles.toOption.foreach: angles =>

                // remember latest irregular + select it
                EditorState.irregularState.update(_.withShape(angles.toVector, selectIt = true))
                EditorState.toolState.update(_.copy(selectedPolygon = None))
            deactivateActiveTool()
          case None                =>
            ()
      case Tool.SelectByColor       =>
        selectPolygonsByColor(faceId)
      case Tool.Measurement         =>
        () // Measurement uses proximity-based selection via ProximityQuery (ADR-013 amendment)
      case Tool.Fan    =>
        setupFaceClickablePoints(faceId, boundaryVerticesOnly = true)
      case Tool.Eraser =>
        () // Eraser uses proximity-based selection via ProximityQuery (ADR-013)
      case Tool.TranslateCopy =>
        () // Add Copy ▸ Translate is a canvas-level drag gesture, not a polygon click
      case Tool.RotateCopy =>
        () // Add Copy ▸ Rotate is a canvas-level drag gesture, not a polygon click
      case Tool.ReflectCopy =>
        () // Add Copy ▸ Reflect is a canvas-level drag gesture, not a polygon click
      case Tool.GlideReflectCopy =>
        () // Add Copy ▸ Glide reflect is a canvas-level drag gesture, not a polygon click
      case Tool.AddPolygon =>
        tools.addSubmode match
          case AddSubmode.Inside  =>
            setupFaceClickablePoints(faceId, edgesOnly = true)
          case AddSubmode.Outside =>
            tools.editorMode match
              case EditorMode.Select =>
                toggleTilingPolygonSelection(faceId)
              case EditorMode.Delete =>
                DeletionOperations.attemptFaceDeletion(faceId)

  private def setupFaceClickablePoints(
      faceId: FaceId,
      edgesOnly: Boolean = false,
      boundaryVerticesOnly: Boolean = false
  ): Unit =
    withNonEmptyTiling: tiling =>
      tiling.findInnerFace(faceId).toOption match
        case Some(face) =>
          EditorState.measurementState.update(_.copy(highlightedPolygonId = Some(faceId)))

          if edgesOnly then
            EditorState.measurementState.update(_.copy(clickablePoints = Nil))
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
                EditorState.measurementState.update(_.copy(clickablePoints = vertexBoundaryPoints))
              else
                EditorState.measurementState.update(_.copy(clickablePoints =
                  centerPoint :: vertexPoints ++ midPoints
                ))

        case None => ()

  def handlePerimeterEdgeClick(edgeId: String, edgeIndex: Int): Unit =
    ifNotProcessing:
      val tools = EditorState.toolState.now()
      // Perimeter-edge interactions only make sense for AddPolygon + Outside (placing a polygon
      // on the boundary). Every other tool — Eraser, Measurement, Fan, the two color pickers,
      // SelectByColor, even AddPolygon + Inside — targets existing faces or measurement points
      // and must not trigger placement, even though the palette still carries a `selectedPolygon`
      // from before the tool switch. Without this guard, clicking a perimeter edge in those modes
      // silently grows the tiling.
      tools.activeTool match
        case Tool.AddPolygon if tools.addSubmode == AddSubmode.Outside =>
          val context = PerimeterClickContext(
            tiling = EditorState.tessellationState.now().currentTiling,
            selectedPolygon = tools.selectedPolygon,
            isIrregularSelected = EditorState.irregularState.now().isSelected
          )
          context match
            case PerimeterClickContext(_, None, false)                  =>
              togglePerimeterEdgeSelection(edgeId)
            case PerimeterClickContext(tiling, _, _) if !tiling.isEmpty =>
              if isPerimeterEdgeAttachable(tiling, edgeIndex, tools) then
                PlacementOperations.attemptPolygonAddition(edgeId, edgeIndex)
            // If pre-flight rejects, do nothing: the user already sees the red preview + dim
            // chevron, so a silent no-op matches the "disabled" affordance — no toast needed.
            case _                                                      =>
              ErrorOperations.showError("No tiling available to grow")
        case _                                                         =>
          ()

  /** Pre-flight angle-at-endpoints check: returns false only when placement is guaranteed-invalid by
    * `PlacementValidation.fitsAtEdge`. Other failure modes (the polygon's far edges crossing distant boundary
    * segments) still need the full DCEL check downstream — those become `attemptPolygonAddition`'s
    * `FailedPolygonPlacement` toast.
    */
  private def isPerimeterEdgeAttachable(
      tiling: TilingDCEL,
      edgeIndex: Int,
      tools: ToolState
  ): Boolean =
    val perimeter = tiling.boundaryVertices.toOption.map(_.map(_.toCoords).slidingO(2).toList)
    val edgeOpt   = perimeter.flatMap(edges => edges.lift(edgeIndex)).map(pair => (pair(0), pair(1)))
    val anglesOpt =
      EditorState.irregularState.now().selectedShape
        .orElse(tools.selectedPolygon.filter(_ >= 3).map(s => RegularPolygon(s).angles))
    (edgeOpt, anglesOpt) match
      case (Some(edge), Some(angles)) => PlacementValidation.fitsAtEdge(tiling, edge, angles)
      case _                          => true
