package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.geometry.{BigLineSegment, BigPoint, RegularPolygon}
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.TilingSymmetry.{BoundaryEdge, BoundaryLocation, BoundaryVertex}
import io.github.scala_tessella.dcel.structure.{FaceId, Vertex, VertexId}
import io.github.scala_tessella.dcel.geometry.BigPoint.centroid
import io.github.scala_tessella.editor.models.{
  AppState,
  ClickablePoint,
  EditorConfig,
  EditorMode,
  EditorState,
  FailedPolygonPlacement,
  Tool
}
import io.github.scala_tessella.editor.operations.OperationGuard.gate
import io.github.scala_tessella.editor.operations.TessellationOperations
import io.github.scala_tessella.editor.operations.TessellationOperations.{VertexCoord, toCoords}
import io.github.scala_tessella.editor.utils.ColorRGB.*
import io.github.scala_tessella.editor.utils.SvgDsl.{
  circleCoordsRadius,
  lineCoords,
  textCoords,
  uniformColorMap
}
import io.github.scala_tessella.editor.utils.geo.TessellationGeometry.*
import io.github.scala_tessella.editor.utils.geo.{LineSegment, Point}
import io.github.scala_tessella.editor.utils.ColorRGB
import io.github.scala_tessella.ring_seq.RingSeq.slidingO
import org.scalajs.dom.EndingType.transparent

