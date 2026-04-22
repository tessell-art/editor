package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.unitArrows
import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.editor.components.IconsSVG.plusIcon
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.models.{EditorConfig, EditorState}
import io.github.scala_tessella.editor.operations.OperationGuard.gate
import io.github.scala_tessella.editor.operations.TessellationOperations.*
import io.github.scala_tessella.editor.utils.geo.{LineSegment, Point}
import io.github.scala_tessella.editor.utils.{PolygonNameGenerator, PolygonSvg}
import io.github.scala_tessella.editor.utils.SvgDsl.*

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
        child.maybe <--
          EditorState.toolState.signal.map(_.selectedPolygon).distinct
            .combineWith(EditorState.isIrregularSelected.signal)
            .map((maybeSides, isIrregular) =>
              if isIrregular then
                Option(
                  div(
                    button(
                      className := "select-all-by-type-btn",
                      s"Select irregular shape",
                      // Replace with gated click composed with current angles
                      inContext { btn =>

                        gate(btn.events(onClick))
                          .withCurrentValueOf(EditorState.recentIrregularPolygon.signal)
                          .collect { case (_, Some(angles)) =>
                            angles
                          } --> { angles =>

                          AppState.selectPolygonsByShape(angles)
                        }
                      },
                      disabled <-- EditorState.isTilingEmptySignal
                    )
                  )
                )
              else
                maybeSides.map { sides =>

                  val polygonName = PolygonNameGenerator.polygonName(sides)
                  div(
                    button(
                      className := "select-all-by-type-btn",
                      s"Select all ${polygonName}s",
                      onClick.preventDefault.map(_ => sides) --> { s =>

                        AppState.selectPolygonsBySides(s)
                      },
                      disabled <-- EditorState.isTilingEmptySignal
                    )
                  )
                }
            )
      )
    )

  private def customPolygonSelector(): Element =
    val customSides = Var(11)
    val inputValue  = Var("11")

    def validateSides(input: String): Int =
      input.toIntOption.getOrElse(3).clamp(3, 100)

    def updateSides(sides: Int): Unit =
      customSides.set(sides)
      inputValue.set(sides.toString)

    val syncInputToSource = customSides.signal.changes.map(_.toString) --> inputValue
    val displaySides      = inputValue.signal.map(validateSides)
    val isSelected        =
      EditorState.toolState.signal.map(_.selectedPolygon).distinct.combineWith(customSides.signal).map {
        (maybeSelected, currentCustom) =>

          maybeSelected.contains(currentCustom)
      }
    div(
      syncInputToSource,
      className <-- polygonButtonClass("polygon-btn custom-polygon-creator", isSelected),
      title <-- displaySides.map(s => s"$s-sided polygon (${PolygonNameGenerator.polygonName(s)})"),
      // Replace imperative guard with gated click stream combined with current validated sides
      inContext { thisDiv =>

        gate(thisDiv.events(onClick))
          .withCurrentValueOf(displaySides)
          .map { case (_, validatedSides) =>
            validatedSides
          } --> { validatedSides =>

          updateSides(validatedSides)
          selectPolygon(validatedSides)
        }
      },
      child <-- displaySides.map(sides => PolygonSvg.regularPreview(sides)),
      input(
        tpe       := "number",
        className := "polygon-label-input",
        minAttr   := "3",
        maxAttr   := "100",
        controlled(
          value <-- inputValue,
          onInput.mapToValue --> inputValue
        ),
        onBlur.compose(_.withCurrentValueOf(inputValue.signal)) --> { case (_, value) =>
          updateSides(validateSides(value))
        },
        onKeyPress.filter(_.key == "Enter").preventDefault
          .compose(_.withCurrentValueOf(inputValue.signal)) --> { case (_, value) =>
          updateSides(validateSides(value))
        },
        onClick.stopPropagation --> {},
        disabled <-- EditorState.isProcessing.signal
      )
    )

  private def polygonButton(sides: Int): Element =
    val isSelected   = EditorState.toolState.signal.map(_.selectedPolygon.contains(sides)).distinct
    button(
      className <-- polygonButtonClass("polygon-btn", isSelected),
      tpe   := "button",
      title := s"$sides-sided polygon (${PolygonNameGenerator.polygonName(sides)})",
      disabled <-- EditorState.isProcessing.signal,
      inContext { thisBtn =>

        gate(thisBtn.events(onClick)) --> { _ =>

          EditorState.isIrregularSelected.set(false)
          selectPolygon(sides)
        }
      },
      PolygonSvg.regularPreview(sides),
      div(className := "polygon-label", sides.toString)
    )

  // ---------- Irregular polygon slot ----------

  // Button-like slot that appears after an irregular polygon is available and is selectable.
  private def irregularPolygonSlot(): Element =
    val isIrregularSelected: Signal[Boolean] =
      EditorState.selectedIrregularPolygon.signal.map(_.isDefined)

    val btnClass = polygonButtonClass("polygon-btn irregular-polygon-slot", isIrregularSelected)

    button(
      className <-- btnClass,
      tpe   := "button",
      title := "Irregular polygon",
      disabled <--
        EditorState.isProcessing.signal
          .combineWith(EditorState.recentIrregularPolygon.signal.map(_.isEmpty))
          .map { (processing, noneRecent) =>

            processing || noneRecent
          },
      // replace filter+now() with gated click + current state
      inContext { thisBtn =>

        gate(thisBtn.events(onClick))
          .withCurrentValueOf(EditorState.recentIrregularPolygon.signal)
          .collect { case (_, Some(_)) =>
            ()
          } --> { _ =>

          initializeWithIrregularIfEmpty()
          selectIrregularInPalette()
        }
      },
      // small corner button
      div(
        className := "corner-button",
        title     := "Adjust head (preview)",
        // stop propagation still needed, then gate the corner click stream
        onClick.stopPropagation --> Observer.empty,
        inContext { cornerDiv =>

          gate(cornerDiv.events(onClick))
            .withCurrentValueOf(EditorState.recentIrregularPolygon.signal)
            .collect { case (_, Some(_)) =>
              ()
            } --> { _ =>

            EditorState.showIrregularPolygonPopup.set(true)
          }
        },
        plusIcon
      ),
      // preview
      child <-- EditorState.recentIrregularPolygon.signal.map {
        case Some(angles) => PolygonSvg.irregularPreview(angles)
        case None         =>
          svg.svg(
            viewBoxCoords(Point(40, 40)),
            svg.rect(
              rectCoords(LineSegment(Point(8, 8), Point(24, 24))),
              svg.fill   := "none",
              svg.stroke := "currentColor"
            )
          )
      },
      div(
        className := "polygon-label",
        child.text <-- EditorState.recentIrregularPolygon.signal.map {
          case None         => "Irregular"
          case Some(angles) => s"Irr-${angles.size}"
        }
      )
    )

  // Big preview that highlights the head edge
  private[components] def bigIrregularWithHead(angles: Vector[AngleDegree]): Element =
    val size = 220
    val pad  = 30.0
    PolygonSvg.irregularBigWithHead(angles, size, pad)
