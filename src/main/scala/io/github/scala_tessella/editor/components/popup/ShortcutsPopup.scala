package io.github.scala_tessella.editor.components.popup

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.unitArrows
import io.github.scala_tessella.editor.models.EditorState

object ShortcutsPopup:

  import PopupCommons._

  private val closeShortcuts: Observer[org.scalajs.dom.MouseEvent] =
    closePopup(EditorState.showShortcutsPopup)

  def element: Element =
    div(
      className := "popup-overlay",
      onClick --> closeShortcuts,
      div(
        className := "popup-content",
        onClick.stopPropagation --> {},
        button(
          className := "popup-close-btn",
          onClick --> closeShortcuts,
          closeIcon
        ),
        h2("Keyboard Shortcuts"),
        div(
          className := "popup-text-scrollable",
          table(
            className := "shortcuts-table",
            thead(
              tr(
                th("Action"),
                th("Shortcut")
              )
            ),
            tbody(
              tr(
                td("Undo"),
                td(kbd(primaryModLabel), " + ", kbd("Z"))
              ),
              tr(
                td("Redo"),
                td(kbd(primaryModLabel), " + ", kbd("Shift"), " + ", kbd("Z"))
              ),
              tr(
                td("Save"),
                td(kbd(primaryModLabel), " + ", kbd("S"))
              ),
              tr(
                td("Deselect All"),
                td(kbd("Esc"))
              ),
              tr(
                td("Zoom In"),
                td(kbd("+"))
              ),
              tr(
                td("Zoom Out"),
                td(kbd("-"))
              ),
              tr(
                td("Rotate Left"),
                td(kbd("E"))
              ),
              tr(
                td("Rotate Right"),
                td(kbd("R"))
              ),
              tr(
                td("Fit to Canvas"),
                td(kbd("F"))
              )
            )
          )
        )
      )
    )
