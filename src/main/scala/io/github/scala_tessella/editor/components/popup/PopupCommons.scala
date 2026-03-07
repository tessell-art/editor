package io.github.scala_tessella.editor.components.popup

import com.raquo.laminar.api.L._
import com.raquo.laminar.api.features.unitArrows
import org.scalajs.dom

object PopupCommons:

  // Simple 'X' close icon
  def closeIcon: Element =
    svg.svg(
      svg.width       := "24",
      svg.height      := "24",
      svg.viewBox     := "0 0 24 24",
      svg.fill        := "none",
      svg.stroke      := "currentColor",
      svg.strokeWidth := "2",
      svg.path(svg.d := "M 18 6 L 6 18"),
      svg.path(svg.d := "M 6 6 L 18 18")
    )

  // Factory to produce a close handler bound to a Var[Boolean]
  def closePopup(state: Var[Boolean]): Observer[dom.MouseEvent] = Observer { _ =>

    state.set(false)
  }

  def popupOverlay(
      closeObserver: Observer[dom.MouseEvent],
      overlayClassName: String = "popup-overlay",
      extraMods: Modifier[HtmlElement]*
  )(children: Modifier[HtmlElement]*): Element =
    val mods =
      List[Modifier[HtmlElement]](
        className := overlayClassName,
        onClick --> closeObserver
      ) ++ extraMods.toList ++ children.toList
    div(mods*)

  def popupContent(
      closeObserver: Observer[dom.MouseEvent],
      contentClassName: String = "popup-content"
  )(children: Modifier[HtmlElement]*): Element =
    val mods =
      List[Modifier[HtmlElement]](
        className := contentClassName,
        onClick.stopPropagation --> {},
        button(
          className := "popup-close-btn",
          onClick --> closeObserver,
          closeIcon
        )
      ) ++ children.toList
    div(mods*)

  // Platform-aware primary modifier label
  val primaryModLabel: String =
    if dom.window.navigator.platform.toLowerCase.contains("mac") then "⌘" else "Ctrl"
