package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{AppState, EditorState}

import com.raquo.laminar.api.L.{*, given}

object GridRenderer:
  private val patternId = "grid-pattern"

  // Defines an SVG pattern for the grid
  def patternDef: Element =
    svg.defs(
      svg.pattern(
        svg.idAttr := patternId,
        svg.width := "50",
        svg.height := "50",
        svg.patternUnits := "userSpaceOnUse",
        svg.path(
          svg.d := "M 50 0 L 0 0 0 50",
          svg.fill := "none",
          svg.stroke := "#444",
          // Adjust stroke width based on zoom to keep it visually constant
          svg.strokeWidth <-- EditorState.viewTransform.signal.map(t => (1.0 / t.scale).max(0.1).min(2.0).toString)
        )
      )
    )

  // Renders a large rectangle filled with the grid pattern
  def element: Element =
    svg.g(
      svg.className := "grid-layer",
      svg.opacity := "0.3",
      svg.rect(
        // A very large rectangle to ensure it covers the viewport at all zoom/pan levels
        svg.x := "-20000",
        svg.y := "-20000",
        svg.width := "40000",
        svg.height := "40000",
        svg.fill := s"url(#$patternId)"
      )
    )