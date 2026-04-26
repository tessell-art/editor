package io.github.scala_tessella.editor.i18n

/** Supported UI locales. Add a new case when adding a language; the ordering here also controls the cycle
  * order in the language toggle.
  *
  * `code` — IETF language tag stored in localStorage. `displayCode` — short uppercase abbreviation shown in
  * the top-bar selector. `displayName` — autoglossonym, used in menus / accessibility hints.
  */
enum Locale(val code: String, val displayCode: String, val displayName: String):
  case En extends Locale("en", "EN", "English")
  case Es extends Locale("es", "ES", "Español")

object Locale:
  val all: List[Locale] = List(En, Es)
  val default: Locale   = En

  def fromCode(code: String): Option[Locale] =
    all.find(_.code == code)

  /** Next locale in the cycle, wrapping around. Used by the top-bar toggle. */
  def next(current: Locale): Locale =
    val tail = all.dropWhile(_ != current).drop(1)
    tail.headOption.getOrElse(all.head)
