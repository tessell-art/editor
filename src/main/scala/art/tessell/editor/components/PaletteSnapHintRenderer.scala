package art.tessell.editor.components

import art.tessell.editor.models.{EditorState, PaletteSnapHint}
import art.tessell.editor.utils.geo.Point
import com.raquo.laminar.api.L.*

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

  def render(hint: PaletteSnapHint, valid: Boolean): Element =
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

    // The "disabled" suffix on the className drives the dimmed-chevron CSS for invalid
    // placements; opacity is also halved here so the dim look survives even if the stylesheet
    // is overridden or absent.
    val baseClass         = "palette-snap-hint"
    val classAttrName     = if valid then baseClass else s"$baseClass disabled"
    svg.g(
      svg.className     := classAttrName,
      svg.pointerEvents := "none",
      svg.polygon(
        svg.points  := chevronPointsStr,
        svg.fill <-- EditorState.overlayPreviewStrokeColor,
        svg.opacity := (if valid then "0.9" else "0.35")
      )
    )
