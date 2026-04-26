package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.models.RecentFile
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.JSON

/** localStorage CRUD for the recently-opened files list (templates loaded via the gallery, etc.).
  *
  * Encoded as a JSON array of `{path, filename, lastOpenedAt}` objects under a single key. Caps and deduping
  * live in [[io.github.scala_tessella.editor.operations.RecentFilesOperations]] so this object stays a thin
  * persistence layer.
  */
object RecentFilesStorage:

  private val key = "tessella.recentFiles"

  def load(): List[RecentFile] =
    readRaw() match
      case None      => List.empty
      case Some(raw) => parse(raw)

  def save(entries: List[RecentFile]): Unit =
    try dom.window.localStorage.setItem(key, serialize(entries))
    catch case _: Throwable => ()

  def clear(): Unit =
    try dom.window.localStorage.removeItem(key)
    catch case _: Throwable => ()

  private def readRaw(): Option[String] =
    try Option(dom.window.localStorage.getItem(key))
    catch case _: Throwable => None

  private def serialize(entries: List[RecentFile]): String =
    val arr = js.Array[js.Any]()
    entries.foreach: e =>
      arr.push(js.Dynamic.literal(
        "path"         -> e.path,
        "filename"     -> e.filename,
        "lastOpenedAt" -> e.lastOpenedAt.toDouble
      ))
    JSON.stringify(arr)

  private def parse(raw: String): List[RecentFile] =
    try
      val arr = JSON.parse(raw).asInstanceOf[js.Array[js.Dynamic]]
      arr.toList.flatMap: o =>
        try
          val path = o.path.asInstanceOf[String]
          val fn   = o.filename.asInstanceOf[String]
          val ts   = o.lastOpenedAt.asInstanceOf[Double].toLong
          if path != null && fn != null then Some(RecentFile(path, fn, ts)) else None
        catch case _: Throwable => None
    catch case _: Throwable => List.empty
