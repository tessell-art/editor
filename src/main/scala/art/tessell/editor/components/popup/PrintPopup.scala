package art.tessell.editor.components.popup

import art.tessell.editor.i18n.I18n
import art.tessell.editor.models.{EditorState, Orientation, PaperSize}
import art.tessell.editor.utils.file.PrintPdfExporter
import com.raquo.laminar.api.L.*

/** Print-to-PDF popup. Two radio groups (paper size + orientation) and a Print button that triggers the
  * browser's print dialog with the chosen page size pre-applied.
  *
  * The browser's print dialog is the actual user-facing destination chooser; "Save as PDF" lives inside that
  * dialog. This popup is just a guided pre-step so the user lands in print preview with the right page setup.
  */
object PrintPopup:

  import PopupCommons._

  private val close: Observer[Any] =
    closePopup(EditorState.popupState.update(_.copy(showPrintPopup = false)))

  def element: Element =
    val paperSize   = Var(PaperSize.A4)
    val orientation = Var(Orientation.Portrait)

    popupOverlay(close, overlayClassName = "popup-overlay print-popup")(
      popupContent(close, contentClassName = "popup-content print-content")(
        h2(child.text <-- I18n.t("popup.print.title")),
        div(
          className := "settings-grid",
          paperRow(paperSize),
          orientationRow(orientation),
          fitRow()
        ),
        div(
          className := "popup-actions",
          button(
            child.text <-- I18n.t("common.cancel"),
            onClick --> { _ =>

              EditorState.popupState.update(_.copy(showPrintPopup = false))
            }
          ),
          button(
            child.text <-- I18n.t("common.print"),
            onClick.compose(stream =>
              stream
                .withCurrentValueOf(paperSize.signal)
                .withCurrentValueOf(orientation.signal)
            ) --> { case (_, paper, orient) =>

              EditorState.popupState.update(_.copy(showPrintPopup = false))
              PrintPdfExporter.printToPdf(paper, orient)
            }
          )
        )
      )
    )

  private def paperRow(paperSize: Var[PaperSize]): Element =
    div(
      className := "settings-row",
      div(className := "settings-label", child.text <-- I18n.t("popup.print.paper")),
      div(
        className   := "settings-control settings-radio-group",
        radioOption("popup.print.paper.a4", PaperSize.A4, paperSize),
        radioOption("popup.print.paper.letter", PaperSize.Letter, paperSize)
      )
    )

  private def radioOption[A](
      labelKey: String,
      value: A,
      selected: Var[A]
  ): Element =
    label(
      className := "settings-radio-label",
      input(
        tpe := "radio",
        checked <-- selected.signal.map(_ == value),
        onChange --> { _ =>

          selected.set(value)
        }
      ),
      span(child.text <-- I18n.t(labelKey))
    )

  private def orientationRow(orientation: Var[Orientation]): Element =
    div(
      className := "settings-row",
      div(className := "settings-label", child.text <-- I18n.t("popup.print.orientation")),
      div(
        className   := "settings-control settings-radio-group",
        radioOption("popup.print.orientation.portrait", Orientation.Portrait, orientation),
        radioOption("popup.print.orientation.landscape", Orientation.Landscape, orientation)
      )
    )

  private def fitRow(): Element =
    div(
      className := "settings-row",
      div(className := "settings-label", child.text <-- I18n.t("popup.print.fit")),
      div(
        className   := "settings-control",
        // Single fixed option for v1 — poster-tiling and explicit scale aren't in scope.
        span(child.text <-- I18n.t("popup.print.fit.toPage"))
      )
    )
