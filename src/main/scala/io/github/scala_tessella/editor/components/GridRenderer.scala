package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L._
import io.github.scala_tessella.editor.models.EditorState

object GridRenderer:
  private val patternId = "grid-pattern"

  // Visual stroke width that stays roughly constant on screen at any zoom level.
  // Inverse of the view scale, clamped to a sensible visible range.
  private[components] def strokeWidthForScale(scale: Double)
      : String = (1.0 / scale).max(0.1).min(2.0).toString

  // Defines an SVG pattern for the grid
  def patternDef: Element =
    svg.defs(
      svg.pattern(
        svg.idAttr       := patternId,
        svg.width        := "50",
        svg.height       := "50",
        svg.patternUnits := "userSpaceOnUse",
        svg.path(
          svg.d      := "M 50 0 L 0 0 0 50",
          svg.fill   := "none",
          svg.stroke := "#444",
          svg.strokeWidth <--
            EditorState.viewState.signal.map(_.viewTransform).distinct.map(t => strokeWidthForScale(t.scale))
        )
      )
    )

  // Renders a large rectangle filled with the grid pattern
  def element: Element =
    svg.g(
      svg.className := "grid-layer",
      svg.opacity   := "0.3",
      svg.rect(
        // A very large rectangle to ensure it covers the viewport at all zoom/pan levels
        svg.x      := "-20000",
        svg.y      := "-20000",
        svg.width  := "40000",
        svg.height := "40000",
        svg.fill   := s"url(#$patternId)"
      )
    )
