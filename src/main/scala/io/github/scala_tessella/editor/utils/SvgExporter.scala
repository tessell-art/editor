package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.models.EditorState.{showDual, showNodeLabels}
import io.github.scala_tessella.editor.models.{AppState, EditorConfig, EditorState}
import io.github.scala_tessella.editor.utils.TessellationGeometry.*
import io.github.scala_tessella.editor.utils.ColorUtils.*
import io.github.scala_tessella.editor.utils.DualTessellation.generateDualLines
import io.github.scala_tessella.dcel.BigDecimalGeometry.BigPoint
import io.github.scala_tessella.dcel.TilingSVG.toMetadata
import io.github.scala_tessella.dcel.{TilingDCEL, Vertex, VertexId}
import io.github.scala_tessella.editor.utils.Geometry.fitPointsToViewBox
import org.scalajs.dom

import scala.math.BigDecimal.RoundingMode

object SvgExporter:

  // "Save As..." functionality
  def saveAsTilingToSVG(): Unit =
    val tiling = EditorState.currentTiling.now()
    if !tiling.isEmpty then
      val currentName = EditorState.currentFileName.now().getOrElse("tessellation.svg")
      Option(dom.window.prompt("Enter file name for the SVG:", currentName)).foreach { newName =>
        if newName.nonEmpty then
          AsyncUtils.withLoadingState(() => {
            val finalName = if newName.toLowerCase.endsWith(".svg") then newName else s"$newName.svg"
            val svgContent = generateSvgContent(tiling, showNodeLabels.now(), showDual.now())
            FileDownloader.trigger(svgContent, finalName, "image/svg+xml;charset=utf-8")
            EditorState.currentFileName.set(Some(finalName))
          })
      }

  // "Save" functionality
  def saveTilingToSVG(): Unit =
    AsyncUtils.withLoadingState(() => {
      EditorState.currentFileName.now().foreach { fileName =>
        val tiling = EditorState.currentTiling.now()
        if !tiling.isEmpty then
          val svgContent = generateSvgContent(tiling, showNodeLabels.now(), showDual.now())
          FileDownloader.trigger(svgContent, fileName, "image/svg+xml;charset=utf-8")
      }
    })

  private [utils] def generateSvgContent(tiling: TilingDCEL, showNodeLabels: Boolean, showDual: Boolean): String =
    val coordinates = tiling.coordinates
    if coordinates.isEmpty then return ""

    val points = coordinates.values.toList.map(_.toPoint)

    val (scale, strokeWidth, strokeWidthPeri) = (EditorConfig.canvasScale, 1.5, 10.5)
    val padding = 20.0

    val (width, height, offsetX, offsetY) = fitPointsToViewBox(points, scale, padding)

    val polygonsXml = generatePolygonsXml(tiling, scale, offsetX, offsetY, strokeWidth)
    val perimeterXml = generatePerimeterXml(tiling, scale, offsetX, offsetY, strokeWidthPeri)
