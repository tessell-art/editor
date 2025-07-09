package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.models.{AppState, EditorConfig, EditorState}
import io.github.scala_tessella.editor.utils.TessellationGeometry.*

import io.github.scala_tessella.tessella.Geometry.Point
import io.github.scala_tessella.tessella.IncrementalTiling
import io.github.scala_tessella.tessella.Topology.NodeOrdering
import org.scalajs.dom
import org.scalajs.dom.{Blob, BlobPropertyBag}

import scala.scalajs.js

object SvgExporter:

  // "Save As..." functionality
  def saveAsTilingToSVG(): Unit =
    val tiling = EditorState.currentTiling.now()
    if !tiling.isEmpty then
      val currentName = EditorState.currentFileName.now().getOrElse("tessellation.svg")
      Option(dom.window.prompt("Enter file name for the SVG:", currentName)).foreach { newName =>
        if newName.nonEmpty then
          val finalName = if newName.toLowerCase.endsWith(".svg") then newName else s"$newName.svg"
          val svgContent = generateSvgContent(tiling)
          FileDownloader.trigger(svgContent, finalName, "image/svg+xml;charset=utf-8")
          EditorState.currentFileName.set(Some(finalName))
      }

  // "Save" functionality
  def saveTilingToSVG(): Unit =
    EditorState.currentFileName.now().foreach { fileName =>
      val tiling = EditorState.currentTiling.now()
      if !tiling.isEmpty then
        val svgContent = generateSvgContent(tiling)
        FileDownloader.trigger(svgContent, fileName, "image/svg+xml;charset=utf-8")
    }

  private def generateSvgContent(tiling: IncrementalTiling): String =
    val coordinates = tiling.coordinates
    if coordinates.isEmpty then return ""

    val bounds = coordinates.values.toList.map(_.toPoint).maybeBounds.get

    val (scale, strokeWidth, strokeWidthPeri) = (EditorConfig.canvasScale, 1.5, 10.5)
    val padding = 20.0

    val width = (bounds.maxX - bounds.minX) * scale + 2 * padding
    val height = (bounds.maxY - bounds.minY) * scale + 2 * padding
    val offsetX = -bounds.minX * scale + padding
    val offsetY = -bounds.minY * scale + padding

    val polygonsXml = tiling.orientedPolygons.map { nodes =>
      val polyTag = nodes.sorted(using NodeOrdering).map(_.toString).mkString("-")
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

    val tilingCoordinatesMetadata =
      if coordinates.isEmpty then ""
      else
        val items = coordinates
          .map { (node, vertex) =>
            s"""        <coord node="${node.toString}" x="${vertex.x.toString}" y="${vertex.y.toString}" />"""
          }
          .mkString("\n")
        s"""
           |    <tilingCoordinates>
           |$items
           |    </tilingCoordinates>"""

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
       |    </rdf:RDF>$tilingCoordinatesMetadata
       |  </metadata>
       |</svg>""".stripMargin