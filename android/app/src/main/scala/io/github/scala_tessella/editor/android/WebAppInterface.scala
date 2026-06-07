package io.github.scala_tessella.editor.android

import android.content.ContentValues
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast

import java.io.OutputStream

/** Minimal, single-purpose JS bridge. The WebView only ever loads vetted,
  * in-APK assets (all external navigation is handed to the system browser by
  * [[WebAssetClient]]), so the classic "malicious remote JS" risk that argues
  * against `@JavascriptInterface` does not apply here.
  *
  * The web app's `FileDownloader` calls [[saveBase64]] to persist an exported
  * file (SVG / DOT) into the public Downloads collection, since Android's
  * DownloadManager cannot fetch the `blob:` URLs the browser path uses.
  */
class WebAppInterface(activity: MainActivity):

  @JavascriptInterface
  def saveBase64(filename: String, mimeType: String, base64Data: String): Unit =
    val bytes    = Base64.decode(base64Data, Base64.DEFAULT)
    val resolver = activity.getContentResolver

    val values = new ContentValues()
    // DISPLAY_NAME is declared on MediaColumns; Scala interop does not resolve
    // it through the Downloads sub-interface (unlike Java).
    // MIME_TYPE is intentionally NOT set: MediaStore infers it from the
    // filename extension and would otherwise append a second extension when the
    // supplied MIME doesn't match (e.g. "tessellation.gv" + text/plain →
    // "tessellation.gv.txt"). Inferring keeps ".gv"/".svg" as-is.
    values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)

    val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    if itemUri == null then toast(s"Could not save $filename")
    else
      var out: OutputStream = null
      try
        out = resolver.openOutputStream(itemUri)
        if out != null then out.write(bytes)
        toast(s"Saved $filename to Downloads")
      catch
        case _: Throwable => toast(s"Could not save $filename")
      finally if out != null then out.close()

  private def toast(message: String): Unit =
    activity.runOnUiThread(() => Toast.makeText(activity, message, Toast.LENGTH_SHORT).show())

object WebAppInterface:
  /** The `window` global name the bundle checks for / calls. */
  val Name = "TessellaAndroid"
