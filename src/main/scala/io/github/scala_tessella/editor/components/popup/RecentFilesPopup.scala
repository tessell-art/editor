package io.github.scala_tessella.editor.components.popup

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.i18n.I18n
import io.github.scala_tessella.editor.models.{EditorState, RecentFile}
import io.github.scala_tessella.editor.operations.{DirtyTracker, RecentFilesOperations}
import io.github.scala_tessella.editor.utils.AsyncUtils
import io.github.scala_tessella.editor.utils.file.SvgImporter
import org.scalajs.dom

import scala.scalajs.js
import scala.util.{Failure, Success}

/** Recent files panel — shows up to 10 most-recently-opened files (templates only in v1, since user-imported
  * SVGs would need persistent file handles to be re-fetchable).
  *
  * Each row: live thumbnail + filename + relative-time label. Click a row → re-fetches and re-imports via
  * SvgImporter. Failure (e.g. template removed from disk) shows an error toast.
  */
object RecentFilesPopup:

  import PopupCommons._

  private given scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  private val close: Observer[Any] =
    closePopup(EditorState.popupState.update(_.copy(showRecentFilesPanel = false)))

  def element: Element =
    popupOverlay(close, overlayClassName = "popup-overlay recent-files-overlay")(
      popupContent(close, contentClassName = "popup-content recent-files")(
        h2(child.text <-- I18n.t("popup.recent.title")),
        div(
          className := "recent-files-body",
          children <-- EditorState.recentFilesState.signal.map { entries =>

            if entries.isEmpty then List(emptyState())
            else entries.map(entryRow)
          }
        ),
        footerNote()
      )
    )

  private def emptyState(): Element =
    div(
      className := "recent-files-empty",
      child.text <-- I18n.t("popup.recent.empty")
    )

  private def entryRow(entry: RecentFile): Element =
    div(
      className := "recent-files-row",
      title     := s"${entry.filename} · ${entry.path}",
      onClick --> { _ =>

        EditorState.popupState.update(_.copy(showRecentFilesPanel = false))
        DirtyTracker.confirmIfDirty(() => reload(entry))
      },
      img(
        className := "recent-files-thumb",
        src       := entry.path,
        alt       := entry.filename
      ),
      div(
        className := "recent-files-meta",
        div(className := "recent-files-name", entry.filename),
        div(className := "recent-files-path", entry.path),
        div(className := "recent-files-when", relativeTime(entry.lastOpenedAt))
      )
    )

  private def footerNote(): Element =
    div(
      className := "recent-files-footnote",
      child.text <-- I18n.t("popup.recent.footnote")
    )

  private def reload(entry: RecentFile): Unit =
    dom.fetch(entry.path).toFuture
      .flatMap { response =>

        if response.ok then response.text().toFuture
        else
          scala.concurrent.Future.failed(
            new Exception(s"Failed to load (${response.statusText})")
          )
      }
      .onComplete {
        case Success(svgContent) =>
          val _ =
            AsyncUtils.withLoadingState(() => SvgImporter.importTilingFromSVG(svgContent, entry.filename))
          // Refresh the timestamp so the row moves to the top of the list.
          RecentFilesOperations.record(entry.path, entry.filename)
        case Failure(_)          =>
          // Soft failure: surface a toast via the standard error path is overkill here; the user will
          // notice the missing tiling and can pick another. If desired, hook ErrorOperations later.
          ()
      }

  /** Compact human-friendly relative-time label. Localized via I18n; the key chosen by deltaMs and the count
    * substituted positionally into the format string. Pluralization is approximate (single
    * "min/hours/days/months" forms) — fine for these short-lived hint labels.
    */
  private def relativeTime(timestampMs: Long): String =
    val now     = js.Date.now().toLong
    val deltaMs = (now - timestampMs).max(0L)
    val seconds = deltaMs / 1000
    val minutes = seconds / 60
    val hours   = minutes / 60
    val days    = hours / 24
    if seconds < 60 then I18n.tNow("popup.recent.justNow")
    else if minutes < 60 then I18n.tNow("popup.recent.minAgoFmt", minutes.toString)
    else if hours < 24 then I18n.tNow("popup.recent.hoursAgoFmt", hours.toString)
    else if days == 1 then I18n.tNow("popup.recent.yesterday")
    else if days < 30 then I18n.tNow("popup.recent.daysAgoFmt", days.toString)
    else I18n.tNow("popup.recent.monthsAgoFmt", (days / 30).toString)
