
package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.editor.models.{AppState, EditorState}
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
        // The native color-picker input:
        input(
          typ := "color",
          value <-- tempColor.signal.map { case (r,g,b) =>
            f"#${r}%02x${g}%02x${b}%02x"
          },
          onInput.mapToValue.map(hex =>
            (
              Integer.parseInt(hex.substring(1,3), 16),
              Integer.parseInt(hex.substring(3,5), 16),
              Integer.parseInt(hex.substring(5,7), 16)
            )
          ) --> tempColor.writer
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