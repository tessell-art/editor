package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.editor.models.{Anchor, ClickablePoint, EditorState, ViewTransform}
import io.github.scala_tessella.editor.utils.geo.TessellationGeometry.toPoint
import io.github.scala_tessella.editor.utils.geo.{LineSegment, Point}
import io.github.scala_tessella.ring_seq.RingSeq.slidingO
import org.scalajs.dom

/** Proximity-based point detection for the eraser tool (ADR-013 Option C).
  *
  * Computes all deletable points (vertices, edge midpoints, face centers) for the entire tiling and finds
  * those within a screen-space radius of the pointer. Points are stored in tiling-world coordinates (same
  * convention as `setupFaceClickablePoints`), but distance comparisons happen in SVG-screen space so the
  * radius means "pixels on screen" regardless of zoom.
  */
object EraserProximityQuery:

  /** Proximity radius in SVG viewBox units for mouse interaction. */
  private[operations] val MOUSE_RADIUS: Double = 30.0

  /** Proximity radius in SVG viewBox units for touch interaction (larger for finger imprecision). */
  private[operations] val TOUCH_RADIUS: Double = 50.0

  /** All clickable points (vertices, edge midpoints, face centers) for every inner face in the tiling. Points
    * are in tiling-world coordinates.
    */
  def allClickablePoints(tiling: TilingDCEL): List[ClickablePoint] =
    if tiling.isEmpty then Nil
    else
      tiling.innerFacesVertices.flatMap: (faceId, faceVertices) =>

        val vertices           = faceVertices.map(_.coords).map(_.toPoint)
        val vertexIdsAndPoints = faceVertices.map(_.id).zip(vertices)

        val vertexPoints =
          vertexIdsAndPoints.map: (vertexId, point) =>
            ClickablePoint(point, Anchor.Vertex(vertexId))

        val edges     = vertexIdsAndPoints.toVector.slidingO(2).toList
        val midPoints =
          edges.map: edge =>

            val p1 = edge(0)._2
            val p2 = edge(1)._2
            ClickablePoint(LineSegment(p1, p2).midPoint, Anchor.MidPoint(edge(0)._1, edge(1)._1))

        val centerX     = vertices.map(_.x).sum / vertices.size
        val centerY     = vertices.map(_.y).sum / vertices.size
        val centerPoint = ClickablePoint(Point(centerX, centerY), Anchor.Center(faceId))

        centerPoint :: vertexPoints ++ midPoints

  /** Finds points within the given SVG-screen-space radius of a pointer position.
    *
    * @param svgPointerPos
    *   pointer position in SVG viewBox coordinates
    * @param allPoints
    *   all clickable points in tiling-world coordinates
    * @param transform
    *   current view transform (pan, scale, rotation)
    * @param radius
    *   proximity radius in SVG viewBox units
    * @return
    *   clickable points within the radius
    */
  def nearbyPoints(
      svgPointerPos: Point,
      allPoints: List[ClickablePoint],
      transform: ViewTransform,
      radius: Double
  ): List[ClickablePoint] =
    val radiusSq = radius * radius
    allPoints.filter: cp =>

      val screenPos = PaletteDragOperations.tilingPointToScreenSvg(cp.point, transform)
      val d         = svgPointerPos - screenPos
      d.dot(d) <= radiusSq

  /** Converts browser client-pixel coordinates to SVG viewBox coordinates using the SVG element's own
    * coordinate mapping (`getScreenCTM`). This correctly handles `preserveAspectRatio` letterboxing, CSS
    * sizing, and any other transforms between client space and the viewBox.
    */
  def clientToSvg(clientX: Double, clientY: Double): Option[Point] =
    EditorState.uiState.now().canvasElementRef.flatMap: canvasElement =>

      val svgEl = canvasElement.asInstanceOf[dom.SVGSVGElement]
      val ctm   = svgEl.getScreenCTM()
      if ctm == null then None
      else
        val pt    = svgEl.createSVGPoint()
        pt.x = clientX
        pt.y = clientY
        val svgPt = pt.matrixTransform(ctm.inverse())
        Some(Point(svgPt.x, svgPt.y))

  /** Tight radius in SVG viewBox units for direct-hit tap-to-delete on touch. */
  private[operations] val DIRECT_HIT_RADIUS: Double = 12.0

  /** Finds a single clickable point that is a direct hit (within tight radius) of the pointer. Returns `Some`
    * only if exactly one point matches — ambiguous hits return `None`.
    */
  def findDirectHit(clientX: Double, clientY: Double): Option[ClickablePoint] =
    clientToSvg(clientX, clientY).flatMap: svgPos =>

      val transform   = EditorState.viewState.now().viewTransform
      val clickable   = EditorState.measurementState.now().clickablePoints
      val directHitSq = DIRECT_HIT_RADIUS * DIRECT_HIT_RADIUS
      val hits        = nearbyPoints(svgPos, clickable, transform, DIRECT_HIT_RADIUS)
      hits match
        case single :: Nil => Some(single)
        case _             => None

  /** Updates `EditorState.measurementState.clickablePoints` with points near the pointer. Called from
    * mouse/touch handlers when the eraser tool is active.
    *
    * @param clientX
    *   browser client X coordinate
    * @param clientY
    *   browser client Y coordinate
    * @param isTouch
    *   true for touch input (uses larger radius)
    */
  def updateNearbyPoints(clientX: Double, clientY: Double, isTouch: Boolean): Unit =
    clientToSvg(clientX, clientY).foreach: svgPos =>

      val tiling    = EditorState.tessellationState.now().currentTiling
      val transform = EditorState.viewState.now().viewTransform
      val radius    = if isTouch then TOUCH_RADIUS else MOUSE_RADIUS
      val all       = allClickablePoints(tiling)
      val nearby    = nearbyPoints(svgPos, all, transform, radius)
      EditorState.measurementState.update(_.copy(
        clickablePoints = nearby,
        highlightedPolygonId = None
      ))
