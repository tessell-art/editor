package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.tessella.Tiling
import io.github.scala_tessella.tessella.Topology.{Edge, Node => TilingNode}
import io.github.scala_tessella.editor.models.{AppState, Point}

object TessellationRenderer:
  def renderTiling(tiling: Tiling): Element =
    val tilingPolygons = tiling.orientedPolygons.map(_.toPolygonPathNodes).zipWithIndex.map {
      case (nodes, index) => renderTilingPolygon(tiling, nodes, s"tiling-poly-$index")
    }

    val perimeterEdges = tiling.perimeter.toRingEdges.zipWithIndex.map {
      case (edge, index) => renderPerimeterEdge(tiling, edge, index, s"perimeter-edge-$index")
    }.toList

    val nodeLabels = children <-- AppState.showNodeLabels.signal.map { showLabels =>
      if (showLabels) renderNodeLabels(tiling)
      else List.empty
    }

    svg.g(
      svg.className := "tessellation",
      tilingPolygons,
      perimeterEdges,
      nodeLabels
    )

  private def renderNodeLabels(tiling: Tiling): List[Element] =
    // Get all unique nodes from the tiling
    val allNodes = tiling.graphNodes

    allNodes.map { node =>
      val vertex = tiling.coords(node)

      // Convert tessella coordinates to canvas coordinates
      val x = vertex.x * 50 + 400
      val y = vertex.y * 50 + 300

      // Offset the label slightly from the vertex to avoid overlap
      val offsetX = x + 8
      val offsetY = y - 8

      svg.text(
        svg.x := offsetX.toString,
        svg.y := offsetY.toString,
        svg.fontSize <-- AppState.viewTransform.signal.map(transform =>
          // Scale font size with zoom but keep it readable
          val baseFontSize = 12
          val scaledSize = (baseFontSize / transform.scale).max(8).min(20)
          scaledSize.toString
        ),
        // Counter-rotate the text to keep it readable
        svg.transform <-- AppState.viewTransform.signal.map(transform =>
          // Rotate around the text position to counter the canvas rotation
          s"rotate(${-transform.rotationDegrees} $offsetX $offsetY)"
        ),
        svg.fill := "#ffeb3b", // Bright yellow for visibility
        svg.fontFamily := "monospace",
        svg.fontWeight := "bold",
        svg.textAnchor := "start",
        svg.dominantBaseline := "middle",
        svg.className := "node-label",
        // Add stroke for better readability
        svg.stroke := "#000",
        svg.strokeWidth <-- AppState.viewTransform.signal.map(transform =>
          (0.5 / transform.scale).max(0.2).min(1.0).toString
        ),
        svg.paintOrder := "stroke fill",
        node.toString
      )
    }

  private def renderTilingPolygon(tiling: Tiling, nodes: Vector[TilingNode], id: String): Element =
    val center = Point(0, 0)
    val isSelected = AppState.selectedTilingPolygons.signal.map(_.contains(id))

    // Convert tessella coordinates to canvas coordinates
    val canvasCenter = Point(center.x * 50 + 400, center.y * 50 + 300)

    val points = nodes.map(tiling.coords).map { vertex =>
      val x = canvasCenter.x + vertex.x * 50
      val y = canvasCenter.y + vertex.y * 50
      s"$x,$y"
    }.mkString(" ")

    svg.polygon(
      svg.points := points,
      svg.fill <-- isSelected.map(selected =>
        if (selected) "rgba(255, 107, 107, 0.4)" else "rgba(100, 108, 255, 0.2)"
      ),
      svg.stroke <-- isSelected.map(selected =>
        if (selected) "#ff6b6b" else "#646cff"
      ),
      svg.strokeWidth <-- isSelected.map(selected => if (selected) "3" else "1.5"),
      svg.className := "tiling-polygon",
      onClick --> { _ => AppState.toggleTilingPolygonSelection(id) }
    )

  private def renderPerimeterEdge(tiling: Tiling, edge: Edge, edgeIndex: Int, id: String): Element =
    val vertex1 = tiling.coords(edge.lesserNode)
    val vertex2 = tiling.coords(edge.greaterNode)
    val isSelected = AppState.selectedPerimeterEdges.signal.map(_.contains(id))

    // Convert tessella coordinates to canvas coordinates
    val x1 = vertex1.x * 50 + 400
    val y1 = vertex1.y * 50 + 300
    val x2 = vertex2.x * 50 + 400
    val y2 = vertex2.y * 50 + 300

    svg.line(
      svg.x1 := x1.toString,
      svg.y1 := y1.toString,
      svg.x2 := x2.toString,
      svg.y2 := y2.toString,
      svg.stroke <-- isSelected.map(selected =>
        if (selected) "#ff6b6b" else "#ff9500"
      ),
      svg.strokeWidth <-- isSelected.map(selected => if (selected) "4" else "3"),
      svg.className := "perimeter-edge",
      onClick --> { _ => AppState.handlePerimeterEdgeClick(id, edgeIndex) }
    )

