package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L._
import io.github.scala_tessella.editor.models.{EditorState, FailedPolygonPlacement}
import io.github.scala_tessella.editor.utils.geo.{Point, PolygonPlacementGeometry}

/** Renders the hover preview of the polygon to be inserted. */
object PreviewPolygonRenderer:

  def renderPreview(placement: FailedPolygonPlacement, valid: Boolean): Element =
    val points =
      PolygonPlacementGeometry
        .computeWireframePoints(placement.angles, placement.edge, placement.tiling, placement.intoFace)
    if valid then renderWireframe(points)
    else renderInvalidWireframe(points)

  /** Free-floating drag-from-palette ghost: same dotted style as the hover preview, vertices already in
    * canvas-view coordinates so it sits inside the canvas content group with no further transform.
    */
  def renderGhost(points: Vector[Point]): Element =
    renderWireframe(points)

  private def renderWireframe(points: Vector[Point]): Element =
    val pointsStr = pointsToString(points)
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

  /** Dotted preview painted in red when the placement is rejected by the cheap angle-at-endpoints check.
    * Distinct className skips the theme-aware stroke binding and the green drop-shadow, mirroring the look of
    * `FailedPolygonRenderer` so the user immediately recognises the "won't fit" state.
    */
  private def renderInvalidWireframe(points: Vector[Point]): Element =
    val pointsStr = pointsToString(points)
    svg.g(
      svg.polygon(
        svg.points          := pointsStr,
        svg.fill            := "none",
        svg.stroke          := "#ff4444",
        svg.strokeWidth     := "2",
        svg.strokeDashArray := "5,5",
        svg.opacity         := "0.9",
        svg.pointerEvents   := "none",
        svg.className       := "inserter-preview-wireframe-invalid"
      )
    )

  private def pointsToString(points: Vector[Point]): String =
    points.map { case (x, y) =>
      s"$x,$y"
    }.mkString(" ")