object TessellationRenderer:

  private val colorPickerCursor   =
    "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='26' height='26' viewBox='0 0 56 56'%3E%3Cpath fill='white' stroke='black' stroke-width='2' d='M39.6 28.9L40.3 28.1c1.1-1.2 1.1-2.6-.1-3.8L39.5 23.6c3.5-3.2 7.5-3.6 9.9-6.1 3.5-3.5 2.3-8.4-.1-10.9s-7.4-3.6-10.9-.1c-2.5 2.4-2.9 6.4-6.1 10l-.7-.7c-1.2-1.2-2.6-1.1-3.8.1l-.7.6c-1.4 1.4-1.2 2.6 0 3.8l1 1L10.6 39C3.3 46.2 6.8 45.1 2.9 50.7l2.1 2.2c5.4-3.9 4.7-0 12-7.3L34.8 27.8l1 1c1.2 1.2 2.4 1.5 3.8.1zM10.1 46.1c-.9-.9-.7-1.8.2-2.7L30.3 23.3l2.5 2.5L12.8 45.9c-.8.8-1.8 1-2.7.2z'/%3E%3C/svg%3E\") 2 24, auto"
  private val selectByColorCursor =
    "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='26' height='31' viewBox='0 0 37.643265 44.674143'%3E%3Cg fill='white' stroke='black' stroke-width='1'%3E%3Cpath d='M 15.302,0 C 6.85,0 0,6.309 0,14.09 c 0,7.781 6.85,14.092 15.302,14.092 1.519,-8.259 4.996,-9.012 8.362,-9.012 0.751,0 1.497,0.038 2.214,0.038 2.521,0 4.687,-0.463 5.502,-4.646 C 32.744,7.586 23.752,0 15.302,0 Z m 14.335958,14.790305 c -0.744518,2.094393 -0.955291,2.261786 -3.024775,2.620009 -0.933269,0.161547 -0.832255,0.05748 -1.541035,-0.01983 -0.399,-0.01 -1.037565,-0.119539 -1.441565,-0.119539 -3.879,0 -7.639278,1.034464 -9.861278,8.777464 C 6.2285932,24.929856 1.9315932,19.753102 1.9315932,14.357102 c 0,-6.1150003 4.4505932,-12.4255088 13.5039578,-12.5590596 4.028562,-0.059427 9.877508,3.1268559 12.564508,6.3888559 0.901,1.0939997 2.079899,4.3374067 1.637899,6.6034067 z'/%3E%3Cpath d='m 10.26,15.943 c -1.565,0 -2.839,1.273 -2.839,2.839 0,1.566 1.273,2.839 2.839,2.839 1.564,0 2.838,-1.273 2.838,-2.839 0,-1.566 -1.273,-2.839 -2.838,-2.839 z m 0,4.178 c -0.738,0 -1.339,-0.602 -1.339,-1.339 0,-0.738 0.601,-1.339 1.339,-1.339 0.737,0 1.338,0.602 1.338,1.339 0,0.737 -0.6,1.339 -1.338,1.339 z'/%3E%3Ccircle cx='8.467' cy='11.012' r='2.0880001'/%3E%3Ccircle cx='13.296' cy='7.2950001' r='2.089'/%3E%3Ccircle cx='19.381001' cy='8.7869997' r='2.089'/%3E%3Ccircle cx='24.089001' cy='12.497' r='2.089'/%3E%3Cg transform='matrix(0.09071207,0,0,0.09071207,11.351823,13.156144)'%3E%3Cpolygon points='57.617,303.138 123.48,224.061 181.017,347.451 244.459,317.867 186.921,194.478 289.834,194.854 57.617,0'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E\") 11 9, auto"
  private val eraserCursor        =
    s"url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='28' height='28' viewBox='${IconsSVG.eraserViewBox}'%3E%3Cpath fill='white' stroke='black' stroke-width='8' d='${IconsSVG.eraserPathD}'/%3E%3C/svg%3E\") 5 20, auto"
  private val inserterCursor      =
    s"url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='27' height='27' viewBox='${IconsSVG.inserterViewBox}'%3E%3Cpath fill='none' stroke='black' stroke-width='2' d='${IconsSVG.inserterPathD}'/%3E%3C/svg%3E\") 13 0, auto"
  private val measurementCursor   =
    "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='28' height='28' viewBox='-871 1129 256 256'%3E%3Cpath fill='white' stroke='black' stroke-width='8' d='M-871,1185.5l199.2,199.7l56.8-56.7l-199.2-199.7L-871,1185.5z M-627,1328.5l-36.3,36.3l-187.3-187.7l36.4-36.2l25.4,25.4 l-11.2,11.2l6,6l11.2-11.2l12,12l-17.2,17.2l6,6l17.2-17.2l12,12l-11.2,11.2l6,6l11.2-11.2l12,12l-17.2,17.2l6,6l17.2-17.2l12,12 l-11.2,11.2l6,6l11.2-11.2l12,12l-17.2,17.2l6,6l17.2-17.2l12,12l-11.2,11.2l6,6l11.2-11.2L-627,1328.5z M-820.3,1165.2c3.1,3,3.2,8,0.2,11.2c-3,3.1-8,3.2-11.2,0.2c-3.1-3-3.2-8-0.2-11.2 C-828.5,1162.3-823.5,1162.2-820.3,1165.2z'/%3E%3C/svg%3E\") 4 4, auto"
  private val deleteCursor        =
    "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='21' height='21' viewBox='0 0 32 32'%3E%3Cpath stroke='white' stroke-width='6' stroke-linecap='round' d='M4 4 L28 28 M4 28 L28 4'/%3E%3Cpath stroke='red' stroke-width='3' stroke-linecap='round' d='M4 4 L28 28 M4 28 L28 4'/%3E%3C/svg%3E\") 10 10, auto"

  private val selectionPattern: Element = svg.defs(
    svg.pattern(
      svg.idAttr       := "selection-pattern",
      svg.patternUnits := "userSpaceOnUse",
      svg.width        := "8",
      svg.height       := "8",
      svg.path(
        svg.d           := "M-2,2 l4,-4 M0,8 l8,-8 M6,10 l4,-4",
        svg.stroke      := "rgba(40, 40, 40, 0.6)",
        svg.strokeWidth := "1.5"
      )
    )
  )

  // New: build polygon elements from a precomputed points string.
  private def renderTilingPolygonFromPoints(pointsStr: String, faceId: FaceId): Element =
    val isSelected = EditorState.selectedTilingPolygons.signal.map(_.contains(faceId))

    val rgbSignal = EditorState.polygonColors.signal.map:
      _.getOrElse(faceId, EditorConfig.defaultPolygonColor).toRgb

    val opacity = EditorState.showUniformity.signal.map: showUni =>
      if showUni then "0.0" else "1.0"

    // Check if this polygon should be hidden due to failed deletion
    val shouldHideForDeletion = EditorState.failedDeletion.signal.map:
      case Some(failedDel) => failedDel.faceId == faceId
      case None            => false

    // Update stroke and styling based on editor mode
    val strokeColorSignal = isSelected.combineWith(EditorState.editorMode.signal).map: (selected, mode) =>
      if selected then "#ff6b6b"
      else
        mode match
          case EditorMode.Select => "#646cff"
          case EditorMode.Delete => "#ff4444"

    val strokeWidthSignal = isSelected.combineWith(EditorState.editorMode.signal).map: (selected, mode) =>
      if selected then "3.5"
      else
        mode match
          case EditorMode.Select => "1.5"
          case EditorMode.Delete => "2.0"

    val basePolygon = svg.polygon(
      svg.points := pointsStr, // static, precomputed
      svg.fill <-- rgbSignal, // reactive (color changes)
      svg.fillOpacity <-- opacity, // reactive (uniformity)
      svg.stroke <-- strokeColorSignal, // reactive
      svg.strokeWidth <-- strokeWidthSignal, // reactive
      svg.className <-- EditorState.editorMode.signal.map {
        case EditorMode.Select => "tiling-polygon"
        case EditorMode.Delete => "tiling-polygon delete-mode"
      },
      // Cursor style and conditional opacity
      svg.style <-- shouldHideForDeletion
        .combineWith(EditorState.editorMode.signal)
        .combineWith(EditorState.activeTool.signal).map {
          case (hidden, mode, tool) =>
            val cursor  = tool match
              case Some(Tool.ColorPicker)         => s"cursor: $colorPickerCursor;"
              case Some(Tool.ShapeAndColorPicker) => s"cursor: $colorPickerCursor;"
              case Some(Tool.SelectByColor)       => s"cursor: $selectByColorCursor;"
              case Some(Tool.Measurement)         => s"cursor: $measurementCursor;"
              case Some(Tool.Eraser)              => s"cursor: $eraserCursor;"
              case Some(Tool.Inserter)            => s"cursor: $inserterCursor;"
              case _                              => mode match
                  case EditorMode.Select => "cursor: pointer;"
                  case EditorMode.Delete => s"cursor: $deleteCursor;"
            val opacity = if hidden then "opacity: 0;" else "opacity: 1;"
            cursor + opacity
        },
      onClick.compose(gate) --> { _ =>

        AppState.handleTilingPolygonClick(faceId)
      }
    )

    val patternOverlay = svg.polygon(
      svg.points        := pointsStr, // static, precomputed
      svg.fill          := "url(#selection-pattern)",
      svg.pointerEvents := "none",
      svg.style <-- shouldHideForDeletion.map(hidden => if hidden then "opacity: 0;" else "opacity: 1;")
    )

    svg.g(
      basePolygon,
      child.maybe <-- isSelected.combineWith(shouldHideForDeletion).map {
        case (selected, hidden) => if selected && !hidden then Some(patternOverlay) else None
      }
    )

  def renderTiling(tiling: TilingDCEL): Element =

    // Precompute face data once per render pass
    val facesData: List[(FaceId, String)] =
      tiling.innerFacesVertices.map: (faceId, faceVertices) =>
        val pointStrings = faceVertices.map: vertex =>
          val point = tilingPointToCanvasView(vertex.coords.toPoint)
          s"${point.x},${point.y}"
        (faceId, pointStrings.mkString(" "))

    val tilingPolygons = facesData.map: (faceId, pointsStr) =>
      renderTilingPolygonFromPoints(pointsStr, faceId)

    val perimeterEdges =
      tiling.boundaryVertices.map(_.toCoords).slidingO(2).toList.zipWithIndex.map: (vs, index) =>
        renderPerimeterEdge((vs(0), vs(1)), index, s"perimeter-edge-$index")

    // Interior edges overlay only when Inserter tool is active AND a polygon is highlighted
    val interiorEdgesOverlay = children <--
      EditorState.isInserterActive
        .combineWith(EditorState.selectedFaceForInsertion)
        .map:
          case (true, Some(fid)) => renderInteriorEdgesForFace(tiling, fid)
          case _                 => List.empty

    val nodeLabels = children <-- EditorState.showNodeLabels.signal.map: showLabels =>
      if showLabels then renderNodeLabels(tiling.coordinates) else List.empty

    val nodeUniformity = children <-- EditorState.showUniformity.signal
      .combineWith(EditorState.uniformityMap.signal)
      .map: (showUni, uniOpt) =>
        if showUni && uniOpt.nonEmpty then renderUniformity(tiling.coordinates, uniOpt.get) else List.empty

    val nodeRotation = children <-- EditorState.showRotation.signal
      .combineWith(EditorState.rotationVertexIds.signal)
      .map: (showRot, rotOpt) =>
        if showRot && rotOpt.nonEmpty then renderRotation(tiling.coordinates, rotOpt.get) else List.empty

    val nodeReflection = children <-- EditorState.showReflection.signal
      .combineWith(EditorState.reflectionVertexIds.signal)
      .map: (showRefl, reflOpt) =>
        if showRefl && reflOpt.nonEmpty then renderReflection(tiling.coordinates, reflOpt.get) else List.empty

    // Failed polygon wireframe overlay for placement (adjust inward orientation in Inserter mode)
    val failedPolygonWireframe = child.maybe <--
      EditorState.failedPlacement.signal
        .combineWith(EditorState.isInserterActive, EditorState.selectedFaceForInsertion)
        .map: (placementOpt, isInserter, faceIdOpt) =>
          placementOpt.map: p =>
            val adjusted =
              (isInserter, faceIdOpt) match
                case (true, Some(fid)) if p.intoFace.isEmpty => p.copy(intoFace = Some(fid))
                case _                                       => p
            FailedPolygonRenderer.renderFailedPlacement(adjusted)

    // Hover preview wireframe for boundary addition
    val previewPolygonWireframe = child.maybe <-- EditorState.previewPlacement.signal.map: placement =>
      placement.map(PreviewPolygonRenderer.renderPreview)

    // Failed polygon wireframe overlay for deletion
    val failedDeletionWireframe = child.maybe <-- EditorState.failedDeletion.signal.map: deletion =>
