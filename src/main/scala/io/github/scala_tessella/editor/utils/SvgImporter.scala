package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.models.{AppState, EditorState}

import io.github.scala_tessella.tessella.Geometry.Point
import io.github.scala_tessella.tessella.TilingCoordinates.Coords
import io.github.scala_tessella.tessella.Topology.{Edge, Node}
import io.github.scala_tessella.tessella.IncrementalTiling
import org.scalajs.dom
import org.scalajs.dom.{FileReader, MIMEType, ProgressEvent, SVGElement}

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
          importTilingFromSVG(content)
          EditorState.currentFileName.set(Some(file.name))
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

  def importTilingFromSVG(svgContent: String): Unit =
    Try {
      val parser = new dom.DOMParser()
      // The second parameter to parseFromString requires a specific MIMEType.
      // While "image/svg+xml" is a correct value, the compiler might need more context,
      // which the corrected imports should provide.
      val doc = parser.parseFromString(svgContent, MIMEType.`image/svg+xml`)
      val polygons = doc.querySelectorAll("polygon[data-nodes]")

      var svgCoords = Map.empty[Int, (Double, Double)]
      var perimeterNodes: List[Int] = Nil
      var tilingPolygons: List[List[Int]] = Nil

      for (i <- 0 until polygons.length)
        val poly = polygons(i).asInstanceOf[SVGElement]
        val nodesStr: Option[String] = Option(poly.getAttribute("data-nodes"))
        val pointsStr: Option[String] = Option(poly.getAttribute("points"))
        val fill = poly.getAttribute("fill")

        (nodesStr, pointsStr) match
          case (Some(ns), Some(ps)) if ns.nonEmpty && ps.nonEmpty =>
            val nodes = ns.split(',').map(_.toInt).toList
            val points = ps.split(' ').filter(_.nonEmpty).map { p =>
              val coords = p.split(',')
              (coords(0).toDouble, coords(1).toDouble)
            }

            if nodes.length == points.length then
              if fill == "none" then
                perimeterNodes = nodes
              else
                tilingPolygons = tilingPolygons :+ nodes
                parseColor(fill).foreach { rgb =>
                  val polyTag = nodes.sorted.map(_.toString).mkString("-")
                  EditorState.polygonColors.update(_ + (polyTag -> rgb))
                }
              nodes.zip(points).foreach { case (node, point) =>
                svgCoords += node -> point
              }
          case _ =>

      if tilingPolygons.isEmpty && perimeterNodes.isEmpty then
        throw new Exception("No valid polygons found in SVG.")

      val scale = 50.0

      val minSvgX = if (svgCoords.isEmpty) 0.0 else svgCoords.values.map(_._1).min
      val minSvgY = if (svgCoords.isEmpty) 0.0 else svgCoords.values.map(_._2).min

      val finalCoords: Coords = svgCoords.map { case (id, (x, y)) =>
        Node(id) -> Point((x - minSvgX) / scale, (y - minSvgY) / scale)
      }

      val polygonsAsNodes = tilingPolygons.map(_.map(Node(_)))
      val perimeterAsNodes = perimeterNodes.map(Node(_))

      IncrementalTiling.maybe(
        polygonsAsNodes.map(_.toVector),
        perimeterAsNodes.toVector,
        finalCoords
      ) match
        case Left(message) => throw new Error(message)
        case Right(tiling) => EditorState.currentTiling.set(tiling)
      AppState.fitTilingToCanvas()
      UndoManager.clearHistory()
    }.recover { case e: Throwable =>
      val explanation: String =
        "Only SVG saved by this editor with dedicated metadata can be loaded."
      dom.window.alert(s"Failed to import SVG: ${e.getMessage}\n$explanation")
      e.printStackTrace()
    }