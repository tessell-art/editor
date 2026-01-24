package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.models.{EditorState, FailedPolygonDeletion, FailedPolygonPlacement}
import org.scalajs.dom

import scala.scalajs.js
import scala.util.Try

object ErrorOperations:

  // Severity levels to drive UX (toast color/behavior) and potential analytics
  enum Severity:
    case Info, Warning, Error

  private var messageTimeoutId: Option[Int] = None

  // Public, centralized entry point for error messages
  // - context: short “where/why” for logs (e.g., "SVG Import")
  // - hint: actionable remediation guidance shown to users
  // - asToast: render a non-blocking toast (default true for minor issues)
  def showError(
      message: String,
      placement: Option[FailedPolygonPlacement] = None,
      deletion: Option[FailedPolygonDeletion] = None,
      context: Option[String] = None,
      hint: Option[String] = None,
      asToast: Boolean = true,
      severity: Severity = Severity.Error
  ): Unit =
    // Cancel any existing timeout for the error message
    messageTimeoutId.foreach(id => dom.window.clearTimeout(id))

    val friendly =
      context match
        case Some(ctx) => s"$ctx: $message"
        case None      => message

    val fullMessage =
      hint match
        case Some(h) if h.nonEmpty => s"$friendly\n\nHint: $h"
        case _                     => friendly

    // Keep existing state updates for on-canvas feedback overlays
    if severity != Severity.Info then
      EditorState.errorMessage.set(Some(fullMessage))
      EditorState.failedPlacement.set(placement)
      EditorState.failedDeletion.set(deletion)

    // Auto-clear overlays and message after timeouts
    Try {
      if (js.typeOf(js.Dynamic.global.window) != "undefined") {
        // Timeout for the error message (10 seconds)
        val newTimeoutId = dom.window.setTimeout(
          () => {
            EditorState.errorMessage.set(None)
            messageTimeoutId = None
          },
          10000
        )
        messageTimeoutId = Some(newTimeoutId)

        // Timeout for the visual feedback (3 seconds)
        dom.window.setTimeout(
          () => {
            EditorState.failedPlacement.set(None)
            EditorState.failedDeletion.set(None)
          },
          3000
        ): Unit

        // Non-blocking toast for user feedback
        if asToast then
          showToast(fullMessage, severity, durationMs = if severity == Severity.Error then 6000 else 4000)
      }
    }.recover {
      case _ => // Ignore errors in test environment
    }: Unit

  // Convenience helpers
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
    messageTimeoutId.foreach(id => dom.window.clearTimeout(id))
    messageTimeoutId = None
    EditorState.errorMessage.set(None)
    EditorState.failedPlacement.set(None)
    EditorState.failedDeletion.set(None)

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
