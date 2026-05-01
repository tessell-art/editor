package io.github.scala_tessella.editor.components.popup

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.i18n.I18n
import io.github.scala_tessella.editor.models.EditorState

object ShortcutsPopup:

  import PopupCommons._

  private val closeShortcuts: Observer[Any] =
    closePopup(EditorState.popupState.update(_.copy(showShortcutsPopup = false)))

  def element: Element =
    popupOverlay(closeShortcuts)(
      popupContent(closeShortcuts)(
        h2(child.text <-- I18n.t("popup.shortcuts.title")),
        div(
          className := "popup-text-scrollable",
          table(
            className := "shortcuts-table",
            thead(
              tr(
                th(child.text <-- I18n.t("popup.shortcuts.action")),
                th(child.text <-- I18n.t("popup.shortcuts.shortcut"))
              )
            ),
            tbody(
              row("popup.shortcuts.undo", kbd(primaryModLabel), " + ", kbd("Z")),
              row("popup.shortcuts.redo", kbd(primaryModLabel), " + ", kbd("Shift"), " + ", kbd("Z")),
              row("popup.shortcuts.save", kbd(primaryModLabel), " + ", kbd("S")),
              row("popup.shortcuts.deselectAll", kbd("Esc")),
              row("popup.shortcuts.zoomIn", kbd("+")),
              row("popup.shortcuts.zoomOut", kbd("-")),
              row("popup.shortcuts.rotateLeft", kbd("E")),
              row("popup.shortcuts.rotateRight", kbd("R")),
              row("popup.shortcuts.fitToCanvas", kbd("F"))
            )
          )
        )
      )
    )

  private def row(actionKey: String, shortcut: Modifier[HtmlElement]*): Element =
    tr(
      td(child.text <-- I18n.t(actionKey)),
      td(shortcut*)
    )
