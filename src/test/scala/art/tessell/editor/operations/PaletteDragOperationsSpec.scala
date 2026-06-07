package art.tessell.editor.operations

import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.dcel.TilingDCEL
import TessellationOperations.toCoords
import art.tessell.editor.models.{EditorConfig, ViewTransform}
import art.tessell.editor.utils.TilingBuilders
import art.tessell.editor.utils.geo.Point
import munit.FunSuite

/** Pure-geometry tests for `PaletteDragOperations`. The gesture itself is covered by the e2e suite (real
  * pointer events, real layout); here we lock the helpers it relies on so a regression in the snap geometry,
  * the canvas transform, or the inside/outside check fails fast and points at the actual line rather than at
  * "the drag doesn't work in the browser".
  */
class PaletteDragOperationsSpec extends FunSuite:

  private val eps = 1e-9

  // ---------- squaredDistanceToSegment ----------

  test("squaredDistanceToSegment returns 0 when the point is at A") {
    val d = PaletteDragOperations.squaredDistanceToSegment(Point(0, 0), Point(0, 0), Point(10, 0))
    assertEqualsDouble(d, 0.0, eps)
  }

  test("squaredDistanceToSegment returns 0 when the point is at B") {
    val d = PaletteDragOperations.squaredDistanceToSegment(Point(10, 0), Point(0, 0), Point(10, 0))
    assertEqualsDouble(d, 0.0, eps)
  }

  test("squaredDistanceToSegment clamps past A: distance to A, not the infinite line") {
    // Point (-3, 4) projects to t = -0.3 on AB; clamp to A=(0,0) → distance² = 9 + 16 = 25.
    val d = PaletteDragOperations.squaredDistanceToSegment(Point(-3, 4), Point(0, 0), Point(10, 0))
    assertEqualsDouble(d, 25.0, eps)
  }

  test("squaredDistanceToSegment clamps past B: distance to B, not the infinite line") {
    // Point (13, 4) projects past B; clamp to B=(10,0) → distance² = 9 + 16 = 25.
    val d = PaletteDragOperations.squaredDistanceToSegment(Point(13, 4), Point(0, 0), Point(10, 0))
    assertEqualsDouble(d, 25.0, eps)
  }

  test("squaredDistanceToSegment uses perpendicular foot when the projection is interior") {
    val d = PaletteDragOperations.squaredDistanceToSegment(Point(5, 4), Point(0, 0), Point(10, 0))
    assertEqualsDouble(d, 16.0, eps)
  }

  test("squaredDistanceToSegment handles degenerate (A==B) without dividing by zero") {
    val d = PaletteDragOperations.squaredDistanceToSegment(Point(3, 4), Point(7, 1), Point(7, 1))
    // Distance from (3,4) to (7,1): √((-4)²+3²) = 5, squared = 25.
    assertEqualsDouble(d, 25.0, eps)
  }

  // ---------- centroidOf ----------

  test("centroidOf empty point set returns the origin") {
    assertEquals(PaletteDragOperations.centroidOf(Vector.empty), Point.origin)
  }

  test("centroidOf single point returns that point") {
    val p = Point(3, 5)
    assertEquals(PaletteDragOperations.centroidOf(Vector(p)), p)
  }

  test("centroidOf is the arithmetic mean of the points") {
    val pts = Vector(Point(0, 0), Point(6, 0), Point(0, 6))
    val c   = PaletteDragOperations.centroidOf(pts)
    assertEqualsDouble(c.x, 2.0, eps)
    assertEqualsDouble(c.y, 2.0, eps)
  }

  // ---------- isInsideCanvas ----------

  test("isInsideCanvas: a point at canvas centre is inside") {
    assert(PaletteDragOperations.isInsideCanvas(EditorConfig.canvasCenter))
  }

  test("isInsideCanvas: rectangle is closed (boundary points are inside)") {
    // Locks the closed-rectangle contract — switching to strict inequalities would silently
    // change the cancellation semantics at the edges.
    assert(PaletteDragOperations.isInsideCanvas(Point(0, 0)))
    assert(PaletteDragOperations.isInsideCanvas(Point(EditorConfig.canvasViewBoxWidth, 0)))
    assert(PaletteDragOperations.isInsideCanvas(Point(0, EditorConfig.canvasViewBoxHeight)))
    assert(
      PaletteDragOperations.isInsideCanvas(
        Point(EditorConfig.canvasViewBoxWidth, EditorConfig.canvasViewBoxHeight)
      )
    )
  }

  test("isInsideCanvas: points outside any edge are rejected") {
    assert(!PaletteDragOperations.isInsideCanvas(Point(-0.1, 100)))
    assert(!PaletteDragOperations.isInsideCanvas(Point(EditorConfig.canvasViewBoxWidth + 0.1, 100)))
    assert(!PaletteDragOperations.isInsideCanvas(Point(100, -0.1)))
    assert(!PaletteDragOperations.isInsideCanvas(Point(100, EditorConfig.canvasViewBoxHeight + 0.1)))
  }

  // ---------- tilingPointToScreenSvg ↔ screenSvgToCanvasView round-trip ----------

  /** `screenSvgToCanvasView` is the inverse of the rotate-scale-translate steps applied AFTER the
    * tiling→canvasView mapping. So the round-trip should land back in canvas-view, i.e. the result of
    * `worldPoint.scaleAndTranslate(canvasScale, canvasCenter)`.
    */
  private def assertRoundTrip(world: Point, transform: ViewTransform): Unit =
    val screen    = PaletteDragOperations.tilingPointToScreenSvg(world, transform)
    val recovered = PaletteDragOperations.screenSvgToCanvasView(screen, transform)
    val expected  = world.scaleAndTranslate(EditorConfig.canvasScale, EditorConfig.canvasCenter)
    assertEqualsDouble(recovered.x, expected.x, 1e-6)
    assertEqualsDouble(recovered.y, expected.y, 1e-6)

  test("transform round-trip: identity transform preserves canvas-view image of any world point") {
    val t = ViewTransform()
    assertRoundTrip(Point.origin, t)
    assertRoundTrip(Point(1, 0), t)
    assertRoundTrip(Point(-2.5, 7.3), t)
  }

  test("transform round-trip: pan-only transform is invertible") {
    val t = ViewTransform(pan = Point(123, -45))
    assertRoundTrip(Point(1, 1), t)
    assertRoundTrip(Point(-3, 4), t)
  }

  test("transform round-trip: scale-only transform is invertible") {
    val t = ViewTransform(scale = 2.5)
    assertRoundTrip(Point(1, 0), t)
    assertRoundTrip(Point(-1, -1), t)
  }

  test("transform round-trip: rotation-only transform is invertible") {
    // 90° and 180° give clean integer canvas-view coords for easy debugging if this fails.
    assertRoundTrip(Point(1, 0), ViewTransform(rotationDegrees = 90))
    assertRoundTrip(Point(-2, 3), ViewTransform(rotationDegrees = 180))
  }

  test("transform round-trip: combined scale + rotate + pan is invertible") {
    val t = ViewTransform(scale = 1.7, rotationDegrees = 45, pan = Point(10, 20))
    assertRoundTrip(Point(1, 0), t)
    assertRoundTrip(Point(-2, 3), t)
    assertRoundTrip(Point(0, 0), t)
  }

  // ---------- defaultGhostVerticesCanvasView ----------

  test("defaultGhostVerticesCanvasView produces one vertex per angle") {
    val triAngles = Vector.fill(3)(AngleDegree(60))
    val hexAngles = Vector.fill(6)(AngleDegree(120))
    assertEquals(PaletteDragOperations.defaultGhostVerticesCanvasView(triAngles).size, 3)
    assertEquals(PaletteDragOperations.defaultGhostVerticesCanvasView(hexAngles).size, 6)
  }

  test("defaultGhostVerticesCanvasView scales the unit-edge polygon by canvasScale") {
    // First edge of the unit-edge polygon goes from (0,0) to (1,0); after scaling by canvasScale,
    // the second vertex should be at (canvasScale, 0). This locks the canvas-view coordinate frame.
    val tri = PaletteDragOperations.defaultGhostVerticesCanvasView(Vector.fill(3)(AngleDegree(60)))
    assertEqualsDouble(tri(0).x, 0.0, eps)
    assertEqualsDouble(tri(0).y, 0.0, eps)
    assertEqualsDouble(tri(1).x, EditorConfig.canvasScale, eps)
    assertEqualsDouble(tri(1).y, 0.0, eps)
  }

  // ---------- centerOn ----------

  test("centerOn: the centroid of the result equals the target") {
    val verts  = Vector(Point(0, 0), Point(10, 0), Point(0, 10))
    val target = Point(100, 50)
    val moved  = PaletteDragOperations.centerOn(target, verts)
    val c      = PaletteDragOperations.centroidOf(moved)
    assertEqualsDouble(c.x, target.x, eps)
    assertEqualsDouble(c.y, target.y, eps)
  }

  test("centerOn preserves shape (pairwise distances unchanged)") {
    val verts = Vector(Point(0, 0), Point(10, 0), Point(0, 10))
    val moved = PaletteDragOperations.centerOn(Point(100, 50), verts)
    for i <- verts.indices; j <- verts.indices if i < j do
      val before = (verts(i) - verts(j)).dot(verts(i) - verts(j))
      val after  = (moved(i) - moved(j)).dot(moved(i) - moved(j))
      assertEqualsDouble(after, before, eps)
  }

  // ---------- snapToPerimeter ----------

  test("snapToPerimeter returns None on an empty tiling") {
    val result = PaletteDragOperations.snapToPerimeter(Point(100, 100), TilingDCEL.empty, ViewTransform())
    assertEquals(result, None)
  }

  test("snapToPerimeter on a single hexagon: hits one of 6 perimeter edges with intoFace=None") {
    val result = PaletteDragOperations.snapToPerimeter(
      EditorConfig.canvasCenter,
      TilingBuilders.hexagon,
      ViewTransform()
    )
    val hit    = result.getOrElse(fail("expected a snap on a non-empty tiling"))
    assert(hit.intoFace.isEmpty, "perimeter snap must not carry a face id")
    assert(hit.edgeIndex >= 0 && hit.edgeIndex < 6, s"edgeIndex out of range: ${hit.edgeIndex}")
    assert(hit.distance >= 0.0)
  }

  test("snapToPerimeter picks the absolute nearest edge: query near each edge's midpoint hits that edge") {
    // Iterate every perimeter edge; place the query exactly at its screen-space midpoint and assert
    // the snap returns that edge's index. This locks the "always nearest, no fixed snap radius"
    // policy — switching to a fixed radius would still satisfy the previous test but break this one
    // by leaving distant edges unmatched even when they're the nearest candidate.
    val tiling = TilingBuilders.hexagon
    val t      = ViewTransform()
    val coords = tiling.boundaryVertices.toOption.get.map(_.toCoords)
    val edges  = coords
      .map(_.point)
      .map(p => PaletteDragOperations.tilingPointToScreenSvg(p, t))
      .toVector
    for i <- edges.indices do
      val a   = edges(i)
      val b   = edges((i + 1) % edges.size)
      val mid = (a + b) / 2.0
      val hit = PaletteDragOperations.snapToPerimeter(mid, tiling, t)
        .getOrElse(fail(s"expected a snap at edge $i midpoint"))
      assertEquals(hit.edgeIndex, i, s"midpoint of edge $i snapped to edge ${hit.edgeIndex}")
  }

  // ---------- snapToInterior ----------

  test("snapToInterior returns None on an empty tiling") {
    val result = PaletteDragOperations.snapToInterior(Point(100, 100), TilingDCEL.empty, ViewTransform())
    assertEquals(result, None)
  }

  test("snapToInterior on a non-empty tiling carries the face id of the snapped edge") {
    val tiling = TilingBuilders.hexagon
    val face   = tiling.innerFaces.headOption.getOrElse(fail("hexagon must have an inner face"))
    val result = PaletteDragOperations.snapToInterior(
      EditorConfig.canvasCenter,
      tiling,
      ViewTransform()
    )
    val hit    = result.getOrElse(fail("expected a snap on a non-empty tiling"))
    assertEquals(
      hit.intoFace,
      Some(face.id),
      "interior snap must carry the face id so attemptPolygonInsertion knows which side to grow into"
    )
  }
