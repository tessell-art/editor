package art.tessell.editor.components.popup

import art.tessell.editor.buildinfo.BuildInfo
import art.tessell.editor.i18n.I18n
import art.tessell.editor.models.EditorState
import com.raquo.laminar.api.L._

object AboutPopup:

  import PopupCommons._

  private val closeAbout: Observer[Any] =
    closePopup(EditorState.popupState.update(_.copy(showAboutPopup = false)))

  private def tokens: Map[String, () => Element] =
    Map(
      "projectName" -> (() => strong("scala-tessella")),
      "ghLink"      ->
        (() =>
          a(
            href   := "https://github.com/scala-tessella",
            target := "_blank",
            rel    := "noopener noreferrer",
            I18n.tNow("popup.about.link.github")
          )
        ),
      "licenseLink" ->
        (() =>
          a(
            href   := "https://github.com/tessell-art/editor/blob/main/LICENSE",
            target := "_blank",
            rel    := "noopener noreferrer",
            I18n.tNow("popup.about.link.license")
          )
        ),
      "sourceLink"  ->
        (() =>
          a(
            href   := "https://github.com/tessell-art/editor",
            target := "_blank",
            rel    := "noopener noreferrer",
            I18n.tNow("popup.about.link.source")
          )
        )
    )

  def element: Element =
    popupOverlay(closeAbout)(
      popupContent(closeAbout)(
        img(
          src       := "tessella-logo.svg",
          alt <-- I18n.t("popup.about.imageAlt"),
          className := "popup-logo"
        ),
        h1(child.text <-- I18n.t("popup.about.title")),
        p(
          className := "about-version",
          child.text <-- I18n.t("popup.about.versionFmt", BuildInfo.version)
        ),
        h2(child.text <-- I18n.t("popup.about.tagline")),
        div(
          className := "popup-text-scrollable",
          tabIndex  := 0,
          aria.label <-- I18n.t("popup.about.bodyAriaLabel"),
          p(child.text <-- I18n.t("popup.about.body.intro")),
          p(I18n.tFragments("popup.about.body.project", tokens)),
          p(child.text <-- I18n.t("popup.about.body.builtWith")),
          p(
            className := "about-license",
            I18n.tFragments("popup.about.body.license", tokens)
          )
        )
      )
    )
