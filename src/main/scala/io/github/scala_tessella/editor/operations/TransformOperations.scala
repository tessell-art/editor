package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.TilingEquivalency.verticallyReflectedCopy
import io.github.scala_tessella.dcel.structure.{FaceId, VertexId}
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.editor.models.{
  AnimationState,
  DoublingAnimation,
  EditorConfig,
  EditorState,
  FanAnimation,
  MirrorAnimation
}
import io.github.scala_tessella.editor.utils.geo.TessellationGeometry.{tilingPointToCanvasView, toPoint}
import io.github.scala_tessella.editor.utils.geo.{Point, Radian}
import io.github.scala_tessella.editor.utils.ColorRGB

import scala.scalajs.js.timers.setTimeout

/** Tiling-transformation operations that mutate the whole tiling and play a transient animation: fan
  * (rotational replication around a boundary vertex), double (mirror around a parallelogon axis), and mirror
  * (vertical reflection).
  *
  * These are distinct from [[SymmetryOperations]] which manages overlay *visibility* (showing
  * rotation/reflection axes without mutating the tiling). Both concerns are "symmetry" at the domain level,
  * split by mutation-vs-overlay along operational lines.
  */
object TransformOperations:

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
    EditorState.animationState.set(AnimationState.initial)

  /** Clears the given animation after `durationMs`, only if it is still the current value (another animation
    * may have replaced it meanwhile). Takes lens-style get/set lambdas over `AnimationState` so the caller
    * scopes the cleanup to one aggregate slice rather than re-deriving the full state.
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

  /** Rotational replication around a boundary vertex. */
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
                  MotionPreferences.effectiveAnimationDurationMs,
                  MotionPreferences.effectiveStaggerMs
                )
              EditorState.animationState.update(_.copy(fanAnimation = Some(animation)))
              scheduleAnimationCleanup(
                getAnimation = _.fanAnimation,
                clearAnimation = _.copy(fanAnimation = None),
                animation = animation,
                durationMs = MotionPreferences.effectiveAnimationDurationMs
              ) {
                if needsFit then ViewOperations.fitTilingToCanvas()
              }
              fitDelayed = true
            case _                                        => ()
        SymmetryOperations.clearOverlays()
        if needsFit && !fitDelayed then ViewOperations.fitTilingToCanvas()
      ,
      onFailure = err => ErrorOperations.showError(s"Cannot fan tiling: ${err.message}")
    )

  /** Double the tiling (for parallelogons). */
  def attemptDoubling(): Unit =
    val tiling = EditorState.tessellationState.now().currentTiling
    if tiling.isEmpty then ()
    else
      val context = snapshotDoublingContext(tiling)
      clearAllAnimations()
      val op      = () => OperationRunner.safely("Error doubling")(context.tiling.doubleArea)

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
                DoublingAnimation(context.facePoints, delta, MotionPreferences.effectiveAnimationDurationMs)
              EditorState.animationState.update(_.copy(doublingAnimation = Some(animation)))
              scheduleAnimationCleanup(
                getAnimation = _.doublingAnimation,
                clearAnimation = _.copy(doublingAnimation = None),
                animation = animation,
                durationMs = MotionPreferences.effectiveAnimationDurationMs
              ) {
                if needsFit then ViewOperations.fitTilingToCanvas()
              }
              fitDelayed = true
            case _                        => ()
          TessellationOperations.clearStaleAfterMutation()
          if needsFit && !fitDelayed then ViewOperations.fitTilingToCanvas()
        ,
        onFailure = err =>
          ErrorOperations.showError(err.message)
      )

  /** Replace the tiling with its vertical mirror image. */
  def attemptMirroring(): Unit =
    val tiling = EditorState.tessellationState.now().currentTiling
    if tiling.isEmpty then ()
    else
      clearAllAnimations()
      val facePoints = precomputeFacePoints(tiling)
      val axisY      = mirrorAxisYFor(tiling)
      val op         = () =>
        OperationRunner.safely("Error mirroring")(Right(tiling.verticallyReflectedCopy))

      OperationRunner.runTilingOp(op)(
        onSuccess =
          if facePoints.nonEmpty then
            val animation =
              MirrorAnimation(
                facePoints = facePoints,
                axisY = axisY,
                durationMs = MotionPreferences.effectiveAnimationDurationMs
              )
            EditorState.animationState.update(_.copy(mirrorAnimation = Some(animation)))
            scheduleAnimationCleanup(
              getAnimation = _.mirrorAnimation,
              clearAnimation = _.copy(mirrorAnimation = None),
              animation = animation,
              durationMs = animation.durationMs
            )()
          TessellationOperations.clearStaleAfterMutation()
        ,
        onFailure = err =>
          ErrorOperations.showError(err.message)
      )
