package io.github.scala_tessella.editor.utils.file

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.{Blob, BlobPropertyBag}

import scala.scalajs.js

object FileDownloader:

  def trigger(content: String, filename: String, mimeType: String): Unit =
    val blobPropertyBag = new BlobPropertyBag { `type` = mimeType }
    val blob            = new Blob(js.Array(content), blobPropertyBag)
    val url             = dom.URL.createObjectURL(blob)
    // Create a Laminar anchor with proper attributes, then click it
    val anchorEl        = a(
      href     := url,
      download := filename,
      // keep it out of flow; not strictly necessary to mount
      display  := "none"
    ).ref

    dom.document.body.appendChild(anchorEl): Unit
    anchorEl.click()
    dom.document.body.removeChild(anchorEl): Unit
    dom.URL.revokeObjectURL(url)
