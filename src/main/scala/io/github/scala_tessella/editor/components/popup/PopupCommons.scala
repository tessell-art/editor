package io.github.scala_tessella.editor.components.popup

import com.raquo.laminar.api.L._
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

  // Platform-aware primary modifier label
  val primaryModLabel: String =
    if dom.window.navigator.platform.toLowerCase.contains("mac") then "⌘" else "Ctrl"
