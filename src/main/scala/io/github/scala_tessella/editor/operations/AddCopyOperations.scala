package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.geometry.{AngleDegree, BigPoint}
import io.github.scala_tessella.dcel.geometry.BigPoint.centroid
import io.github.scala_tessella.dcel.structure.{FaceId, VertexId}
import io.github.scala_tessella.editor.models.{
  AddSubmode,
  Anchor,
  EditorConfig,
  EditorState,
  ReflectCopyDrag,
  RotateCopyDrag,
  Tool,
  TranslateCopyDrag
}
import io.github.scala_tessella.editor.operations.OperationGuard.ifNotProcessing
import io.github.scala_tessella.editor.operations.PaletteDragOperations.screenSvgToCanvasView
import io.github.scala_tessella.editor.utils.geo.TessellationGeometry.{tilingPointToCanvasView, toPoint}
import io.github.scala_tessella.editor.utils.geo.{LineSegment, Point, Radian}
import io.github.scala_tessella.ring_seq.RingSeq.slidingO
import spire.math.Rational

/** Operations for the **Edit ▸ Add Copy** family — growing a tiling by welding on a copy of itself under a
  * plane isometry (dcel 0.1.1). **Translate** and **Rotate** are wired; reflect / glide reflect are reserved
  * for later iterations.
  *
  * Both are direct-manipulation gestures driven by [[interactions.MouseEventHandler]] (which suppresses
  * panning while the tool is active) and rendered as a dashed skeleton overlay.
  *
  *   - **Translate**: drag the skeleton; the `from` endpoint is the vertex nearest the press, the `to`
  *     endpoint snaps to the vertex nearest the release. Both are exact tiling vertices (`vertex.coords`,
  *     exact `BigPoint`s) so the vector is exact and the copy can coincide exactly.
  *   - **Rotate**: press near a centre dot (vertex / edge-midpoint / symmetric face-centre), drag around it;
  *     the rotation snaps to the angles that can weld for that centre type (vertex → edge alignments;
  *     midpoint → 180°; face centre → 360/k multiples). The exact pivot is recomputed from the anchor at
  *     commit via `BigPoint.centroid`; the angle is tolerated by the weld so a `Double`-derived value is
  *     fine.
  *
  * Either way the validation pipeline runs and a rejected copy surfaces via [[ErrorOperations]].
  */
