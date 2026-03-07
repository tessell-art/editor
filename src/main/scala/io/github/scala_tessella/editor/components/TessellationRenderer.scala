package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.structure.FaceId
import io.github.scala_tessella.editor.models.{
  AppState,
  DoublingAnimation,
  EditorConfig,
  EditorMode,
  EditorState,
  FanAnimation,
  MirrorAnimation
}
import io.github.scala_tessella.editor.operations.OperationGuard.gate
import io.github.scala_tessella.editor.utils.ColorRGB.*
import io.github.scala_tessella.editor.utils.geo.TessellationGeometry.*

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

    val perimeterEdges = TessellationEdgeRenderer.renderPerimeterEdges(tiling, tilingPointToCanvasView)

    // Interior edges overlay only when Inserter tool is active AND a polygon is highlighted
    val interiorEdgesOverlay = children <--
      EditorState.isInserterActive
        .combineWith(EditorState.selectedFaceForInsertion)
        .map:
          case (true, Some(fid)) =>
            TessellationEdgeRenderer.renderInteriorEdgesForFace(tiling, fid, tilingPointToCanvasView)
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
          .map: p =>
            TessellationMeasurementRenderer.renderClickablePoint(p, tilingPointToCanvasView)

    val measurementSignals =
      EditorState.measurementStartPoint.signal
        .combineWith(EditorState.measurementPreviousEndPoint.signal, EditorState.measurementEndPoint.signal)

    val measurementStartPointDisplay =
      child.maybe <-- EditorState.measurementStartPoint.signal.map:
        _.map: p =>
          TessellationMeasurementRenderer.renderMeasurementStartPoint(p, tilingPointToCanvasView)
    val measurementEndPointDisplay   =
      child.maybe <-- EditorState.measurementEndPoint.signal.map:
        _.map: p =>
          TessellationMeasurementRenderer.renderMeasurementEndPoint(p, tilingPointToCanvasView)
    val measurementLineDisplay       = child.maybe <-- measurementSignals.map:
      case (Some(start), _, Some(end)) =>
        Some(TessellationMeasurementRenderer.renderMeasurementLine(start, end, tilingPointToCanvasView))
      case _                           => None

    val previousMeasurementLineDisplay = child.maybe <-- measurementSignals.map:
      case (Some(start), Some(previousEnd), _) =>
        Some(
          TessellationMeasurementRenderer.renderPreviousMeasurementLine(
            start,
            previousEnd,
            tilingPointToCanvasView
          )
        )
      case _                                   => None

    val measurementAngleArcDisplay = child.maybe <-- measurementSignals.map:
      case (Some(start), Some(previousEnd), Some(end)) =>
        Some(
          TessellationMeasurementRenderer.renderMeasurementAngleArc(
            start,
            previousEnd,
            end,
            tilingPointToCanvasView
          )
        )
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
