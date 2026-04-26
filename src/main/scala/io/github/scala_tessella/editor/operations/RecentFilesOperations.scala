package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.models.{EditorState, RecentFile}
import io.github.scala_tessella.editor.utils.RecentFilesStorage

import scala.scalajs.js

/** Updates the recent-files state and persists to localStorage. Cap and dedup logic lives here so
  * `RecentFilesStorage` stays a thin storage layer.
  */
object RecentFilesOperations:

  private val maxEntries = 10

  /** Records a successful file open. Existing entries with the same `path` are replaced (the timestamp
    * refreshes), the new entry moves to the head, and the list is capped at 10.
    */
  def record(path: String, filename: String): Unit =
    val now        = js.Date.now().toLong
    val entry      = RecentFile(path, filename, now)
    val current    = EditorState.recentFilesState.now()
    val withoutDup = current.filterNot(_.path == path)
    val updated    = (entry :: withoutDup).take(maxEntries)
    RecentFilesStorage.save(updated)
    EditorState.recentFilesState.set(updated)

  /** Clears all recent-file entries. Used by a hypothetical "Clear recent" affordance (not wired in v1). */
  def clear(): Unit =
    RecentFilesStorage.clear()
    EditorState.recentFilesState.set(List.empty)
