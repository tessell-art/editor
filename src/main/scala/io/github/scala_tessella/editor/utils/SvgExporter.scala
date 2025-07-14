package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.models.{AppState, EditorConfig, EditorState}
import io.github.scala_tessella.editor.utils.TessellationGeometry.*
import io.github.scala_tessella.editor.utils.ColorUtils.*

import io.github.scala_tessella.tessella.BigDecimalGeometry.BigCoords
import io.github.scala_tessella.tessella.IncrementalTiling
import io.github.scala_tessella.tessella.Topology.NodeOrdering
import org.scalajs.dom

object SvgExporter:

  // "Save As..." functionality
  def saveAsTilingToSVG(): Unit =
    val tiling = EditorState.currentTiling.now()
    if !tiling.isEmpty then
      val currentName = EditorState.currentFileName.now().getOrElse("tessellation.svg")
      Option(dom.window.prompt("Enter file name for the SVG:", currentName)).foreach { newName =>
        if newName.nonEmpty then
          val finalName = if newName.toLowerCase.endsWith(".svg") then newName else s"$newName.svg"
          val svgContent = generateSvgContent(tiling, EditorState.showNodeLabels.now())
          FileDownloader.trigger(svgContent, finalName, "image/svg+xml;charset=utf-8")
          EditorState.currentFileName.set(Some(finalName))
      }

  // "Save" functionality
  def saveTilingToSVG(): Unit =
    EditorState.currentFileName.now().foreach { fileName =>
      val tiling = EditorState.currentTiling.now()
      if !tiling.isEmpty then
        val svgContent = generateSvgContent(tiling, EditorState.showNodeLabels.now())
        FileDownloader.trigger(svgContent, fileName, "image/svg+xml;charset=utf-8")
    }

  private [utils] def generateSvgContent(tiling: IncrementalTiling, showNodeLabels: Boolean): String =
    val coordinates = tiling.coordinates
    if coordinates.isEmpty then return ""

    val bounds = coordinates.values.toList.map(_.toPoint).maybeBounds.get

    val (scale, strokeWidth, strokeWidthPeri) = (EditorConfig.canvasScale, 1.5, 10.5)
    val padding = 20.0

    val width = (bounds.maxX - bounds.minX) * scale + 2 * padding
    val height = (bounds.maxY - bounds.minY) * scale + 2 * padding
    val offsetX = -bounds.minX * scale + padding
    val offsetY = -bounds.minY * scale + padding

    val polygonsXml = generatePolygonsXml(tiling, coordinates, scale, offsetX, offsetY, strokeWidth)
    val perimeterXml = generatePerimeterXml(tiling, coordinates, scale, offsetX, offsetY, strokeWidthPeri)
    val labelsXml = if showNodeLabels then generateLabelsXml(coordinates, scale, offsetX, offsetY) else ""
    val metadataXml = generateMetadataXml(coordinates)

    s"""<svg xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:cc="http://creativecommons.org/ns#" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:tessella="https://github.com/scala-tessella/tessella" width="$width" height="$height" xmlns="http://www.w3.org/2000/svg">
       |  <rect width="100%" height="100%" fill="white"/>
       |$perimeterXml
       |$polygonsXml
       |$labelsXml
       |$metadataXml
       |</svg>""".stripMargin

  private [utils] def generatePolygonsXml(tiling: IncrementalTiling, coordinates: BigCoords, scale: Double, offsetX: Double, offsetY: Double, strokeWidth: Double): String =
    val polygonsXml = tiling.orientedPolygons.map { nodes =>
      val polyTag = nodes.sorted(using NodeOrdering).map(_.toString).mkString("-")
      val color = AppState.getOrAssignPolygonColor(polyTag).toRgbString

      val points = nodes.map(coordinates).map { vertex =>
        val x = vertex.x * scale + offsetX
        val y = vertex.y * scale + offsetY
        s"$x,$y"
      }.mkString(" ")

      val nodesStr = nodes.map(_.toString).mkString(",")

      s"""    <polygon data-nodes="$nodesStr" points="$points" fill="$color" stroke="#333" stroke-width="$strokeWidth" />"""
    }.mkString("\n")
    s"""  <g id="tiling-polygons">
       |$polygonsXml
       |  </g>""".stripMargin

  private [utils] def generatePerimeterXml(tiling: IncrementalTiling, coordinates: BigCoords, scale: Double, offsetX: Double, offsetY: Double, strokeWidthPeri: Double): String =
    val perimeterNodes = tiling.perimeter
    if perimeterNodes.isEmpty then ""
    else
      val points = perimeterNodes.map(coordinates).map { vertex =>
        val x = vertex.x * scale + offsetX
        val y = vertex.y * scale + offsetY
        s"$x,$y"
      }.mkString(" ")
      val nodesStr = perimeterNodes.map(_.toString).mkString(",")
      s"""  <polygon data-nodes="$nodesStr" points="$points" fill="none" stroke="#e4e4e4" stroke-width="$strokeWidthPeri" />"""

  private [utils] def generateLabelsXml(coordinates: BigCoords, scale: Double, offsetX: Double, offsetY: Double): String =
    coordinates.map { (node, vertex) =>
      val labelX = vertex.x * scale + offsetX + 4
      val labelY = vertex.y * scale + offsetY - 4
      s"""  <text x="$labelX" y="$labelY" font-family="monospace" font-weight="bold" font-size="12" fill="#000" text-anchor="start" dominant-baseline="middle" stroke="#fff" stroke-width="0.5" paint-order="stroke fill">${node.toString}</text>"""
    }.mkString("\n")

  private [utils] def generateMetadataXml(coordinates: BigCoords): String =
    val tilingCoordinatesMetadata =
      if coordinates.isEmpty then ""
      else
        val items = coordinates
          .map { (node, vertex) =>
            s"""      <tessella:coord node="${node.toString}" x="${vertex.x.toString}" y="${vertex.y.toString}" />"""
          }
          .mkString("\n")
        s"""
           |    <tessella:tilingCoordinates>
           |$items
           |    </tessella:tilingCoordinates>"""
    s"""  <metadata>
       |    <rdf:RDF>
       |      <cc:Work>
       |        <dc:source rdf:resource="https://github.com/scala-tessella/tessella">Tessella</dc:source>
       |        <cc:license rdf:resource="https://www.apache.org/licenses/LICENSE-2.0"/>
       |      </cc:Work>
       |    </rdf:RDF>$tilingCoordinatesMetadata
       |  </metadata>"""