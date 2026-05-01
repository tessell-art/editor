package io.github.scala_tessella.editor.components.popup

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.i18n.I18n
import io.github.scala_tessella.editor.operations.DirtyTracker

/** Modal that gates destructive actions (New, Load, template, recent reload, browser close) when the current
  * tiling has unsaved changes. Three resolutions:
  *   - Cancel — drop the pending action, keep editing.
  *   - Discard — run the pending action without saving.
  *   - Save — save first, then run the pending action.
  *
  * Driven by `popupState.showUnsavedConfirm`; the buttons all dispatch into `DirtyTracker`.
  */
object UnsavedChangesPopup:

  import PopupCommons._

  private val cancelObserver: Observer[Any] =
    Observer { _ =>

      DirtyTracker.cancel()
    }

  def element: Element =
    popupOverlay(cancelObserver, overlayClassName = "popup-overlay unsaved-confirm-overlay")(
      popupContent(cancelObserver, contentClassName = "popup-content unsaved-confirm")(
        h2(child.text <-- I18n.t("unsaved.title")),
        p(
          className := "unsaved-confirm-body",
          child.text <-- I18n.t("unsaved.body")
        ),
        div(
          className := "unsaved-confirm-actions",
          button(
            tpe       := "button",
            className := "unsaved-confirm-btn unsaved-confirm-btn--secondary",
            onClick --> { _ =>

              DirtyTracker.cancel()
            },
            child.text <-- I18n.t("unsaved.cancel")
          ),
          button(
            tpe       := "button",
            className := "unsaved-confirm-btn unsaved-confirm-btn--danger",
            onClick --> { _ =>

              DirtyTracker.discardAndRun()
            },
            child.text <-- I18n.t("unsaved.discard")
          ),
          button(
            tpe       := "button",
            className := "unsaved-confirm-btn unsaved-confirm-btn--primary",
            onClick --> { _ =>

              DirtyTracker.saveAndRun()
            },
            child.text <-- I18n.t("unsaved.save")
          )
        )
      )
    )
