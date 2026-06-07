package art.tessell.editor.android

import android.content.Intent
import android.webkit.{WebResourceRequest, WebResourceResponse, WebView, WebViewClient}
import androidx.webkit.WebViewAssetLoader

/** Serves bundled assets through the loader's synthetic https origin and routes
  * any off-origin navigation (e.g. the About popup's GitHub links) to the
  * system browser, keeping the in-app WebView scoped to local content.
  */
class WebAssetClient(assetLoader: WebViewAssetLoader) extends WebViewClient:

  override def shouldInterceptRequest(
      view: WebView,
      request: WebResourceRequest
  ): WebResourceResponse =
    assetLoader.shouldInterceptRequest(request.getUrl)

  override def shouldOverrideUrlLoading(
      view: WebView,
      request: WebResourceRequest
  ): Boolean =
    val uri = request.getUrl
    if uri.getHost == WebAssetClient.AssetHost then
      false // let the WebView load it via shouldInterceptRequest
    else
      view.getContext.startActivity(
        new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
      true

object WebAssetClient:
  private val AssetHost = "appassets.androidplatform.net"