object AddCopyOperations:

  /** Snap radius for the translate release endpoint, in canvas-view units (≈ 0.4 tiling units). Releasing
    * farther than this from any vertex cancels the drag rather than snapping to a distant vertex.
    */
  private val snapRadiusCv: Double = 0.4 * EditorConfig.canvasScale

  /** Angular tolerance (degrees) for snapping the rotate drag to a candidate angle. */
  private val snapAngleToleranceDeg: Double = 10.0

  /** Epsilon (tiling-coord units) for the rotational-symmetry coincidence check. */
  private val symmetryEps: Double = 1e-6

  /** Activates the Translate-copy tool. The tiling's vertices then show as clipping-point dots; the user
    * drags the skeleton and releases on a vertex to weld a translated copy. No-op while processing or empty.
    */
  def enterTranslateCopyMode(): Unit =
    ifNotProcessing:
      if !EditorState.tessellationState.now().currentTiling.isEmpty then
        MeasurementOperations.clearAll()
        clearDrag()
        EditorState.toolState.update(_.copy(activeTool = Tool.TranslateCopy))

  /** Leaves the Translate-copy tool, returning to the default AddPolygon mode and dropping any in-flight
    * drag.
    */
  def exitMode(): Unit =
    clearDrag()
    EditorState.toolState.update(_.copy(activeTool = Tool.AddPolygon, addSubmode = AddSubmode.Outside))

  private def clearDrag(): Unit =
    EditorState.previewState.update(
      _.copy(translateCopyDrag = None, rotateCopyDrag = None, reflectCopyDrag = None)
    )

  /** Distinct tiling vertices as `(id, canvas-view point)`, for dot rendering and snap hit-testing. */
  def vertexAnchorsCv(tiling: TilingDCEL): List[(VertexId, Point)] =
    if tiling.isEmpty then Nil
    else
      tiling.innerFacesVertices
        .flatMap((_, faceVertices) => faceVertices.map(v => v.id -> v.coords.toPoint))
        .toMap
        .view
        .mapValues(tilingPointToCanvasView)
        .toList

  /** Skeleton snapshot: each inner face as a canvas-view `"x1,y1 x2,y2 …"` points string. Mirrors
    * [[TransformOperations]]' face-point precompute so the dashed overlay matches the rendered polygons.
    */
  private def precomputeFacePoints(tiling: TilingDCEL): List[(FaceId, String)] =
    tiling.innerFacesVertices.map: (faceId, faceVertices) =>

      val pointStrings = faceVertices.map: vertex =>

        val p = tilingPointToCanvasView(vertex.coords.toPoint)
        s"${p.x},${p.y}"
      (faceId, pointStrings.mkString(" "))

  /** Pointer client pixels → canvas-view coords (the frame the content `<g>` paints into). */
  private def clientToCanvasView(clientX: Double, clientY: Double): Option[Point] =
    EraserProximityQuery
      .clientToSvg(clientX, clientY)
      .map(screenSvgToCanvasView(_, EditorState.viewState.now().viewTransform))

  private def nearest(target: Point, anchors: List[(VertexId, Point)]): Option[(VertexId, Point)] =
    anchors.minByOption { case (_, p) =>
      p.distanceTo(target)
    }

  /** Begin a skeleton drag: pick the source vertex nearest the press and snapshot the skeleton. No-op on an
    * empty tiling or when the pointer can't be projected onto the canvas.
    */
  def beginDrag(clientX: Double, clientY: Double): Unit =
    val tiling = EditorState.tessellationState.now().currentTiling
    if !tiling.isEmpty then
      clientToCanvasView(clientX, clientY).foreach: pressCv =>

        val anchors = vertexAnchorsCv(tiling)
        nearest(pressCv, anchors).foreach: (sourceId, sourceCv) =>
          EditorState.previewState.update(
            _.copy(translateCopyDrag =
              Some(
                TranslateCopyDrag(
                  facePoints = precomputeFacePoints(tiling),
                  sourceVertexId = sourceId,
                  sourcePointCv = sourceCv,
                  grabPointCv = pressCv,
                  deltaCv = Point(0, 0),
                  snapTarget = None
                )
              )
            )
          )

  /** Update the live drag: the skeleton tracks the cursor 1:1, and the vertex nearest the projected source
    * (within the snap radius, excluding the source itself) is highlighted as the prospective target.
    */
  def updateDrag(clientX: Double, clientY: Double): Unit =
    EditorState.previewState.now().translateCopyDrag.foreach: drag =>
      clientToCanvasView(clientX, clientY).foreach: currentCv =>

        val deltaCv    = currentCv - drag.grabPointCv
        val candidate  = drag.sourcePointCv + deltaCv
        val tiling     = EditorState.tessellationState.now().currentTiling
        val anchors    = vertexAnchorsCv(tiling).filterNot { case (id, _) =>
          id == drag.sourceVertexId
        }
        val snapTarget =
          nearest(candidate, anchors).filter { case (_, p) =>
            p.distanceTo(candidate) <= snapRadiusCv
          }
        EditorState.previewState.update(
          _.copy(translateCopyDrag = Some(drag.copy(deltaCv = deltaCv, snapTarget = snapTarget)))
        )

  /** Finish the drag: if a target vertex is snapped, weld a copy translated from the source vertex to the
    * target vertex (exact `BigPoint`s). Otherwise the drag is simply cancelled. The mode stays active so the
    * user can add further copies.
    */
  def endDrag(): Unit =
    val dragOpt = EditorState.previewState.now().translateCopyDrag
    clearDrag()
    for
      drag          <- dragOpt
      (targetId, _) <- drag.snapTarget
      tiling         = EditorState.tessellationState.now().currentTiling
      from          <- tiling.findVertex(drag.sourceVertexId).toOption.map(_.coords)
      to            <- tiling.findVertex(targetId).toOption.map(_.coords)
    do
      OperationRunner.runTilingOp(() =>
        OperationRunner.safely("Error adding translated copy")(tiling.maybeAddTranslatedCopy(from, to))
      )(
        onSuccess = TessellationOperations.clearStaleAfterMutation(),
        onFailure = err => ErrorOperations.showError(s"Cannot add translated copy: ${err.message}")
      )

  // ---- Rotate ---------------------------------------------------------------------------------------------

  /** Activates the Rotate-copy tool. Rotation centres (vertices, edge midpoints, symmetric face centres) show
    * as dots; the user presses one and drags around it to set the angle. No-op while processing or empty.
    */
  def enterRotateCopyMode(): Unit =
    ifNotProcessing:
      if !EditorState.tessellationState.now().currentTiling.isEmpty then
        MeasurementOperations.clearAll()
        clearDrag()
        EditorState.toolState.update(_.copy(activeTool = Tool.RotateCopy))

  /** All tiling anchors as `(anchor, canvas-view point)`: every distinct vertex, every distinct edge
    * midpoint, and face centres. When `symmetricFacesOnly`, face centres are restricted to faces with
    * rotational symmetry (order ≥ 2) — the only ones usable as a rotation centre; reflection axes accept any
    * face centre. For dot rendering and press hit-testing.
    */
  def anchorsCv(tiling: TilingDCEL, symmetricFacesOnly: Boolean): List[(Anchor, Point)] =
    if tiling.isEmpty then Nil
    else
      val faces           = tiling.innerFacesVertices
      val vertexAnchors   =
        faces
          .flatMap((_, vs) => vs.map(v => v.id -> v.coords.toPoint))
          .toMap
          .map((id, p) => (Anchor.Vertex(id): Anchor) -> tilingPointToCanvasView(p))
          .toList
      val midpointAnchors =
        faces
          .flatMap: (_, vs) =>
            vs.toVector.slidingO(2).toList.map: pair =>

              val (a, b) = (pair(0), pair(1))
              val mid    = LineSegment(a.coords.toPoint, b.coords.toPoint).midPoint
              Set(a.id, b.id) -> ((Anchor.MidPoint(a.id, b.id): Anchor) -> tilingPointToCanvasView(mid))
          .toMap
          .values
          .toList
      val faceAnchors     =
        faces.flatMap: (fid, vs) =>

          val pts = vs.map(_.coords.toPoint)
          Option.when(!symmetricFacesOnly || faceSymmetryOrder(pts) >= 2)(
            (Anchor.Center(fid): Anchor) -> tilingPointToCanvasView(centroidOf(pts))
          )
      vertexAnchors ++ midpointAnchors ++ faceAnchors

  /** Rotation centres: vertices, edge midpoints, and centres of rotationally-symmetric faces. */
  def rotationCentres(tiling: TilingDCEL): List[(Anchor, Point)] =
    anchorsCv(tiling, symmetricFacesOnly = true)

  /** Reflection axis anchors: every vertex, edge midpoint, and face centre (any line is valid). */
  def reflectAnchors(tiling: TilingDCEL): List[(Anchor, Point)] =
    anchorsCv(tiling, symmetricFacesOnly = false)

  /** Largest `k` dividing `n` such that rotating the vertex ring by 360/k about its centroid maps the polygon
    * onto itself (coincidence within `symmetryEps`). Uses coordinates, so it captures side lengths too (a
    * rectangle → 2, a square → 4, an equilateral triangle → 3, a scalene shape → 1). `1` means no rotational
    * symmetry.
    */
  def faceSymmetryOrder(faceVertices: List[Point]): Int =
    val n = faceVertices.size
    if n < 2 then 1
    else
      val c                         = centroidOf(faceVertices)
      def mapsOnto(k: Int): Boolean =
        val rotated = faceVertices.map(_.rotateAround(c, Radian.fromDegrees(360.0 / k)))
        rotated.forall(rp => faceVertices.exists(_.distanceTo(rp) < symmetryEps))
      (n to 2 by -1).find(k => n % k == 0 && mapsOnto(k)).getOrElse(1)

  /** Snap angles allowed for a rotation centre, by anchor type (see object doc). */
  private def candidateAngles(tiling: TilingDCEL, anchor: Anchor): List[AngleDegree] =
    anchor match
      case Anchor.MidPoint(_, _) =>
        List(AngleDegree(180))
      case Anchor.Center(fid)    =>
        val pts = tiling.findInnerFaceVertices(fid).toOption.map(_.map(_.coords.toPoint)).getOrElse(Nil)
        val k   = faceSymmetryOrder(pts)
        (1 until k).toList.map(m => AngleDegree(360 * m) / k)
      case Anchor.Vertex(id)     =>
        val dirs  = incidentEdgeAnglesDeg(tiling, id)
        val diffs =
          for
            a <- dirs
            b <- dirs
            if a != b
          yield normalizeSignedDeg(b - a)
        diffs
          .filter(d => math.abs(d) > 0.5)
          .distinctBy(d => math.round(d * 1000))
          .map(d => AngleDegree(Rational(d)))

  /** Polar angles (degrees) of every edge incident to the vertex, gathered from the face rings it belongs to,
    * deduped within a small tolerance.
    */
  private def incidentEdgeAnglesDeg(tiling: TilingDCEL, vertexId: VertexId): List[Double] =
    val angles =
      tiling.innerFacesVertices.flatMap: (_, vs) =>

        val ring = vs.toVector
        val n    = ring.size
        ring.indices.filter(i => ring(i).id == vertexId).flatMap: i =>

          val centre     = ring(i).coords.toPoint
          val neighbours = List(ring((i - 1 + n) % n), ring((i + 1) % n))
          neighbours.map(nb => centre.angleTo(nb.coords.toPoint).toDegrees)
    angles.distinctBy(a => math.round(a * 1000)).toList

  /** Exact point recomputed from an anchor — vertex coord, edge-midpoint, or face centroid. Used as the
    * rotation pivot and as the reflection axis endpoints.
    */
  private def anchorBigPoint(tiling: TilingDCEL, anchor: Anchor): Option[BigPoint] =
    anchor match
      case Anchor.Vertex(id)     => tiling.findVertex(id).toOption.map(_.coords)
      case Anchor.MidPoint(a, b) =>
        for
          va <- tiling.findVertex(a).toOption
          vb <- tiling.findVertex(b).toOption
        yield List(va.coords, vb.coords).centroid
      case Anchor.Center(fid)    =>
        tiling.findInnerFaceVertices(fid).toOption.map(vs => vs.map(_.coords).centroid)

  private def centroidOf(points: List[Point]): Point =
    if points.isEmpty then Point.origin
    else points.reduce(_ + _) / points.size.toDouble

  /** Reduce a degree value to `(-180, 180]`. */
  private def normalizeSignedDeg(d: Double): Double =
    val m = ((d % 360) + 360) % 360
    if m > 180.0 then m - 360.0 else m

  private def signedDeg(angle: AngleDegree): Double = normalizeSignedDeg(angle.toRational.toDouble)

  private def circularDistanceDeg(a: Double, b: Double): Double = math.abs(normalizeSignedDeg(a - b))

  private def nearestCentre(target: Point, centres: List[(Anchor, Point)]): Option[(Anchor, Point)] =
    centres.minByOption { case (_, p) =>
      p.distanceTo(target)
    }

  /** Begin a rotate drag: pick the centre anchor nearest the press, snapshot the skeleton, and precompute the
    * candidate snap angles for that centre.
    */
  def beginRotateDrag(clientX: Double, clientY: Double): Unit =
    val tiling = EditorState.tessellationState.now().currentTiling
    if !tiling.isEmpty then
      clientToCanvasView(clientX, clientY).foreach: pressCv =>
        nearestCentre(pressCv, rotationCentres(tiling)).foreach: (anchor, centerCv) =>
          EditorState.previewState.update(
            _.copy(rotateCopyDrag =
              Some(
                RotateCopyDrag(
                  facePoints = precomputeFacePoints(tiling),
                  centerAnchor = anchor,
                  centerCv = centerCv,
                  candidates = candidateAngles(tiling, anchor),
                  grabAngle = centerCv.angleTo(pressCv),
                  appliedDeg = 0.0,
                  snapped = None
                )
              )
            )
          )

  /** Update the live rotate drag: compute the free (clockwise-positive) angle from the grab, then snap to the
    * nearest candidate within tolerance. The skeleton renders at the snapped angle (or free when unsnapped).
    */
  def updateRotateDrag(clientX: Double, clientY: Double): Unit =
    EditorState.previewState.now().rotateCopyDrag.foreach: drag =>
      clientToCanvasView(clientX, clientY).foreach: currentCv =>

        val freeDeg            = drag.centerCv.angleTo(currentCv).normalizeDeltaAngle(drag.grabAngle).toDegrees
        val best               =
          drag.candidates
            .map(c => c -> signedDeg(c))
            .minByOption { case (_, cd) =>
              circularDistanceDeg(freeDeg, cd)
            }
            .filter { case (_, cd) =>
              circularDistanceDeg(freeDeg, cd) <= snapAngleToleranceDeg
            }
        val (snapped, applied) = best match
          case Some((c, cd)) => (Some(c), cd)
          case None          => (None, freeDeg)
        EditorState.previewState.update(
          _.copy(rotateCopyDrag = Some(drag.copy(appliedDeg = applied, snapped = snapped)))
        )

  /** Finish the rotate drag: if snapped to a candidate angle, weld a copy rotated about the centre's exact
    * pivot. Otherwise cancel. The mode stays active for adding further copies.
    */
  def endRotateDrag(): Unit =
    val dragOpt = EditorState.previewState.now().rotateCopyDrag
    clearDrag()
    for
      drag   <- dragOpt
      angle  <- drag.snapped
      tiling  = EditorState.tessellationState.now().currentTiling
      centre <- anchorBigPoint(tiling, drag.centerAnchor)
    do
      OperationRunner.runTilingOp(() =>
        OperationRunner.safely("Error adding rotated copy")(tiling.maybeAddRotatedCopy(centre, angle))
      )(
        onSuccess = TessellationOperations.clearStaleAfterMutation(),
        onFailure = err => ErrorOperations.showError(s"Cannot add rotated copy: ${err.message}")
      )

  // ---- Reflect / Glide reflect ----------------------------------------------------------------------------

  /** Activates the Reflect-copy tool. Anchors (vertices, edge midpoints, face centres) show as dots; the user
    * presses one (axis point A) and drags to another (axis point B) to define the mirror line. No-op while
    * processing or empty.
    */
  def enterReflectCopyMode(): Unit = enterAxisCopyMode(Tool.ReflectCopy)

  /** Activates the Glide-reflect-copy tool. Same two-anchor axis gesture as Reflect, but the copy is
    * reflected across A–B *and* slid along it by the vector B − A (so the A→B direction and length matter).
    */
  def enterGlideReflectCopyMode(): Unit = enterAxisCopyMode(Tool.GlideReflectCopy)

  private def enterAxisCopyMode(tool: Tool): Unit =
    ifNotProcessing:
      if !EditorState.tessellationState.now().currentTiling.isEmpty then
        MeasurementOperations.clearAll()
        clearDrag()
        EditorState.toolState.update(_.copy(activeTool = tool))

  /** Begin a reflect drag: axis point A is the anchor nearest the press; B starts coincident with A
    * (degenerate, no preview) until the user drags.
    */
  def beginReflectDrag(clientX: Double, clientY: Double): Unit =
    beginAxisDrag(clientX, clientY, glide = false)

  /** Begin a glide-reflect drag (same gesture as reflect, flagged as a glide). */
  def beginGlideReflectDrag(clientX: Double, clientY: Double): Unit =
    beginAxisDrag(clientX, clientY, glide = true)

  private def beginAxisDrag(clientX: Double, clientY: Double, glide: Boolean): Unit =
    val tiling = EditorState.tessellationState.now().currentTiling
    if !tiling.isEmpty then
      clientToCanvasView(clientX, clientY).foreach: pressCv =>
        nearestCentre(pressCv, reflectAnchors(tiling)).foreach: (anchor, aCv) =>
          EditorState.previewState.update(
            _.copy(reflectCopyDrag =
              Some(
                ReflectCopyDrag(
                  facePoints = precomputeFacePoints(tiling),
                  axisAnchor = anchor,
                  axisACv = aCv,
                  axisBCv = aCv,
                  snapTarget = None,
                  glide = glide
                )
              )
            )
          )

  /** Update the live reflect drag: axis point B snaps to the nearest anchor (≠ A) within the snap radius; the
    * skeleton mirrors across line A–B. Off any anchor, B follows the cursor (preview only, no commit).
    */
  def updateReflectDrag(clientX: Double, clientY: Double): Unit =
    EditorState.previewState.now().reflectCopyDrag.foreach: drag =>
      clientToCanvasView(clientX, clientY).foreach: currentCv =>

        val tiling     = EditorState.tessellationState.now().currentTiling
        val others     = reflectAnchors(tiling).filterNot { case (anchor, _) =>
          anchor == drag.axisAnchor
        }
        val snapTarget =
          nearestCentre(currentCv, others).filter { case (_, p) =>
            p.distanceTo(currentCv) <= snapRadiusCv
          }
        val bCv        = snapTarget.map((_, p) => p).getOrElse(currentCv)
        EditorState.previewState.update(
          _.copy(reflectCopyDrag = Some(drag.copy(axisBCv = bCv, snapTarget = snapTarget)))
        )

  /** Finish the reflect / glide-reflect drag: if snapped to a second anchor, weld a copy mirrored across the
    * exact line through the two anchors (and, for glide, slid along it by B − A). Otherwise cancel. The mode
    * stays active for adding further copies.
    */
  def endReflectDrag(): Unit =
    val dragOpt = EditorState.previewState.now().reflectCopyDrag
    clearDrag()
    for
      drag         <- dragOpt
      (bAnchor, _) <- drag.snapTarget
      tiling        = EditorState.tessellationState.now().currentTiling
      a            <- anchorBigPoint(tiling, drag.axisAnchor)
      b            <- anchorBigPoint(tiling, bAnchor)
    do
      val (label, op) =
        if drag.glide then
          ("glide-reflected", () => tiling.maybeAddGlideReflectedCopy(a, b))
        else
          ("mirrored", () => tiling.maybeAddMirroredCopy(a, b))
      OperationRunner.runTilingOp(() => OperationRunner.safely(s"Error adding $label copy")(op()))(
        onSuccess = TessellationOperations.clearStaleAfterMutation(),
        onFailure = err => ErrorOperations.showError(s"Cannot add $label copy: ${err.message}")
      )
