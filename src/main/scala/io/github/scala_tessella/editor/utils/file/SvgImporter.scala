package io.github.scala_tessella.editor.utils.file

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.conversion.TilingSVG
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.operations.{
  ErrorOperations, MeasurementOperations, SymmetryOperations, ViewOperations
}
import io.github.scala_tessella.editor.utils.ColorRGB.parseColor
import io.github.scala_tessella.editor.utils.{AsyncUtils, ColorRGB, UndoManager}
import org.scalajs.dom
import org.scalajs.dom.{FileReader, MIMEType, ProgressEvent}

import scala.util.Try

object SvgImporter:

  def trigger(): Unit =
    val inputEl = input(
      tpe     := "file",
      accept  := ".svg,image/svg+xml",
      display := "none"
    ).ref

    dom.document.body.appendChild(inputEl): Unit
    inputEl.onchange = _ => {
      val fileOpt = Option(inputEl.files).flatMap(fs => Option(fs.item(0)))
      fileOpt.foreach: file =>

        val reader = new FileReader()
        reader.onload = (_: ProgressEvent) => {
          val content =
            Option(reader.result).fold(""):
              _.toString
          AsyncUtils.withLoadingState(() => importTilingFromSVG(content, file.name), Some("Importing SVG..."))
        }
        reader.readAsText(file)
      // Clean up the temporary input element
      dom.document.body.removeChild(inputEl): Unit
    }
    inputEl.click()

  def importTilingFromSVG(svgContent: String, filename: String): Unit =
    Try {
      AsyncUtils.setLoadingMessage("Parsing SVG metadata...")
      val parser = new dom.DOMParser()
      val doc    = parser.parseFromString(svgContent, MIMEType.`image/svg+xml`)

      // Prefer namespace-aware selection for the tessella DCEL metadata
      val ns        = "https://github.com/scala-tessella/tessella"
      val tessElems = Option(doc.getElementsByTagNameNS(ns, "tessella-dcel"))
        .filter:
          _.length > 0
        .map:
          _.item(0)

      // Fallback for cases where namespace lookups might fail (e.g., missing prefix binding)
      val tessElem =
        tessElems
          .orElse:
            Option(doc.querySelector("metadata tessella\\:tessella-dcel"))
          .getOrElse(
            throw new Exception("No Tessella DCEL metadata found in the SVG.")
          )

      val metadataStr = tessElem.outerHTML

      AsyncUtils.setLoadingMessage("Validating tessellation...")

      TilingSVG.fromMetadata(metadataStr) match
        case Left(err)                 =>
          throw new Exception(s"Failed to parse Tessella DCEL metadata: ${err.message}")
        case Right(tiling: TilingDCEL) =>
          val faces     = tiling.innerFaces
          // Strict color preservation: require one valid fill for every imported face.
          val polyFills = readPolygonFillsStrict(doc, expectedCount = faces.size)

          MeasurementOperations.clearAll()
          SymmetryOperations.clearOverlays()
          // Load the tiling into the editor
          EditorState.tessellationState.update(_.copy(currentTiling = tiling))

          // Map SVG polygon colors to faces by order (export preserves this order).
          val colorMap =
            faces
              .zip(polyFills)
              .map:
                case (face, rgb) => face.id -> rgb
              .toMap
          EditorState.polygonColors.set(colorMap)
          EditorState.currentFileName.set(Some(filename))
          ViewOperations.fitTilingToCanvas()
          UndoManager.clearHistory()
    }.recover { case e: Throwable =>
      // Friendlier, centralized message with a remediation hint, via non-blocking toast
      val hint =
        "This SVG likely lacks Tessella DCEL metadata.\nUse File → Save SVG in this editor to produce importable files."
      ErrorOperations.showError(
        message = s"Failed to import SVG: ${e.getMessage}",
        context = Some("SVG Import"),
        hint = Some(hint),
        asToast = true,
        severity = ErrorOperations.Severity.Error
      )
    }: Unit

  private def readPolygonFillsStrict(doc: dom.Document, expectedCount: Int): List[ColorRGB] =
    val svgPolys   = doc.querySelectorAll("#tiling-polygons polygon")
    val foundCount = svgPolys.length

    if foundCount != expectedCount then
      throw new Exception(
        s"Strict color import failed: expected $expectedCount polygon fills, found $foundCount."
      )

    (0 until foundCount).map { i =>

      val el      = svgPolys(i)
      val rawFill = Option(el.getAttribute("fill")).map(_.trim).getOrElse("")
      parseColor(rawFill).getOrElse(
        throw new Exception(s"Strict color import failed: invalid fill at polygon #${i + 1}: '$rawFill'.")
      )
    }.toList
