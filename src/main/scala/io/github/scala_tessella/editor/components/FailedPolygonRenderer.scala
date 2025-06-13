package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.tessella.{RegularPolygon, Tiling}
import io.github.scala_tessella.tessella.Topology.Edge
import io.github.scala_tessella.editor.models.{AppState, FailedPolygonPlacement}
import scala.math._

object FailedPolygonRenderer:
  
  def renderFailedPlacement(placement: FailedPolygonPlacement): Element =
    try {
      val wireframePoints = calculateWireframePoints(placement)
      
      val points = wireframePoints.map { case (x, y) =>
        s"$x,$y"
      }.mkString(" ")

      svg.polygon(
        svg.points := points,
        svg.fill := "none",
        svg.stroke := "#ff4444",
        svg.strokeWidth := "2",
        svg.strokeDashArray := "5,5",
        svg.opacity := "0.8",
        svg.className := "failed-polygon-wireframe",
        // Add a subtle animation to make it more noticeable
        svg.animateTransform(
          svg.attributeName := "transform",
          svg.attributeType := "XML",
          svg.typ := "scale",
          svg.values := "1;1.05;1",
          svg.dur := "1s",
          svg.repeatCount := "indefinite"
        )
      )
    } catch {
      case e: Exception =>
        // If we can't calculate the wireframe, return empty element
        svg.g()
    }

  private def calculateWireframePoints(placement: FailedPolygonPlacement): Vector[(Double, Double)] =
    import io.github.scala_tessella.tessella.RegularPolygon.Polygon
    
    val FailedPolygonPlacement(_, polygonSides, edge, tiling) = placement
    val polygon = Polygon(polygonSides)
    
    // Get the edge coordinates
    val vertex1 = tiling.coords(edge.lesserNode)
    val vertex2 = tiling.coords(edge.greaterNode)
    
    // Calculate edge vector and perpendicular
    val edgeVectorX = vertex2.x - vertex1.x
    val edgeVectorY = vertex2.y - vertex1.y
    val edgeLength = sqrt(edgeVectorX * edgeVectorX + edgeVectorY * edgeVectorY)
    
    // Normalize edge vector
    val edgeUnitX = edgeVectorX / edgeLength
    val edgeUnitY = edgeVectorY / edgeLength
    
    // Perpendicular vector (pointing "outward" from the tiling)
    val perpX = -edgeUnitY
    val perpY = edgeUnitX
    
    // Calculate the center of the failed polygon
    // This is an approximation - place it at a reasonable distance from the edge
    val sideLength = edgeLength
    val apothem = sideLength / (2 * tan(Pi / polygonSides))
    
    val centerX = (vertex1.x + vertex2.x) / 2 + perpX * apothem
    val centerY = (vertex1.y + vertex2.y) / 2 + perpY * apothem
    
    // Generate polygon vertices around the calculated center
    val angleStep = 2 * Pi / polygonSides
    val radius = sideLength / (2 * sin(Pi / polygonSides))
    
    // Start angle to align one edge with the perimeter edge
    val startAngle = atan2(edgeUnitY, edgeUnitX) + Pi / 2
    
    (0 until polygonSides).map { i =>
      val angle = startAngle + i * angleStep
      val x = centerX + radius * cos(angle)
      val y = centerY + radius * sin(angle)
      
      // Convert to canvas coordinates
      val canvasX = x * 50 + 400
      val canvasY = y * 50 + 300
      
      (canvasX, canvasY)
    }.toVector