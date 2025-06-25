package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.models.{AppState, EditorState}
import io.github.scala_tessella.tessella.IncrementalTiling
import io.github.scala_tessella.tessella.Topology.NodeOrdering
import org.scalajs.dom
import org.scalajs.dom.{Blob, BlobPropertyBag}

import scala.scalajs.js

object SvgExporter:

  def exportTilingToSVG(): Unit =
    val tiling = EditorState.currentTiling.now()
    if !tiling.isEmpty then
      val svgContent = generateSvgContent(tiling)
      FileDownloader.trigger(svgContent, "tessellation.svg", "image/svg+xml;charset=utf-8")

  private def generateSvgContent(tiling: IncrementalTiling): String =
    val coordinates = tiling.coordinates
    if (coordinates.isEmpty) return ""

    val allPoints = coordinates.values.toList
    val minX = allPoints.map(_.x).min
    val maxX = allPoints.map(_.x).max
    val minY = allPoints.map(_.y).min
    val maxY = allPoints.map(_.y).max

    val (scale, strokeWidth, strokeWidthPeri) = (50.0, 1.5, 10.5)
    val padding = 20.0

    val width = (maxX - minX) * scale + 2 * padding
    val height = (maxY - minY) * scale + 2 * padding
    val offsetX = -minX * scale + padding
    val offsetY = -minY * scale + padding

    val polygonsXml = tiling.orientedPolygons.map { nodes =>
      val polyTag = nodes.sorted(NodeOrdering).map(_.toString).mkString("-")
      val (r, g, b): (Int, Int, Int) = AppState.getOrAssignPolygonColor(polyTag)
      val color = s"rgb($r, $g, $b)"

      val points = nodes.map(coordinates).map { vertex =>
        val x = vertex.x * scale + offsetX
        val y = vertex.y * scale + offsetY
        s"$x,$y"
      }.mkString(" ")

      val nodesStr = nodes.map(_.toString).mkString(",")

      s"""    <polygon data-nodes="$nodesStr" points="$points" fill="$color" stroke="#333" stroke-width="$strokeWidth" />"""
    }.mkString("\n")

    val polygonsGroup =
      s"""  <g id="tiling-polygons">
         |$polygonsXml
         |  </g>""".stripMargin

    val perimeterNodes = tiling.perimeter
    val perimeterXml =
      if perimeterNodes.isEmpty then ""
      else
        val points = perimeterNodes.map(coordinates).map { vertex =>
          val x = vertex.x * scale + offsetX
          val y = vertex.y * scale + offsetY
          s"$x,$y"
        }.mkString(" ")
        val nodesStr = perimeterNodes.map(_.toString).mkString(",")
        s"""  <polygon data-nodes="$nodesStr" points="$points" fill="none" stroke="#e4e4e4" stroke-width="$strokeWidthPeri" />"""

    val labelsXml =
      if !EditorState.showNodeLabels.now() then ""
      else
        tiling.coordinates.map { (node, vertex) =>
          val labelX = vertex.x * scale + offsetX + 8
          val labelY = vertex.y * scale + offsetY - 8
          s"""  <text x="$labelX" y="$labelY" font-family="monospace" font-weight="bold" font-size="12" fill="#000" text-anchor="start" dominant-baseline="middle" stroke="#fff" stroke-width="0.5" paint-order="stroke fill">${node.toString}</text>"""
        }.mkString("\n")

    s"""<svg xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:cc="http://creativecommons.org/ns#" xmlns:dc="http://purl.org/dc/elements/1.1/" width="$width" height="$height" xmlns="http://www.w3.org/2000/svg">
       |  <rect width="100%" height="100%" fill="white"/>
       |$perimeterXml
       |$polygonsGroup
       |$labelsXml
       |  <metadata>
       |    <rdf:RDF>
       |      <cc:Work>
       |        <dc:source rdf:resource="https://github.com/scala-tessella/tessella">Tessella</dc:source>
       |        <cc:license rdf:resource="https://www.apache.org/licenses/LICENSE-2.0"/>
       |      </cc:Work>
       |    </rdf:RDF>
       |  </metadata>
       |</svg>""".stripMargin