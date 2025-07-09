package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{AppState, ClickablePoint, EditorMode, EditorState, Tool}
import io.github.scala_tessella.editor.models.EditorConfig.*
import io.github.scala_tessella.editor.operations.ColorOperations.getOrAssignPolygonColor
import io.github.scala_tessella.editor.utils.TessellationGeometry.*

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.tessella.BigDecimalGeometry.BigCoords
import io.github.scala_tessella.tessella.IncrementalTiling
import io.github.scala_tessella.tessella.Topology.{Edge, NodeOrdering, Node as TilingNode}
import org.scalajs.dom.EndingType.transparent

object TessellationRenderer:

  private val colorPickerCursor = "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='26' height='26' viewBox='0 0 56 56'%3E%3Cpath fill='white' stroke='black' stroke-width='2' d='M39.6 28.9L40.3 28.1c1.1-1.2 1.1-2.6-.1-3.8L39.5 23.6c3.5-3.2 7.5-3.6 9.9-6.1 3.5-3.5 2.3-8.4-.1-10.9s-7.4-3.6-10.9-.1c-2.5 2.4-2.9 6.4-6.1 10l-.7-.7c-1.2-1.2-2.6-1.1-3.8.1l-.7.6c-1.4 1.4-1.2 2.6 0 3.8l1 1L10.6 39C3.3 46.2 6.8 45.1 2.9 50.7l2.1 2.2c5.4-3.9 4.7-0 12-7.3L34.8 27.8l1 1c1.2 1.2 2.4 1.5 3.8.1zM10.1 46.1c-.9-.9-.7-1.8.2-2.7L30.3 23.3l2.5 2.5L12.8 45.9c-.8.8-1.8 1-2.7.2z'/%3E%3C/svg%3E\") 2 24, auto"
  private val selectByColorCursor = "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='26' height='31' viewBox='0 0 37.643265 44.674143'%3E%3Cg fill='white' stroke='black' stroke-width='1'%3E%3Cpath d='M 15.302,0 C 6.85,0 0,6.309 0,14.09 c 0,7.781 6.85,14.092 15.302,14.092 1.519,-8.259 4.996,-9.012 8.362,-9.012 0.751,0 1.497,0.038 2.214,0.038 2.521,0 4.687,-0.463 5.502,-4.646 C 32.744,7.586 23.752,0 15.302,0 Z m 14.335958,14.790305 c -0.744518,2.094393 -0.955291,2.261786 -3.024775,2.620009 -0.933269,0.161547 -0.832255,0.05748 -1.541035,-0.01983 -0.399,-0.01 -1.037565,-0.119539 -1.441565,-0.119539 -3.879,0 -7.639278,1.034464 -9.861278,8.777464 C 6.2285932,24.929856 1.9315932,19.753102 1.9315932,14.357102 c 0,-6.1150003 4.4505932,-12.4255088 13.5039578,-12.5590596 4.028562,-0.059427 9.877508,3.1268559 12.564508,6.3888559 0.901,1.0939997 2.079899,4.3374067 1.637899,6.6034067 z'/%3E%3Cpath d='m 10.26,15.943 c -1.565,0 -2.839,1.273 -2.839,2.839 0,1.566 1.273,2.839 2.839,2.839 1.564,0 2.838,-1.273 2.838,-2.839 0,-1.566 -1.273,-2.839 -2.838,-2.839 z m 0,4.178 c -0.738,0 -1.339,-0.602 -1.339,-1.339 0,-0.738 0.601,-1.339 1.339,-1.339 0.737,0 1.338,0.602 1.338,1.339 0,0.737 -0.6,1.339 -1.338,1.339 z'/%3E%3Ccircle cx='8.467' cy='11.012' r='2.0880001'/%3E%3Ccircle cx='13.296' cy='7.2950001' r='2.089'/%3E%3Ccircle cx='19.381001' cy='8.7869997' r='2.089'/%3E%3Ccircle cx='24.089001' cy='12.497' r='2.089'/%3E%3Cg transform='matrix(0.09071207,0,0,0.09071207,11.351823,13.156144)'%3E%3Cpolygon points='57.617,303.138 123.48,224.061 181.017,347.451 244.459,317.867 186.921,194.478 289.834,194.854 57.617,0'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E\") 11 9, auto"
  private val measurementCursor = "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='28' height='28' viewBox='-871 1129 256 256'%3E%3Cpath fill='white' stroke='black' stroke-width='8' d='M-871,1185.5l199.2,199.7l56.8-56.7l-199.2-199.7L-871,1185.5z M-627,1328.5l-36.3,36.3l-187.3-187.7l36.4-36.2l25.4,25.4 l-11.2,11.2l6,6l11.2-11.2l12,12l-17.2,17.2l6,6l17.2-17.2l12,12l-11.2,11.2l6,6l11.2-11.2l12,12l-17.2,17.2l6,6l17.2-17.2l12,12 l-11.2,11.2l6,6l11.2-11.2l12,12l-17.2,17.2l6,6l17.2-17.2l12,12l-11.2,11.2l6,6l11.2-11.2L-627,1328.5z M-820.3,1165.2c3.1,3,3.2,8,0.2,11.2c-3,3.1-8,3.2-11.2,0.2c-3.1-3-3.2-8-0.2-11.2 C-828.5,1162.3-823.5,1162.2-820.3,1165.2z'/%3E%3C/svg%3E\") 4 4, auto"
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
      val polyTag = nodes.sorted(using NodeOrdering).map(_.toString).mkString("-")
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

    val clickablePointsDisplay = children <-- EditorState.clickablePoints.signal
      .combineWith(EditorState.measurementStartPoint.signal)
      .map { (points, startPointOpt) =>
        points.filterNot(p => startPointOpt.contains(p)).map(renderClickablePoint)
      }

    val measurementStartPointDisplay = child.maybe <-- EditorState.measurementStartPoint.signal.map(_.map(renderMeasurementStartPoint))
    val measurementEndPointDisplay = child.maybe <-- EditorState.measurementEndPoint.signal.map(_.map(renderMeasurementEndPoint))
    val measurementLineDisplay = child.maybe <-- EditorState.measurementStartPoint.signal
      .combineWith(EditorState.measurementEndPoint.signal)
      .map {
        case (Some(start), Some(end)) => Some(renderMeasurementLine(start, end))
        case _                        => None
      }

    svg.g(
      svg.className := "tessellation",
      selectionPattern,
      tilingPolygons,
      perimeterEdges,
      nodeLabels,
      failedPolygonWireframe,
      failedDeletionWireframe,
      clickablePointsDisplay,
      measurementStartPointDisplay,
      measurementEndPointDisplay,
      measurementLineDisplay
    )

  private def renderNodeLabels(coordinates: BigCoords): List[Element] =
    // Get all unique nodes from the tiling
    val allNodes = coordinates.keys.toList

    allNodes.map { node =>
      val vertex = coordinates(node).toPoint

      // Convert tessella coordinates to canvas coordinates
      val (x, y) = tilingPointToCanvasView(vertex)

      // Offset the label slightly from the vertex to avoid overlap
      val offsetX = x + 4
      val offsetY = y - 4

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

  private def renderClickablePoint(p: ClickablePoint): Element =
    val (x, y) = tilingPointToCanvasView(p.point)

    svg.circle(
      svg.cx := x.toString,
      svg.cy := y.toString,
      svg.r := "4",
      svg.fill := "#ff9500",
      svg.stroke := "black",
      svg.strokeWidth := "1",
      svg.className := "clickable-point",
      onClick.preventDefault.mapTo(p) --> (point => AppState.handlePointClickForMeasurement(point)),
      svg.style := "cursor: crosshair;",
      svg.pointerEvents := "visible"
    )

  private def renderMeasurementPoint(p: ClickablePoint, isStartPoint: Boolean = true): Element =
    val (x, y) = tilingPointToCanvasView(p.point)

    svg.circle(
      svg.cx := x.toString,
      svg.cy := y.toString,
      svg.r := "5",
      svg.fill := (if isStartPoint then "#00C853" else "#D50000"),
      svg.stroke := "black",
      svg.strokeWidth := "1",
      svg.className := s"measurement-${if isStartPoint then "start" else "end"}-point",
      onClick.preventDefault.mapTo(p) --> (point => AppState.handlePointClickForMeasurement(point))
    )

  private def renderMeasurementStartPoint(p: ClickablePoint): Element =
    renderMeasurementPoint(p, isStartPoint = true)

  private def renderMeasurementEndPoint(p: ClickablePoint): Element =
    renderMeasurementPoint(p, isStartPoint = false)

  private def renderMeasurementLine(start: ClickablePoint, end: ClickablePoint): Element =
    val (x1, y1) = tilingPointToCanvasView(start.point)
    val (x2, y2) = tilingPointToCanvasView(end.point)

    svg.line(
      svg.x1 := x1.toString,
      svg.y1 := y1.toString,
      svg.x2 := x2.toString,
      svg.y2 := y2.toString,
      svg.stroke := "#ffffff",
      svg.strokeWidth := "2",
      svg.strokeDashArray := "5, 5",
      svg.className := "measurement-line",
      svg.pointerEvents := "none"
    )

  private def renderTilingPolygon(coordinates: BigCoords, nodes: Vector[TilingNode], id: String, polyTag: String): Element =
    val isSelected = EditorState.selectedTilingPolygons.signal.map(_.contains(id))

    val points = nodes.map(coordinates).map { vertex =>
      val (x, y) = tilingPointToCanvasView(vertex.toPoint)
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
        .combineWith(EditorState.activeTool.signal).map {
          case (hidden, mode, tool) =>
            val cursor = tool match
              case Some(Tool.ColorPicker)   => s"cursor: $colorPickerCursor;"
              case Some(Tool.SelectByColor) => s"cursor: $selectByColorCursor;"
              case Some(Tool.Measurement) => s"cursor: $measurementCursor;"
              case _ => mode match
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

  private def renderPerimeterEdge(coordinates: BigCoords, edge: Edge, edgeIndex: Int, id: String): Element =
    val vertex1 = coordinates(edge.lesserNode).toPoint
    val vertex2 = coordinates(edge.greaterNode).toPoint
    val isSelected = EditorState.selectedPerimeterEdges.signal.map(_.contains(id))

    // Convert tessella coordinates to canvas coordinates
    val (x1, y1) = tilingPointToCanvasView(vertex1)
    val (x2, y2) = tilingPointToCanvasView(vertex2)

    // A wider, transparent line for easier interaction, especially on touch devices
    val interactionArea = svg.line(
      svg.x1 := x1.toString,
      svg.y1 := y1.toString,
      svg.x2 := x2.toString,
      svg.y2 := y2.toString,
      svg.stroke := transparent,
      svg.strokeWidth := "12", // Increased width for a larger touch target
      svg.strokeLineCap := "round",
      svg.className := "perimeter-edge-transparent",
      svg.pointerEvents <-- EditorState.highlightedPolygonId.signal.map(_.fold("visiblePainted")(_ => "none")),
      // Enhanced visual feedback and click handling
      onMouseEnter --> { _ =>
        // Optional: Could trigger additional state changes here
      },
      onMouseLeave --> { _ =>
        // Optional: Could trigger additional state changes here
      },
      onClick --> { _ => AppState.handlePerimeterEdgeClick(id, edgeIndex) }
    )

    // The visible line that the user sees
    val visibleLine = svg.line(
      svg.x1 := x1.toString,
      svg.y1 := y1.toString,
      svg.x2 := x2.toString,
      svg.y2 := y2.toString,
      svg.stroke := "#ff9500",
      svg.strokeWidth := "4",
      svg.strokeLineCap := "round",
      svg.className := "perimeter-edge",
      svg.pointerEvents := "none" // This part does not need to capture pointer events
    )

    // Grouping the visible line and its interaction area
    svg.g(
      visibleLine,
      interactionArea
    )