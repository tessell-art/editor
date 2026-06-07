package art.tessell.editor.utils

import art.tessell.editor.i18n.Locale
import org.scalajs.dom

/** localStorage persistence for the user's selected UI locale. Key is read once on app boot to initialize
  * `EditorState.localeState`; the language toggle in the top bar writes back through [[save]]. Failures are
  * swallowed so a quirky storage configuration can't crash startup.
  */
object LocaleStorage:

  private val key = "tessella.locale"

  def load(): Locale =
    try
      Option(dom.window.localStorage.getItem(key))
        .flatMap(Locale.fromCode)
        .getOrElse(Locale.default)
    catch
      case _: Throwable => Locale.default

  def save(locale: Locale): Unit =
    try dom.window.localStorage.setItem(key, locale.code)
    catch case _: Throwable => ()
