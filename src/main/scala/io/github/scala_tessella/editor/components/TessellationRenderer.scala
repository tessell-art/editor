package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.geometry.{AngleDegree, RegularPolygon}
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.structure.{FaceId, Vertex}
import io.github.scala_tessella.editor.models.{
  AppState,
  ClickablePoint,
  DoublingAnimation,
  EditorConfig,
  EditorMode,
  EditorState,
  FailedPolygonPlacement,
  FanAnimation,
  MirrorAnimation,
  Tool
}
import io.github.scala_tessella.editor.operations.OperationGuard.gate
import io.github.scala_tessella.editor.operations.TessellationOperations
import io.github.scala_tessella.editor.operations.TessellationOperations.{VertexCoord, toCoords}
import io.github.scala_tessella.editor.utils.ColorRGB.*
import io.github.scala_tessella.editor.utils.SvgDsl.{
  circleCoordsRadius,
  lineCoords
}
import io.github.scala_tessella.editor.utils.geo.TessellationGeometry.*
import io.github.scala_tessella.editor.utils.geo.{LineSegment, Point}
import io.github.scala_tessella.ring_seq.RingSeq.slidingO
import org.scalajs.dom.EndingType.transparent

object TessellationRenderer:

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
    val isSelected =
      EditorState.selectedTilingPolygons.signal.map:
        _.contains(faceId)

    val rgbSignal =
      EditorState.polygonColors.signal.map:
        _.getOrElse(faceId, EditorConfig.defaultPolygonColor).toRgb

    val opacity =
      EditorState.showUniformity.signal.map: showUni =>
        if showUni then "0.0" else "1.0"

    // Check if this polygon should be hidden due to failed deletion
    val shouldHideForDeletion =
      EditorState.failedDeletion.signal.map:
        case Some(failedDel) => failedDel.faceId == faceId
        case None            => false

    // Update stroke and styling based on editor mode
    val strokeColorSignal =
      isSelected
        .combineWith(EditorState.editorMode.signal)
        .map: (selected, mode) =>
          if selected then "#ff6b6b"
          else
            mode match
              case EditorMode.Select => "#646cff"
              case EditorMode.Delete => "#ff4444"

    val strokeWidthSignal =
      isSelected
        .combineWith(EditorState.editorMode.signal)
        .map: (selected, mode) =>
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
      svg.className <-- EditorState.editorMode.signal.map:
        case EditorMode.Select => "tiling-polygon"
        case EditorMode.Delete => "tiling-polygon delete-mode"
      ,
      // Cursor style and conditional opacity
      svg.style <-- shouldHideForDeletion
        .combineWith(EditorState.editorMode.signal)
        .combineWith(EditorState.activeTool.signal)
        .map:
          case (hidden, mode, tool) =>
            val cursor  = TessellationCursorStyles.polygonCursorCss(mode, tool)
            val opacity = if hidden then "opacity: 0;" else "opacity: 1;"
            cursor + opacity
      ,
      onClick.compose(gate) --> { _ =>

        AppState.handleTilingPolygonClick(faceId)
      }
    )

    val patternOverlay = svg.polygon(
      svg.points        := pointsStr, // static, precomputed
      svg.fill          := "url(#selection-pattern)",
      svg.pointerEvents := "none",
      svg.style <-- shouldHideForDeletion.map: hidden =>
        if hidden then "opacity: 0;" else "opacity: 1;"
    )

    svg.g(
      basePolygon,
      child.maybe <-- isSelected
        .combineWith(shouldHideForDeletion)
        .map:
          case (selected, hidden) => if selected && !hidden then Some(patternOverlay) else None
    )

  def renderTiling(tiling: TilingDCEL): Element =

    // Precompute face data once per render pass
    val facesData: List[(FaceId, String)] =
      tiling.innerFacesVertices.map: (faceId, faceVertices) =>
        val pointStrings =
          faceVertices.map: vertex =>
            val point = tilingPointToCanvasView(vertex.coords.toPoint)
            s"${point.x},${point.y}"
        (faceId, pointStrings.mkString(" "))

    val tilingPolygons =
      facesData.map: (faceId, pointsStr) =>
        renderTilingPolygonFromPoints(pointsStr, faceId)

    val perimeterEdges =
      tiling.boundaryVertices
        .map:
          _.toCoords
        .slidingO(2).toList.zipWithIndex
        .map: (vs, index) =>
          renderPerimeterEdge((vs(0), vs(1)), index, s"perimeter-edge-$index")

    // Interior edges overlay only when Inserter tool is active AND a polygon is highlighted
    val interiorEdgesOverlay = children <--
      EditorState.isInserterActive
        .combineWith(EditorState.selectedFaceForInsertion)
        .map:
          case (true, Some(fid)) => renderInteriorEdgesForFace(tiling, fid)
          case _                 => List.empty

    val nodeLabels = children <-- EditorState.showNodeLabels.signal.map: showLabels =>
      if showLabels then
        TessellationOverlayRenderer.renderNodeLabels(tiling.coordinates, tilingPointToCanvasView)
      else List.empty

    val nodeUniformity = children <-- EditorState.showUniformity.signal
      .combineWith(EditorState.uniformityMap.signal)
      .map: (showUni, uniOpt) =>
        if showUni && uniOpt.nonEmpty then
          TessellationOverlayRenderer.renderUniformity(
            tiling.coordinates,
            uniOpt.get,
            tilingPointToCanvasView
          )
        else List.empty

    val nodeRotation = children <-- EditorState.showRotation.signal
      .combineWith(EditorState.rotationVertexIds.signal)
      .map: (showRot, rotOpt) =>
        if showRot && rotOpt.nonEmpty then
          TessellationOverlayRenderer.renderRotation(
            tiling.coordinates,
            rotOpt.get,
            tilingPointToCanvasView
          )
        else List.empty

    val nodeReflection = children <-- EditorState.showReflection.signal
      .combineWith(EditorState.reflectionVertexIds.signal)
      .map: (showRefl, reflOpt) =>
        if showRefl && reflOpt.nonEmpty then
          TessellationOverlayRenderer.renderReflection(
            tiling.coordinates,
            reflOpt.get,
            tilingPointToCanvasView
          )
        else List.empty

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
      placement.map:
        PreviewPolygonRenderer.renderPreview

    // Failed polygon wireframe overlay for deletion
    val failedDeletionWireframe = child.maybe <-- EditorState.failedDeletion.signal.map: deletion =>
