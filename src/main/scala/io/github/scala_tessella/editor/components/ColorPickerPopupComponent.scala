package io.github.scala_tessella.editor.components

import be.doeraene.webcomponents.ui5.ColourPicker
import be.doeraene.webcomponents.ui5.scaladsl.colour.Colour
import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.operations.ColorOperations.applyColorToSelectedPolygons

import scala.scalajs.js.timers.setTimeout

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
          } --> tempColor.writer,
          // After the component mounts, find and remove the alpha slider from its shadow DOM
          onMountCallback(ctx =>
            setTimeout(0): // Wait a tick for the shadow DOM to be ready
              for
                shadowRoot <- Option(ctx.thisNode.ref.shadowRoot)
                alphaSlider <- Option(shadowRoot.querySelector("ui5-slider.ui5-color-picker-alpha-slider"))
                parentNode <- Option(alphaSlider.parentNode)
              do
                parentNode.removeChild(alphaSlider)
          )
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