package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{AppState, EditorConfig, EditorState}
import io.github.scala_tessella.editor.utils.PolygonNameGenerator
import io.github.scala_tessella.editor.operations.TessellationOperations.selectPolygon

import com.raquo.laminar.api.L.{*, given}
import com.raquo.laminar.api.features.unitArrows
import io.github.scala_tessella.tessella.Geometry.Radian.{TAU, TAU_2}

import scala.math.{cos, sin}

object PolygonPaletteComponent:

  extension (i: Int)
    private def clamp(min: Int, max: Int): Int =
      Math.max(min, Math.min(i, max))

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
    val customSides = Var(11) // This remains the "source of truth" for the integer value.
    val inputValue = Var(customSides.now().toString) // A separate Var for the input's string value.

    // Helper function to validate and clamp the input value
    def validateSides(input: String): Int =
      input.toIntOption.getOrElse(3).clamp(3, 100)

    // Helper function to update both customSides and inputValue
    def updateSides(sides: Int): Unit =
      customSides.set(sides)
      inputValue.set(sides.toString)

    // This observer will sync the input field if `customSides` is ever changed programmatically.
    val syncInputToSource = customSides.signal.changes.map(_.toString) --> inputValue

    // Create a signal that parses the input value to determine the shape to display
    val displaySides = inputValue.signal.map(validateSides)

    // Signal to check if the custom polygon is currently selected. Uses the validated `customSides`.
    val isSelected = EditorState.selectedPolygon.signal.combineWith(customSides.signal).map {
      (maybeSelected, currentCustom) => maybeSelected.contains(currentCustom)
    }

    // Create a combined signal for the CSS class to handle selection and disabled states.
    val cssClass = isSelected.combineWith(EditorState.isProcessing.signal).map { (selected, processing) =>
      val baseClass = if (selected) "polygon-btn selected" else "polygon-btn"
      val customClass = s"$baseClass custom-polygon-creator"
      if (processing) s"$customClass disabled" else customClass
    }

    // An observer that validates the input and updates the "source of truth" (`customSides`).
    val validateAndUpdateObserver = Observer[Any] { _ =>
      val validatedSides = validateSides(inputValue.now())
      updateSides(validatedSides)
    }

    div(
      syncInputToSource, // The observer needs to be owned by the element
      className <-- cssClass,
      title <-- displaySides.map(s => s"$s-sided polygon (${PolygonNameGenerator.polygonName(s)})"),
      // The whole div is clickable to select the polygon with the current number of sides
      onClick.filter(_ => !EditorState.isProcessing.now()) --> { _ =>
        // Validate the input first, then select the polygon
        val validatedSides = validateSides(inputValue.now())
        updateSides(validatedSides)
        selectPolygon(validatedSides)
      },
      // Dynamic SVG preview, which updates as the user types
      child <-- displaySides.map(sides => polygonSvg(sides)),
      // Number input, which acts as the label
      input(
        tpe := "number",
        className := "polygon-label-input",
        minAttr := "3",
        maxAttr := "100",
        // Control the input with our local string Var for a fluid typing experience
        controlled(
          value <-- inputValue,
          onInput.mapToValue --> inputValue
        ),
        // Validate when the user finishes editing (leaves the field or presses Enter)
        onBlur --> validateAndUpdateObserver,
        onKeyPress.filter(_.key == "Enter").preventDefault --> validateAndUpdateObserver,
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
      val angle = (TAU * i / sides) - TAU_2 // Start from top
      val x = centerX + radius * cos(angle.toDouble)
      val y = centerY + radius * sin(angle.toDouble)
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