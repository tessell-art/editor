package io.github.scala_tessella.editor.operations

import com.raquo.laminar.api.L.Var
import io.github.scala_tessella.dcel.TilingEquivalency.verticallyReflectedCopy
import io.github.scala_tessella.dcel.geometry.RegularPolygon
import io.github.scala_tessella.dcel.structure.{FaceId, Vertex, VertexId}
import io.github.scala_tessella.dcel.{TilingDCEL, ValidationError}
import io.github.scala_tessella.editor.models.EditorState.{currentTiling, polygonColors}
import io.github.scala_tessella.editor.models.{
  AppState,
  DoublingAnimation,
  EditorConfig,
  EditorState,
  FailedPolygonPlacement,
  FanAnimation,
  MirrorAnimation
}
import io.github.scala_tessella.editor.operations.OperationGuard.ifNotProcessing
import io.github.scala_tessella.editor.operations.ColorOperations.ensureColorsForFaces
import io.github.scala_tessella.editor.utils.PolygonNameGenerator.polygonName
import io.github.scala_tessella.editor.utils.geo.TessellationGeometry.{tilingPointToCanvasView, toPoint}
import io.github.scala_tessella.editor.utils.geo.{Point, Radian}
import io.github.scala_tessella.editor.utils.{Logger, UndoManager}
import io.github.scala_tessella.ring_seq.RingSeq.slidingO

