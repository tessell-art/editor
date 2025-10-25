package io.github.scala_tessella.editor.components.popup

import com.raquo.laminar.api.L._
import com.raquo.laminar.api.features.unitArrows
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.buildinfo.BuildInfo

object AboutPopup:

  import PopupCommons._

  private val closeAbout: Observer[org.scalajs.dom.MouseEvent] =
    closePopup(EditorState.showAboutPopup)

  def element: Element =
    div(
      className := "popup-overlay",
      display <-- EditorState.showAboutPopup.signal.map(if _ then "flex" else "none"),
      onClick --> closeAbout,
      div(
        className := "popup-content",
        onClick.stopPropagation --> {},
        button(
          className := "popup-close-btn",
          onClick --> closeAbout,
          closeIcon
        ),
        img(
          src       := "tessella-logo.svg",
          alt       := "Tessella Logo",
          className := "popup-logo"
        ),
        h1("Tessella"),
        p(
          className := "about-version",
          s"Editor v${BuildInfo.version}"
        ),
        h2("Simple polygon tessellation editor"),
        div(
          className := "popup-text-scrollable",
          p(
            "Interactively create, view, and manipulate tessellations of the plane made of simple (regular and irregular) polygons."
          ),
          p(
            "The editor is part of the ",
            b("scala-tessella"),
            " project. For more information, and to contribute, please visit the ",
            a(
              href   := "https://github.com/scala-tessella",
              target := "_blank",
              rel    := "noopener noreferrer",
              "GitHub organization"
            ),
            "."
          ),
          p("Built with Scala.js and Laminar.")
        )
      )
    )