//      deletion.map(x => FailedPolygonRenderer.renderFailedDeletion(x, tiling.coordinates))
      None

    val clickablePointsDisplay = children <-- EditorState.clickablePoints.signal
      .combineWith(EditorState.measurementStartPoint.signal)
      .map: (points, startPointOpt) =>
        points.filterNot(p => startPointOpt.contains(p)).map(renderClickablePoint)

    val measurementStartPointDisplay =
      child.maybe <-- EditorState.measurementStartPoint.signal.map:
        _.map(renderMeasurementStartPoint)
    val measurementEndPointDisplay   =
      child.maybe <-- EditorState.measurementEndPoint.signal.map:
        _.map(renderMeasurementEndPoint)
    val measurementLineDisplay       = child.maybe <-- EditorState.measurementStartPoint.signal
      .combineWith(EditorState.measurementEndPoint.signal)
      .map:
        case (Some(start), Some(end)) => Some(renderMeasurementLine(start, end))
        case _                        => None

    val previousMeasurementLineDisplay = child.maybe <-- EditorState.measurementStartPoint.signal
      .combineWith(EditorState.measurementPreviousEndPoint.signal)
      .map:
        case (Some(start), Some(previousEnd)) => Some(renderPreviousMeasurementLine(start, previousEnd))
        case _                                => None

    val measurementAngleArcDisplay = child.maybe <-- EditorState.measurementStartPoint.signal
      .combineWith(EditorState.measurementPreviousEndPoint.signal, EditorState.measurementEndPoint.signal)
      .map:
        case (Some(start), Some(previousEnd), Some(end)) =>
          Some(renderMeasurementAngleArc(start, previousEnd, end))
        case _                                           => None

    svg.g(
      svg.className := "tessellation",
      selectionPattern,
      tilingPolygons,
      perimeterEdges,
      interiorEdgesOverlay,
      nodeUniformity,
      nodeRotation,
      nodeReflection,
      nodeLabels,
      failedPolygonWireframe,
      previewPolygonWireframe,
      failedDeletionWireframe,
      clickablePointsDisplay,
      measurementStartPointDisplay,
      measurementEndPointDisplay,
      measurementLineDisplay,
      previousMeasurementLineDisplay,
      measurementAngleArcDisplay
    )

  private def renderNodeLabels(coordinates: Map[VertexId, BigPoint]): List[Element] =
    coordinates.toList.map { (vertexId, bigPoint) =>
      val vertex = bigPoint.toPoint

      // Convert tessella coordinates to canvas coordinates
      val point = tilingPointToCanvasView(vertex)

      // Offset the label slightly from the vertex to avoid overlap
      val offset = point + Point(4, -4)

      svg.text(
        textCoords(offset),
        svg.fontSize <-- EditorState.viewTransform.signal.map: transform =>
          // Scale the font size with zoom but keep it readable
          val baseFontSize = 12
          val scaledSize   = (baseFontSize / transform.scale).max(8).min(20)
          scaledSize.toString
        ,
        // Counter-rotate the text to keep it readable
        svg.transform <-- EditorState.viewTransform.signal.map: transform =>
          // Rotate around the text position to counter the canvas rotation
          s"rotate(${-transform.rotationDegrees} ${offset.x} ${offset.y})",
        svg.fill             := "#ffeb3b", // Bright yellow for visibility
        svg.fontFamily       := "monospace",
        svg.fontWeight       := "bold",
        svg.textAnchor       := "start",
        svg.dominantBaseline := "middle",
        svg.className        := "node-label",
        // Add stroke for better readability
        svg.stroke           := "#000",
        svg.strokeWidth <-- EditorState.viewTransform.signal.map: transform =>
          (0.5 / transform.scale).max(0.2).min(1.0).toString,
        svg.paintOrder       := "stroke fill",
        vertexId.value
      )
    }

  private def renderUniformity(
      coordinates: Map[VertexId, BigPoint],
      uniMap: Map[VertexId, Int]
  ): List[Element] =

    coordinates.toList
      .filter { (vertexId, _) =>

        uniMap.contains(vertexId)
      }
      .map { (vertexId, bigPoint) =>
        val vertex = bigPoint.toPoint

        // Convert tessella coordinates to canvas coordinates
        val point = tilingPointToCanvasView(vertex)
        val color = uniformColorMap.getOrElse(uniMap(vertexId), "black")

        svg.circle(
          circleCoordsRadius(point, 16),
          svg.fill        := color,
          svg.stroke      := color,
          svg.strokeWidth := "1"
        )
      }

  private def renderRotation(
      coordinates: Map[VertexId, BigPoint],
      rotList: List[BoundaryLocation],
      durationSeconds: Int = 30
  ): List[Element] =
    val rotCoords = rotList.map:
      case BoundaryVertex(i)  => i -> coordinates(i)
      case BoundaryEdge(i, j) => i -> BigLineSegment(coordinates(i), coordinates(j)).midPoint

    if rotCoords.isEmpty then Nil
    else
      val center   = tilingPointToCanvasView(rotCoords.map(_._2).centroid.toPoint)
      val elements = rotCoords.map: (id, coords) =>
        val vertex     = tilingPointToCanvasView(coords.toPoint)
        val segment    = LineSegment(center, vertex).extendFromOrigin
        val p1         = segment.p1
        val p2         = segment.p2
        val width      = segment.length / 10
        val vector     = segment.unitVector * width
        val p3         = p2 + Point(-vector.y, vector.x)
        val gradientId = s"rot-grad-${id.value}"

        svg.g(
          svg.defs(
            svg.linearGradient(
              svg.idAttr        := gradientId,
              svg.gradientUnits := "userSpaceOnUse",
              svg.x1            := p2.x.toString,
              svg.y1            := p2.y.toString,
              svg.x2            := p3.x.toString,
              svg.y2            := p3.y.toString,
              svg.stop(svg.offsetAttr := "0%", svg.stopColor   := "Gold", svg.stopOpacity := "0.8"),
              svg.stop(svg.offsetAttr := "100%", svg.stopColor := "Blue", svg.stopOpacity := "0.0")
            )
          ),
          svg.polygon(
            svg.points        := s"${p1.x},${p1.y} ${p2.x},${p2.y} ${p3.x},${p3.y}",
            svg.fill          := s"url(#$gradientId)",
            svg.stroke        := "none",
            svg.pointerEvents := "none"
          )
        )

      List(
        svg.g(
          elements,
          svg.animateTransform(
            svg.attributeName := "transform",
            svg.attributeType := "XML",
            svg.tpe           := "rotate",
            svg.from          := s"360 ${center.x} ${center.y}",
            svg.to            := s"0 ${center.x} ${center.y}",
            svg.dur           := s"${durationSeconds}s",
            svg.repeatCount   := "indefinite"
          )
        )
      )

  private def renderReflection(
      coordinates: Map[VertexId, BigPoint],
      refList: List[(BoundaryLocation, BoundaryLocation)]
  ): List[Element] =

    def locationToPoint(loc: BoundaryLocation): Point =
      tilingPointToCanvasView(loc match {
        case BoundaryVertex(i)  => coordinates(i).toPoint
        case BoundaryEdge(i, j) => LineSegment(coordinates(i).toPoint, coordinates(j).toPoint).midPoint
      })

    refList.map: (loc1, loc2) =>
      val vertex1          = locationToPoint(loc1)
      val vertex2          = locationToPoint(loc2)
      svg.line(
        lineCoords(LineSegment(vertex1, vertex2).extendFromMidPoint),
        svg.stroke          := "DarkOrange",
        svg.strokeWidth     := "1",
        svg.strokeDashArray := "5, 5",
        svg.className       := "previous-measurement-line",
        svg.pointerEvents   := "none"
      )

  private def renderClickablePoint(p: ClickablePoint): Element =
    val point = tilingPointToCanvasView(p.point)

    svg.circle(
      circleCoordsRadius(point, 4),
      svg.fill          := "#ff9500",
      svg.stroke        := "black",
      svg.strokeWidth   := "1",
      svg.className     := "clickable-point",
      onMountCallback { ctx =>
        // Guard the event stream at the source
        gate(
          ctx.thisNode.events(onClick.preventDefault.mapTo(p))
        )
          .withCurrentValueOf(EditorState.activeTool.signal)
          .foreach {
            case (point, Some(Tool.Measurement)) => AppState.handlePointClickForMeasurement(point)
            case (point, Some(Tool.Inserter))    => AppState.handlePointClickForInsertion(point)
            case (point, _)                      => AppState.handlePointClickForDeletion(point)
          }(using ctx.owner): Unit
      },
      svg.style         := "cursor: crosshair;",
      svg.style <-- EditorState.activeTool.signal.map {
        case Some(Tool.Measurement) => s"cursor: crosshair;"
        case Some(Tool.Inserter)    => s"cursor: pointer;"
        case _                      => s"cursor: $deleteCursor;"
      },
      svg.pointerEvents := "visible"
    )

  private def renderMeasurementPoint(p: ClickablePoint, isStartPoint: Boolean = true): Element =
    val point = tilingPointToCanvasView(p.point)

    svg.circle(
      circleCoordsRadius(point, 5),
      svg.fill        := (if isStartPoint then "#00C853" else "#D50000"),
      svg.stroke      := "black",
      svg.strokeWidth := "1",
      svg.className   := s"measurement-${if isStartPoint then "start" else "end"}-point",
      onClick.preventDefault.mapTo(p) --> AppState.handlePointClickForMeasurement
    )

  private def renderMeasurementStartPoint(p: ClickablePoint): Element =
    renderMeasurementPoint(p, isStartPoint = true)

  private def renderMeasurementEndPoint(p: ClickablePoint): Element =
    renderMeasurementPoint(p, isStartPoint = false)

  private def renderMeasurementAngleArc(
      start: ClickablePoint,
      previousEnd: Point,
      end: ClickablePoint
  ): Element =
    val point  = tilingPointToCanvasView(start.point)
    val radius = 25.0

    val p1 = previousEnd
    val p2 = end.point

    val angle1 = start.point.angleTo(p1)
    val angle2 = start.point.angleTo(p2)

    val startArc = point.offsetPolar(radius, angle1)
    val endArc   = point.offsetPolar(radius, angle2)

    val deltaAngle = angle2.normalizeDeltaAngle(angle1)

    val largeArcFlag = 0
    val sweepFlag    = if deltaAngle.toDouble > 0 then 1 else 0

    val dAttribute =
      s"M ${startArc.x} ${startArc.y} A $radius $radius 0 $largeArcFlag $sweepFlag ${endArc.x} ${endArc.y}"

    svg.path(
      svg.d             := dAttribute,
      svg.fill          := "none",
      svg.stroke        := "white",
      svg.strokeWidth   := "1",
      svg.className     := "measurement-angle-arc",
      svg.pointerEvents := "none"
    )

  private def renderPreviousMeasurementLine(start: ClickablePoint, end: Point): Element =
    val point1 = tilingPointToCanvasView(start.point)
    val point2 = tilingPointToCanvasView(end)

    svg.line(
      lineCoords(LineSegment(point1, point2)),
      svg.stroke        := "#ffffff",
      svg.strokeWidth   := "1",
      svg.className     := "previous-measurement-line",
      svg.pointerEvents := "none"
    )

  private def renderMeasurementLine(start: ClickablePoint, end: ClickablePoint): Element =
    val point1 = tilingPointToCanvasView(start.point)
    val point2 = tilingPointToCanvasView(end.point)

    svg.line(
      lineCoords(LineSegment(point1, point2)),
      svg.stroke          := "#ffffff",
      svg.strokeWidth     := "2",
      svg.strokeDashArray := "5, 5",
      svg.className       := "measurement-line",
      svg.pointerEvents   := "none"
    )

  private def rawRender(faceId: FaceId, vertices: List[Vertex]): List[Element] =
    val edges = vertices.map(_.toCoords).slidingO(2).toList
    edges.zipWithIndex.map { case (pair, idx) =>
      renderInteriorEdge((pair(0), pair(1)), faceId, s"interior-edge-${faceId.value}-$idx")
    }

  // New: render interactive interior edges for inserter tool
  private def renderInteriorEdges(tiling: TilingDCEL): List[Element] =
    if tiling.isEmpty then Nil
    else
      tiling.innerFacesVertices.flatMap(rawRender)

  // New: render interactive interior edges for one selected face (Inserter mode)
  private def renderInteriorEdgesForFace(tiling: TilingDCEL, faceId: FaceId): List[Element] =
    if tiling.isEmpty then Nil
    else
      rawRender(faceId, tiling.findInnerFaceVertices(faceId).toOption.get)

  private def renderInteriorEdge(
      edge: (VertexCoord, VertexCoord),
      faceId: FaceId,
      id: String
  ): Element =
    val v1     = edge._1.point
    val v2     = edge._2.point
    val point1 = tilingPointToCanvasView(v1)
    val point2 = tilingPointToCanvasView(v2)

    val interactionArea = svg.line(
      lineCoords(LineSegment(point1, point2)),
      svg.stroke        := transparent,
      svg.strokeWidth   := "10",
      svg.strokeLineCap := "round",
      svg.className     := "interior-edge-transparent",
      // Show inner preview oriented into this face
      onMouseEnter.compose(gate) --> { _ =>

        (EditorState.selectedPolygon.now(), EditorState.isIrregularSelected.now()) match
          case (maybeSides, isIrregular) =>
            val tiling = EditorState.currentTiling.now()
            if isIrregular then
              val angles = EditorState.recentIrregularPolygon.now().get
              EditorState.previewPlacement.set(
                Some(io.github.scala_tessella.editor.models.FailedPolygonPlacement(
                  0,
                  angles,
                  edge,
                  tiling,
                  intoFace = Some(faceId)
                ))
              )
            else
              val sides = maybeSides.getOrElse(0)
              EditorState.previewPlacement.set(
                Some(io.github.scala_tessella.editor.models.FailedPolygonPlacement(
                  0,
                  RegularPolygon(sides).angles,
                  edge,
                  tiling,
                  intoFace = Some(faceId)
                ))
              )
      },
      onMouseLeave.compose(gate) --> { _ =>

        EditorState.previewPlacement.set(None)
      },
      // Trigger insertion directly when clicking the highlighted interior edge
      onClick.preventDefault.compose(gate) --> { _ =>

        EditorState.activeTool.now() match
          case Some(Tool.Inserter) =>
            TessellationOperations.attemptPolygonInsertion(edge._1.id, edge._2.id)
            EditorState.previewPlacement.set(None)
          case _                   => ()
      }
    )

    val visibleLine = svg.line(
      lineCoords(LineSegment(point1, point2)),
      svg.stroke        := "#20A4BE",
      svg.strokeWidth   := "3",
      svg.strokeLineCap := "round",
      svg.className     := "interior-edge",
      svg.pointerEvents := "none"
    )

    svg.g(
      svg.className := "interior-edge-group",
      visibleLine,
      interactionArea
    )

  private def renderPerimeterEdge(
      edge: (VertexCoord, VertexCoord),
      edgeIndex: Int,
      id: String
  ): Element =
    val vertex1    = edge._1.point
    val vertex2    = edge._2.point
    val isSelected = EditorState.selectedPerimeterEdges.signal.map(_.contains(id))

    // Convert tessella coordinates to canvas coordinates
    val point1 = tilingPointToCanvasView(vertex1)
    val point2 = tilingPointToCanvasView(vertex2)

    // A wider, transparent line for easier interaction, especially on touch devices
    val interactionArea = svg.line(
      lineCoords(LineSegment(point1, point2)),
      svg.stroke        := transparent,
      svg.strokeWidth   := "12", // Increased width for a larger touch target
      svg.strokeLineCap := "round",
      svg.className     := "perimeter-edge-transparent",
      svg.pointerEvents <-- EditorState.highlightedPolygonId.signal.map(_.fold("visiblePainted")(_ =>
        "none"
      )),
      // Enhanced visual feedback and click handling
      onMouseEnter.compose(gate) --> { _ =>

        (
          EditorState.selectedPolygon.now(),
          EditorState.isIrregularSelected.now(),
          EditorState.recentIrregularPolygon.now()
        ) match
          case (maybeSides, isIrregular, maybeAngles) =>
            val tiling = EditorState.currentTiling.now()
            val angles =
              if isIrregular then
                maybeAngles.get
              else
                RegularPolygon(maybeSides.get).angles
            EditorState.previewPlacement.set(Some(FailedPolygonPlacement(
              edgeIndex,
              angles,
              edge,
              tiling
            )))
      },
      onMouseLeave.compose(gate) --> { _ =>

        EditorState.previewPlacement.set(None)
      },
      onClick.compose(gate) --> { _ =>

        AppState.handlePerimeterEdgeClick(id, edgeIndex)
      }
    )

    // The visible line that the user sees
    val visibleLine = svg.line(
      lineCoords(LineSegment(point1, point2)),
      svg.stroke        := "#ff9500",
      svg.strokeWidth   := "4",
      svg.strokeLineCap := "round",
      svg.className     := "perimeter-edge",
      svg.pointerEvents := "none" // This part does not need to capture pointer events
    )

    // Grouping the visible line and its interaction area
    svg.g(
      visibleLine,
      interactionArea
    )