//    val dualXml = if showDual then generateDualTessellationXml(tiling, coordinates, scale, offsetX, offsetY) else ""
    val dualXml = ""
    val labelsXml = if showNodeLabels then generateLabelsXml(coordinates, scale, offsetX, offsetY) else ""
    val metadataXml = generateMetadataXml(tiling)

    val sWidth = f"$width%1.4f"
    val sHeight = f"$height%1.4f"

    s"""<svg xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:cc="http://creativecommons.org/ns#" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:tess="https://github.com/scala-tessella/tessella" width="$sWidth" height="$sHeight" viewBox="0 0 $sWidth $sHeight" xmlns="http://www.w3.org/2000/svg">
       |  <rect width="100%" height="100%" fill="white"/>
       |$perimeterXml
       |$polygonsXml${if showDual then s"\n$dualXml" else ""}${if showNodeLabels then s"\n$labelsXml" else ""}
       |$metadataXml
       |</svg>""".stripMargin

  private [utils] def pointsString(nodes: Seq[VertexId], coordinates: Map[VertexId, BigPoint], scale: Double, offsetX: Double, offsetY: Double): String =
    nodes.map(coordinates).map { vertex =>
      val x = (vertex.x * scale + offsetX).setScale(6, RoundingMode.HALF_UP)
      val y = (vertex.y * scale + offsetY).setScale(6, RoundingMode.HALF_UP)
      s"$x,$y"
    }.mkString(" ")

  private def pointsString(vertices: Seq[Vertex], scale: Double, offsetX: Double, offsetY: Double): String =
    vertices.map { vertex =>
      val x = (vertex.coords.x * scale + offsetX).setScale(6, RoundingMode.HALF_UP)
      val y = (vertex.coords.y * scale + offsetY).setScale(6, RoundingMode.HALF_UP)
      s"$x,$y"
    }.mkString(" ")

  private [utils] def generatePolygonsXml(tiling: TilingDCEL, scale: Double, offsetX: Double, offsetY: Double, strokeWidth: Double): String =
    val polygonsXml =
      tiling.innerFacesVertices.map { (faceId, faceVertices) =>
        val color = AppState.getOrAssignPolygonColor(faceId).toRgbString
        val points = pointsString(faceVertices, scale, offsetX, offsetY)
        val nodesStr = "" //nodes.map(_.toString).mkString(",")

        s"""    <polygon data-nodes="$nodesStr" points="$points" fill="$color" />"""
      }.mkString("\n")
    s"""  <g id="tiling-polygons" stroke="#333" stroke-width="$strokeWidth">
       |$polygonsXml
       |  </g>""".stripMargin

  private [utils] def generatePerimeterXml(tiling: TilingDCEL, scale: Double, offsetX: Double, offsetY: Double, strokeWidthPeri: Double): String =
    val perimeterNodes = tiling.boundaryVertices
    if perimeterNodes.isEmpty then ""
    else
      val points = pointsString(perimeterNodes, scale, offsetX, offsetY)
      val nodesStr = ""// perimeterNodes.map(_.toString).mkString(",")

      s"""  <polygon data-nodes="$nodesStr" points="$points" fill="none" stroke="#e4e4e4" stroke-width="$strokeWidthPeri" />"""

  private[utils] def generateDualTessellationXml(tiling: TilingDCEL, scale: Double, offsetX: Double, offsetY: Double): String =
    val dualLines = generateDualLines(tiling)
    if dualLines.isEmpty then ""
    else
      val dualLinesXml = dualLines.map { case (midPoint, center) =>
        val x1 = (midPoint.x * scale + offsetX).setScale(6, RoundingMode.HALF_UP)
        val y1 = (midPoint.y * scale + offsetY).setScale(6, RoundingMode.HALF_UP)
        val x2 = (center.x * scale + offsetX).setScale(6, RoundingMode.HALF_UP)
        val y2 = (center.y * scale + offsetY).setScale(6, RoundingMode.HALF_UP)

        s"""    <line x1="$x1" y1="$y1" x2="$x2" y2="$y2" />"""
      }.mkString("\n")
      s"""  <g id="dual-tessellation" stroke="red" stroke-width="1">
         |$dualLinesXml
         |  </g>""".stripMargin

  private [utils] def generateLabelsXml(coordinates: Map[VertexId, BigPoint], scale: Double, offsetX: Double, offsetY: Double): String =
    if coordinates.isEmpty then ""
    else
      val nodesXml = coordinates.map { (node, vertex) =>
        val labelX = (vertex.x * scale + offsetX + 4).setScale(4, RoundingMode.HALF_UP)
        val labelY = (vertex.y * scale + offsetY - 4).setScale(4, RoundingMode.HALF_UP)
        s"""    <text x="$labelX" y="$labelY" >${node.toString}</text>"""
      }.mkString("\n")
      s"""  <g id="node-labels" font-family="monospace" font-weight="bold" font-size="12" fill="#000" text-anchor="start" dominant-baseline="middle" stroke="#fff" stroke-width="0.5" paint-order="stroke fill">
         |$nodesXml
         |  </g>""".stripMargin

  private [utils] def generateMetadataXml(tiling: TilingDCEL): String =
    val tessellaMetadata =
      tiling.toMetadata
    s"""  <metadata>
       |    $tessellaMetadata
       |    <rdf:RDF>
       |      <cc:Work>
       |        <dc:source rdf:resource="https://github.com/scala-tessella/tessella">Tessella</dc:source>
       |        <cc:license rdf:resource="https://www.apache.org/licenses/LICENSE-2.0"/>
       |      </cc:Work>
       |    </rdf:RDF>
       |  </metadata>"""