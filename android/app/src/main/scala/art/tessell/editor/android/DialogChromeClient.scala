package art.tessell.editor.android

import android.app.AlertDialog
import android.content.Context
import android.webkit.{JsPromptResult, WebChromeClient, WebView}
import android.widget.EditText

import art.tessell.editor.R

/** Replaces the default JS-prompt dialog — whose title is the asset-loader URL
  * ("The page at https://appassets.androidplatform.net says:") — with a clean
  * dialog titled with the app name. The app's only JS dialog is the
  * "Save SVG as…" filename prompt (`window.prompt`).
  */
class DialogChromeClient(context: Context) extends WebChromeClient:

  override def onJsPrompt(
      view: WebView,
      url: String,
      message: String,
      defaultValue: String,
      result: JsPromptResult
  ): Boolean =
    val input = new EditText(context)
    input.setSingleLine(true)
    input.setText(defaultValue)
    input.setSelection(input.getText.length)
    new AlertDialog.Builder(context)
      .setTitle(R.string.app_name)
      .setMessage(message)
      .setView(input)
      .setPositiveButton(android.R.string.ok, (_, _) => result.confirm(input.getText.toString))
      .setNegativeButton(android.R.string.cancel, (_, _) => result.cancel())
      .setOnCancelListener(_ => result.cancel())
      .show()
    true
