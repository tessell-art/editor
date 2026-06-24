package art.tessell.editor.android

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.WebView
import android.widget.FrameLayout
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
      new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    )

    // targetSdk 35 forces edge-to-edge from Android 15 on: without this the
    // content draws under the status/navigation bars and the top toolbar (menu,
    // language and mode switches) ends up buried beneath the system clock and
    // notification icons. Host the WebView in a padded container and inset that
    // container by the system-bar + display-cutout insets so the page lays out
    // inside the safe area. Padding is applied to the container, not the WebView
    // itself: Chromium ignores WebView padding for content layout, but a parent
    // ViewGroup's padding repositions its child. getInsets(Type) is API 30+, so
    // fall back to the deprecated accessors for our minSdk 29 floor.
    val root = new FrameLayout(this)
    // The status-bar strip exposed by the top inset sits directly above the
    // app shell's top bar, which is --menu-bg (#1f2329) and intentionally dark
    // in both light and dark themes. Paint the container the same colour so the
    // strip merges into one continuous bar instead of flashing white in dark
    // mode. Status-bar icons are set light in themes.xml to match.
    root.setBackgroundColor(0xFF1F2329)
    root.addView(wv)
    root.setOnApplyWindowInsetsListener { (v: View, insets: WindowInsets) =>
      val (l, t, r, b) =
        if Build.VERSION.SDK_INT >= Build.VERSION_CODES.R then
          val bars =
            insets.getInsets(WindowInsets.Type.systemBars | WindowInsets.Type.displayCutout)
          (bars.left, bars.top, bars.right, bars.bottom)
        else
          (insets.getSystemWindowInsetLeft, insets.getSystemWindowInsetTop,
           insets.getSystemWindowInsetRight, insets.getSystemWindowInsetBottom)
      v.setPadding(l, t, r, b)
      insets
    }

    webView = wv
    setContentView(root)
    wv.loadUrl(MainActivity.IndexUrl)

  // Back navigates WebView history first; only exits the app at the root.
  override def onBackPressed(): Unit =
    if webView != null && webView.canGoBack then webView.goBack()
    else super.onBackPressed()

object MainActivity:
  private val IndexUrl =
    "https://appassets.androidplatform.net/assets/index.html"
