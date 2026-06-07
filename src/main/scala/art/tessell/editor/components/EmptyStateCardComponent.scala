package art.tessell.editor.components

import art.tessell.editor.AppState
import art.tessell.editor.i18n.I18n
import com.raquo.laminar.api.L.*

/** Centered card shown when the canvas has no tessellation. Replaces the previous in-SVG "Empty tessellation"
  * / "Add the next polygon" overlay text.
  *
  * Three slots per the wireframes:
  *   - "Pick a shape from the palette" — informational hint pointing at the palette.
  *   - "Open template…" — opens the template gallery.
  *   - "Load SVG…" — triggers the file picker via `SvgImporter`.
  *
  * The card is hidden as soon as the tiling becomes non-empty; once at least one polygon is on the canvas,
  * the mode badge is the only persistent on-canvas hint.
  */
object EmptyStateCardComponent:

  def element: Element =
    div(
      className := "empty-state-card",
      h2(className := "empty-state-title", child.text <-- I18n.t("emptyState.title")),
      p(
        className  := "empty-state-hint",
        span(child.text <-- I18n.t("emptyState.hint"))
//        " ",
//        span(className := "empty-state-arrow", "→")
      ),
      div(
        className  := "empty-state-divider",
        span(child.text <-- I18n.t("emptyState.divider"))
      ),
      div(
        className  := "empty-state-actions",
        button(
          tpe       := "button",
          className := "empty-state-action",
          onClick --> { _ =>

            AppState.openTemplateGallery()
          },
          child.text <-- I18n.t("emptyState.openTemplate")
        ),
        button(
          tpe       := "button",
          className := "empty-state-action empty-state-action--primary",
          onClick --> { _ =>

            AppState.loadSvgFile()
          },
          child.text <-- I18n.t("emptyState.loadSvg")
        )
      )
    )
