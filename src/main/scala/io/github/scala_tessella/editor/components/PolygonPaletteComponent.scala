package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.{PolygonNameGenerator, TilingGenerator}
import io.github.scala_tessella.editor.operations.TessellationOperations.{clearTiling, selectPolygon}

import scala.math.{Pi, cos, sin}

object PolygonPaletteComponent:
  def element: Element =
    div(
      className := "polygon-palette",
      h2("Select a Polygon Shape"),
      div(
        className := "palette-grid",
        EditorState.polygonSides.map(sides => polygonButton(sides))
      ),
      div(
        className := "selected-info",
        child.maybe <-- EditorState.selectedPolygon.signal.map(_.map(sides =>
          p(s"Selected: $sides-sided polygon (${PolygonNameGenerator.polygonName(sides)})")
        ))
      ),
      tilingStatus(),
      tilingControls()
    )

  private def tilingStatus(): Element =
    div(
      className := "tiling-status",
      child <-- EditorState.currentTiling.signal.combineWith(EditorState.isProcessing.signal).map {
        case (Some(_), false) => p("Tiling: Active", className := "status active")
        case (Some(_), true) => p("Tiling: Processing...", className := "status processing")
        case (None, false) => p("Tiling: Empty", className := "status empty")
        case (None, true) => p("Tiling: Creating...", className := "status processing")
      }
    )

  private def tilingControls(): Element =
    div(
      className := "tiling-controls",
      h3("Tessellation"),
      button(
        "Generate Hexagon Tiling",
        disabled <-- EditorState.isProcessing.signal,
        onClick.filter(_ => !EditorState.isProcessing.now()) --> { _ => TilingGenerator.generateHexagonTiling() }
      ),
      button(
        "Generate Triangle Tiling",
        disabled <-- EditorState.isProcessing.signal,
        onClick.filter(_ => !EditorState.isProcessing.now()) --> { _ => TilingGenerator.generateTriangleTiling() }
      ),
      button(
        "Generate Mixed Tiling",
        disabled <-- EditorState.isProcessing.signal,
        onClick.filter(_ => !EditorState.isProcessing.now()) --> { _ => TilingGenerator.generateMixedTiling() }
      ),
      button(
        "Clear Tiling",
        disabled <-- EditorState.isProcessing.signal,
        onClick.filter(_ => !EditorState.isProcessing.now()) --> { _ => clearTiling() }
      )
    )

  private def polygonButton(sides: Int): Element =
    button(
      className <-- EditorState.selectedPolygon.signal.combineWith(EditorState.isProcessing.signal).map { (selected, processing) =>
        val baseClass = if (selected.contains(sides)) "polygon-btn selected" else "polygon-btn"
        if processing then s"$baseClass disabled" else baseClass
      },
      tpe := "button",
      title := s"$sides-sided polygon (${PolygonNameGenerator.polygonName(sides)})",
      disabled <-- EditorState.isProcessing.signal,
      onClick.filter(_ => !EditorState.isProcessing.now()) --> { _ => selectPolygon(sides) },
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