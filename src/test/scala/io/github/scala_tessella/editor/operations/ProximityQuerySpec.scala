package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.{Anchor, EditorState, Tool, ViewTransform}
import io.github.scala_tessella.editor.utils.TilingBuilders
import io.github.scala_tessella.editor.utils.geo.Point
import munit.FunSuite

class ProximityQuerySpec extends FunSuite with EditorStateFixture:

  test("allClickablePoints returns 9 points for a square (4 vertices + 4 midpoints + 1 center)") {
    val tiling = TilingBuilders.freshSquare()
    val points = ProximityQuery.allClickablePoints(tiling)
    assertEquals(points.size, 9)
  }

  test("allClickablePoints returns 7 points for a triangle (3 vertices + 3 midpoints + 1 center)") {
    val tiling = TilingBuilders.freshTriangle()
    val points = ProximityQuery.allClickablePoints(tiling)
    assertEquals(points.size, 7)
  }

  test("allClickablePoints returns empty list for empty tiling") {
    val points = ProximityQuery.allClickablePoints(TilingDCEL.empty)
    assertEquals(points, Nil)
  }

  test("allClickablePoints contains all anchor types for a square") {
    val tiling = TilingBuilders.freshSquare()
    val points = ProximityQuery.allClickablePoints(tiling)

    val vertexCount   = points.count(_.anchor match { case Anchor.Vertex(_) => true; case _ => false })
    val midPointCount = points.count(_.anchor match { case Anchor.MidPoint(_, _) => true; case _ => false })
    val centerCount   = points.count(_.anchor match { case Anchor.Center(_) => true; case _ => false })

    assertEquals(vertexCount, 4)
    assertEquals(midPointCount, 4)
    assertEquals(centerCount, 1)
  }

  test("nearbyPoints returns empty when pointer is far from all points") {
    val tiling    = TilingBuilders.freshSquare()
    val allPoints = ProximityQuery.allClickablePoints(tiling)
    val transform = ViewTransform()
    // Very far away in SVG space
    val farPoint  = Point(-9999.0, -9999.0)
    val nearby    = ProximityQuery.nearbyPoints(farPoint, allPoints, transform, 30.0)
    assertEquals(nearby, Nil)
  }

  test("nearbyPoints returns points within radius") {
    val tiling     = TilingBuilders.freshSquare()
    val allPoints  = ProximityQuery.allClickablePoints(tiling)
    val transform  = ViewTransform()
    // Project the first point to screen to get a position near it
    val firstPoint = allPoints.head
    val screenPos  = PaletteDragOperations.tilingPointToScreenSvg(firstPoint.point, transform)
    // Offset slightly within radius
    val nearPos    = screenPos + Point(5.0, 0.0)
    val nearby     = ProximityQuery.nearbyPoints(nearPos, allPoints, transform, 30.0)
    assert(nearby.nonEmpty, "Expected at least one nearby point")
    assert(nearby.contains(firstPoint), "Expected the closest point to be in results")
  }

  test("nearbyPoints respects radius — tight radius excludes distant points") {
    val tiling      = TilingBuilders.freshSquare()
    val allPoints   = ProximityQuery.allClickablePoints(tiling)
    val transform   = ViewTransform()
    val firstPoint  = allPoints.head
    val screenPos   = PaletteDragOperations.tilingPointToScreenSvg(firstPoint.point, transform)
    // Offset by 20 units
    val offsetPos   = screenPos + Point(20.0, 0.0)
    // Radius 10 should miss the point, radius 25 should hit it
    val tightNearby = ProximityQuery.nearbyPoints(offsetPos, allPoints, transform, 10.0)
    val wideNearby  = ProximityQuery.nearbyPoints(offsetPos, allPoints, transform, 25.0)
    assert(!tightNearby.contains(firstPoint), "Tight radius should not contain the point")
    assert(wideNearby.contains(firstPoint), "Wide radius should contain the point")
  }

  test("Eraser polygon click should NOT populate clickable points (ADR-013)") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id

    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.toolState.update(_.copy(activeTool = Tool.Eraser))

    SelectionOperations.handleTilingPolygonClick(faceId)

    assertEquals(EditorState.measurementState.now().clickablePoints, Nil)
    assertEquals(EditorState.measurementState.now().highlightedPolygonId, None)
  }
