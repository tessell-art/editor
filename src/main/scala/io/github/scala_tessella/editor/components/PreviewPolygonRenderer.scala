package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.FailedPolygonPlacement
import io.github.scala_tessella.editor.utils.PolygonPlacementGeometry
import com.raquo.laminar.api.L.{*, given}

/** Renders the hover preview of the polygon to be inserted. */
object PreviewPolygonRenderer:

  def renderPreview(placement: FailedPolygonPlacement): Element =
    val pointsStr =
      PolygonPlacementGeometry
        .computeWireframePoints(placement.polygonSides, placement.edge, placement.tiling)
        .map { case (x, y) => s"$x,$y" }
        .mkString(" ")

    svg.g(
      svg.polygon(
        svg.points := pointsStr,
        svg.fill := "none",
        svg.stroke := "#ffffff",
        svg.strokeWidth := "2",
        svg.strokeDashArray := "5,5",
        svg.opacity := "0.9",
        svg.pointerEvents := "none",
        svg.className := "inserter-preview-wireframe"
      )
    )