object GridRenderer:
  def element: Element =
    svg.g(
      svg.className := "grid-pattern",
      svg.opacity := "0.3",
      // Vertical lines
      (0 to 800 by 50).map(x =>
        svg.line(
          svg.x1 := x.toString, svg.y1 := "0",
          svg.x2 := x.toString, svg.y2 := "600",
          svg.stroke := "#444", svg.strokeWidth := "1"
        )
      ),
      // Horizontal lines
      (0 to 600 by 50).map(y =>
        svg.line(
          svg.x1 := "0", svg.y1 := y.toString,
          svg.x2 := "800", svg.y2 := y.toString,
          svg.stroke := "#444", svg.strokeWidth := "1"
        )
      )
    )

object PolygonRenderer:
  def renderCanvasPolygon(polygon: io.github.scala_tessella.editor.models.CanvasPolygon): Element =
    import scala.math.{cos, Pi, sin}

    val points = (0 until polygon.sides).map { i =>
      val angle = (2 * Pi * i / polygon.sides) - (Pi / 2) + polygon.rotation
      val x = polygon.center.x + polygon.radius * cos(angle)
      val y = polygon.center.y + polygon.radius * sin(angle)
      s"$x,$y"
    }.mkString(" ")

    val isSelected = AppState.selectedElements.signal.map(_.contains(polygon.id))

    svg.g(
      svg.polygon(
        svg.points := points,
        svg.fill := "rgba(100, 108, 255, 0.3)",
        svg.stroke <-- isSelected.map(selected => if (selected) "#ff6b6b" else "#646cff"),
        svg.strokeWidth <-- isSelected.map(selected => if (selected) "3" else "2"),
        svg.className := "clickable-polygon",
        onClick --> { _ => AppState.toggleSelection(polygon.id) }
      ),
      // Polygon center point
      svg.circle(
        svg.cx := polygon.center.x.toString,
        svg.cy := polygon.center.y.toString,
        svg.r := "3",
        svg.fill := "#ff6b6b",
        svg.className := "polygon-center"
      )
    )

  def renderCanvasText(text: io.github.scala_tessella.editor.models.CanvasText): Element =
    val isSelected = AppState.selectedElements.signal.map(_.contains(text.id))

    svg.text(
      svg.x := text.position.x.toString,
      svg.y := text.position.y.toString,
      svg.fontSize := text.fontSize.toString,
      svg.fill <-- isSelected.map(selected => if (selected) "#ff6b6b" else "#ccc"),
      svg.fontWeight <-- isSelected.map(selected => if (selected) "bold" else "normal"),
      svg.textAnchor := "middle",
      svg.className := "clickable-text",
      text.text,
      onClick --> { _ => AppState.toggleSelection(text.id) }
    )

  def renderPolygonPoints(polygon: io.github.scala_tessella.editor.models.CanvasPolygon): List[Element] =
    import scala.math.{cos, Pi, sin}

    (0 until polygon.sides).map { i =>
      val angle = (2 * Pi * i / polygon.sides) - (Pi / 2) + polygon.rotation
      val x = polygon.center.x + polygon.radius * cos(angle)
      val y = polygon.center.y + polygon.radius * sin(angle)

      svg.circle(
        svg.cx := x.toString,
        svg.cy := y.toString,
        svg.r := "2",
        svg.fill := "#4ade80",
        svg.className := "polygon-point",
        onClick --> { _ => println(s"Clicked point $i of polygon ${polygon.id}") }
      )
    }.toList