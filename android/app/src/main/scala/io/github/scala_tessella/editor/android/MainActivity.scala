package io.github.scala_tessella.editor.android

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader

/** Single-activity WebView shell hosting the bundled Vite `dist/` build.
  *
  * Assets are served through [[WebViewAssetLoader]] over a synthetic https
  * origin rather than `file://`: Chromium (and therefore the Android WebView)
  * refuses to load `type="module"` scripts over `file://` for CORS reasons,
  * which the Vite output relies on. See ADR-005.
  */
class MainActivity extends Activity:

  private var webView: WebView = scala.compiletime.uninitialized

  override protected def onCreate(savedInstanceState: Bundle): Unit =
    super.onCreate(savedInstanceState)

    val assetLoader =
      new WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
        .build()

    val wv       = new WebView(this)
    val settings = wv.getSettings
    settings.setJavaScriptEnabled(true)
    settings.setDomStorageEnabled(true)        // localStorage user prefs persist
    settings.setAllowFileAccess(false)         // assets come via the loader, not file://
    settings.setAllowContentAccess(false)
    settings.setMediaPlaybackRequiresUserGesture(false)
    settings.setBuiltInZoomControls(false)     // canvas owns pinch-zoom, not the WebView
    settings.setSupportZoom(false)

    wv.setWebViewClient(new WebAssetClient(assetLoader))
    // WebChromeClient so window.prompt/alert/confirm surface native dialogs
    // (the "Save SVG as…" filename prompt relies on window.prompt); the custom
    // client drops the default URL-as-title for a clean app-titled dialog.
    wv.setWebChromeClient(new DialogChromeClient(this))
    wv.addJavascriptInterface(new WebAppInterface(this), WebAppInterface.Name)
    wv.setLayoutParams(
      new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    )

    webView = wv
    setContentView(wv)
    wv.loadUrl(MainActivity.IndexUrl)

  // Back navigates WebView history first; only exits the app at the root.
  override def onBackPressed(): Unit =
    if webView != null && webView.canGoBack then webView.goBack()
    else super.onBackPressed()

object MainActivity:
  private val IndexUrl =
    "https://appassets.androidplatform.net/assets/index.html"
