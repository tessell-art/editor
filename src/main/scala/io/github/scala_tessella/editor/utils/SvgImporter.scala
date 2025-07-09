package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.models.{AppState, EditorConfig, EditorState}

import io.github.scala_tessella.tessella.Geometry.Point
import io.github.scala_tessella.tessella.TilingCoordinates.Coords
import io.github.scala_tessella.tessella.Topology.{Edge, Node}
import io.github.scala_tessella.tessella.IncrementalTiling
import io.github.scala_tessella.tessella.BigDecimalGeometry.{BigCoords, BigPoint}

import org.scalajs.dom
import org.scalajs.dom.{FileReader, MIMEType, ProgressEvent, SVGElement}

import scala.scalajs.js.RegExp
import scala.util.{Failure, Success, Try}

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
          importTilingFromSVG(content, file.name)
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

      val coordElements = doc.querySelectorAll("metadata tilingCoordinates coord")
      val finalCoords: BigCoords =
        (0 until coordElements.length).flatMap { i =>
          val coordElement = coordElements(i).asInstanceOf[dom.Element]
          for {
            nodeStr <- Option(coordElement.getAttribute("node"))
            xStr    <- Option(coordElement.getAttribute("x"))
            yStr    <- Option(coordElement.getAttribute("y"))
          } yield Node(nodeStr.toInt) -> BigPoint(BigDecimal(xStr), BigDecimal(yStr))
        }.toMap

      val polygons = doc.querySelectorAll("polygon[data-nodes]")
      var perimeterNodes: List[Int] = Nil
      var tilingPolygons: List[List[Int]] = Nil

      for (i <- 0 until polygons.length)
        val poly = polygons(i).asInstanceOf[SVGElement]
        val nodesStr: Option[String] = Option(poly.getAttribute("data-nodes"))
        val fill = poly.getAttribute("fill")

        nodesStr match
          case Some(ns) if ns.nonEmpty =>
            val nodes = ns.split(',').map(_.toInt).toList
            if fill == "none" then
              perimeterNodes = nodes
            else
              tilingPolygons = tilingPolygons :+ nodes
              parseColor(fill).foreach { rgb =>
                val polyTag = nodes.sorted.map(_.toString).mkString("-")
                EditorState.polygonColors.update(_ + (polyTag -> rgb))
              }
          case _ =>

      /*
      // The old method for extracting coordinates from polygon `points` and `data-nodes` attributes
      // is now replaced by reading the `<tilingCoordinates>` metadata.
      var svgCoords = Map.empty[Int, (BigDecimal, BigDecimal)]

      for (i <- 0 until polygons.length)
        val poly = polygons(i).asInstanceOf[SVGElement]
        val nodesStr: Option[String] = Option(poly.getAttribute("data-nodes"))
        val pointsStr: Option[String] = Option(poly.getAttribute("points"))
        val fill = poly.getAttribute("fill")

        (nodesStr, pointsStr) match
          case (Some(ns), Some(ps)) if ns.nonEmpty && ps.nonEmpty =>
            val nodes = ns.split(',').map(_.toInt).toList
            val points = ps.split(' ').filter(_.nonEmpty).map { p =>
              val coords: Array[String] = p.split(',')
              (BigDecimal(coords(0)), BigDecimal(coords(1)))
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

      val scale = EditorConfig.canvasScale

      val minSvgX = if svgCoords.isEmpty then BigDecimal(0) else svgCoords.values.map(_._1).min
      val minSvgY = if svgCoords.isEmpty then BigDecimal(0) else svgCoords.values.map(_._2).min

      svgCoords.map { case (id, (x, y)) =>
        Node(id) -> BigPoint((x - minSvgX) / scale, (y - minSvgY) / scale)
      }
      */

      if tilingPolygons.isEmpty && perimeterNodes.isEmpty then
        throw new Exception("No valid polygons found in SVG.")

      if finalCoords.isEmpty && (tilingPolygons.nonEmpty || perimeterNodes.nonEmpty) then
        throw new Exception("No coordinate metadata found in SVG. This might be an old SVG format which is no longer supported.")

      val polygonsAsNodes = tilingPolygons.map(_.map(Node(_)))
      val perimeterAsNodes = perimeterNodes.map(Node(_))

      IncrementalTiling.maybe(
        polygonsAsNodes.map(_.toVector),
        perimeterAsNodes.toVector,
        finalCoords
      ) match
        case Left(message) => throw new Error(message)
        case Right(tiling) =>
          EditorState.currentTiling.set(tiling)
          EditorState.currentFileName.set(Some(filename))
          AppState.fitTilingToCanvas()
          UndoManager.clearHistory()
    }.recover { case e: Throwable =>
      val explanation: String =
        "Only SVG saved by this editor with dedicated metadata can be loaded."
      dom.window.alert(s"Failed to import SVG: ${e.getMessage}\n$explanation")
      e.printStackTrace()
    }