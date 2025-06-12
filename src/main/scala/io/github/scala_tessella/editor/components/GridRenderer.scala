package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.tessella.Tiling
import io.github.scala_tessella.tessella.Topology.{Edge, Node => TilingNode}
import io.github.scala_tessella.editor.models.{AppState, Point}

object GridRenderer:
  def element: Element =
    svg.g(
      svg.className := "grid-pattern",
      svg.opacity := "0.3",
      // Vertical lines
      (0 to 800 by 50).map(x =>
        svg.line(
          svg.x1 := x.toString, svg.y1 := "0",
          svg.x2 := x.toString, svg.y2 := "600",
          svg.stroke := "#444", svg.strokeWidth := "1"
        )
      ),
      // Horizontal lines
      (0 to 600 by 50).map(y =>
        svg.line(
          svg.x1 := "0", svg.y1 := y.toString,
          svg.x2 := "800", svg.y2 := y.toString,
          svg.stroke := "#444", svg.strokeWidth := "1"
        )
      )
    )
