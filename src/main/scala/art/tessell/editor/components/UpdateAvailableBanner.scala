package art.tessell.editor.components

import art.tessell.editor.i18n.I18n
import art.tessell.editor.operations.UpdateChecker
import com.raquo.laminar.api.L.*

/** Thin strip surfaced when `UpdateChecker` detects a newer published bundle. Two actions: **Reload**
  * (primary, dirty-state-aware via `UpdateChecker.reloadNow`) and **Dismiss** (×, suppresses the announcement
  * for the rest of the session). Renders nothing when there is no pending update.
  */
object UpdateAvailableBanner:

  def element: Element =
    div(
      child.maybe <-- UpdateChecker.latestVersion.map:
        case Some(version) => Some(banner(version))
        case None          => None
    )

  private def banner(newVersion: String): Element =
    div(
      role      := "status",
      aria.live := "polite",
      className := "update-available-banner",
      span(
        className := "update-available-banner-text",
        child.text <-- I18n.t("update.available.message"),
        span(
          className := "update-available-banner-version",
          newVersion
        )
      ),
      button(
        tpe       := "button",
        className := "update-available-banner-reload",
        child.text <-- I18n.t("update.available.reload"),
        onClick --> { _ =>

          UpdateChecker.reloadNow()
        }
      ),
      button(
        tpe       := "button",
        className := "update-available-banner-dismiss",
        aria.label <-- I18n.t("update.available.dismissAriaLabel"),
        "×",
        onClick --> { _ =>

          UpdateChecker.dismiss()
        }
      )
    )
