package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{AppState, EditorConfig, EditorState}
import io.github.scala_tessella.editor.utils.PolygonNameGenerator
import io.github.scala_tessella.editor.operations.TessellationOperations.selectPolygon

import com.raquo.laminar.api.L.{*, given}
import com.raquo.laminar.api.features.unitArrows

import scala.math.{Pi, cos, sin}

object PolygonPaletteComponent:
  def element: Element =
    div(
      className := "polygon-palette",
//      h2("Polygon Shape"),
      div(
        className := "palette-grid",
        EditorConfig.polygonSides.map(sides => polygonButton(sides)),
        customPolygonSelector()
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

  private def customPolygonSelector(): Element =
    val customSides = Var(11) // Local state for the input field, default to 11

    // Signal to check if the custom polygon is currently selected
    val isSelected = EditorState.selectedPolygon.signal.combineWith(customSides.signal).map {
      (maybeSelected, currentCustom) => maybeSelected.contains(currentCustom)
    }

    // Create a combined signal for the CSS class to handle selection and disabled states
    val cssClass = isSelected.combineWith(EditorState.isProcessing.signal).map { (selected, processing) =>
      val baseClass = if (selected) "polygon-btn selected" else "polygon-btn"
      val customClass = s"$baseClass custom-polygon-creator"
      if (processing) s"$customClass disabled" else customClass
    }

    div(
      className <-- cssClass,
      title <-- customSides.signal.map(s => s"$s-sided polygon (${PolygonNameGenerator.polygonName(s)})"),
      // The whole div is clickable to select the polygon with the current number of sides
      onClick.filter(_ => !EditorState.isProcessing.now()) --> { _ =>
        selectPolygon(customSides.now())
      },
      // Dynamic SVG preview, which updates as the number of sides changes
      child <-- customSides.signal.map(sides =>
        if (sides >= 3 && sides <= 100) polygonSvg(sides) else div() // Render svg only if sides are valid
      ),
      // Number input, which acts as the label
      input(
        tpe := "number",
        className := "polygon-label-input",
        minAttr := "3",
        maxAttr := "100",
        // Two-way binding between the input field and the `customSides` variable
        controlled(
          value <-- customSides.signal.map(_.toString),
          onInput.mapToValue.map(_.toIntOption.getOrElse(3)).map(s => Math.max(3, Math.min(s, 100))) --> customSides
        ),
        // Prevent clicks on the input from triggering the container's onClick handler
        onClick.stopPropagation --> {},
        disabled <-- EditorState.isProcessing.signal
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