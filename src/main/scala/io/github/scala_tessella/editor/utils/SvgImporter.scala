package io.github.scala_tessella.editor.utils

import com.raquo.laminar.api.L._
import io.github.scala_tessella.dcel.{TilingDCEL, TilingSVG}
import io.github.scala_tessella.editor.models.{AppState, EditorState}
import io.github.scala_tessella.editor.operations.ErrorOperations
import org.scalajs.dom
import org.scalajs.dom.{FileReader, MIMEType, ProgressEvent}

import scala.scalajs.js.RegExp
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
      fileOpt.foreach { file =>
        val reader = new FileReader()
        reader.onload = (_: ProgressEvent) => {
          val content = Option(reader.result).fold("")(_.toString)
          AsyncUtils.withLoadingState(() => importTilingFromSVG(content, file.name))
        }
        reader.readAsText(file)
      }
      // Clean up the temporary input element
      dom.document.body.removeChild(inputEl): Unit
    }
    inputEl.click()

  private[utils] def parseColor(colorStr: String): Option[(Int, Int, Int)] =
    Option(colorStr).flatMap { s =>
      val rgbRegex = new RegExp("rgb\\((\\d+),\\s*(\\d+),\\s*(\\d+)\\)")
      Option(rgbRegex.exec(s)).flatMap { result =>

        if result.length == 4 then
          for
            rStr <- result(1).toOption
            gStr <- result(2).toOption
            bStr <- result(3).toOption
            r    <- Try(rStr.toInt).toOption
            g    <- Try(gStr.toInt).toOption
            b    <- Try(bStr.toInt).toOption
          yield (r, g, b)
        else None
      }
    }

  def importTilingFromSVG(svgContent: String, filename: String): Unit =
    Try {
      val parser = new dom.DOMParser()
      val doc    = parser.parseFromString(svgContent, MIMEType.`image/svg+xml`)

      // Prefer namespace-aware selection for the tessella DCEL metadata
      val ns        = "https://github.com/scala-tessella/tessella"
      val tessElems = Option(doc.getElementsByTagNameNS(ns, "tessella-dcel"))
        .filter(_.length > 0)
        .map(_.item(0))

      // Fallback for cases where namespace lookups might fail (e.g., missing prefix binding)
      val tessElem =
        tessElems.orElse {
          Option(doc.querySelector("metadata tessella\\:tessella-dcel"))
        }.getOrElse(
          throw new Exception("No Tessella DCEL metadata found in the SVG.")
        )

      // Collect polygon fills (in order) to restore colors
      val svgPolys                         = doc.querySelectorAll("#tiling-polygons polygon")
      val polyFills: List[(Int, Int, Int)] =
        (0 until svgPolys.length).flatMap { i =>
          val el = svgPolys(i)
          parseColor(Option(el.getAttribute("fill")).getOrElse(""))
        }.toList

      val metadataStr = tessElem.outerHTML

      TilingSVG.fromMetadata(metadataStr) match
        case Left(err)                 =>
          throw new Error(s"Failed to parse Tessella DCEL metadata: ${err.message}")
        case Right(tiling: TilingDCEL) =>
          AppState.clearMeasurements()
          // Load the tiling into the editor
          EditorState.currentTiling.set(tiling)

          // Map SVG polygon colors to faces by order (export preserves this order)
          val faces    = tiling.innerFaces
          val colorMap =
            faces
              .zip(polyFills) // zip truncates safely if lengths differ
              .map { case (face, (r, g, b)) =>
                face.id -> ColorRGB(r, g, b)
              }
              .toMap
          EditorState.polygonColors.set(colorMap)

          EditorState.currentFileName.set(Some(filename))
          AppState.fitTilingToCanvas()
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
      e.printStackTrace()
    }: Unit
