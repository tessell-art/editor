package io.github.scala_tessella.editor.components

import be.doeraene.webcomponents.ui5.ColourPicker
import be.doeraene.webcomponents.ui5.scaladsl.colour.Colour
import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.operations.ColorOperations.applyColorToSelectedPolygons

object ColorPickerPopupComponent:
  def element(isOpen: Var[Boolean], tempColor: Var[(Int, Int, Int)]): Element =
    div(
      className := "color-picker-popup",
      display <-- isOpen.signal.map(open => if open then "flex" else "none"),
      // Background overlay
      onClick --> { _ => isOpen.set(false) }, // Click outside closes
      div(
        className := "popup-content",
        onClick.map(_.stopPropagation()) --> Observer.empty,
        h3("Select Color"),
        // The laminar-ui5 color-picker:
        ColourPicker(
          _.value <-- tempColor.signal.map { case (r, g, b) => Colour(r, g, b) },
          _.events.onChange.map { event =>
            val color = event.target.value
            (color.red, color.green, color.blue)
          } --> tempColor.writer
        ),
        div(
          className := "popup-actions",
          button("Cancel", onClick --> { _ => isOpen.set(false) }),
          button("OK",
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