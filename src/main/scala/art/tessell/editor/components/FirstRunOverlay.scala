package art.tessell.editor.components

import art.tessell.editor.AppState
import art.tessell.editor.i18n.I18n
import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.unitArrows

/** First-run welcome overlay — shown only the very first time the app loads. Distinct from the empty-state
  * card (which always shows when the canvas is empty); this one is a one-shot orientation surface for
  * brand-new users.
  *
  * Three CTA cards: Blank canvas, From a template, Open SVG. Clicking any one dismisses the overlay (and
  * persists the "seen" flag) and runs its action. There is no explicit "skip" — picking "Blank canvas" is the
  * implicit skip path.
  */
object FirstRunOverlay:

  def element: Element =
    div(
      className := "first-run-overlay",
      // Backdrop click is NOT a dismissal — the user must pick one of the three paths so the
      // welcome message gets read at least once.
      onClick.stopPropagation --> {},
      div(
        className := "first-run-card",
        h1(className := "first-run-title", child.text <-- I18n.t("firstRun.title")),
        p(
          className  := "first-run-subtitle",
          child.text <-- I18n.t("firstRun.subtitle")
        ),
        div(
          className  := "first-run-cards",
          ctaCard(
            "firstRun.blank.headline",
            "firstRun.blank.body",
            blankCanvasIcon,
            () => AppState.dismissFirstRun()
          ),
          ctaCard(
            "firstRun.template.headline",
            "firstRun.template.body",
            templatesIcon,
            () =>

              AppState.openTemplateGallery()
              AppState.dismissFirstRun()
          ),
          ctaCard(
            "firstRun.openSvg.headline",
            "firstRun.openSvg.body",
            openFileIcon,
            () =>

              AppState.loadSvgFile()
              AppState.dismissFirstRun()
          )
        )
      )
    )

  private def ctaCard(headlineKey: String, bodyKey: String, icon: Element, onSelect: () => Unit): Element =
    button(
      tpe       := "button",
      className := "first-run-cta",
      onClick --> { _ =>

        onSelect()
      },
      div(className := "first-run-cta-icon", icon),
      div(className := "first-run-cta-headline", child.text <-- I18n.t(headlineKey)),
      div(className := "first-run-cta-body", child.text <-- I18n.t(bodyKey))
    )

  // Lightweight SVG glyphs — no dependency on IconsSVG, kept here so the overlay's identity isn't
  // tied to the rest of the toolbar iconography. Phase 5 polish can replace with shared assets.

  private def blankCanvasIcon: Element =
    svg.svg(
      svg.viewBox     := "0 0 24 24",
      svg.fill        := "none",
      svg.stroke      := "currentColor",
      svg.strokeWidth := "1.5",
      svg.rect(svg.x := "4", svg.y := "4", svg.width := "16", svg.height := "16", svg.rx := "1.5")
    )

  private def templatesIcon: Element =
    svg.svg(
      svg.viewBox     := "0 0 24 24",
      svg.fill        := "none",
      svg.stroke      := "currentColor",
      svg.strokeWidth := "1.5",
      // Three small squares in a 2-row grid suggesting a gallery
      svg.rect(svg.x := "3", svg.y  := "4", svg.width  := "8", svg.height := "8", svg.rx := "1"),
      svg.rect(svg.x := "13", svg.y := "4", svg.width  := "8", svg.height := "8", svg.rx := "1"),
      svg.rect(svg.x := "3", svg.y  := "14", svg.width := "8", svg.height := "8", svg.rx := "1")
    )

  private def openFileIcon: Element =
    svg.svg(
      svg.viewBox     := "0 0 24 24",
      svg.fill        := "none",
      svg.stroke      := "currentColor",
      svg.strokeWidth := "1.5",
      // Folder shape
      svg.path(svg.d := "M 3 7 L 3 19 L 21 19 L 21 9 L 12 9 L 10 7 Z")
    )
