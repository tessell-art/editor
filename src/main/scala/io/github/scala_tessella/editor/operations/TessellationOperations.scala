package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.dcel.TilingEquivalency.verticallyReflectedCopy
import io.github.scala_tessella.dcel.geometry.RegularPolygon
import io.github.scala_tessella.dcel.structure.{FaceId, Vertex, VertexId}
import io.github.scala_tessella.dcel.{TilingDCEL, ValidationError}
import io.github.scala_tessella.editor.models.{
  AnimationState,
  DoublingAnimation,
  EditorConfig,
  EditorState,
  FailedPolygonPlacement,
  FanAnimation,
  MirrorAnimation,
  VertexCoord
}
import io.github.scala_tessella.editor.operations.OperationGuard.ifNotProcessing
import io.github.scala_tessella.editor.operations.ColorOperations.ensureColorsForFaces
import io.github.scala_tessella.editor.utils.PolygonNameGenerator.polygonName
import io.github.scala_tessella.editor.utils.geo.TessellationGeometry.{tilingPointToCanvasView, toPoint}
import io.github.scala_tessella.editor.utils.geo.{Point, Radian}
import io.github.scala_tessella.editor.utils.{ColorRGB, Logger, UndoManager}
import io.github.scala_tessella.ring_seq.RingSeq.slidingO

