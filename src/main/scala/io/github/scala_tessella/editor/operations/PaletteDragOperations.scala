package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.dcel.structure.FaceId
import io.github.scala_tessella.editor.models.{
  AddSubmode, EditorConfig, EditorState, FailedPolygonPlacement, PaletteSnapHint, Tool, VertexCoord,
  ViewTransform
}
import io.github.scala_tessella.editor.operations.TessellationOperations.toCoords
import io.github.scala_tessella.editor.utils.geo.Geometry.buildUnitEdgePolygon
import io.github.scala_tessella.editor.utils.geo.PolygonPlacementGeometry
import io.github.scala_tessella.editor.utils.geo.{Point, Radian}
import io.github.scala_tessella.ring_seq.RingSeq.slidingO

/** Drag-from-palette gesture (Phase 5.6) snap + place support.
  *
  * The palette component drives the gesture (pointerdown/move/up + pointer capture). This object supplies the
  * geometry. During the drag we maintain two parallel previews:
  *
  *   - A free-floating *ghost* polygon that follows the pointer anywhere on the canvas. When near a snappable
  *     edge it borrows that edge's orientation, but its position stays at the cursor. This is what the user
  *     sees.
  *   - The would-be *commit placement* — same shape, glued to the snapped edge — which is held in
  *     `previewState.previewPlacement` so that `commitDragRelease` can hand it to the existing
  *     `PlacementOperations` without recomputing.
  *
  * The hover-edge dotted preview that the click-to-place path renders is suppressed during a drag (see
  * `TessellationRenderer`) so the ghost owns the visible role.
  */
