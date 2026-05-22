package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L._
import io.github.nguyenyou.ui5.webcomponents.laminar.ColorPicker
import io.github.scala_tessella.editor.i18n.I18n
import io.github.scala_tessella.editor.models.{ColorPickerContext, EditorState}
import io.github.scala_tessella.editor.operations.ColorOperations.applyColorToSelectedPolygons
import io.github.scala_tessella.editor.utils.ColorRGB
import io.github.scala_tessella.editor.utils.ColorRGB._

object ColorPickerPopupComponent:

  /** `tempColor` and `showColorPicker` are nested inside `ColorState` rather than standalone `Var`s. Callers
    * supply a `Signal`/`Observer` pair for the working colour and a close action — all three wired up at the
    * call site from the aggregate.
    */
  def element(
      tempColorSignal: Signal[ColorRGB],
      tempColorObserver: Observer[ColorRGB],
      close: () => Unit
  ): Element =
    div(
      className := "color-picker-popup",
      // Background overlay
      onClick --> { _ =>

        close()
      }, // Click outside closes
      div(
        className := "popup-content",
        onClick.map(_.stopPropagation()) --> Observer.empty,
        h3(
          child.text <--
            EditorState.colorState.signal.map(_.colorPickerContext).distinct
              .combineWith(EditorState.localeState.signal)
              .map: (context, _) =>

                val key = context match
                  case ColorPickerContext.FillSelected => "popup.colorPicker.fillSelectedTitle"
                  case ColorPickerContext.Default      => "popup.colorPicker.title"
                I18n.tNow(key)
        ),
        ColorPicker(
          _.simplified := true,
          _.value <-- tempColorSignal.map(_.toRgba),
          _.onChange.map { event =>

            val color = event.target._colorValue._rgb
            ColorRGB(color.r.toInt, color.g.toInt, color.b.toInt)
          } --> tempColorObserver
        )(),
        div(
          className := "popup-actions",
          button(
            child.text <-- I18n.t("common.cancel"),
            onClick --> { _ =>

              close()
            }
          ),
          button(
            child.text <-- I18n.t("common.ok"),
            onClick.compose(_.withCurrentValueOf(tempColorSignal)) --> { (_, newColor) =>

              EditorState.colorState.update(_.copy(fillColor = newColor))

              // Apply the new color to all currently selected polygons
              applyColorToSelectedPolygons(newColor)

              close()
            }
          )
        )
      )
    )
