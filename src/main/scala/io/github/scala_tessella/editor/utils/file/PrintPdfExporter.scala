package io.github.scala_tessella.editor.utils.file

import io.github.scala_tessella.editor.models.{Orientation, PaperSize}
import org.scalajs.dom

/** Prints the current canvas to PDF via the browser's `window.print()` dialog.
  *
  * The user-chosen `PaperSize` + `Orientation` is injected into a transient `@page` CSS rule, so the print
  * preview opens with those defaults. The rest of the print styling (hiding app chrome, scaling the canvas to
  * fit the page) lives in `styles/Print.css` under `@media print`.
  *
  * No PDF library: the browser's print dialog offers "Save as PDF" as one of the destinations.
  */
object PrintPdfExporter:

  def printToPdf(paperSize: PaperSize, orientation: Orientation): Unit =
    val style = dom.document.createElement("style").asInstanceOf[dom.HTMLStyleElement]
    style.setAttribute("data-print-page", "tessella")
    style.textContent = s"@page { size: ${pageSize(paperSize, orientation)}; margin: 1cm; }"
    val _     = dom.document.head.appendChild(style)
    try dom.window.print()
    finally
      // Remove the rule whether or not print succeeded so subsequent prints can re-inject afresh.
      // Some browsers keep the print dialog modal until the user dismisses it; this `finally` runs
      // after that path returns control.
      try {
        val _ = dom.document.head.removeChild(style)
      } catch case _: Throwable => ()

  private def pageSize(paper: PaperSize, orientation: Orientation): String =
    val paperToken       = paper match
      case PaperSize.A4     => "A4"
      case PaperSize.Letter => "Letter"
    val orientationToken = orientation match
      case Orientation.Portrait  => "portrait"
      case Orientation.Landscape => "landscape"
    s"$paperToken $orientationToken"
