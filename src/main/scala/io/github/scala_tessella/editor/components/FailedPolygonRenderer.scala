package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{FailedPolygonDeletion, FailedPolygonPlacement}
import io.github.scala_tessella.editor.utils.TessellationGeometry.*
import io.github.scala_tessella.editor.utils.PolygonPlacementGeometry

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.BigDecimalGeometry.BigPoint
import io.github.scala_tessella.dcel.{TilingDCEL, VertexId}
import io.github.scala_tessella.tessella.Topology.{Node as TilingNode}
import io.github.scala_tessella.tessella.Geometry.Point
import io.github.scala_tessella.tessella.BigDecimalGeometry.{AngleDegree, BigCoords}

import scala.math.*

object FailedPolygonRenderer:

  def renderFailedPlacement(placement: FailedPolygonPlacement): Element =
    try {
      val wireframePoints = calculateWireframePoints(placement)
      val points = wireframePoints.map { case (x, y) => s"$x,$y" }.mkString(" ")

      svg.g(
        // Use CSS for transform origin so animation will scale from edge
        //        svg.style := s"transform-origin: $attachmentX $attachmentY;",
        svg.polygon(
          svg.points := points,
          svg.fill := "none",
          svg.stroke := "#ff4444",
          svg.strokeWidth := "2",
          svg.strokeDashArray := "5,5",
          svg.opacity := "0.8",
          svg.className := "failed-polygon-wireframe",
        )
      )
    } catch {
      case _: Exception => svg.g()
    }

  def renderFailedDeletion(deletion: FailedPolygonDeletion, coordinates: BigCoords): Element =
    try {
      val FailedPolygonDeletion(_, polygonNodes) = deletion

      val points = polygonNodes.map(coordinates).map { vertex =>
        val (x, y) = tilingPointToCanvasView(vertex.toPoint)
        s"$x,$y"
      }.mkString(" ")

      svg.g(
        svg.polygon(
          svg.points := points,
          svg.fill := "none",
          svg.stroke := "#ff4444",
          svg.strokeWidth := "3",
          svg.strokeDashArray := "8,4",
          svg.opacity := "0.9",
          svg.className := "failed-deletion-wireframe",
        )
      )
    } catch {
      case _: Exception => svg.g()
    }

  extension (bigPoint: BigPoint)

    def toPoint: Point =
      Point(bigPoint.x.toDouble, bigPoint.y.toDouble)

  private def calculateWireframePoints(placement: FailedPolygonPlacement): Vector[(Double, Double)] =
    PolygonPlacementGeometry.computeWireframePoints(placement.polygonSides, placement.edge, placement.tiling, placement.intoFace)

  //    // Get the edge coordinates
