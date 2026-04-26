package io.github.scala_tessella.editor.components.popup

import com.raquo.laminar.api.L._
import io.github.scala_tessella.editor.i18n.I18n
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.buildinfo.BuildInfo

object AboutPopup:

  import PopupCommons._

  private val closeAbout: Observer[org.scalajs.dom.MouseEvent] =
    closePopup(EditorState.popupState.update(_.copy(showAboutPopup = false)))

  def element: Element =
    popupOverlay(closeAbout)(
      popupContent(closeAbout)(
        img(
          src       := "tessella-logo.svg",
          alt       := "Tessella Logo",
          className := "popup-logo"
        ),
        h1(child.text <-- I18n.t("popup.about.title")),
        p(
          className := "about-version",
          child.text <-- I18n.t("popup.about.versionFmt", BuildInfo.version)
        ),
        h2(child.text <-- I18n.t("popup.about.tagline")),
        // TODO i18n long-form: the body paragraphs below stay English in v1; mixed inline links + bold
        // make per-fragment keys clumsy. Translate when the i18n pipeline gains rich-text support.
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
