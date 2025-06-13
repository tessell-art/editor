
package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.tessella.{RegularPolygon, Tiling}
import io.github.scala_tessella.tessella.Topology.Edge
import io.github.scala_tessella.editor.models.{AppState, FailedPolygonPlacement}
import io.github.scala_tessella.tessella.Geometry.Point

import scala.math.*

object FailedPolygonRenderer:

  def renderFailedPlacement(placement: FailedPolygonPlacement): Element =
    try {
      val wireframePoints = calculateWireframePoints(placement)
      val points = wireframePoints.map { case (x, y) => s"$x,$y" }.mkString(" ")

      // Find transform origin at the edge midpoint in canvas coordinates
      val FailedPolygonPlacement(_, polygonSides, edge, tiling) = placement
      val vertex1 = tiling.coords(edge.lesserNode)
      val vertex2 = tiling.coords(edge.greaterNode)
      val attachmentX = (vertex1.x + vertex2.x) / 2 * 50 + 400
      val attachmentY = (vertex1.y + vertex2.y) / 2 * 50 + 300

      // Get flip info for animation direction
      val (_, wasFlipped) = calculateOutwardDirectionWithFlipInfo(edge, tiling)
      val scaleValues = if (wasFlipped) "1;0.95;1" else "1;1.05;1"

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
//          svg.animateTransform(
//            svg.attributeName := "transform",
//            svg.attributeType := "XML",
//            svg.typ := "scale",
//            svg.values := scaleValues,
//            svg.dur := "1s",
//            svg.repeatCount := "indefinite"
//          )
        )
      )
    } catch {
      case _: Exception => svg.g()
    }

  private def calculateWireframePoints(placement: FailedPolygonPlacement): Vector[(Double, Double)] =
    import io.github.scala_tessella.tessella.RegularPolygon.Polygon

    val FailedPolygonPlacement(_, polygonSides, edge, tiling) = placement
    val polygon = Polygon(polygonSides)

    // Get the edge coordinates
    val vertex1 = tiling.coords(edge.lesserNode)
    val vertex2 = tiling.coords(edge.greaterNode)

    // Calculate edge vector and length
    val edgeVectorX = vertex2.x - vertex1.x
    val edgeVectorY = vertex2.y - vertex1.y
    val edgeLength = sqrt(edgeVectorX * edgeVectorX + edgeVectorY * edgeVectorY)

    // Normalize edge vector
    val edgeUnitX = edgeVectorX / edgeLength
    val edgeUnitY = edgeVectorY / edgeLength

    // Calculate the correct outward direction and whether we flipped
    val (outwardDirection, wasFlipped) = calculateOutwardDirectionWithFlipInfo(edge, tiling)
    val (perpX, perpY) = outwardDirection

    // Calculate the center of the failed polygon
    val sideLength = edgeLength
    val apothem = sideLength / (2 * tan(Pi / polygonSides))

    val centerX = (vertex1.x + vertex2.x) / 2 + perpX * apothem
    val centerY = (vertex1.y + vertex2.y) / 2 + perpY * apothem

    // Generate polygon vertices around the calculated center
    val angleStep = 2 * Pi / polygonSides
    val direction = if (wasFlipped) -1 else 1
    val radius = sideLength / (2 * sin(Pi / polygonSides))

    // For odd-sided and flipped, add half-step correction
    val parityCorrection =
      if (wasFlipped && polygonSides % 2 == 1) angleStep / 2
      else 0.0
    val extraCorrection =
      if (polygonSides % 2 == 1) angleStep / 2
      else 0.0

    val edgeAngle = atan2(edgeUnitY, edgeUnitX)
    val baseStartAngle = edgeAngle + Pi / 2
    val edgeCenterCorrection = -Pi / polygonSides

    val startAngle =
      baseStartAngle + edgeCenterCorrection + (direction * parityCorrection) + extraCorrection

    (0 until polygonSides).map { i =>
      val angle = startAngle + direction * i * angleStep
      val x = centerX + radius * cos(angle)
      val y = centerY + radius * sin(angle)
      val canvasX = x * 50 + 400
      val canvasY = y * 50 + 300
      (canvasX, canvasY)
    }.toVector

  private def calculateOutwardDirectionWithFlipInfo(edge: Edge, tiling: Tiling): ((Double, Double), Boolean) =
    try {
      val vertex1 = tiling.coords(edge.lesserNode)
      val vertex2 = tiling.coords(edge.greaterNode)

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
      val containingPolygon: Option[tiling.PolygonPath] = tiling.orientedPolygons.find { poly =>
        val polyNodes = poly.toPolygonPathNodes
        val polyEdges = polyNodes.zip(polyNodes.tail :+ polyNodes.head).map { case (n1, n2) =>
          if (n1 < n2) Edge(n1, n2) else Edge(n2, n1)
        }
        polyEdges.contains(edge)
      }

      containingPolygon match {
        case Some(poly) =>
          val polyVertices: Vector[Point] = poly.toPolygonPathNodes.map(tiling.coords)

          // Check which test point is inside the containing polygon
          val point1Inside = isPointInPolygon(testPoint1X, testPoint1Y, polyVertices)
          val point2Inside = isPointInPolygon(testPoint2X, testPoint2Y, polyVertices)

          // Return the direction that points OUTWARD and whether we used the flipped direction
          if (point1Inside) {
            // perp1 points inward, so use perp2 (outward) - this is a flip
            ((perp2X, perp2Y), true)
          } else if (point2Inside) {
            // perp2 points inward, so use perp1 (outward) - this is not a flip
            ((perp1X, perp1Y), false)
          } else {
            // Fallback: neither point is clearly inside, use perp1 without flip
            ((perp1X, perp1Y), false)
          }

        case None =>
          // Fallback if we can't find the containing polygon
          ((perp1X, perp1Y), false)
      }
    } catch {
      case e: Exception =>
        // Fallback to simple perpendicular if calculation fails
        val vertex1 = tiling.coords(edge.lesserNode)
        val vertex2 = tiling.coords(edge.greaterNode)
        val edgeVectorX = vertex2.x - vertex1.x
        val edgeVectorY = vertex2.y - vertex1.y
        val length = sqrt(edgeVectorX * edgeVectorX + edgeVectorY * edgeVectorY)
        ((-edgeVectorY / length, edgeVectorX / length), false)
    }

  // Point-in-polygon test using ray casting algorithm
  private def isPointInPolygon(px: Double, py: Double, vertices: Vector[Point]): Boolean =
    var inside = false
    var j = vertices.length - 1

    for (i <- vertices.indices) {
      val xi = vertices(i).x
      val yi = vertices(i).y
      val xj = vertices(j).x
      val yj = vertices(j).y

      if (((yi > py) != (yj > py)) && (px < (xj - xi) * (py - yi) / (yj - yi) + xi)) {
        inside = !inside
      }
      j = i
    }

    inside