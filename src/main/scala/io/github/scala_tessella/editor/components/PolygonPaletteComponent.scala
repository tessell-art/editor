package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.editor.models.AppState
import io.github.scala_tessella.editor.utils.{PolygonUtils, TilingGenerator}
import scala.math.{cos, Pi, sin}

object PolygonPalette:
  def element: Element =
    div(
      className := "polygon-palette",
      h2("Select a Polygon Shape"),
      div(
        className := "palette-grid",
        AppState.polygonSides.map(sides => polygonButton(sides))
      ),
      div(
        className := "selected-info",
        child.maybe <-- AppState.selectedPolygon.signal.map(_.map(sides =>
          p(s"Selected: ${sides}-sided polygon (${PolygonUtils.polygonName(sides)})")
        ))
      ),
      tilingControls()
    )

  private def tilingControls(): Element =
    div(
      className := "tiling-controls",
      h3("Tessellation"),
      button("Generate Hexagon Tiling", onClick --> { _ => TilingGenerator.generateHexagonTiling() }),
      button("Generate Triangle Tiling", onClick --> { _ => TilingGenerator.generateTriangleTiling() }),
      button("Generate Mixed Tiling", onClick --> { _ => TilingGenerator.generateMixedTiling() }),
      button("Clear Tiling", onClick --> { _ => AppState.currentTiling.set(None) })
    )

  private def polygonButton(sides: Int): Element =
    button(
      className <-- AppState.selectedPolygon.signal.map(selected =>
        if (selected.contains(sides)) "polygon-btn selected" else "polygon-btn"
      ),
      tpe := "button",
      title := s"${sides}-sided polygon (${PolygonUtils.polygonName(sides)})",
      onClick --> { _ => AppState.selectedPolygon.set(Some(sides)) },
      polygonSvg(sides),
      div(className := "polygon-label", sides.toString)
    )

  private def polygonSvg(sides: Int): Element =
    val size = 40
    val centerX = size / 2.0
    val centerY = size / 2.0
    val radius = size * 0.35

    val points = (0 until sides).map { i =>
      val angle = (2 * Pi * i / sides) - (Pi / 2) // Start from top
      val x = centerX + radius * cos(angle)
      val y = centerY + radius * sin(angle)
      s"$x,$y"
    }.mkString(" ")

    svg.svg(
      svg.width := size.toString,
      svg.height := size.toString,
      svg.viewBox := s"0 0 $size $size",
      svg.polygon(
        svg.points := points,
        svg.fill := "currentColor",
        svg.stroke := "currentColor",
        svg.strokeWidth := "1"
      )
    )