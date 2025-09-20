package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{AppState, EditorConfig, EditorState}
import io.github.scala_tessella.editor.utils.PolygonNameGenerator
import io.github.scala_tessella.editor.operations.TessellationOperations.selectPolygon
import io.github.scala_tessella.editor.operations.ErrorOperations.showError
import com.raquo.laminar.api.L.{*, given}
import com.raquo.laminar.api.features.unitArrows
import io.github.scala_tessella.dcel.BigDecimalGeometry.AngleDegree
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.editor.operations.SelectionOperations.clearAllSelections
import io.github.scala_tessella.editor.utils.Geometry.Radian.{TAU, TAU_2}
import org.scalajs.dom

import scala.math.{cos, sin}

object PolygonPaletteComponent:

  extension (i: Int)
    private def clamp(min: Int, max: Int): Int =
      Math.max(min, Math.min(i, max))

  private def polygonButtonClass(baseClasses: String, isSelectedSignal: Signal[Boolean]): Signal[String] =
    isSelectedSignal.combineWith(EditorState.isProcessing.signal).map { (selected, processing) =>
      val fullBaseClasses = if selected then s"$baseClasses selected" else baseClasses
      if processing then s"$fullBaseClasses disabled" else fullBaseClasses
    }

  def element: Element =
    div(
      className := "polygon-palette",
//      h2("Polygon Shape"),
      div(
        className := "palette-grid",
        EditorConfig.polygonSides.map(sides => polygonButton(sides)),
        customPolygonSelector(),
        irregularPolygonSlot() // <-- new selectable slot
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

    // An observer that validates the input and updates the "source of truth" (`customSides`).
    val validateAndUpdateObserver = Observer[Any] { _ =>
      val validatedSides = validateSides(inputValue.now())
      updateSides(validatedSides)
    }

    div(
      syncInputToSource, // The observer needs to be owned by the element
      className <-- polygonButtonClass("polygon-btn custom-polygon-creator", isSelected),
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
    val isSelected = EditorState.selectedPolygon.signal.map(_.contains(sides))
    button(
      className <-- polygonButtonClass("polygon-btn", isSelected),
      tpe := "button",
      title := s"$sides-sided polygon (${PolygonNameGenerator.polygonName(sides)})",
      disabled <-- EditorState.isProcessing.signal,
      onClick.filter(_ => !EditorState.isProcessing.now()) --> { _ =>
        // Selecting a regular polygon deselects irregular
        EditorState.isIrregularSelected.set(false)
        selectPolygon(sides)
      },
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

  // ---------- Irregular polygon slot ----------

  // Button-like slot that appears after an irregular polygon is available and is selectable.
  private def irregularPolygonSlot(): Element =
    val isIrregularSelected: Signal[Boolean] =
      EditorState.selectedIrregularPolygon.signal.map(_.isDefined)

    val btnClass = polygonButtonClass("polygon-btn irregular-polygon-slot", isIrregularSelected)

    // When clicked, select irregular. If tiling is empty, create it from the irregular polygon.
    val onSelectIrregular: Observer[dom.MouseEvent] =
      Observer { _ =>
        if !EditorState.isProcessing.now() && EditorState.recentIrregularPolygon.now().isDefined then
          if EditorState.currentTiling.now().isEmpty then
            // Initialize tiling with the irregular polygon
            TilingDCEL.createSimplePolygon(EditorState.recentIrregularPolygon.now().get.toList).toOption match
              case Some(tiling) =>
                EditorState.currentTiling.set(tiling)
                clearAllSelections()
              case None =>
                showError("Failed to create tiling from irregular polygon")
          // Select irregular in palette and clear regular selection
          EditorState.selectedPolygon.set(None)
          EditorState.isIrregularSelected.set(true)
      }

    button(
      className <-- btnClass,
      tpe := "button",
      title := "Irregular polygon",
      disabled <-- EditorState.isProcessing.signal
        .combineWith(EditorState.recentIrregularPolygon.signal.map(_.isEmpty))
        .map { (processing, noneRecent) => processing || noneRecent },
      onClick.filter(_ => !EditorState.isProcessing.now()) --> onSelectIrregular,
      child <-- EditorState.recentIrregularPolygon.signal.map {
        case Some(angles) => irregularPolygonSvg(angles)
        case None =>
          // simple placeholder
          svg.svg(
            svg.width := "40",
            svg.height := "40",
            svg.viewBox := "0 0 40 40",
            svg.rect(
              svg.x := "8",
              svg.y := "8",
              svg.width := "24",
              svg.height := "24",
              svg.fill := "none",
              svg.stroke := "currentColor"
            )
          )
      },
      div(
        className := "polygon-label",
        child.text <-- EditorState.recentIrregularPolygon.signal.map {
          case None          => "Irregular"
          case Some(angles)  => s"Irr-${angles.size}"
        }
      )
    )

  // Render the irregular polygon preview from AngleDegree vector (unit edges)
  private def irregularPolygonSvg(anglesDeg: Vector[AngleDegree]): Element =
    val size = 40
    val pad = 4.0

    // Walk edges of length 1, turning by exterior angles (180 - interior)
    val turns = anglesDeg.map(_.supplement)
    var x = 0.0
    var y = 0.0
    var heading = AngleDegree(0) // degrees
    val pts = collection.mutable.ArrayBuffer[(Double, Double)]()
    pts += ((x, y))
    turns.foreach { t =>
      val rad = heading.toBigRadian.toBigDecimal.toDouble
      x = x + Math.cos(rad)
      y = y + Math.sin(rad)
      pts += ((x, y))
      heading = heading + t
    }

    val xs = pts.map(_._1); val ys = pts.map(_._2)
    val minX = xs.min; val maxX = xs.max
    val minY = ys.min; val maxY = ys.max
    val w = Math.max(1e-6, maxX - minX)
    val h = Math.max(1e-6, maxY - minY)
    val scale = (size - 2 * pad) / Math.max(w, h)
    val offX = (size - scale * w) / 2.0 - scale * minX
    val offY = (size - scale * h) / 2.0 - scale * minY

    val svgPoints = pts.toVector.map { case (px, py) =>
      val sx = offX + px * scale
      val sy = offY + py * scale
      f"$sx%.3f,$sy%.3f"
    }.mkString(" ")

    svg.svg(
      svg.width := size.toString,
      svg.height := size.toString,
      svg.viewBox := s"0 0 $size $size",
      svg.polygon(
        svg.points := svgPoints,
        svg.fill := "currentColor",
        svg.stroke := "currentColor",
        svg.strokeWidth := "1"
      )
    )