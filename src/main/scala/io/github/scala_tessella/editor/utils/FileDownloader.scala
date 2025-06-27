package io.github.scala_tessella.editor.utils

import org.scalajs.dom
import org.scalajs.dom.{Blob, BlobPropertyBag}

import scala.scalajs.js

object FileDownloader:

  def trigger(content: String, filename: String, mimeType: String): Unit =
    val blobPropertyBag = new BlobPropertyBag { `type` = mimeType }
    val blob = new Blob(js.Array(content), blobPropertyBag)
    val url = dom.URL.createObjectURL(blob)
    val a = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
    a.href = url
    a.download = filename
    dom.document.body.appendChild(a)
    a.click()
    dom.document.body.removeChild(a)
    dom.URL.revokeObjectURL(url)
