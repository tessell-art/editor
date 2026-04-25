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

    // Interior edges overlay only when AddPolygon's Inside sub-mode is active AND a polygon is highlighted
    val interiorEdgesOverlay = children <--
      EditorState.isAddInsideActive
        .combineWith(EditorState.selectedFaceForInsertion)
        .map:
          case (true, Some(fid)) =>
            TessellationEdgeRenderer.renderInteriorEdgesForFace(tiling, fid, tilingPointToCanvasView)
          case _                 => List.empty

    // Secondary overlays (labels + uniformity / rotation / reflection) honour the user's toggle AND
    // the level-of-detail threshold — at low zoom they auto-hide to reduce visual noise.

    val nodeLabels = children <--
      EditorState.viewState.signal.map(_.showNodeLabels).distinct
        .combineWith(EditorState.isAboveLodThreshold)
        .map: (showLabels, aboveLod) =>
          if showLabels && aboveLod then
            TessellationOverlayRenderer.renderNodeLabels(tiling.coordinates, tilingPointToCanvasView)
          else List.empty

    val nodeUniformity = children <--
      EditorState.viewState.signal.map(_.showUniformity).distinct
        .combineWith(EditorState.viewState.signal.map(_.uniformityMap).distinct)
        .combineWith(EditorState.isAboveLodThreshold)
        .map: (showUni, uniOpt, aboveLod) =>
          if showUni && aboveLod && uniOpt.nonEmpty then
            TessellationOverlayRenderer.renderUniformity(
              tiling.coordinates,
              uniOpt.get,
              tilingPointToCanvasView
            )
          else List.empty

    val nodeRotation = children <--
      EditorState.viewState.signal.map(_.showRotation).distinct
        .combineWith(EditorState.viewState.signal.map(_.rotationVertexIds).distinct)
        .combineWith(EditorState.isAboveLodThreshold)
        .map: (showRot, rotOpt, aboveLod) =>
          if showRot && aboveLod && rotOpt.nonEmpty then
            TessellationOverlayRenderer.renderRotation(
              tiling.coordinates,
              rotOpt.get,
              tilingPointToCanvasView
            )
          else List.empty

    val nodeReflection = children <--
      EditorState.viewState.signal.map(_.showReflection).distinct
        .combineWith(EditorState.viewState.signal.map(_.reflectionVertexIds).distinct)
        .combineWith(EditorState.isAboveLodThreshold)
        .map: (showRefl, reflOpt, aboveLod) =>
          if showRefl && aboveLod && reflOpt.nonEmpty then
            TessellationOverlayRenderer.renderReflection(
              tiling.coordinates,
              reflOpt.get,
              tilingPointToCanvasView
            )
          else List.empty

    // Failed polygon wireframe overlay for placement (adjust inward orientation in AddPolygon Inside mode)
    val failedPolygonWireframe = child.maybe <--
      EditorState.errorState.signal.map(_.failedPlacement).distinct
        .combineWith(EditorState.isAddInsideActive, EditorState.selectedFaceForInsertion)
        .map: (placementOpt, isAddInside, faceIdOpt) =>
          placementOpt.map: p =>

            val adjusted =
              (isAddInside, faceIdOpt) match
                case (true, Some(fid)) if p.intoFace.isEmpty => p.copy(intoFace = Some(fid))
                case _                                       => p
            FailedPolygonRenderer.renderFailedPlacement(adjusted)

    // Hover preview wireframe for boundary addition
    val previewPolygonWireframe = child.maybe <--
      EditorState.previewState.signal.map(_.previewPlacement).distinct.map: placement =>
        placement.map:
          PreviewPolygonRenderer.renderPreview

    // Failed polygon wireframe overlay for deletion
    val failedDeletionWireframe = child.maybe <--
      EditorState.errorState.signal.map(_.failedDeletion).distinct.map: deletion =>
//      deletion.map(x => FailedPolygonRenderer.renderFailedDeletion(x, tiling.coordinates))
        None

    val clickablePointsDisplay = children <--
      EditorState.measurementState.signal.map(_.clickablePoints).distinct
        .combineWith(EditorState.measurementState.signal.map(_.measurementStartPoint).distinct)
        .map: (points, startPointOpt) =>
          points
            .filterNot: p =>
              startPointOpt.contains(p)
            .map: p =>
              TessellationMeasurementRenderer.renderClickablePoint(p, tilingPointToCanvasView)

    val measurementSignals =
      EditorState.measurementState.signal.map(_.measurementStartPoint).distinct
        .combineWith(
          EditorState.measurementState.signal.map(_.measurementPreviousEndPoint).distinct,
          EditorState.measurementState.signal.map(_.measurementEndPoint).distinct
        )

    val measurementStartPointDisplay =
      child.maybe <-- EditorState.measurementState.signal.map(_.measurementStartPoint).distinct.map:
        _.map: p =>
          TessellationMeasurementRenderer.renderMeasurementStartPoint(p, tilingPointToCanvasView)
    val measurementEndPointDisplay   =
      child.maybe <-- EditorState.measurementState.signal.map(_.measurementEndPoint).distinct.map:
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
