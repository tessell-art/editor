package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{AppState, EditorMode, EditorState}
import io.github.scala_tessella.editor.operations.ColorOperations.getOrAssignPolygonColor

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.tessella.Geometry.Point
import io.github.scala_tessella.tessella.{IncrementalTiling, TilingCoordinates}
import io.github.scala_tessella.tessella.TilingCoordinates.Coords
import io.github.scala_tessella.tessella.Topology.{Edge, NodeOrdering, Node as TilingNode}

object TessellationRenderer:

  private val eyedropperCursor = "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='26' height='26' viewBox='0 0 56 56'%3E%3Cpath fill='white' stroke='black' stroke-width='2' d='M39.6 28.9L40.3 28.1c1.1-1.2 1.1-2.6-.1-3.8L39.5 23.6c3.5-3.2 7.5-3.6 9.9-6.1 3.5-3.5 2.3-8.4-.1-10.9s-7.4-3.6-10.9-.1c-2.5 2.4-2.9 6.4-6.1 10l-.7-.7c-1.2-1.2-2.6-1.1-3.8.1l-.7.6c-1.4 1.4-1.2 2.6 0 3.8l1 1L10.6 39C3.3 46.2 6.8 45.1 2.9 50.7l2.1 2.2c5.4-3.9 4.7-0 12-7.3L34.8 27.8l1 1c1.2 1.2 2.4 1.5 3.8.1zM10.1 46.1c-.9-.9-.7-1.8.2-2.7L30.3 23.3l2.5 2.5L12.8 45.9c-.8.8-1.8 1-2.7.2z'/%3E%3C/svg%3E\") 2 24, auto"
  private val deleteCursor = "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='21' height='21' viewBox='0 0 32 32'%3E%3Cpath stroke='white' stroke-width='6' stroke-linecap='round' d='M4 4 L28 28 M4 28 L28 4'/%3E%3Cpath stroke='red' stroke-width='3' stroke-linecap='round' d='M4 4 L28 28 M4 28 L28 4'/%3E%3C/svg%3E\") 10 10, auto"

  private val selectionPattern: Element = svg.defs(
    svg.pattern(
      svg.idAttr := "selection-pattern",
      svg.patternUnits := "userSpaceOnUse",
      svg.width := "8",
      svg.height := "8",
      svg.path(
        svg.d := "M-2,2 l4,-4 M0,8 l8,-8 M6,10 l4,-4",
        svg.stroke := "rgba(40, 40, 40, 0.6)",
        svg.strokeWidth := "1.5"
      )
    )
  )

  def renderTiling(tiling: IncrementalTiling): Element =

    val tilingPolygons = tiling.orientedPolygons.map { nodes =>
      val polyTag = nodes.sorted(NodeOrdering).map(_.toString).mkString("-")
      val polygonId = s"tiling-poly-$polyTag"
      getOrAssignPolygonColor(polyTag)
      renderTilingPolygon(tiling.coordinates, nodes, polygonId, polyTag)
    }

    val perimeterEdges = tiling.perimeter.toEdgesO.zipWithIndex.map {
      case (edge, index) => renderPerimeterEdge(tiling.coordinates, edge, index, s"perimeter-edge-$index")
    }.toList

    val nodeLabels = children <-- EditorState.showNodeLabels.signal.map { showLabels =>
      if showLabels then renderNodeLabels(tiling.coordinates)
      else List.empty
    }

    // Failed polygon wireframe overlay for placement
    val failedPolygonWireframe = child.maybe <-- EditorState.failedPlacement.signal.map { placement =>
      placement.map(FailedPolygonRenderer.renderFailedPlacement)
    }

    // Failed polygon wireframe overlay for deletion
    val failedDeletionWireframe = child.maybe <-- EditorState.failedDeletion.signal.map { deletion =>
      deletion.map(x => FailedPolygonRenderer.renderFailedDeletion(x, tiling.coordinates))
    }

    svg.g(
      svg.className := "tessellation",
      selectionPattern,
      tilingPolygons,
      perimeterEdges,
      nodeLabels,
      failedPolygonWireframe,
      failedDeletionWireframe
    )

  private def renderNodeLabels(coordinates: Coords): List[Element] =
    // Get all unique nodes from the tiling
    val allNodes = coordinates.keys.toList

    allNodes.map { node =>
      val vertex = coordinates(node)

      // Convert tessella coordinates to canvas coordinates
      val x = vertex.x * 50 + 400
      val y = vertex.y * 50 + 300

      // Offset the label slightly from the vertex to avoid overlap
      val offsetX = x + 8
      val offsetY = y - 8

      svg.text(
        svg.x := offsetX.toString,
        svg.y := offsetY.toString,
        svg.fontSize <-- EditorState.viewTransform.signal.map(transform =>
          // Scale font size with zoom but keep it readable
          val baseFontSize = 12
          val scaledSize = (baseFontSize / transform.scale).max(8).min(20)
          scaledSize.toString
        ),
        // Counter-rotate the text to keep it readable
        svg.transform <-- EditorState.viewTransform.signal.map(transform =>
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
        svg.strokeWidth <-- EditorState.viewTransform.signal.map(transform =>
          (0.5 / transform.scale).max(0.2).min(1.0).toString
        ),
        svg.paintOrder := "stroke fill",
        node.toString
      )
    }

  private def renderTilingPolygon(coordinates: Coords, nodes: Vector[TilingNode], id: String, polyTag: String): Element =
    val center = Point(0, 0)
    val isSelected = EditorState.selectedTilingPolygons.signal.map(_.contains(id))

    val canvasCenter = Point(center.x * 50 + 400, center.y * 50 + 300)

    val points = nodes.map(coordinates).map { vertex =>
      val x = canvasCenter.x + vertex.x * 50
      val y = canvasCenter.y + vertex.y * 50
      s"$x,$y"
    }.mkString(" ")

    val rgbSignal = EditorState.polygonColors.signal.map { colors =>
      val (r, g, b) = colors.getOrElse(polyTag, (200, 200, 200))
      s"rgb($r, $g, $b)"
    }

    // Check if this polygon should be hidden due to failed deletion
    val shouldHideForDeletion = EditorState.failedDeletion.signal.map {
      case Some(failedDel) => failedDel.polygonId == id
      case None => false
    }

    // Update stroke and styling based on editor mode
    val strokeColorSignal = isSelected.combineWith(EditorState.editorMode.signal).map {
      case (selected, mode) =>
        if selected then "#ff6b6b"
        else mode match
          case EditorMode.Select => "#646cff"
          case EditorMode.Delete => "#ff4444" // Red tint for delete mode
    }

    val strokeWidthSignal = isSelected.combineWith(EditorState.editorMode.signal).map {
      case (selected, mode) =>
        if selected then "3.5"
        else mode match
          case EditorMode.Select => "1.5"
          case EditorMode.Delete => "2.0" // Slightly thicker in delete mode
    }

    val basePolygon = svg.polygon(
      svg.points := points,
      svg.fill <-- rgbSignal,
      svg.stroke <-- strokeColorSignal,
      svg.strokeWidth <-- strokeWidthSignal,
      svg.className <-- EditorState.editorMode.signal.map {
        case EditorMode.Select => "tiling-polygon"
        case EditorMode.Delete => "tiling-polygon delete-mode"
      },
      // Add cursor style based on mode
      svg.style <-- shouldHideForDeletion
        .combineWith(EditorState.editorMode.signal)
        .combineWith(EditorState.isEyedropperActive)
        .combineWith(EditorState.isColorSelectorActive).map {
        case (hidden, mode, eyeOn, colorOn) =>
          val cursor =
            if eyeOn then s"cursor: $eyedropperCursor;"
            else if colorOn then "cursor: pointer;"
            else mode match
              case EditorMode.Select => "cursor: pointer;"
              case EditorMode.Delete => s"cursor: $deleteCursor;"
          val opacity = if hidden then "opacity: 0;" else "opacity: 1;"
          cursor + opacity
      },
      onClick --> { _ => AppState.handleTilingPolygonClick(id) }
    )

    val patternOverlay = svg.polygon(
      svg.points := points,
      svg.fill := "url(#selection-pattern)",
      svg.pointerEvents := "none",
      // Hide pattern overlay when showing deletion wireframe
      svg.style <-- shouldHideForDeletion.map(hidden => if hidden then "opacity: 0;" else "opacity: 1;")
    )

    svg.g(
      basePolygon,
      child.maybe <-- isSelected.combineWith(shouldHideForDeletion).map {
        case (selected, hidden) => if (selected && !hidden) Some(patternOverlay) else None
      }
    )

  private def renderPerimeterEdge(coordinates: Coords, edge: Edge, edgeIndex: Int, id: String): Element =
    val vertex1 = coordinates(edge.lesserNode)
    val vertex2 = coordinates(edge.greaterNode)
    val isSelected = EditorState.selectedPerimeterEdges.signal.map(_.contains(id))

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