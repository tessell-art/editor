package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.models.{EditorState, PaletteSnapHint}
import io.github.scala_tessella.editor.utils.geo.Point

/** Visual cue for the drag-from-palette gesture (Phase 5.6): a small filled chevron sitting on the edge
  * that's currently latched as the commit target, pointing in the *growth direction* — outward for Outside
  * addition, into the snapped face for Inside. The chevron's base is parallel to the edge regardless of
  * polygon shape (the orientation comes from the edge's perpendicular, not from the placement centroid), so a
  * rhombus or other irregular doesn't skew it.
  *
  * Input geometry is in canvas-view coordinates so the result drops straight into the canvas `<g>` content
  * group with no further transform.
  */
object PaletteSnapHintRenderer:

  // Tuning constants in canvas-view units (≈ SVG viewBox px on a 1:1-rendered canvas).
  private val chevronOffset: Double    = 4.0  // distance from edge midpoint to chevron base
  private val chevronLength: Double    = 14.0 // base → apex
  private val chevronHalfWidth: Double = 8.0  // half of base width

  def render(hint: PaletteSnapHint): Element =
    val midpoint   = (hint.edgeStart + hint.edgeEnd) / 2.0
    val normal     = hint.growthNormal
    // The chevron base spans along the edge tangent (perpendicular to the growth normal); apex
    // points in the growth direction.
    val tangent    = normal.perp
    val baseCenter = midpoint + (normal * chevronOffset)
    val apex       = baseCenter + (normal * chevronLength)
    val baseLeft   = baseCenter + (tangent * chevronHalfWidth)
    val baseRight  = baseCenter - (tangent * chevronHalfWidth)

    val chevronPointsStr =
      Vector(apex, baseLeft, baseRight)
        .map { case (x, y) =>
          s"$x,$y"
        }
        .mkString(" ")

    svg.g(
      svg.className     := "palette-snap-hint",
      svg.pointerEvents := "none",
      svg.polygon(
        svg.points  := chevronPointsStr,
        svg.fill <-- EditorState.overlayPreviewStrokeColor,
        svg.opacity := "0.9"
      )
    )