//    val vertex1 = tiling.coordinates(edge._1).toPoint
//    val vertex2 = tiling.coordinates(edge._2).toPoint
//
//    // Calculate edge vector and length
//    val edgeVectorX = vertex2.x - vertex1.x
//    val edgeVectorY = vertex2.y - vertex1.y
//    val edgeLength = sqrt(edgeVectorX * edgeVectorX + edgeVectorY * edgeVectorY)
//
//    // Normalize edge vector
//    val edgeUnitX = edgeVectorX / edgeLength
//    val edgeUnitY = edgeVectorY / edgeLength
//
//    // Calculate the correct outward direction and whether we flipped
//    val (outwardDirection, wasFlipped) = calculateOutwardDirectionWithFlipInfo(edge, tiling)
//    val (perpX, perpY) = outwardDirection
//
//    val halfAngleStep: AngleDegree = AngleDegree(180) / polygonSides
//
//    // Calculate the center of the failed polygon
//    val sideLength = edgeLength
//    val apothem = sideLength / (2 * tan(halfAngleStep.toBigRadian.toBigDecimal.toDouble))
//
//    val centerX = (vertex1.x + vertex2.x) / 2 + perpX * apothem
//    val centerY = (vertex1.y + vertex2.y) / 2 + perpY * apothem
//
//    // Generate polygon vertices around the calculated center
//    val angleStep: AngleDegree = halfAngleStep * 2
//    val radius = sideLength / (2 * sin(halfAngleStep.toBigRadian.toBigDecimal.toDouble))
//    val winding = if wasFlipped then -1 else 1
//    val edgeAngle = atan2(edgeUnitY, edgeUnitX)
//    val isOdd = polygonSides % 2 == 1
//
//    val baseOffset: AngleDegree = AngleDegree(90) - halfAngleStep
//    val oddHalfStep = if isOdd && !wasFlipped then halfAngleStep else AngleDegree(0)
//
//    val startAngle = edgeAngle + (baseOffset + oddHalfStep).toBigRadian.toBigDecimal.toDouble
//
//    (0 until polygonSides).map { i =>
//      val angle = startAngle + (angleStep * winding * i).toBigRadian.toBigDecimal.toDouble
//      val x = centerX + radius * cos(angle)
//      val y = centerY + radius * sin(angle)
//      tilingPointToCanvasView(Point(x, y))
//    }.toVector

  private def calculateOutwardDirectionWithFlipInfo(edge: (VertexId, VertexId), tiling: TilingDCEL): ((Double, Double), Boolean) =
    try
      val vertex1 = tiling.coordinates(edge._1).toPoint
      val vertex2 = tiling.coordinates(edge._2).toPoint

      // Calculate edge vector
      val edgeVectorX = vertex2.x - vertex1.x
      val edgeVectorY = vertex2.y - vertex1.y
      val edgeLength = sqrt(edgeVectorX * edgeVectorX + edgeVectorY * edgeVectorY)

      // Calculate both possible perpendicular directions (normalized)
      val perp1X = -edgeVectorY / edgeLength  // First perpendicular
      val perp1Y = edgeVectorX / edgeLength
      val perp2X = edgeVectorY / edgeLength   // Second perpendicular (opposite of first)
      val perp2Y = -edgeVectorX / edgeLength

      // Calculate edge midpoint
      val midX = (vertex1.x + vertex2.x) / 2
      val midY = (vertex1.y + vertex2.y) / 2

      // Test points: move half unit in each perpendicular direction from edge midpoint
      val testPoint1X = midX + perp1X * 0.5
      val testPoint1Y = midY + perp1Y * 0.5
      val testPoint2X = midX + perp2X * 0.5
      val testPoint2Y = midY + perp2Y * 0.5

      // Find the polygon that contains this perimeter edge
      val containingPolygon: Option[Vector[TilingNode]] = None
//        tiling.orientedPolygons.find { polyNodes =>
//          val polyEdges = polyNodes.zip(polyNodes.tail :+ polyNodes.head).map { case (n1, n2) => Edge(n2, n1) }
//          polyEdges.contains(edge)
//        }

      containingPolygon match
        case Some(polyNodes) =>
//          val polyVertices: Vector[Point] = polyNodes.map(tiling.coordinates).map(_.toPoint)
//
//          // Check which test point is inside the containing polygon
//          val point1Inside = isPointInPolygon(testPoint1X, testPoint1Y, polyVertices)
//          val point2Inside = isPointInPolygon(testPoint2X, testPoint2Y, polyVertices)
//
//          // Return the direction that points OUTWARD and whether we used the flipped direction
//          if point1Inside then
//            // perp1 points inward, so use perp2 (outward) - this is a flip
//            ((perp2X, perp2Y), true)
//          else if point2Inside then
//            // perp2 points inward, so use perp1 (outward) - this is not a flip
//            ((perp1X, perp1Y), false)
//          else
//            // Fallback: neither point is clearly inside, use perp1 without flip
            ((perp1X, perp1Y), false)

        case None =>
          // Fallback if we can't find the containing polygon
          ((perp1X, perp1Y), false)
    catch
      case e: Exception =>
        // Fallback to simple perpendicular if calculation fails
        val vertex1 = tiling.coordinates(edge._1).toPoint
        val vertex2 = tiling.coordinates(edge._2).toPoint
        val edgeVectorX = vertex2.x - vertex1.x
        val edgeVectorY = vertex2.y - vertex1.y
        val length = sqrt(edgeVectorX * edgeVectorX + edgeVectorY * edgeVectorY)
        ((-edgeVectorY / length, edgeVectorX / length), false)

  // Point-in-polygon test using ray casting algorithm
  private def isPointInPolygon(px: Double, py: Double, vertices: Vector[Point]): Boolean =
    var inside = false
    var j = vertices.length - 1

    for (i <- vertices.indices)
      val xi = vertices(i).x
      val yi = vertices(i).y
      val xj = vertices(j).x
      val yj = vertices(j).y

      if ((yi > py) != (yj > py)) && (px < (xj - xi) * (py - yi) / (yj - yi) + xi) then
        inside = !inside
      j = i

    inside