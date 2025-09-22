package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{AppState, EditorConfig, EditorState}
import io.github.scala_tessella.editor.utils.PolygonNameGenerator
import io.github.scala_tessella.editor.utils.Geometry.Radian.{TAU, TAU_2}
import io.github.scala_tessella.editor.operations.TessellationOperations.*
import io.github.scala_tessella.editor.operations.OperationGuard.gate

import com.raquo.laminar.api.L.{*, given}
import com.raquo.laminar.api.features.unitArrows
import io.github.scala_tessella.dcel.BigDecimalGeometry.AngleDegree
import io.github.scala_tessella.ring_seq.RingSeq.{rotateLeft, rotateRight, reflectAt}
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
        child.maybe <-- EditorState.selectedPolygon.signal
          .combineWith(EditorState.isIrregularSelected.signal)
          .map((maybeSides, isIrregular) =>
              if isIrregular then
                Option(
                  div(
                    button(
                      className := "select-all-by-type-btn",
                      s"Select irregular shape",
                      onClick.preventDefault --> { _ => AppState.selectPolygonsByShape(EditorState.recentIrregularPolygon.now().get) },
                      disabled <-- EditorState.currentTiling.signal.map(_.isEmpty)
                    )
                  )
                )
              else
                maybeSides.map { sides =>
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
            }
          )
      )
    )

  private def customPolygonSelector(): Element =
    val customSides = Var(11)
    val inputValue = Var(customSides.now().toString)

    def validateSides(input: String): Int =
      input.toIntOption.getOrElse(3).clamp(3, 100)

    def updateSides(sides: Int): Unit =
      customSides.set(sides)
      inputValue.set(sides.toString)

    val syncInputToSource = customSides.signal.changes.map(_.toString) --> inputValue
    val displaySides = inputValue.signal.map(validateSides)
    val isSelected = EditorState.selectedPolygon.signal.combineWith(customSides.signal).map {
      (maybeSelected, currentCustom) => maybeSelected.contains(currentCustom)
    }
    val validateAndUpdateObserver = Observer[Any] { _ =>
      val validatedSides = validateSides(inputValue.now())
      updateSides(validatedSides)
    }

    div(
      syncInputToSource,
      className <-- polygonButtonClass("polygon-btn custom-polygon-creator", isSelected),
      title <-- displaySides.map(s => s"$s-sided polygon (${PolygonNameGenerator.polygonName(s)})"),
      // Replace imperative guard with gated click stream combined with current validated sides
      inContext { thisDiv =>
        gate(thisDiv.events(onClick))
          .withCurrentValueOf(displaySides)
          .map { case (_, validatedSides) => validatedSides } --> { validatedSides =>
          updateSides(validatedSides)
          selectPolygon(validatedSides)
        }
      },
      child <-- displaySides.map(sides => polygonSvg(sides)),
      input(
        tpe := "number",
        className := "polygon-label-input",
        minAttr := "3",
        maxAttr := "100",
        controlled(
          value <-- inputValue,
          onInput.mapToValue --> inputValue
        ),
        onBlur --> validateAndUpdateObserver,
        onKeyPress.filter(_.key == "Enter").preventDefault --> validateAndUpdateObserver,
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
      inContext { thisBtn =>
        gate(thisBtn.events(onClick)) --> { _ =>
          EditorState.isIrregularSelected.set(false)
          selectPolygon(sides)
        }
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
      val angle = (TAU * i / sides) - TAU_2 // Start from the top
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

    button(
      className <-- btnClass,
      tpe := "button",
      title := "Irregular polygon",
      disabled <-- EditorState.isProcessing.signal
        .combineWith(EditorState.recentIrregularPolygon.signal.map(_.isEmpty))
        .map { (processing, noneRecent) => processing || noneRecent },
      // replace filter+now() with gated click + current state
      inContext { thisBtn =>
        gate(thisBtn.events(onClick))
          .withCurrentValueOf(EditorState.recentIrregularPolygon.signal)
          .collect { case (_, Some(_)) => () } --> { _ =>
          initializeWithIrregularIfEmpty()
          selectIrregularInPalette()
        }
      },
      // small corner button
      div(
        className := "corner-button",
        title := "Adjust head (preview)",
        // stop propagation still needed, then gate the corner click stream
        onClick.stopPropagation --> Observer.empty,
        inContext { cornerDiv =>
          gate(cornerDiv.events(onClick))
            .withCurrentValueOf(EditorState.recentIrregularPolygon.signal)
            .collect { case (_, Some(_)) => () } --> { _ =>
            EditorState.showIrregularPolygonPopup.set(true)
          }
        },
        svg.svg(
          svg.width := "12", svg.height := "12", svg.viewBox := "0 -4 24 24",
          svg.path(svg.d := "M12 5v14M5 12h14", svg.stroke := "currentColor", svg.fill := "none", svg.strokeWidth := "2", svg.strokeLineCap := "round")
        )
      ),
      // preview
      child <-- EditorState.recentIrregularPolygon.signal.map {
        case Some(angles) => irregularPolygonSvg(angles)
        case None =>
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
          case None => "Irregular"
          case Some(angles) => s"Irr-${angles.size}"
        }
      )
    )

  // Big preview that highlights the head edge
  private[components] def bigIrregularWithHead(angles: Vector[AngleDegree]): Element =
    val size = 220
    val pad = 12.0

    // compute polygon points in local unit-edges like in thumbnail
    def unitPoints(a: Vector[AngleDegree]): Vector[(Double, Double)] =
      val turns = a.map(_.supplement)
      var x = 0.0
      var y = 0.0
      var heading = AngleDegree(0)
      val pts = collection.mutable.ArrayBuffer[(Double, Double)]()
      pts += ((x, y))
      turns.foreach { t =>
        val rad = heading.toBigRadian.toBigDecimal.toDouble
        x = x + Math.cos(rad)
        y = y + Math.sin(rad)
        pts += ((x, y))
        heading = heading + t
      }
      // keep only N vertices
      pts.toVector.dropRight(1)

    val basePts = unitPoints(angles)
    val xs = basePts.map(_._1)
    val ys = basePts.map(_._2)
    val minX = xs.min
    val maxX = xs.max
    val minY = ys.min
    val maxY = ys.max
    val w = Math.max(1e-6, maxX - minX)
    val h = Math.max(1e-6, maxY - minY)
    val scale = (size - 2 * pad) / Math.max(w, h)
    val offX = (size - scale * w) / 2.0 - scale * minX
    val offY = (size - scale * h) / 2.0 - scale * minY

    def toStr(p: (Double, Double)) =
      val sx = offX + p._1 * scale
      val sy = offY + p._2 * scale
      f"$sx%.3f,$sy%.3f"

    val pointsStr = basePts.map(toStr).mkString(" ")

    // render the highlighted head edge as a line on top
    def edgeLine(i: Int): Element =
      val n = basePts.size
      val a = basePts(i % n)
      val b = basePts((i + 1) % n)
      val ax = offX + a._1 * scale
      val ay = offY + a._2 * scale
      val bx = offX + b._1 * scale
      val by = offY + b._2 * scale
      svg.line(
        svg.x1 := f"$ax%.3f", svg.y1 := f"$ay%.3f",
        svg.x2 := f"$bx%.3f", svg.y2 := f"$by%.3f",
        svg.stroke := "#ff6b6b",
        svg.strokeWidth := "4",
        svg.strokeLineCap := "round",
        svg.pointerEvents := "none"
      )

    svg.svg(
      svg.width := size.toString,
      svg.height := size.toString,
      svg.viewBox := s"0 0 $size $size",
      svg.polygon(
        svg.points := pointsStr,
        svg.fill := "currentColor",
        svg.stroke := "currentColor",
        svg.strokeWidth := "1.5"
      ),
      edgeLine(((1 % basePts.size) + basePts.size) % basePts.size)
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

    val xs = pts.map(_._1)
    val ys = pts.map(_._2)
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