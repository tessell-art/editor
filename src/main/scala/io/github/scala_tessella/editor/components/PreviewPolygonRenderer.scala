package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L._
import io.github.scala_tessella.editor.models.{EditorState, FailedPolygonPlacement}
import io.github.scala_tessella.editor.utils.geo.{Point, PolygonPlacementGeometry}

/** Renders the hover preview of the polygon to be inserted. */
object PreviewPolygonRenderer:

  def renderPreview(placement: FailedPolygonPlacement): Element =
    val points =
      PolygonPlacementGeometry
        .computeWireframePoints(placement.angles, placement.edge, placement.tiling, placement.intoFace)
    renderWireframe(points)

  /** Free-floating drag-from-palette ghost: same dotted style as the hover preview, vertices already in
    * canvas-view coordinates so it sits inside the canvas content group with no further transform.
    */
  def renderGhost(points: Vector[Point]): Element =
    renderWireframe(points)

  private def renderWireframe(points: Vector[Point]): Element =
    val pointsStr = points.map { case (x, y) =>
      s"$x,$y"
    }.mkString(" ")
    svg.g(
      svg.polygon(
        svg.points := pointsStr,
        svg.fill   := "none",
        svg.stroke <-- EditorState.overlayPreviewStrokeColor, // theme-aware
        svg.strokeWidth     := "2",
        svg.strokeDashArray := "5,5",
        svg.opacity         := "0.9",
        svg.pointerEvents   := "none",
        svg.className       := "inserter-preview-wireframe"
      )
    )
