package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{AppState, EditorState}
import io.github.scala_tessella.editor.utils.PolygonNameGenerator
import io.github.scala_tessella.editor.operations.TessellationOperations.selectPolygon

import com.raquo.laminar.api.L.{*, given}

import scala.math.{Pi, cos, sin}

object PolygonPaletteComponent:
  def element: Element =
    div(
      className := "polygon-palette",
//      h2("Polygon Shape"),
      div(
        className := "palette-grid",
        EditorState.polygonSides.map(sides => polygonButton(sides))
      ),
      div(
        className := "selected-info",
        child.maybe <-- EditorState.selectedPolygon.signal.map(_.map { sides =>
          val polygonName = PolygonNameGenerator.polygonName(sides)
          div(
//            p(s"Selected: $sides-sided polygon ($polygonName)"),
            button(
              className := "select-all-by-type-btn",
              s"Select all ${polygonName}s",
              onClick.preventDefault.map(_ => sides) --> { s => AppState.selectPolygonsBySides(s) },
              disabled <-- EditorState.currentTiling.signal.map(_.isEmpty)
            )
          )
        })
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