object PaletteDragOperations:

  // No snap radius: while the pointer is over the canvas, the ghost always orients to the *nearest*
  // edge (and that edge becomes the commit target on release). Keying snap on a fixed pixel radius
  // breaks down for large polygons — a 12-gon's centroid sits much further from any candidate edge
  // than a triangle's, and a per-shape radius would still misbehave for irregular shapes whose
  // centroid-to-edge distance varies edge-by-edge. "Always nearest while over the canvas" is the
  // simpler and predictable rule. Cancel by releasing OFF the canvas (e.g. drag back over the
  // palette), or by triggering pointercancel.

  /** Outcome of a single drag step. Either a snap (which both orients the ghost and primes a commit) or a
    * pure ghost (cursor over canvas, no edge in range). Tool guard already passed.
    */
  sealed private trait DragStep
  private case class Snapped(
      placement: FailedPolygonPlacement,
      ghostCanvasView: Vector[Point],
      snapHint: PaletteSnapHint
  ) extends DragStep
  private case class FreeFloating(ghostCanvasView: Vector[Point]) extends DragStep

  /** Result of a successful snap. `intoFace` is set iff the gesture targets an interior edge (Inside
    * sub-mode); otherwise `None` for boundary placement.
    */
  private[operations] case class SnapHit(
      edgeIndex: Int,
      edge: (VertexCoord, VertexCoord),
      intoFace: Option[FaceId],
      distance: Double
  )

  /** Closest point on segment AB to point P, clamped to [A, B]; returns the squared distance. */
  private[operations] def squaredDistanceToSegment(p: Point, a: Point, b: Point): Double =
    val ab      = b - a
    val ap      = p - a
    val ab2     = ab.dot(ab)
    val tRaw    = if ab2 == 0 then 0.0 else ap.dot(ab) / ab2
    val t       = Math.max(0.0, Math.min(1.0, tRaw))
    val closest = a + (ab * t)
    val d       = p - closest
    d.dot(d)

  /** Apply the canvas content-group transform to a tiling-world point: `tiling → canvasView → rotate around
    * canvasCenter → scale → translate by pan`. Mirrors the SVG `transform` attribute built in
    * `EditorCanvasComponent.contentGroup`.
    */
  private[operations] def tilingPointToScreenSvg(
      worldPoint: Point,
      transform: ViewTransform
  ): Point =
    val canvasView = worldPoint.scaleAndTranslate(EditorConfig.canvasScale, EditorConfig.canvasCenter)
    val rotated    = canvasView.rotateAround(
      EditorConfig.canvasCenter,
      Radian.fromDegrees(transform.rotationDegrees)
    )
    rotated * transform.scale + transform.pan

  /** Inverse of the canvas content-group transform: SVG-viewBox screen point → canvas-view (the frame the
    * `<g>` paints into). Same algebra as `TouchEventHandler.screenToWorld`.
    */
  private[operations] def screenSvgToCanvasView(
      screen: Point,
      transform: ViewTransform
  ): Point =
    val rotRad         = Radian.fromDegrees(transform.rotationDegrees)
    val rotationCenter = EditorConfig.canvasCenter
    val p1             = (screen - transform.pan) / transform.scale
    val p2             = p1 - rotationCenter
    val p3             = p2.rotate(rotRad * -1)
    p3 + rotationCenter

  /** Convert a client-pixel pointer position into the SVG viewBox coordinate frame the canvas paints into.
    * Uses the canvas DOM element's bounding rect so it stays correct when the SVG is CSS-sized to something
    * other than its 800×600 viewBox.
    */
  private def clientPointToSvg(clientX: Double, clientY: Double): Option[Point] =
    EditorState.uiState.now().canvasElementRef.map: canvasElement =>

      val rect = canvasElement.getBoundingClientRect()
      val rx   = if rect.width == 0 then 0.0
      else (clientX - rect.left) * (EditorConfig.canvasViewBoxWidth / rect.width)
      val ry   = if rect.height == 0 then 0.0
      else (clientY - rect.top) * (EditorConfig.canvasViewBoxHeight / rect.height)
      Point(rx, ry)

  /** Pick the absolute nearest perimeter edge to `p`. Returns `None` only when the tiling is empty. */
  private[operations] def snapToPerimeter(
      p: Point,
      tiling: TilingDCEL,
      transform: ViewTransform
  ): Option[SnapHit] =
    if tiling.isEmpty then None
    else
      val perimeter = tiling.boundaryVertices.toOption.get.map(_.toCoords).slidingO(2).toList
      perimeter.zipWithIndex.iterator
        .map { case (pair, idx) =>
          val a    = tilingPointToScreenSvg(pair(0).point, transform)
          val b    = tilingPointToScreenSvg(pair(1).point, transform)
          val dist = squaredDistanceToSegment(p, a, b)
          (idx, pair, dist)
        }
        .toList
        .sortBy(_._3)
        .headOption
        .map { case (idx, pair, d2) =>
          SnapHit(edgeIndex = idx, edge = (pair(0), pair(1)), intoFace = None, distance = Math.sqrt(d2))
        }

  /** Inside sub-mode: pick the absolute nearest interior edge across all inner faces, returning the face the
    * snapped edge belongs to so `attemptPolygonInsertion` knows which side to grow into.
    */
  private[operations] def snapToInterior(
      p: Point,
      tiling: TilingDCEL,
      transform: ViewTransform
  ): Option[SnapHit] =
    if tiling.isEmpty then None
    else
      val candidates =
        for
          face        <- tiling.innerFaces.iterator
          vertices    <- tiling.findInnerFaceVertices(face.id).toOption.iterator
          edges        = vertices.map(_.toCoords).slidingO(2).toList
          (pair, idx) <- edges.zipWithIndex.iterator
        yield
          val a    = tilingPointToScreenSvg(pair(0).point, transform)
          val b    = tilingPointToScreenSvg(pair(1).point, transform)
          val dist = squaredDistanceToSegment(p, a, b)
          (face.id, idx, pair, dist)
      candidates
        .toList
        .sortBy(_._4)
        .headOption
        .map { case (faceId, idx, pair, d2) =>
          SnapHit(
            edgeIndex = idx,
            edge = (pair(0), pair(1)),
            intoFace = Some(faceId),
            distance = Math.sqrt(d2)
          )
        }

  /** True when `p` (in SVG viewBox coords) lies inside the canvas viewBox rectangle. Outside the canvas, the
    * gesture stops trying to snap — releasing there cancels the drag.
    */
  private[operations] def isInsideCanvas(p: Point): Boolean =
    p.x >= 0 && p.x <= EditorConfig.canvasViewBoxWidth &&
      p.y >= 0 && p.y <= EditorConfig.canvasViewBoxHeight

  /** Centroid of a non-empty point set (arithmetic mean). */
  private[operations] def centroidOf(points: Vector[Point]): Point =
    if points.isEmpty then Point.origin
    else
      val sum = points.foldLeft(Point.origin)(_ + _)
      sum / points.size.toDouble

  /** Build a polygon's vertices in canvas-view coordinates with default orientation (first edge horizontal,
    * pointing right). Edge length is one tiling-world unit, so we multiply by `canvasScale` to land in
    * canvas-view space.
    */
  private[operations] def defaultGhostVerticesCanvasView(angles: Vector[AngleDegree]): Vector[Point] =
    val local = buildUnitEdgePolygon(
      angles.tail.map(_.supplement.toBigRadian.toBigDecimal.toDouble).map(Radian(_))
    )
    local.map(_ * EditorConfig.canvasScale)

  /** Reposition a polygon's vertices so its centroid lands on `target`. */
  private[operations] def centerOn(target: Point, vertices: Vector[Point]): Vector[Point] =
    val offset = target - centroidOf(vertices)
    vertices.map(_ + offset)

  /** Compute the next drag step: a snap (with edge-aligned ghost + pending placement) or a free-floating
    * ghost. Returns `None` when not in `AddPolygon` (the gesture is then a no-op preview-wise).
    */
  private def computeStep(
      clientX: Double,
      clientY: Double,
      angles: Vector[AngleDegree]
  ): Option[DragStep] =
    val tools = EditorState.toolState.now()
    if tools.activeTool != Tool.AddPolygon then None
    else
      clientPointToSvg(clientX, clientY).map: pointerSvg =>

        val tiling        = EditorState.tessellationState.now().currentTiling
        val transform     = EditorState.viewState.now().viewTransform
        val pointerCanvas = screenSvgToCanvasView(pointerSvg, transform)

        // Off-canvas: free-floating ghost only, no commit target. Lets the user "drag back to the
        // palette" to cancel without forcing them to use Escape / pointercancel.
        val hitOpt =
          if !isInsideCanvas(pointerSvg) then None
          else
            tools.addSubmode match
              case AddSubmode.Outside => snapToPerimeter(pointerSvg, tiling, transform)
              case AddSubmode.Inside  => snapToInterior(pointerSvg, tiling, transform)

        hitOpt match
          case Some(hit) =>
            // The would-be commit placement (glued to edge); also produces oriented vertices we
            // can shift to the cursor for the ghost.
            val placement       = FailedPolygonPlacement(
              edgeIndex = hit.edgeIndex,
              angles = angles,
              edge = hit.edge,
              tiling = tiling,
              intoFace = hit.intoFace
            )
            val placementPoints =
              PolygonPlacementGeometry.computeWireframePoints(angles, hit.edge, tiling, hit.intoFace)
            val ghostShape      =
              if placementPoints.isEmpty then defaultGhostVerticesCanvasView(angles)
              else placementPoints
            // Snap-hint geometry: edge endpoints in canvas-view + a unit normal *perpendicular to
            // the edge*, oriented toward the growth side. Using the true edge perpendicular (not
            // the centroid → midpoint vector) keeps the chevron's base parallel to the edge for
            // irregular shapes too — a rhombus's centroid sits off-axis relative to its
            // attaching edge, which would otherwise skew the chevron. The placement's centroid
            // is consulted only to choose *which* of the two perpendiculars to take.
            val edgeStartCv     = hit.edge._1.point.scaleAndTranslate(
              EditorConfig.canvasScale,
              EditorConfig.canvasCenter
            )
            val edgeEndCv       = hit.edge._2.point.scaleAndTranslate(
              EditorConfig.canvasScale,
              EditorConfig.canvasCenter
            )
            val edgeMidCv       = (edgeStartCv + edgeEndCv) / 2.0
            val edgeTangent     = (edgeEndCv - edgeStartCv).normalized
            val perpCandidate   = edgeTangent.perp
            val growthNormal    =
              if placementPoints.isEmpty then perpCandidate
              else
                val centroidVec = centroidOf(placementPoints) - edgeMidCv
                if perpCandidate.dot(centroidVec) >= 0 then perpCandidate
                else perpCandidate * -1.0
            val snapHint        = PaletteSnapHint(edgeStartCv, edgeEndCv, growthNormal)
            Snapped(placement, centerOn(pointerCanvas, ghostShape), snapHint)
          case None      =>
            FreeFloating(centerOn(pointerCanvas, defaultGhostVerticesCanvasView(angles)))

  /** Apply a drag step to `previewState`: ghost is always set when over the canvas; `previewPlacement` and
    * `paletteSnapHint` are set only when snapped to an edge. The cheap angle-at-endpoints check decides
    * `previewIsValid`; an invalid snap still latches (so the user sees the red preview + dim chevron), but
    * `commitDragRelease` will refuse to place.
    */
  def applyDragStep(clientX: Double, clientY: Double, angles: Vector[AngleDegree]): Unit =
    computeStep(clientX, clientY, angles) match
      case Some(Snapped(placement, ghost, snapHint)) =>
        // Outside-mode snaps target a perimeter edge; check fits there.
        // Inside-mode snaps target an interior edge — there is no boundary wedge to check, so
        // we don't pre-reject. (`attemptPolygonInsertion` remains the safety net.)
        val valid =
          if placement.intoFace.isDefined then true
          else PlacementValidation.fitsAtEdge(placement.tiling, placement.edge, angles)
        EditorState.previewState.update(
          _.copy(
            previewPlacement = Some(placement),
            paletteGhost = Some(ghost),
            paletteSnapHint = Some(snapHint),
            previewIsValid = valid
          )
        )
      case Some(FreeFloating(ghost))                 =>
        EditorState.previewState.update(
          _.copy(
            previewPlacement = None,
            paletteGhost = Some(ghost),
            paletteSnapHint = None,
            previewIsValid = true
          )
        )
      case None                                      =>
        EditorState.previewState.update(
          _.copy(
            previewPlacement = None,
            paletteGhost = None,
            paletteSnapHint = None,
            previewIsValid = true
          )
        )

  /** Release-on-snap: route the latched commit placement to the matching placement operation. The dragged
    * shape's selection (regular sides / irregular index) is applied by `PaletteDragGesture.endDrag`
    * immediately before this fires, so `PlacementOperations` reads the right shape from state.
    *
    * Always clears the ghost and the drag-active flag.
    */
  def commitDragRelease(): Unit =
    val previewBefore = EditorState.previewState.now()
    val placementOpt  = previewBefore.previewPlacement
    val wasValid      = previewBefore.previewIsValid
    EditorState.previewState.update(
      _.copy(
        previewPlacement = None,
        paletteGhost = None,
        paletteSnapHint = None,
        previewIsValid = true
      )
    )
    EditorState.uiState.update(_.copy(isPaletteDragActive = false))
    if wasValid then
      placementOpt.foreach: placement =>
        placement.intoFace match
          case Some(_) =>
            // Inside sub-mode: route through the same insertion path as the click-on-interior-edge flow.
            PlacementOperations.attemptPolygonInsertion(placement.edge._1.id, placement.edge._2.id)
          case None    =>
            // Outside: edgeId is unused inside the operation (its callers just log it on error), so a
            // generated label keeps the call site readable without inventing a registry.
            PlacementOperations.attemptPolygonAddition(
              s"perimeter-edge-${placement.edgeIndex}",
              placement.edgeIndex
            )
    // Invalid drop: silent no-op. The dim chevron + red preview already told the user.

  /** Cancel-without-place: pointer cancelled, gesture interrupted, etc. Clears all transient state. */
  def cancelDrag(): Unit =
    EditorState.previewState.update(
      _.copy(
        previewPlacement = None,
        paletteGhost = None,
        paletteSnapHint = None,
        previewIsValid = true
      )
    )
    EditorState.uiState.update(_.copy(isPaletteDragActive = false))
