
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
      svg.strokeLineCap := "round", // Rounded line caps for better appearance
      svg.className := "perimeter-edge",
      // Enhanced visual feedback
      onMouseEnter --> { _ =>
        // Optional: Could trigger additional state changes here
      },
      onMouseLeave --> { _ =>
        // Optional: Could trigger additional state changes here  
      },
      onClick --> { _ => AppState.handlePerimeterEdgeClick(id, edgeIndex) }
    )