import scala.scalajs.js.timers.setTimeout
object TessellationOperations:

  sealed private trait PolygonPlacementKind:
    def angles: Vector[AngleDegree]

  private case class RegularPlacement(sides: Int) extends PolygonPlacementKind:
    override val angles: Vector[AngleDegree] = RegularPolygon(sides).angles

  private case class IrregularPlacement(override val angles: Vector[AngleDegree]) extends PolygonPlacementKind

  private case class PolygonPlacementContext(tiling: TilingDCEL, placement: PolygonPlacementKind)

  private case class FanContext(
      tiling: TilingDCEL,
      faceIds: List[FaceId],
      facePoints: List[(FaceId, String)],
      maxFaceId: Int,
      colors: Map[FaceId, ColorRGB],
      faceCount: Int,
      pivotOpt: Option[Point],
      boundaryAngleOpt: Option[Radian]
  )

  private case class DoublingContext(
      tiling: TilingDCEL,
      faceIds: Vector[FaceId],
      facePoints: List[(FaceId, String)],
      originalCenter: Option[Point],
      maxFaceId: Int,
      colors: Map[FaceId, ColorRGB],
      fillFallback: ColorRGB
  )

  private def clearAllAnimations(): Unit =
    EditorState.animationState.update(_.copy(fanAnimation = None))
    EditorState.animationState.update(_.copy(doublingAnimation = None))
    EditorState.animationState.update(_.copy(mirrorAnimation = None))

  private def clearSymmetryOverlaysOnSuccess(): Unit =
    SymmetryOperations.clearOverlays()

  private def clearSymmetryAndPerimeterSelectionOnSuccess(): Unit =
    clearSymmetryOverlaysOnSuccess()
    EditorState.tessellationState.update(_.copy(selectedPerimeterEdges = Set.empty))

  /** Clears the given animation after `durationMs`, only if it is still the current value (another animation
    * may have replaced it meanwhile). Takes lens-style get/set lambdas over `AnimationState` since the
    * animation Vars are now nested per ADR-002.
    */
  private def scheduleAnimationCleanup[A <: AnyRef](
      getAnimation: AnimationState => Option[A],
      clearAnimation: AnimationState => AnimationState,
      animation: A,
      durationMs: Int
  )(onDone: => Unit = ()): Unit =
    setTimeout(durationMs) {
      val current = getAnimation(EditorState.animationState.now())
      if current.exists(_ eq animation) then
        EditorState.animationState.update(clearAnimation)
      onDone
    }: Unit

  extension (vertex: Vertex)

    def toCoords: VertexCoord =
      (vertex.id, vertex.coords.toPoint)

  private def resolvePolygonPlacementKind(
      maybeSides: Option[Int],
      isIrregularSelected: Boolean,
      recentIrregular: Option[Vector[AngleDegree]]
  ): Option[PolygonPlacementKind] =
    (maybeSides, isIrregularSelected) match
      case (None, false)        =>
        Logger.warn("Both regular polygon and irregular polygon unselected")
        None
      case (Some(_), true)      =>
        Logger.error("Should not happen: both regular polygon and irregular polygon selected")
        None
      case (Some(sides), false) => Some(RegularPlacement(sides))
      case (None, true)         =>
        recentIrregular match
          case Some(angles) => Some(IrregularPlacement(angles))
          case None         =>
            Logger.error("Should not happen: irregular polygon selected but no recent shape available")
            None

  private def currentPolygonPlacementContext(emptyTilingMessage: String): Option[PolygonPlacementContext] =
    val tiling      = EditorState.tessellationState.now().currentTiling
    val maybeSides  = EditorState.toolState.now().selectedPolygon
    val isIrregular = EditorState.irregularState.now().isIrregularSelected
    val recentShape = EditorState.irregularState.now().recentIrregularPolygon
    if tiling.isEmpty then
      ErrorOperations.showError(emptyTilingMessage)
      None
    else
      resolvePolygonPlacementKind(maybeSides, isIrregular, recentShape).map: placement =>
        PolygonPlacementContext(tiling, placement)

  private def snapshotFanContext(vertexId: VertexId): FanContext =
    val tiling           = EditorState.tessellationState.now().currentTiling
    val faceIds          = tiling.innerFaces.map(_.id)
    val boundaryAngleOpt = boundaryInnerAngleAt(tiling, vertexId)
    FanContext(
      tiling = tiling,
      faceIds = faceIds,
      facePoints = precomputeFacePoints(tiling),
      maxFaceId = faceIds.map(_.value).maxOption.getOrElse(0),
      colors = EditorState.colorState.now().polygonColors,
      faceCount = faceIds.size,
      pivotOpt = tiling.findVertex(vertexId).toOption.map(_.coords.toPoint),
      boundaryAngleOpt = boundaryAngleOpt
    )

  private def snapshotDoublingContext(tiling: TilingDCEL): DoublingContext =
    val faceIds = tiling.innerFaces.map(_.id).toVector
    DoublingContext(
      tiling = tiling,
      faceIds = faceIds,
      facePoints = precomputeFacePoints(tiling),
      originalCenter = averagePoint(faceIds.flatMap(id => faceCentroid(tiling, id))),
      maxFaceId = faceIds.map(_.value).max,
      colors = EditorState.colorState.now().polygonColors,
      fillFallback = EditorState.colorState.now().fillColor
    )

  private def newFaceIdsFrom(startingFrom: Int, count: Int): Vector[FaceId] = (0 until count).map(offset =>
    FaceId(startingFrom + offset + 1)
  ).toVector

  private def mirrorAxisYFor(tiling: TilingDCEL): Double =
    val ys =
      tiling.innerFacesVertices
        .flatMap: (_, faceVertices) =>
          faceVertices.map: vertex =>
            tilingPointToCanvasView(vertex.coords.toPoint).y
    if ys.isEmpty then EditorConfig.canvasCenter.y
    else (ys.min + ys.max) / 2.0

  def selectPolygon(sides: Int): Unit =
    ifNotProcessing:
      // Selecting a regular polygon deselects the irregular
      EditorState.irregularState.update(_.copy(isIrregularSelected = false))
      EditorState.toolState.update(_.copy(selectedPolygon = Some(sides)))

      if EditorState.tessellationState.now().currentTiling.isEmpty then
        UndoManager.saveState()
        try
          val tiling = TilingDCEL.createRegularPolygon(RegularPolygon(sides))
          EditorState.tessellationState.update(_.copy(currentTiling = tiling))
          ensureColorsForFaces(tiling.innerFaces.map(_.id), EditorState.colorState.now().fillColor)
          SelectionOperations.clearAllSelections()
        catch
          case e: Throwable =>
            UndoManager.undo()
            ErrorOperations.showError(s"Failed to create tiling from $sides-sided polygon")

  def clearTiling(): Unit =
    ifNotProcessing:
      if !EditorState.tessellationState.now().currentTiling.isEmpty then
        UndoManager.saveState()

      EditorState.tessellationState.update(_.copy(currentTiling = TilingDCEL.empty))
      clearSymmetryAndPerimeterSelectionOnSuccess()
      EditorState.colorState.update(_.copy(polygonColors = Map.empty))
      EditorState.tessellationState.update(_.copy(selectedTilingPolygons = Set.empty))

  /** Select the irregular polygon in the palette (deselect regular if any). */
  def selectIrregularInPalette(): Unit =
    ifNotProcessing:
      if EditorState.irregularState.now().recentIrregularPolygon.isDefined then
        EditorState.toolState.update(_.copy(selectedPolygon = None))
        EditorState.irregularState.update(_.copy(isIrregularSelected = true))

  /** If the tiling is empty and a recent irregular exists, initialize the tiling with it. */
  def initializeWithIrregularIfEmpty(): Unit =
    ifNotProcessing:
      if EditorState.tessellationState.now().currentTiling.isEmpty then
        EditorState.irregularState.now().recentIrregularPolygon match
          case Some(angles) =>
            UndoManager.saveState()
            TilingDCEL.createSimplePolygon(angles).toOption match
              case Some(tiling) =>
                EditorState.tessellationState.update(_.copy(currentTiling = tiling))
                ensureColorsForFaces(tiling.innerFaces.map(_.id), EditorState.colorState.now().fillColor)
                SelectionOperations.clearAllSelections()
              case None         =>
                UndoManager.undo()
                ErrorOperations.showError("Failed to create tiling from irregular polygon")
          case None         => ()

  // Attempt to delete a face by FaceId (stable, DCEL-native)
  def attemptFaceDeletion(faceId: FaceId): Unit =
    val op = () => EditorState.tessellationState.now().currentTiling.maybeDeleteFace(faceId)
    OperationRunner.runTilingOp(op)(
      onSuccess = clearSymmetryOverlaysOnSuccess(),
      onFailure = err => ErrorOperations.showError(s"Cannot remove polygon: ${err.message}")
    )

  // Attempt to delete a vertex by VertexId
  def attemptVertexDeletion(vertexId: VertexId): Unit =
    val op = () => EditorState.tessellationState.now().currentTiling.maybeDeleteVertex(vertexId)
    OperationRunner.runTilingOp(op)(
      onSuccess = clearSymmetryOverlaysOnSuccess(),
      onFailure = err => ErrorOperations.showError(s"Cannot remove vertex: ${err.message}")
    )

  // Attempt to delete an edge by endpoints (stable VertexId pair)
  def attemptEdgeDeletion(startVertexId: VertexId, endVertexId: VertexId): Unit =
    val op =
      () => EditorState.tessellationState.now().currentTiling.maybeDeleteEdge(startVertexId, endVertexId)
    OperationRunner.runTilingOp(op)(
      onSuccess = clearSymmetryOverlaysOnSuccess(),
      onFailure = err => ErrorOperations.showError(s"Cannot remove edge: ${err.message}")
    )

  def attemptFanning(vertexId: VertexId): Unit =
    val context = snapshotFanContext(vertexId)
    clearAllAnimations()
    val op      = () => context.tiling.fanAt(vertexId)
    OperationRunner.runTilingOp(op)(
      onSuccess =
        val newFaceCount = EditorState.tessellationState.now().currentTiling.innerFaces.size
        val needsFit     = ViewOperations.isTilingLargerThanCanvas
        var fitDelayed   = false
        if context.faceCount > 0 && newFaceCount > context.faceCount && newFaceCount % context.faceCount == 0
        then
          val copies       = newFaceCount / context.faceCount
          val fillFallback = EditorState.colorState.now().fillColor
          (1 until copies).foreach: copyIndex =>
            context.faceIds.indices.foreach: id =>

              val rgb   = context.colors.getOrElse(context.faceIds(id), fillFallback)
              val newId = FaceId(context.maxFaceId + (copyIndex - 1) * context.faceCount + id + 1)
              EditorState.colorState.update(s => s.copy(polygonColors = s.polygonColors + (newId -> rgb)))
          (context.pivotOpt, context.boundaryAngleOpt) match
            case (Some(pivot), Some(angle)) if copies > 1 =>
              val animation =
                FanAnimation(
                  context.facePoints,
                  pivot,
                  copies,
                  angle,
                  EditorConfig.fanAnimationDurationMs,
                  EditorConfig.fanAnimationStaggerMs
                )
              EditorState.animationState.update(_.copy(fanAnimation = Some(animation)))
              scheduleAnimationCleanup(
                getAnimation = _.fanAnimation,
                clearAnimation = _.copy(fanAnimation = None),
                animation = animation,
                durationMs = EditorConfig.fanAnimationDurationMs
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
    val tiling = EditorState.tessellationState.now().currentTiling
    if tiling.isEmpty then ()
    else
      val context = snapshotDoublingContext(tiling)
      clearAllAnimations()
      val op      = () =>
        try
          context.tiling.doubleArea
        catch
          case e: Exception => Left(ValidationError(s"Error doubling: ${e.getMessage}"))

      OperationRunner.runTilingOp(op)(
        onSuccess =
          val newFaceIds    = newFaceIdsFrom(context.maxFaceId, context.faceIds.size)
          context.faceIds.indices.foreach: id =>

            val rgb = context.colors.getOrElse(context.faceIds(id), context.fillFallback)
            EditorState.colorState.update(s =>
              s.copy(polygonColors = s.polygonColors + (newFaceIds(id) -> rgb))
            )
          val updatedTiling = EditorState.tessellationState.now().currentTiling
          val newCenter     = averagePoint(newFaceIds.flatMap(id => faceCentroid(updatedTiling, id)))
          val needsFit      = ViewOperations.isTilingLargerThanCanvas
          var fitDelayed    = false
          (context.originalCenter, newCenter) match
            case (Some(orig), Some(next)) =>
              val delta     = next - orig
              val animation =
                DoublingAnimation(context.facePoints, delta, EditorConfig.fanAnimationDurationMs)
              EditorState.animationState.update(_.copy(doublingAnimation = Some(animation)))
              scheduleAnimationCleanup(
                getAnimation = _.doublingAnimation,
                clearAnimation = _.copy(doublingAnimation = None),
                animation = animation,
                durationMs = EditorConfig.fanAnimationDurationMs
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
    val tiling = EditorState.tessellationState.now().currentTiling
    if tiling.isEmpty then ()
    else
      clearAllAnimations()
      val facePoints = precomputeFacePoints(tiling)
      val axisY      = mirrorAxisYFor(tiling)
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
                axisY = axisY,
                durationMs = EditorConfig.fanAnimationDurationMs
              )
            EditorState.animationState.update(_.copy(mirrorAnimation = Some(animation)))
            scheduleAnimationCleanup(
              getAnimation = _.mirrorAnimation,
              clearAnimation = _.copy(mirrorAnimation = None),
              animation = animation,
              durationMs = animation.durationMs
            )()
          clearSymmetryAndPerimeterSelectionOnSuccess()
        ,
        onFailure = err =>
          ErrorOperations.showError(err.message)
      )

  // Handle perimeter-edge click with polygon growth
  def attemptPolygonAddition(edgeId: String, edgeIndex: Int): Unit =
    currentPolygonPlacementContext("No tiling available to grow").foreach: context =>

      val tiling         = context.tiling
      val perimeterEdges = tiling.boundaryVertices.toOption.get.map(_.toCoords).slidingO(2).toList
      val op             = () =>
        try
          if edgeIndex < perimeterEdges.length then
            val selectedEdge = perimeterEdges(edgeIndex)
            context.placement match
              case RegularPlacement(sides)    =>
                tiling.maybeAddRegularPolygonToBoundary(selectedEdge.head.id, RegularPolygon(sides))
              case IrregularPlacement(angles) =>
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
            val angles       = context.placement.angles
            val placement    =
              FailedPolygonPlacement(edgeIndex, angles, (selectedEdge(0), selectedEdge(1)), tiling)
            val truncated    = err.message
            context.placement match
              case RegularPlacement(sides) =>
                ErrorOperations.showError(
                  s"Growing ${polygonName(sides)}s on this perimeter edge is invalid. $truncated",
                  Some(placement)
                )
              case IrregularPlacement(_)   =>
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
    currentPolygonPlacementContext("No tiling available for insertion").foreach: context =>

      val tiling = context.tiling
      val op     = () =>
        try
          context.placement match
            case RegularPlacement(sides)    =>
              tiling.maybeAddRegularPolygon(startVertexId, endVertexId, RegularPolygon(sides))
            case IrregularPlacement(angles) =>
              tiling.maybeAddSimplePolygon(startVertexId, endVertexId, angles)
        catch
          case e: Exception => Left(ValidationError(s"Error inserting polygon: ${e.getMessage}"))

      OperationRunner.runTilingOp(op)(
        onSuccess =
          clearSymmetryAndPerimeterSelectionOnSuccess(),
        onFailure = error => {
          val curr           = EditorState.tessellationState.now().currentTiling
          val maybeFaceId    = findFaceContainingEdge(curr, startVertexId, endVertexId)
          val startCoordsOpt = tiling.findVertex(startVertexId).toOption.map(_.toCoords)
          val endCoordsOpt   = tiling.findVertex(endVertexId).toOption.map(_.toCoords)
          val edgeOpt        =
            for
              startCoords <- startCoordsOpt
              endCoords   <- endCoordsOpt
            yield (startCoords, endCoords)

          val placementOpt = edgeOpt.map: edge =>
            FailedPolygonPlacement(
              edgeIndex = 0, // not needed for interior wireframe
              angles = context.placement.angles,
              edge = edge,
              tiling = curr,
              intoFace = maybeFaceId
            )

          context.placement match
            case RegularPlacement(_)   =>
              ErrorOperations.showError(
                s"Cannot insert regular polygon: ${error.message}",
                placement = placementOpt
              )
            case IrregularPlacement(_) =>
              ErrorOperations.showError(
                s"Cannot insert irregular polygon: ${error.message}",
                placement = placementOpt
              )
        }
      )