//      deletion.map(x => FailedPolygonRenderer.renderFailedDeletion(x, tiling.coordinates))
      None

    val clickablePointsDisplay = children <-- EditorState.clickablePoints.signal
      .combineWith(EditorState.measurementStartPoint.signal)
      .map: (points, startPointOpt) =>
        points
          .filterNot: p =>
            startPointOpt.contains(p)
          .map:
            renderClickablePoint

    val measurementSignals =
      EditorState.measurementStartPoint.signal
        .combineWith(EditorState.measurementPreviousEndPoint.signal, EditorState.measurementEndPoint.signal)

    val measurementStartPointDisplay =
      child.maybe <-- EditorState.measurementStartPoint.signal.map:
        _.map:
          renderMeasurementStartPoint
    val measurementEndPointDisplay   =
      child.maybe <-- EditorState.measurementEndPoint.signal.map:
        _.map:
          renderMeasurementEndPoint
    val measurementLineDisplay       = child.maybe <-- measurementSignals.map:
      case (Some(start), _, Some(end)) => Some(renderMeasurementLine(start, end))
      case _                           => None

    val previousMeasurementLineDisplay = child.maybe <-- measurementSignals.map:
      case (Some(start), Some(previousEnd), _) => Some(renderPreviousMeasurementLine(start, previousEnd))
      case _                                   => None

    val measurementAngleArcDisplay = child.maybe <-- measurementSignals.map:
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

  def renderFanAnimation(animation: FanAnimation): Element =
    TessellationAnimationRenderer.renderFanAnimation(
      animation = animation,
      renderPolygon = renderTilingPolygonFromPoints,
      toCanvasPoint = tilingPointToCanvasView
    )

  def renderDoublingAnimation(animation: DoublingAnimation): Element =
    TessellationAnimationRenderer.renderDoublingAnimation(
      animation = animation,
      renderPolygon = renderTilingPolygonFromPoints
    )

  def renderMirrorAnimation(animation: MirrorAnimation): Element =
    TessellationAnimationRenderer.renderMirrorAnimation(
      animation = animation,
      renderPolygon = renderTilingPolygonFromPoints
    )

  private def renderClickablePoint(p: ClickablePoint): Element =
    val point = tilingPointToCanvasView(p.point)

    svg.circle(
      circleCoordsRadius(point, 4),
      svg.fill          := "#ff9500",
      svg.stroke        := "black",
      svg.strokeWidth   := "1",
      svg.className     := "clickable-point",
      onMountCallback: ctx =>
        // Guard the event stream at the source
        gate(
          ctx.thisNode.events(onClick.preventDefault.mapTo(p))
        )
          .withCurrentValueOf(EditorState.activeTool.signal)
          .foreach {
            case (point, Some(Tool.Measurement)) => AppState.handlePointClickForMeasurement(point)
            case (point, Some(Tool.Fan))         => AppState.handlePointClickForFan(point)
            case (point, Some(Tool.Inserter))    => AppState.handlePointClickForInsertion(point)
            case (point, _)                      => AppState.handlePointClickForDeletion(point)
          }(using ctx.owner): Unit,
      svg.style         := "cursor: crosshair;",
      svg.style <-- EditorState.activeTool.signal.map: tool =>
        TessellationCursorStyles.clickablePointCursorCss(tool),
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
    edges.zipWithIndex.map:
      case (pair, idx) =>
        renderInteriorEdge((pair(0), pair(1)), faceId, s"interior-edge-${faceId.value}-$idx")

  // New: render interactive interior edges for inserter tool
  private def renderInteriorEdges(tiling: TilingDCEL): List[Element] =
    if tiling.isEmpty then Nil
    else
      tiling.innerFacesVertices.flatMap:
        rawRender

  // New: render interactive interior edges for one selected face (Inserter mode)
  private def renderInteriorEdgesForFace(tiling: TilingDCEL, faceId: FaceId): List[Element] =
    if tiling.isEmpty then Nil
    else
      tiling.findInnerFaceVertices(faceId).toOption match
        case Some(vertices) => rawRender(faceId, vertices)
        case None           => Nil

  private def renderInteriorEdge(
      edge: (VertexCoord, VertexCoord),
      faceId: FaceId,
      id: String
  ): Element =
    val v1     = edge._1.point
    val v2     = edge._2.point
    val point1 = tilingPointToCanvasView(v1)
    val point2 = tilingPointToCanvasView(v2)

    val previewState: Signal[(Option[Int], Boolean, Option[Vector[AngleDegree]], TilingDCEL)] =
      EditorState.selectedPolygon.signal
        .combineWith(EditorState.isIrregularSelected.signal)
        .combineWith(EditorState.recentIrregularPolygon.signal)
        .combineWith(EditorState.currentTiling.signal)
        .map:
          case (maybeSides, isIrregular, maybeAngles, tiling) =>
            (maybeSides, isIrregular, maybeAngles, tiling)

    val interactionArea = svg.line(
      lineCoords(LineSegment(point1, point2)),
      svg.stroke        := transparent,
      svg.strokeWidth   := "10",
      svg.strokeLineCap := "round",
      svg.className     := "interior-edge-transparent",
      // Show inner preview oriented into this face
      onMouseEnter.compose(stream =>
        gate(stream).withCurrentValueOf(previewState)
      ) --> {
        case (
              _,
              maybeSides: Option[Int],
              isIrregular: Boolean,
              maybeAngles: Option[Vector[AngleDegree]],
              tiling: TilingDCEL
            ) =>
          val placementOpt =
            if isIrregular then
              maybeAngles.map { angles =>

                io.github.scala_tessella.editor.models.FailedPolygonPlacement(
                  0,
                  angles,
                  edge,
                  tiling,
                  intoFace = Some(faceId)
                )
              }
            else
              maybeSides.filter(_ >= 3).map { sides =>

                io.github.scala_tessella.editor.models.FailedPolygonPlacement(
                  0,
                  RegularPolygon(sides).angles,
                  edge,
                  tiling,
                  intoFace = Some(faceId)
                )
              }
          EditorState.previewPlacement.set(placementOpt)
      },
      onMouseLeave.compose(gate) --> { _ =>

        EditorState.previewPlacement.set(None)
      },
      // Trigger insertion directly when clicking the highlighted interior edge
      onClick.preventDefault.compose(stream =>
        gate(stream).withCurrentValueOf(EditorState.activeTool.signal)
      ) --> {
        case (_, Some(Tool.Inserter)) =>
          TessellationOperations.attemptPolygonInsertion(edge._1.id, edge._2.id)
          EditorState.previewPlacement.set(None)
        case _                        => ()
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
    val isSelected =
      EditorState.selectedPerimeterEdges.signal.map:
        _.contains(id)

    // Convert tessella coordinates to canvas coordinates
    val point1 = tilingPointToCanvasView(vertex1)
    val point2 = tilingPointToCanvasView(vertex2)

    val previewState: Signal[(Option[Int], Boolean, Option[Vector[AngleDegree]], TilingDCEL)] =
      EditorState.selectedPolygon.signal
        .combineWith(EditorState.isIrregularSelected.signal)
        .combineWith(EditorState.recentIrregularPolygon.signal)
        .combineWith(EditorState.currentTiling.signal)
        .map:
          case (maybeSides, isIrregular, maybeAngles, tiling) =>
            (maybeSides, isIrregular, maybeAngles, tiling)

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
      onMouseEnter.compose(stream =>
        gate(stream).withCurrentValueOf(previewState)
      ) --> {
        case (
              _,
              maybeSides: Option[Int],
              isIrregular: Boolean,
              maybeAngles: Option[Vector[AngleDegree]],
              tiling: TilingDCEL
            ) =>
          val placementOpt =
            if isIrregular then
              maybeAngles.map(angles =>
                FailedPolygonPlacement(
                  edgeIndex,
                  angles,
                  edge,
                  tiling
                )
              )
            else
              maybeSides.filter(_ >= 3).map(sides =>
                FailedPolygonPlacement(
                  edgeIndex,
                  RegularPolygon(sides).angles,
                  edge,
                  tiling
                )
              )
          EditorState.previewPlacement.set(placementOpt)
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
      svg.stroke <-- EditorState.perimeterEdgeColor.signal.map:
        _.toRgb
      ,
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
