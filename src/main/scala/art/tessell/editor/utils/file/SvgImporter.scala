package art.tessell.editor.utils.file

import art.tessell.editor.i18n.I18n
import art.tessell.editor.models.EditorState
import art.tessell.editor.operations.{
  DirtyTracker, ErrorOperations, MeasurementOperations, SymmetryOperations, UndoManager, ViewOperations
}
import art.tessell.editor.utils.{AsyncUtils, ColorRGB}
import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.conversion.TilingSVG
import art.tessell.editor.utils.ColorRGB.parseColor
import org.scalajs.dom
import org.scalajs.dom.{FileReader, MIMEType, ProgressEvent}

import scala.util.Try

object SvgImporter:

  private val metadataNamespace = "https://github.com/scala-tessella/tessella"

  private val importFailureHint =
    "This SVG likely lacks Tessella DCEL metadata.\n" +
      "Use File → Save SVG in this editor to produce importable files."

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
          AsyncUtils.withLoadingState(
            () => importTilingFromSVG(content, file.name),
            Some(I18n.tNow("loading.importingSvg"))
          )
        }
        reader.readAsText(file)
      // Clean up the temporary input element
      dom.document.body.removeChild(inputEl): Unit
    }
    inputEl.click()

  /** Top-level import flow. Runs four parse/validate steps short-circuiting on the first `Left`, then either
    * loads the tiling into the editor or surfaces the error via `ErrorOperations`.
    */
  def importTilingFromSVG(svgContent: String, filename: String): Unit =
    AsyncUtils.setLoadingMessage(I18n.tNow("loading.parsingSvg"))
    val result: Either[String, Unit] =
      for
        doc       <- parseSvg(svgContent)
        metaElem  <- findTessellaMetadata(doc)
        _          = AsyncUtils.setLoadingMessage(I18n.tNow("loading.validating"))
        tiling    <- parseTiling(metaElem.outerHTML)
        polyFills <- readPolygonFillsStrict(doc, expectedCount = tiling.innerFaces.size)
      yield loadTilingIntoEditor(tiling, polyFills, filename)

    result.left.foreach(showImportError)

  /** Wrap the only DOM call that can actually throw (`new DOMParser()` on exotic platforms). */
  private def parseSvg(svgContent: String): Either[String, dom.Document] =
    Try {
      val parser = new dom.DOMParser()
      parser.parseFromString(svgContent, MIMEType.`image/svg+xml`)
    }.toEither.left.map(e => s"Failed to parse SVG document: ${e.getMessage}")

  /** Locate the `<tessella:tessella-dcel>` metadata element. Prefers namespace-aware selection; falls back to
    * a `querySelector` with an escaped colon in case the prefix binding is missing.
    */
  private def findTessellaMetadata(doc: dom.Document): Either[String, dom.Element] =
    val namespaceMatch =
      Option(doc.getElementsByTagNameNS(metadataNamespace, "tessella-dcel"))
        .filter(_.length > 0)
        .map(_.item(0))
    namespaceMatch
      .orElse(Option(doc.querySelector("metadata tessella\\:tessella-dcel")))
      .collect { case el: dom.Element =>
        el
      }
      .toRight("No Tessella DCEL metadata found in the SVG.")

  private def parseTiling(metadataStr: String): Either[String, TilingDCEL] =
    TilingSVG.fromMetadata(metadataStr).left.map(err =>
      s"Failed to parse Tessella DCEL metadata: ${err.message}"
    )

  /** Read the `<polygon>` fills strictly: the count must match, every fill must parse. */
  private def readPolygonFillsStrict(
      doc: dom.Document,
      expectedCount: Int
  ): Either[String, List[ColorRGB]] =
    val svgPolys   = doc.querySelectorAll("#tiling-polygons polygon")
    val foundCount = svgPolys.length
    if foundCount != expectedCount then
      Left(s"Strict color import failed: expected $expectedCount polygon fills, found $foundCount.")
    else
      val perPolygon: List[Either[String, ColorRGB]] = (0 until foundCount).toList.map { i =>

        val rawFill = Option(svgPolys(i).getAttribute("fill")).map(_.trim).getOrElse("")
        parseColor(rawFill).toRight(
          s"Strict color import failed: invalid fill at polygon #${i + 1}: '$rawFill'."
        )
      }
      // Sequence List[Either] → Either[_, List] short-circuiting on the first Left.
      perPolygon.foldRight[Either[String, List[ColorRGB]]](Right(Nil)) { (e, acc) =>

        for c <- e; rest <- acc yield c :: rest
      }

  private def loadTilingIntoEditor(
      tiling: TilingDCEL,
      polyFills: List[ColorRGB],
      filename: String
  ): Unit =
    MeasurementOperations.clearAll()
    SymmetryOperations.clearOverlays()
    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    val colorMap =
      tiling.innerFaces.zip(polyFills).map { case (face, rgb) =>
        face.id -> rgb
      }.toMap
    EditorState.colorState.update(_.copy(polygonColors = colorMap))
    EditorState.fileState.update(_.copy(currentFileName = Some(filename)))
    ViewOperations.fitTilingToCanvas()
    UndoManager.clearHistory()
    // Loaded tiling matches the source file → it's the new "saved" baseline.
    DirtyTracker.markSaved()

  private def showImportError(reason: String): Unit =
    ErrorOperations.showError(
      message = s"Failed to import SVG: $reason",
      context = Some("SVG Import"),
      hint = Some(importFailureHint),
      asToast = true,
      severity = ErrorOperations.Severity.Error
    )
