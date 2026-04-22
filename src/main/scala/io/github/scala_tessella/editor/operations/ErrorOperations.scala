package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.models.{EditorState, FailedPolygonDeletion, FailedPolygonPlacement}
import org.scalajs.dom

import scala.scalajs.js
import scala.util.Try

object ErrorOperations:

  /** Severity levels to drive UX (toast color/behaviour) and potential analytics. */
  enum Severity:
    case Info, Warning, Error

  /** Race-free single-shot timer. `schedule` cancels the previous pending fire first, so it is safe to call
    * repeatedly — the most recent scheduling wins. Each instance tracks exactly one timeout, so errors
    * scheduled back-to-back never cancel each other's unrelated timers.
    */
  final private class SingleTimeout:
    private var currentId: Option[Int] = None

    def schedule(delayMs: Int)(onFire: => Unit): Unit =
      cancel()
      val id = dom.window.setTimeout(
        () => {
          currentId = None
          onFire
        },
        delayMs
      )
      currentId = Some(id)

    def cancel(): Unit =
      currentId.foreach(dom.window.clearTimeout)
      currentId = None

  // The error-message text stays for longer than the on-canvas overlay, so they're tracked
  // separately. Rapid consecutive errors now correctly cancel *both* previous timers.
  private val messageTimeout = new SingleTimeout
  private val overlayTimeout = new SingleTimeout

  /** Public, centralized entry point for error messages.
    *   - `context`: short "where/why" label for the user-facing message (e.g. "SVG Import").
    *   - `hint`: actionable remediation guidance shown below the message.
    *   - `asToast`: render a non-blocking toast (default true for minor issues).
    */
  def showError(
      message: String,
      placement: Option[FailedPolygonPlacement] = None,
      deletion: Option[FailedPolygonDeletion] = None,
      context: Option[String] = None,
      hint: Option[String] = None,
      asToast: Boolean = true,
      severity: Severity = Severity.Error
  ): Unit =
    val friendly    = context.fold(message)(ctx => s"$ctx: $message")
    val fullMessage =
      hint match
        case Some(h) if h.nonEmpty => s"$friendly\n\nHint: $h"
        case _                     => friendly

    // On-canvas feedback overlays (one atomic state update).
    if severity != Severity.Info then
      EditorState.errorState.update(
        _.copy(
          errorMessage = Some(fullMessage),
          failedPlacement = placement,
          failedDeletion = deletion
        )
      )

    // Auto-clear overlays and message after their respective timeouts.
    Try {
      if js.typeOf(js.Dynamic.global.window) != "undefined" then
        // 10s: clear the error message.
        messageTimeout.schedule(10000) {
          EditorState.errorState.update(_.copy(errorMessage = None))
        }
        // 3s: clear the on-canvas placement/deletion overlays.
        overlayTimeout.schedule(3000) {
          EditorState.errorState.update(
            _.copy(failedPlacement = None, failedDeletion = None)
          )
        }
        if asToast then
          showToast(fullMessage, severity, durationMs = if severity == Severity.Error then 6000 else 4000)
    }.recover {
      case _ => // Ignore errors in test environment (no `window`)
    }: Unit

  def info(
      message: String,
      context: Option[String] = None,
      hint: Option[String] = None,
      asToast: Boolean = true
  ): Unit =
    showError(message, context = context, hint = hint, asToast = asToast, severity = Severity.Info)

  def warn(
      message: String,
      context: Option[String] = None,
      hint: Option[String] = None,
      asToast: Boolean = true
  ): Unit =
    showError(message, context = context, hint = hint, asToast = asToast, severity = Severity.Warning)

  def error(
      message: String,
      context: Option[String] = None,
      hint: Option[String] = None,
      asToast: Boolean = true
  ): Unit =
    showError(message, context = context, hint = hint, asToast = asToast, severity = Severity.Error)

  def clearError(): Unit =
    messageTimeout.cancel()
    overlayTimeout.cancel()
    EditorState.errorState.update(
      _.copy(errorMessage = None, failedPlacement = None, failedDeletion = None)
    )

  // --- Minimal toast/snackbar implementation (non-blocking UI) ---

  private def ensureToastContainer(): dom.HTMLElement =
    Option(dom.document.getElementById("toast-container")) match
      case Some(el: dom.HTMLElement) => el
      case _                         => createToastContainer()

  private def createToastContainer(): dom.HTMLElement =
    dom.document.createElement("div") match
      case container: dom.HTMLDivElement =>
        container.id = "toast-container"
        dom.document.body.appendChild(container): Unit
        container
      case _                             =>
        // Fallback: attach to the body directly if narrowing failed (should not happen for "div")
        val fallback = dom.document.body
        fallback

  private def showToast(text: String, severity: Severity, durationMs: Int): Unit =
    val container = ensureToastContainer()

    // Create a div and safely narrow via pattern match
    dom.document.createElement("div") match
      case toast: dom.HTMLDivElement =>
        toast.setAttribute("role", "status")
        toast.setAttribute("aria-live", "polite")
        toast.className = "editor-toast"
        toast.setAttribute("data-severity", severity.toString.toLowerCase)
        toast.textContent = text

        // Click to dismiss early
        toast.onclick = _ => removeToast(toast)

        container.appendChild(toast): Unit

        // Animate in
        dom.window.requestAnimationFrame { _ =>

          toast.style.opacity = "1"
          toast.style.transform = "translateY(0)"
        }: Unit

        // Auto-dismiss
        dom.window.setTimeout(() => removeToast(toast), durationMs): Unit
        // Keep a placeholder listener for future extensibility
        toast.addEventListener("transitionend", (_: dom.Event) => ()): Unit

      case _ =>
        () // If not an HTMLDivElement, do nothing safely

  private def removeToast(toast: dom.HTMLDivElement): Unit =
    // Animate out then remove
    toast.style.opacity = "0"
    toast.style.transform = "translateY(8px)"
    // Remove after transition
    dom.window.setTimeout(
      () => Option(toast.parentNode).foreach(_.removeChild(toast)),
      180
    ): Unit
