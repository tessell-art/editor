package art.tessell.editor.utils.file

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.{Blob, BlobPropertyBag}

import scala.scalajs.js

object FileDownloader:

  def trigger(content: String, filename: String, mimeType: String): Unit =
    val androidBridge = js.Dynamic.global.TessellaAndroid
    if !js.isUndefined(androidBridge) && androidBridge != null then
      androidSave(androidBridge, content, filename, mimeType)
    else
      browserDownload(content, filename, mimeType)

  /** Android WebView shell: DownloadManager cannot fetch the `blob:` URLs the browser path produces, so hand
    * the bytes to the native `saveBase64` bridge instead. `btoa` is Latin-1 only, hence the UTF-8 round-trip.
    */
  private def androidSave(bridge: js.Dynamic, content: String, filename: String, mimeType: String): Unit =
    val base64 = dom.window.btoa(
      js.Dynamic.global
        .unescape(js.Dynamic.global.encodeURIComponent(content))
        .asInstanceOf[String]
    )
    bridge.saveBase64(filename, mimeType, base64)

  private def browserDownload(content: String, filename: String, mimeType: String): Unit =
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
