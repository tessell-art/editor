package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.models.{AppState, EditorState}
import io.github.scala_tessella.dcel.{TilingDCEL, TilingSVG}
import io.github.scala_tessella.editor.operations.ErrorOperations
import org.scalajs.dom
import org.scalajs.dom.{FileReader, MIMEType, ProgressEvent}

import scala.scalajs.js.RegExp
import scala.util.Try

object SvgImporter:

  def trigger(): Unit =
    val input = dom.document.createElement("input").asInstanceOf[dom.html.Input]
    input.`type` = "file"
    input.accept = ".svg,image/svg+xml"
    input.onchange = _ => {
      val file = input.files(0)
      if file != null then
        val reader = new FileReader()
        // The onload event for FileReader provides a ProgressEvent, not a UIEvent
        reader.onload = (_: ProgressEvent) => {
          val content = reader.result.toString
          // Wrap the import logic in withLoadingState to manage the isProcessing flag
          AsyncUtils.withLoadingState(() => importTilingFromSVG(content, file.name))
        }
        reader.readAsText(file)
    }
    input.click()

  private def parseColor(colorStr: String): Option[(Int, Int, Int)] =
    Option(colorStr).flatMap { s =>
      val rgbRegex = new RegExp("rgb\\((\\d+),\\s*(\\d+),\\s*(\\d+)\\)")
      val result = rgbRegex.exec(s)
      if result != null && result.length == 4 then
        for
          // exec can return undefined for captures, so we convert to Option
          rStr <- result(1).toOption
          gStr <- result(2).toOption
          bStr <- result(3).toOption
          // Safely convert strings to integers
          r <- Try(rStr.toInt).toOption
          g <- Try(gStr.toInt).toOption
          b <- Try(bStr.toInt).toOption
        yield (r, g, b)
      else None
    }

  def importTilingFromSVG(svgContent: String, filename: String): Unit =
    Try {
      val parser = new dom.DOMParser()
      val doc = parser.parseFromString(svgContent, MIMEType.`image/svg+xml`)

      // Prefer namespace-aware selection for the tessella DCEL metadata
      val ns = "https://github.com/scala-tessella/tessella"
      val tessElems = doc.getElementsByTagNameNS(ns, "tessella-dcel")

      // Fallback for cases where namespace lookups might fail (e.g., missing prefix binding)
      val tessElem =
        if tessElems != null && tessElems.length > 0 then
          tessElems(0)
        else
          // Escape the colon in the CSS selector for namespaced elements
          Option(doc.querySelector("metadata tessella\\:tessella-dcel")).getOrElse(
            throw new Exception("No Tessella DCEL metadata found in the SVG.")
          )

      // Collect polygon fills (in order) to restore colors
      val svgPolys = doc.querySelectorAll("#tiling-polygons polygon")
      val polyFills: List[(Int, Int, Int)] =
        (0 until svgPolys.length).flatMap { i =>
          val el = svgPolys(i).asInstanceOf[dom.Element]
          parseColor(Option(el.getAttribute("fill")).getOrElse(""))
        }.toList

      val metadataStr = tessElem.asInstanceOf[dom.Element].outerHTML

      TilingSVG.fromMetadata(metadataStr) match
        case Left(err) =>
          throw new Error(s"Failed to parse Tessella DCEL metadata: ${err.message}")
        case Right(tiling: TilingDCEL) =>
          // Load the tiling into the editor
          EditorState.currentTiling.set(tiling)

          // Map SVG polygon colors to faces by order (export preserves this order)
          val faces = tiling.innerFaces.toList
          val colorMap =
            faces
              .zip(polyFills) // zip truncates safely if lengths differ
              .map { case (face, rgb) => face.id.value -> rgb }
              .toMap
          EditorState.polygonColors.set(colorMap)

          EditorState.currentFileName.set(Some(filename))
          AppState.fitTilingToCanvas()
          UndoManager.clearHistory()
    }.recover { case e: Throwable =>
      // Friendlier, centralized message with remediation hint, via non-blocking toast
      val hint =
        "This SVG likely lacks Tessella DCEL metadata.\nUse File → Save SVG in this editor to produce importable files."
      ErrorOperations.showError(
        message = s"Failed to import SVG: ${e.getMessage}",
        context = Some("SVG Import"),
        hint = Some(hint),
        asToast = true,
        severity = ErrorOperations.Severity.Error
      )
      e.printStackTrace()
    }
