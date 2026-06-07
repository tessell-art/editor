package art.tessell.editor.i18n

import art.tessell.editor.models.EditorState
import com.raquo.laminar.api.L.*

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

  /** Render a localized rich template into a sequence of inline children, suitable as a child modifier on
    * `<li>`, `<p>`, `<span>` etc. The template may contain `{tokenName}` placeholders that get substituted
    * with the corresponding element produced by `tokens(name)`. Reacts to locale changes — the tokens are
    * factories (re-invoked on each emission) so any `tNow` lookups inside them pick up the new locale.
    *
    * Unmatched tokens render as the literal `{name}`, so a missing factory surfaces visibly in QA.
    */
  def tFragments(key: String, tokens: Map[String, () => Element]): Modifier[HtmlElement] =
    children <-- t(key).map(parseTemplate(_, tokens))

  private val tokenPattern: scala.util.matching.Regex = """\{([a-zA-Z][a-zA-Z0-9]*)\}""".r

  private def parseTemplate(
      template: String,
      tokens: Map[String, () => Element]
  ): List[Element] =
    val parts   = scala.collection.mutable.ListBuffer.empty[Element]
    var lastEnd = 0
    tokenPattern.findAllMatchIn(template).foreach { m =>

      if m.start > lastEnd then
        parts += span(template.substring(lastEnd, m.start))
      val name = m.group(1)
      tokens.get(name) match
        case Some(factory) => parts += factory()
        case None          => parts += span(s"{$name}")
      lastEnd = m.end
    }
    if lastEnd < template.length then
      parts += span(template.substring(lastEnd))
    parts.toList
