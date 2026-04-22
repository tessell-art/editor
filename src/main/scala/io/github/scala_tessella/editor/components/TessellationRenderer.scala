package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.editor.models.{DoublingAnimation, EditorState, FanAnimation, MirrorAnimation}
import io.github.scala_tessella.editor.utils.geo.TessellationGeometry.*

object TessellationRenderer:

  def renderTiling(tiling: TilingDCEL): Element =

    val tilingPolygons =
      TessellationPolygonRenderer.renderTilingPolygons(tiling, tilingPointToCanvasView)

    val perimeterEdges = TessellationEdgeRenderer.renderPerimeterEdges(tiling, tilingPointToCanvasView)

    // Interior edges overlay only when Inserter tool is active AND a polygon is highlighted
    val interiorEdgesOverlay = children <--
      EditorState.isInserterActive
        .combineWith(EditorState.selectedFaceForInsertion)
        .map:
          case (true, Some(fid)) =>
            TessellationEdgeRenderer.renderInteriorEdgesForFace(tiling, fid, tilingPointToCanvasView)
          case _                 => List.empty

    val nodeLabels = children <-- EditorState.viewState.signal.map(_.showNodeLabels).distinct.map:
      showLabels =>
        if showLabels then
          TessellationOverlayRenderer.renderNodeLabels(tiling.coordinates, tilingPointToCanvasView)
        else List.empty

    val nodeUniformity = children <--
      EditorState.viewState.signal.map(_.showUniformity).distinct
        .combineWith(EditorState.viewState.signal.map(_.uniformityMap).distinct)
        .map: (showUni, uniOpt) =>
          if showUni && uniOpt.nonEmpty then
            TessellationOverlayRenderer.renderUniformity(
              tiling.coordinates,
              uniOpt.get,
              tilingPointToCanvasView
            )
          else List.empty

    val nodeRotation = children <--
      EditorState.viewState.signal.map(_.showRotation).distinct
        .combineWith(EditorState.viewState.signal.map(_.rotationVertexIds).distinct)
        .map: (showRot, rotOpt) =>
          if showRot && rotOpt.nonEmpty then
            TessellationOverlayRenderer.renderRotation(
              tiling.coordinates,
              rotOpt.get,
              tilingPointToCanvasView
            )
          else List.empty

    val nodeReflection = children <--
      EditorState.viewState.signal.map(_.showReflection).distinct
        .combineWith(EditorState.viewState.signal.map(_.reflectionVertexIds).distinct)
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

    val clickablePointsDisplay = children <--
      EditorState.clickablePoints.signal
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
      TessellationPolygonRenderer.selectionPattern,
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
      renderPolygon = TessellationPolygonRenderer.renderTilingPolygonFromPoints,
      toCanvasPoint = tilingPointToCanvasView
    )

  def renderDoublingAnimation(animation: DoublingAnimation): Element =
    TessellationAnimationRenderer.renderDoublingAnimation(
      animation = animation,
      renderPolygon = TessellationPolygonRenderer.renderTilingPolygonFromPoints
    )

  def renderMirrorAnimation(animation: MirrorAnimation): Element =
    TessellationAnimationRenderer.renderMirrorAnimation(
      animation = animation,
      renderPolygon = TessellationPolygonRenderer.renderTilingPolygonFromPoints
    )