import scala.scalajs.js.timers.setTimeout
object TessellationOperations:

  type VertexCoord = (id: VertexId, point: Point)

  private def clearAllAnimations(): Unit =
    EditorState.fanAnimation.set(None)
    EditorState.doublingAnimation.set(None)
    EditorState.mirrorAnimation.set(None)

  private def clearSymmetryOverlaysOnSuccess(): Unit =
    AppState.clearSymmetryOverlays()

  private def clearSymmetryAndPerimeterSelectionOnSuccess(): Unit =
    clearSymmetryOverlaysOnSuccess()
    EditorState.selectedPerimeterEdges.set(Set.empty)

  private def scheduleAnimationCleanup[A <: AnyRef](
      state: Var[Option[A]],
      animation: A,
      durationMs: Int
  )(onDone: => Unit = ()): Unit =
    setTimeout(durationMs) {
      state.update {
        case Some(current) if current eq animation => None
        case other                                 => other
      }
      onDone
    }: Unit

  extension (vertex: Vertex)

    def toCoords: VertexCoord =
      (vertex.id, vertex.coords.toPoint)

  def selectPolygon(sides: Int): Unit =
    ifNotProcessing:
      // Selecting a regular polygon deselects the irregular
      EditorState.isIrregularSelected.set(false)
      EditorState.selectedPolygon.set(Some(sides))

      if currentTiling.now().isEmpty then
        UndoManager.saveState()
        try
          val tiling = TilingDCEL.createRegularPolygon(RegularPolygon(sides))
          currentTiling.set(tiling)
          ensureColorsForFaces(tiling.innerFaces.map(_.id), EditorState.fillColor.now())
          SelectionOperations.clearAllSelections()
        catch
          case e: Throwable =>
            UndoManager.undo()
            ErrorOperations.showError(s"Failed to create tiling from $sides-sided polygon")

  def clearTiling(): Unit =
    ifNotProcessing:
      if !currentTiling.now().isEmpty then
        UndoManager.saveState()

      currentTiling.set(TilingDCEL.empty)
      clearSymmetryAndPerimeterSelectionOnSuccess()
      EditorState.polygonColors.set(Map.empty)
      EditorState.selectedTilingPolygons.set(Set.empty)

  /** Select the irregular polygon in the palette (deselect regular if any). */
  def selectIrregularInPalette(): Unit =
    ifNotProcessing:
      if EditorState.recentIrregularPolygon.now().isDefined then
        EditorState.selectedPolygon.set(None)
        EditorState.isIrregularSelected.set(true)

  /** If the tiling is empty and a recent irregular exists, initialize the tiling with it. */
  def initializeWithIrregularIfEmpty(): Unit =
    ifNotProcessing:
      if currentTiling.now().isEmpty then
        EditorState.recentIrregularPolygon.now() match
          case Some(angles) =>
            UndoManager.saveState()
            TilingDCEL.createSimplePolygon(angles).toOption match
              case Some(tiling) =>
                currentTiling.set(tiling)
                ensureColorsForFaces(tiling.innerFaces.map(_.id), EditorState.fillColor.now())
                SelectionOperations.clearAllSelections()
              case None         =>
                UndoManager.undo()
                ErrorOperations.showError("Failed to create tiling from irregular polygon")
          case None         => ()

  // Attempt to delete a face by FaceId (stable, DCEL-native)
  def attemptFaceDeletion(faceId: FaceId): Unit =
    val op = () => currentTiling.now().maybeDeleteFace(faceId)
    OperationRunner.runTilingOp(op)(
      onSuccess = clearSymmetryOverlaysOnSuccess(),
      onFailure = err => ErrorOperations.showError(s"Cannot remove polygon: ${err.message}")
    )

  // Attempt to delete a vertex by VertexId
  def attemptVertexDeletion(vertexId: VertexId): Unit =
    val op = () => currentTiling.now().maybeDeleteVertex(vertexId)
    OperationRunner.runTilingOp(op)(
      onSuccess = clearSymmetryOverlaysOnSuccess(),
      onFailure = err => ErrorOperations.showError(s"Cannot remove vertex: ${err.message}")
    )

  // Attempt to delete an edge by endpoints (stable VertexId pair)
  def attemptEdgeDeletion(startVertexId: VertexId, endVertexId: VertexId): Unit =
    val op = () => currentTiling.now().maybeDeleteEdge(startVertexId, endVertexId)
    OperationRunner.runTilingOp(op)(
      onSuccess = clearSymmetryOverlaysOnSuccess(),
      onFailure = err => ErrorOperations.showError(s"Cannot remove edge: ${err.message}")
    )

  def attemptFanning(vertexId: VertexId): Unit =
    val tiling           = currentTiling.now()
    clearAllAnimations()
    val faceIds          = tiling.innerFaces.map(_.id)
    val facePoints       = precomputeFacePoints(tiling)
    val maxFaceId        = faceIds.map(_.value).maxOption.getOrElse(0)
    val colors           = polygonColors.now()
    val faceCount        = faceIds.size
    val pivotOpt         = tiling.findVertex(vertexId).toOption.map(_.coords.toPoint)
    val boundaryAngleOpt = boundaryInnerAngleAt(tiling, vertexId)
    val op               = () => tiling.fanAt(vertexId)
    OperationRunner.runTilingOp(op)(
      onSuccess =
        val newFaceCount = currentTiling.now().innerFaces.size
        val needsFit     = ViewOperations.isTilingLargerThanCanvas
        var fitDelayed   = false
        if faceCount > 0 && newFaceCount > faceCount && newFaceCount % faceCount == 0 then
          val copies       = newFaceCount / faceCount
          val fillFallback = EditorState.fillColor.now()
          (1 until copies).foreach: copyIndex =>
            faceIds.indices.foreach: id =>
              val rgb   = colors.getOrElse(faceIds(id), fillFallback)
              val newId = FaceId(maxFaceId + (copyIndex - 1) * faceCount + id + 1)
              polygonColors.update(_ + (newId -> rgb))
          (pivotOpt, boundaryAngleOpt) match
            case (Some(pivot), Some(angle)) if copies > 1 =>
              val animation =
                FanAnimation(
                  facePoints,
                  pivot,
                  copies,
                  angle,
                  EditorConfig.fanAnimationDurationMs,
                  EditorConfig.fanAnimationStaggerMs
                )
              EditorState.fanAnimation.set(Some(animation))
              scheduleAnimationCleanup(
                EditorState.fanAnimation,
                animation,
                EditorConfig.fanAnimationDurationMs
              ) {
                if needsFit then ViewOperations.fitTilingToCanvas()
              }
              fitDelayed = true
            case _                                        => ()
        clearSymmetryOverlaysOnSuccess()
        if needsFit && !fitDelayed then ViewOperations.fitTilingToCanvas()
      ,
      onFailure = err => ErrorOperations.showError(s"Cannot fan tiling: ${err.message}")
    )

  private def boundaryInnerAngleAt(tiling: TilingDCEL, vertexId: VertexId): Option[Radian] =
    tiling.getInnerAnglesAtVertex(vertexId).toOption
      .map:
        _.sumExact.toRational.toDouble
      .map:
        Radian.fromDegrees

  private def precomputeFacePoints(tiling: TilingDCEL): List[(FaceId, String)] =
    tiling.innerFacesVertices.map: (faceId, faceVertices) =>
      val pointStrings =
        faceVertices.map: vertex =>
          val point = tilingPointToCanvasView(vertex.coords.toPoint)
          s"${point.x},${point.y}"
      (faceId, pointStrings.mkString(" "))

  private def faceCentroid(tiling: TilingDCEL, faceId: FaceId): Option[Point] =
    tiling.findInnerFaceVertices(faceId).toOption.map: vertices =>
      val points = vertices.map(_.coords.toPoint).map(tilingPointToCanvasView)
      averagePoint(points).getOrElse(Point.origin)

  private def averagePoint(points: Iterable[Point]): Option[Point] =
    if points.isEmpty then None
    else
      val (sx, sy, count) = points.foldLeft((0.0, 0.0, 0)): (acc, p) =>
        (acc._1 + p.x, acc._2 + p.y, acc._3 + 1)
      Some(Point(sx / count, sy / count))

  def attemptDoubling(): Unit =
    val tiling = currentTiling.now()
    if tiling.isEmpty then ()
    else
      clearAllAnimations()
      val faceIds        =
        tiling.innerFaces.map:
          _.id
      val facePoints     = precomputeFacePoints(tiling)
      val originalCenter = averagePoint(faceIds.flatMap(id => faceCentroid(tiling, id)))
      val maxFaceId      =
        faceIds
          .map: faceId =>
            faceId.value
          .max
      val colors         = polygonColors.now()
      val fillFallback   = EditorState.fillColor.now()
      val op             = () =>
        try
          tiling.doubleArea
        catch
          case e: Exception => Left(ValidationError(s"Error doubling: ${e.getMessage}"))

      OperationRunner.runTilingOp(op)(
        onSuccess =
          faceIds.indices.foreach: id =>
            val rgb = colors.getOrElse(faceIds(id), fillFallback)
            polygonColors.update(_ + (FaceId(maxFaceId + id + 1) -> rgb))
          val newFaceIds = faceIds.indices.map(id => FaceId(maxFaceId + id + 1))
          val newCenter  = averagePoint(newFaceIds.flatMap(id => faceCentroid(currentTiling.now(), id)))
          val needsFit   = ViewOperations.isTilingLargerThanCanvas
          var fitDelayed = false
          (originalCenter, newCenter) match
            case (Some(orig), Some(next)) =>
              val delta     = next - orig
              val animation = DoublingAnimation(facePoints, delta, EditorConfig.fanAnimationDurationMs)
              EditorState.doublingAnimation.set(Some(animation))
              scheduleAnimationCleanup(
                EditorState.doublingAnimation,
                animation,
                EditorConfig.fanAnimationDurationMs
              ) {
                if needsFit then ViewOperations.fitTilingToCanvas()
              }
              fitDelayed = true
            case _                        => ()
          clearSymmetryAndPerimeterSelectionOnSuccess()
          if needsFit && !fitDelayed then ViewOperations.fitTilingToCanvas()
        ,
        onFailure = err =>
          ErrorOperations.showError(err.message)
      )

  def attemptMirroring(): Unit =
    val tiling = currentTiling.now()
    if tiling.isEmpty then ()
    else
      clearAllAnimations()
      val facePoints = precomputeFacePoints(tiling)
      val op         = () =>
        try
          Right(tiling.verticallyReflectedCopy)
        catch
          case e: Exception => Left(ValidationError(s"Error mirroring: ${e.getMessage}"))

      OperationRunner.runTilingOp(op)(
        onSuccess =
          if facePoints.nonEmpty then
            val animation =
              MirrorAnimation(
                facePoints = facePoints,
                axisY = EditorConfig.canvasCenter.y,
                durationMs = EditorConfig.fanAnimationDurationMs
              )
            EditorState.mirrorAnimation.set(Some(animation))
            scheduleAnimationCleanup(EditorState.mirrorAnimation, animation, animation.durationMs)()
          clearSymmetryAndPerimeterSelectionOnSuccess()
        ,
        onFailure = err =>
          ErrorOperations.showError(err.message)
      )

  // Handle perimeter-edge click with polygon growth
  def attemptPolygonAddition(edgeId: String, edgeIndex: Int): Unit =
    (currentTiling.now(), EditorState.selectedPolygon.now(), EditorState.isIrregularSelected.now()) match
      case (tiling, _, _) if tiling.isEmpty =>
        ErrorOperations.showError("No tiling available to grow")
      case (_, None, false)                 =>
        Logger.warn("Both regular polygon and irregular polygon unselected")
      case (_, Some(_), true)               =>
        Logger.error("Should not happen: both regular polygon and irregular polygon selected")
      case (tiling, maybeSides, _)          =>
        val perimeterEdges = tiling.boundaryVertices.map(_.toCoords).slidingO(2).toList
        val op             = () =>
          try
            if edgeIndex < perimeterEdges.length then
              val selectedEdge = perimeterEdges(edgeIndex)
              if maybeSides.isDefined then
                tiling.maybeAddRegularPolygonToBoundary(selectedEdge.head.id, RegularPolygon(maybeSides.get))
              else
                val angles = EditorState.recentIrregularPolygon.now().get
                tiling.maybeAddSimplePolygonToBoundary(selectedEdge.head.id, angles)
            else
              Left(ValidationError("Invalid edge index"))
          catch
            case e: Exception => Left(ValidationError(s"Error growing edge: ${e.getMessage}"))

        OperationRunner.runTilingOp(op)(
          onSuccess =
            clearSymmetryAndPerimeterSelectionOnSuccess(),
          onFailure = err =>
            if edgeIndex < perimeterEdges.length then
              val selectedEdge = perimeterEdges(edgeIndex)
              val angles       = maybeSides.map(sides => RegularPolygon(sides).angles).getOrElse(
                EditorState.recentIrregularPolygon.now().get
              )
              val placement    =
                FailedPolygonPlacement(edgeIndex, angles, (selectedEdge(0), selectedEdge(1)), tiling)
              val truncated    = err.message
              if maybeSides.isDefined then
                ErrorOperations.showError(
                  s"Growing ${polygonName(maybeSides.get)}s on this perimeter edge is invalid. $truncated",
                  Some(placement)
                )
              else
                ErrorOperations.showError(
                  s"Growing the given ${angles.size}-sides irregular polygon on this perimeter edge is invalid. $truncated",
                  Some(placement)
                )
            else
              ErrorOperations.showError(err.message)
        )

  // Helper: try to find the inner face that contains this directed edge; if not found, None
  private def findFaceContainingEdge(tiling: TilingDCEL, v1: VertexId, v2: VertexId): Option[FaceId] =
    // We look for a face whose boundary contains the directed edge (v1 -> v2).
    // If DCEL provides halfEdges on face with next pointers, prefer that; here we rely on vertex order on the face.
    tiling.innerFaces.find { face =>

      tiling
        .findInnerFaceVertices(face.id)
        .toOption
        .exists(vertices =>
          vertices.map(_.id).toVector.slidingO(2).exists(pair => pair(0) == v1 && pair(1) == v2)
        )
    }.map(_.id)

  def attemptPolygonInsertion(startVertexId: VertexId, endVertexId: VertexId): Unit =
    (currentTiling.now(), EditorState.selectedPolygon.now(), EditorState.isIrregularSelected.now()) match
      case (tiling, _, _) if tiling.isEmpty =>
        ErrorOperations.showError("No tiling available for insertion")
      case (_, None, false)                 =>
        Logger.warn("Both regular polygon and irregular polygon unselected")
      case (_, Some(_), true)               =>
        Logger.error("Should not happen: both regular polygon and irregular polygon selected")
      case (tiling, maybeSides, _)          =>
        val op = () =>
          try
            if maybeSides.isDefined then
              tiling.maybeAddRegularPolygon(startVertexId, endVertexId, RegularPolygon(maybeSides.get))
            else
              val angles = EditorState.recentIrregularPolygon.now().get
              tiling.maybeAddSimplePolygon(startVertexId, endVertexId, angles)
          catch
            case e: Exception => Left(ValidationError(s"Error inserting polygon: ${e.getMessage}"))

        OperationRunner.runTilingOp(op)(
          onSuccess =
            clearSymmetryAndPerimeterSelectionOnSuccess(),
          onFailure = error => {
            val curr           = currentTiling.now()
            val maybeFaceId    = findFaceContainingEdge(curr, startVertexId, endVertexId)
            val startCoordsOpt = tiling.findVertex(startVertexId).toOption.map(_.toCoords)
            val endCoordsOpt   = tiling.findVertex(endVertexId).toOption.map(_.toCoords)
            val edgeOpt        =
              for
                startCoords <- startCoordsOpt
                endCoords   <- endCoordsOpt
              yield (startCoords, endCoords)

            if maybeSides.isDefined then
              val placementOpt =
                edgeOpt.map(edge =>
                  FailedPolygonPlacement(
                    edgeIndex = 0, // not needed for interior wireframe
                    angles = RegularPolygon(maybeSides.get).angles,
                    edge = edge,
                    tiling = curr,
                    intoFace = maybeFaceId
                  )
                )
              ErrorOperations.showError(
                s"Cannot insert regular polygon: ${error.message}",
                placement = placementOpt
              )
            else
              val maybeAngles  = EditorState.recentIrregularPolygon.now()
              val placementOpt =
                for
                  edge   <- edgeOpt
                  angles <- maybeAngles
                yield FailedPolygonPlacement(
                  edgeIndex = 0, // not needed for interior wireframe
                  angles = angles,
                  edge = edge,
                  tiling = curr,
                  intoFace = maybeFaceId
                )
              ErrorOperations.showError(
                s"Cannot insert irregular polygon: ${error.message}",
                placement = placementOpt
              )
          }
        )
