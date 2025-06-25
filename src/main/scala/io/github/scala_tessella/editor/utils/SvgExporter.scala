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
      triggerDownload(svgContent, "tessellation.svg")

  private def generateSvgContent(tiling: IncrementalTiling): String =
    val coordinates = tiling.coordinates
    if (coordinates.isEmpty) return ""

    val allPoints = coordinates.values.toList
    val minX = allPoints.map(_.x).min
    val maxX = allPoints.map(_.x).max
    val minY = allPoints.map(_.y).min
    val maxY = allPoints.map(_.y).max

    val (scale, strokeWidth) = (50.0, 1.5)
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

      s"""  <polygon points="$points" fill="$color" stroke="#333" stroke-width="$strokeWidth" />"""
    }.mkString("\n")

    s"""<svg xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:cc="http://creativecommons.org/ns#" xmlns:dc="http://purl.org/dc/elements/1.1/" width="$width" height="$height" xmlns="http://www.w3.org/2000/svg">
       |  <rect width="100%" height="100%" fill="white"/>
       |$polygonsXml
       |  <metadata>
       |    <rdf:RDF>
       |      <cc:Work>
       |        <dc:source rdf:resource="https://github.com/scala-tessella/tessella">Tessella</dc:source>
       |        <cc:license rdf:resource="https://www.apache.org/licenses/LICENSE-2.0"/>
       |      </cc:Work>
       |    </rdf:RDF>
       |  </metadata>
       |</svg>""".stripMargin

  private def triggerDownload(content: String, filename: String): Unit =
    val blobPropertyBag = new BlobPropertyBag { `type` = "image/svg+xml;charset=utf-8" }
    val blob = new Blob(js.Array(content), blobPropertyBag)
    val url = dom.URL.createObjectURL(blob)
    val a = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
    a.href = url
    a.download = filename
    dom.document.body.appendChild(a)
    a.click()
    dom.document.body.removeChild(a)
    dom.URL.revokeObjectURL(url)
