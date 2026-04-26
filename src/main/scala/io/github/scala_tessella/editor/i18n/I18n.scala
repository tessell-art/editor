package io.github.scala_tessella.editor.i18n

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.models.EditorState

/** Translation lookup. Two flavors:
  *
  *   - [[t]] — `Signal[String]` for use inside Laminar bindings (`child.text <-- t("…")`,
  *     `title <-- t("…")`). Re-evaluates when the user changes locale.
  *   - [[tNow]] — synchronous `String` for places without signal context (browser dialogs, error messages,
  *     log lines).
  *
  * Lookup order: current locale → English → `!key!` (visible marker so untranslated strings surface in QA
  * rather than render as empty text).
  *
  * Format strings use `{0}`, `{1}`, … placeholders that are substituted positionally from the `args` vararg.
  */
object I18n:

  def tNow(key: String, args: String*): String =
    format(lookup(EditorState.localeState.now(), key), args)

  def t(key: String, args: String*): Signal[String] =
    EditorState.localeState.signal.map(loc => format(lookup(loc, key), args))

  private def lookup(locale: Locale, key: String): String =
    Translations.catalogs.get(locale)
      .flatMap(_.get(key))
      .orElse(Translations.en.get(key))
      .getOrElse(s"!$key!")

  private def format(template: String, args: Seq[String]): String =
    if args.isEmpty then template
    else
      args.zipWithIndex.foldLeft(template) { case (acc, (value, idx)) =>
        acc.replace(s"{$idx}", value)
      }
