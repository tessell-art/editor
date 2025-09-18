package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{FailedPolygonDeletion, FailedPolygonPlacement}
import io.github.scala_tessella.editor.utils.TessellationGeometry.*
import io.github.scala_tessella.editor.utils.PolygonPlacementGeometry

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.BigDecimalGeometry.BigPoint
import io.github.scala_tessella.editor.utils.Geometry.Point
import io.github.scala_tessella.dcel.BigDecimalGeometry.AngleDegree


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

//  def renderFailedDeletion(deletion: FailedPolygonDeletion, coordinates: BigCoords): Element =
//    try {
//      val FailedPolygonDeletion(_, polygonNodes) = deletion
//
//      val points = polygonNodes.map(coordinates).map { vertex =>
//        val (x, y) = tilingPointToCanvasView(vertex.toPoint)
//        s"$x,$y"
//      }.mkString(" ")
//
//      svg.g(
//        svg.polygon(
//          svg.points := points,
//          svg.fill := "none",
//          svg.stroke := "#ff4444",
//          svg.strokeWidth := "3",
//          svg.strokeDashArray := "8,4",
//          svg.opacity := "0.9",
//          svg.className := "failed-deletion-wireframe",
//        )
//      )
//    } catch {
//      case _: Exception => svg.g()
//    }

  private def calculateWireframePoints(placement: FailedPolygonPlacement): Vector[(Double, Double)] =
    PolygonPlacementGeometry.computeWireframePoints(placement.polygonSides, placement.edge, placement.tiling, placement.intoFace)
