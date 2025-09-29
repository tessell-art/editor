package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.nguyenyou.ui5.webcomponents.laminar.ColorPicker
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.operations.ColorOperations.applyColorToSelectedPolygons
import io.github.scala_tessella.editor.utils.ColorRGB
import io.github.scala_tessella.editor.utils.ColorRGB.*

object ColorPickerPopupComponent:
  def element(isOpen: Var[Boolean], tempColor: Var[ColorRGB]): Element =
    div(
      className := "color-picker-popup",
      display <-- isOpen.signal.map(open => if open then "flex" else "none"),
      // Background overlay
      onClick --> { _ =>

        isOpen.set(false)
      }, // Click outside closes
      div(
        className := "popup-content",
        onClick.map(_.stopPropagation()) --> Observer.empty,
        h3("Select Color"),
        ColorPicker(
          _.simplified := true,
          _.value <-- tempColor.signal.map(_.toRgba),
          _.onChange.map { event =>
            val color = event.target._colorValue._rgb
            ColorRGB(color.r.toInt, color.g.toInt, color.b.toInt)
          } --> tempColor.writer
        )(),
        div(
          className := "popup-actions",
          button(
            "Cancel",
            onClick --> { _ =>

              isOpen.set(false)
            }
          ),
          button(
            "OK",
            onClick --> { _ =>
              val newColor = tempColor.now()
              EditorState.fillColor.set(newColor)

              // Apply the new color to all currently selected polygons
              applyColorToSelectedPolygons(newColor)

              isOpen.set(false)
            }
          )
        )
      )
    )
