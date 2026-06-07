package art.tessell.editor.utils

import org.scalajs.dom

/** localStorage flag indicating the welcome / first-run overlay has been shown and dismissed. Set on
  * dismissal; read on app startup to decide whether to show the overlay.
  */
object FirstRunStorage:

  private val key = "tessella.hasSeenFirstRun"

  def hasSeenFirstRun: Boolean =
    try Option(dom.window.localStorage.getItem(key)).contains("true")
    catch case _: Throwable => false

  def markSeenFirstRun(): Unit =
    try dom.window.localStorage.setItem(key, "true")
    catch case _: Throwable => ()

  /** Reset for testing / dev convenience — not wired to any UI in v1. */
  def clear(): Unit =
    try dom.window.localStorage.removeItem(key)
    catch case _: Throwable => ()
