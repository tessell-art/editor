package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.editor.models.{
  Anchor, DoublingAnimation, EditorState, FanAnimation, MirrorAnimation, ReflectCopyDrag, RotateCopyDrag,
  Tool,
  TranslateCopyDrag
}
import io.github.scala_tessella.editor.operations.AddCopyOperations
import io.github.scala_tessella.editor.utils.SvgDsl.circleCoordsRadius
import io.github.scala_tessella.editor.utils.geo.Point
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

    // Node labels honour the user's toggle AND the level-of-detail threshold — at low zoom
    // they auto-hide to reduce visual noise. The uniformity dots and symmetry axes below are
    // exempt: they describe global structure and stay visible at any zoom.

    val nodeLabels = children <--
      EditorState.viewState.signal.map(_.showNodeLabels).distinct
        .combineWith(EditorState.isAboveLodThreshold)
        .map: (showLabels, aboveLod) =>
          if showLabels && aboveLod then
            TessellationOverlayRenderer.renderNodeLabels(tiling.coordinates, tilingPointToCanvasView)
          else List.empty

    // Uniformity dots are exempt from the LOD threshold: showing uniformity makes the
    // polygon fills transparent, so the dots are the whole view — culling them on zoom-out
    // would blank the canvas exactly when the overall pattern is most worth seeing.
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

    // Symmetry axes (rotational / reflectional) are likewise exempt from the LOD threshold:
    // they describe the global structure of the tiling, which is most useful when zoomed out.
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

    // Hover preview wireframe for boundary addition (click-to-place path). Suppressed during a
    // drag-from-palette gesture — the free-floating ghost (next overlay) owns the visible role
    // there, while `previewPlacement` continues to silently track the snapped commit target.
    val previewPolygonWireframe = child.maybe <--
      EditorState.previewState.signal.map(_.previewPlacement).distinct
        .combineWith(
          EditorState.uiState.signal.map(_.isPaletteDragActive).distinct,
          EditorState.previewState.signal.map(_.previewIsValid).distinct
        )
        .map: (placement, paletteDragActive, valid) =>
          if paletteDragActive then None
          else placement.map(PreviewPolygonRenderer.renderPreview(_, valid))

    // Free-floating ghost wireframe for the drag-from-palette gesture: tracks the cursor across the
    // canvas and re-orients to the nearest snappable edge while in range. Turns red when the
    // snapped edge is angle-invalid, matching the dim chevron + red preview.
    val paletteDragGhostWireframe = child.maybe <--
      EditorState.previewState.signal.map(_.paletteGhost).distinct
        .combineWith(EditorState.previewState.signal.map(_.previewIsValid).distinct)
        .map: (ghost, valid) =>
          ghost.map(PreviewPolygonRenderer.renderGhost(_, valid))

    // Snap-target hint: halo on the latched edge + directional chevron showing growth side.
    val paletteSnapHintOverlay = child.maybe <--
      EditorState.previewState.signal.map(_.paletteSnapHint).distinct
        .combineWith(EditorState.previewState.signal.map(_.previewIsValid).distinct)
        .map: (hint, valid) =>
          hint.map(PaletteSnapHintRenderer.render(_, valid))

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

    // Add Copy ▸ Translate: vertices shown as passive "clipping point" dots while the tool is active; the
    // vertex currently snapped as the release target is enlarged and recoloured.
    val translateCopyDots = children <--
      EditorState.toolState.signal.map(_.activeTool == Tool.TranslateCopy).distinct
        .combineWith(EditorState.previewState.signal.map(_.translateCopyDrag).distinct)
        .map:
          case (true, dragOpt) =>
            val snapId = dragOpt.flatMap(_.snapTarget.map(_._1))
            AddCopyOperations.vertexAnchorsCv(tiling).map: (id, p) =>
              renderVertexDot(p, snapId.contains(id))
          case _               => Nil

    // The dashed skeleton of the whole tiling, translated by the live (free) drag offset.
    val translateCopySkeleton = child.maybe <--
      EditorState.previewState.signal.map(_.translateCopyDrag).distinct
        .map(_.map(renderTranslateSkeleton))

    // Add Copy ▸ Rotate: rotation-centre dots (colour-coded by kind) while the tool is active; the picked
    // centre is enlarged during a drag.
    val rotateCopyCentres = children <--
      EditorState.toolState.signal.map(_.activeTool == Tool.RotateCopy).distinct
        .combineWith(EditorState.previewState.signal.map(_.rotateCopyDrag).distinct)
        .map:
          case (true, dragOpt) =>
            val picked = dragOpt.map(_.centerAnchor)
            AddCopyOperations.rotationCentres(tiling).map: (anchor, p) =>
              renderCentreDot(p, anchor, picked.contains(anchor))
          case _               => Nil

    // The dashed skeleton rotated by the live (snapped or free) angle about the picked centre.
    val rotateCopySkeleton = child.maybe <--
      EditorState.previewState.signal.map(_.rotateCopyDrag).distinct
        .map(_.map(renderRotateSkeleton))

    // Add Copy ▸ Reflect / Glide reflect: axis-anchor dots while either tool is active; axis point A and the
    // snapped B enlarged.
    val reflectCopyAnchors = children <--
      EditorState.toolState.signal
        .map(t => t.activeTool == Tool.ReflectCopy || t.activeTool == Tool.GlideReflectCopy)
        .distinct
        .combineWith(EditorState.previewState.signal.map(_.reflectCopyDrag).distinct)
        .map:
          case (true, dragOpt) =>
            val picked: Set[Anchor] =
              dragOpt.toSet.flatMap(d => Set(d.axisAnchor) ++ d.snapTarget.map(_._1))
            AddCopyOperations.reflectAnchors(tiling).map: (anchor, p) =>
              renderCentreDot(p, anchor, picked.contains(anchor))
          case _               => Nil

    // The dashed skeleton mirrored across the live axis A–B, with a spanning axis guide line.
    val reflectCopySkeleton = child.maybe <--
      EditorState.previewState.signal.map(_.reflectCopyDrag).distinct
        .map(_.flatMap(renderReflectSkeleton))

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
      paletteSnapHintOverlay,
      paletteDragGhostWireframe,
      failedDeletionWireframe,
      clickablePointsDisplay,
      translateCopyDots,
      translateCopySkeleton,
      rotateCopyCentres,
      rotateCopySkeleton,
      reflectCopyAnchors,
      reflectCopySkeleton,
      measurementStartPointDisplay,
      measurementEndPointDisplay,
      measurementLineDisplay,
      previousMeasurementLineDisplay,
      measurementAngleArcDisplay
    )

  /** A single clipping-point dot (canvas-view coords). The live snap target is enlarged and tinted green. */
  private def renderVertexDot(point: Point, isSnapTarget: Boolean): Element =
    svg.circle(
      circleCoordsRadius(point, if isSnapTarget then 7 else 4),
      svg.fill          := (if isSnapTarget then "#34c759" else "#ff9500"),
      svg.stroke        := "black",
      svg.strokeWidth   := "1",
      svg.opacity       := (if isSnapTarget then "1.0" else "0.85"),
      svg.pointerEvents := "none",
      svg.className     := "translate-copy-vertex"
    )

  /** Dashed outline of every face, grouped and translated by the live drag offset. Face point strings are
    * already in canvas-view coords (snapshot at drag start), so only the group transform changes per move.
    */
  private def renderTranslateSkeleton(drag: TranslateCopyDrag): Element =
    svg.g(
      svg.transform     := s"translate(${drag.deltaCv.x}, ${drag.deltaCv.y})",
      svg.pointerEvents := "none",
      svg.className     := "translate-copy-skeleton",
      drag.facePoints.map: (_, pointsStr) =>
        svg.polygon(
          svg.points          := pointsStr,
          svg.fill            := "none",
          svg.stroke <-- EditorState.overlayPreviewStrokeColor,
          svg.strokeWidth     := "2",
          svg.strokeDashArray := "5,5",
          svg.opacity         := "0.9"
        )
    )

  /** A rotation-centre dot, colour-coded by anchor kind (vertex = orange, edge midpoint = blue, face centre =
    * green). The picked centre is enlarged during a drag.
    */
  private def renderCentreDot(point: Point, anchor: Anchor, isPicked: Boolean): Element =
    val fill          = anchor match
      case Anchor.Vertex(_)      => "#ff9500"
      case Anchor.MidPoint(_, _) => "#0a84ff"
      case Anchor.Center(_)      => "#34c759"
    svg.circle(
      circleCoordsRadius(point, if isPicked then 7 else 4),
      svg.fill          := fill,
      svg.stroke        := "black",
      svg.strokeWidth   := "1",
      svg.opacity       := (if isPicked then "1.0" else "0.85"),
      svg.pointerEvents := "none",
      svg.className     := "rotate-copy-centre"
    )

  /** Dashed skeleton rotated about the picked centre by the live angle, with a small angle readout (the label
    * itself is not rotated).
    */
  private def renderRotateSkeleton(drag: RotateCopyDrag): Element =
    svg.g(
      svg.pointerEvents := "none",
      svg.className     := "rotate-copy-skeleton",
      svg.g(
        svg.transform  := s"rotate(${drag.appliedDeg} ${drag.centerCv.x} ${drag.centerCv.y})",
        drag.facePoints.map: (_, pointsStr) =>
          svg.polygon(
            svg.points          := pointsStr,
            svg.fill            := "none",
            svg.stroke <-- EditorState.overlayPreviewStrokeColor,
            svg.strokeWidth     := "2",
            svg.strokeDashArray := "5,5",
            svg.opacity         := "0.9"
          )
      ),
      svg.text(
        svg.x          := drag.centerCv.x.toString,
        svg.y          := (drag.centerCv.y - 10).toString,
        svg.textAnchor := "middle",
        svg.fontSize   := "12",
        svg.fill <-- EditorState.overlayPreviewStrokeColor,
        s"${math.round(drag.appliedDeg)}°"
      )
    )

  /** How far the mirror-axis guide line is extended past the picked anchors, in canvas-view units. */
  private val axisGuideExtent: Double = 5000.0

  /** Dashed skeleton reflected across the live axis A–B, plus the spanning axis guide line. `None` while the
    * axis is degenerate (B still coincident with A). The reflection is applied as an SVG `matrix(...)`
    * derived from the axis: across a line through A at angle θ, the linear part is
    * `[cos2θ sin2θ; sin2θ −cos2θ]`.
    */
  private def renderReflectSkeleton(drag: ReflectCopyDrag): Option[Element] =
    val a   = drag.axisACv
    val b   = drag.axisBCv
    val dx  = b.x - a.x
    val dy  = b.y - a.y
    val len = math.hypot(dx, dy)
    if len < 1e-6 then None
    else
      val theta2            = 2.0 * a.angleTo(b).toDouble
      val cos2              = math.cos(theta2)
      val sin2              = math.sin(theta2)
      val (ma, mb, mc, md)  = (cos2, sin2, sin2, -cos2)
      val me                = a.x - (ma * a.x + mc * a.y)
      val mf                = a.y - (mb * a.x + md * a.y)
      val (ux, uy)          = (dx / len, dy / len)
      // Glide reflect = reflect then slide along the axis by B − A. In SVG, `translate(g) matrix(M)` applies
      // M (the reflection) first, then the glide translation — exactly reflect-then-glide.
      val skeletonTransform =
        if drag.glide then s"translate($dx $dy) matrix($ma $mb $mc $md $me $mf)"
        else s"matrix($ma $mb $mc $md $me $mf)"
      Some(
        svg.g(
          svg.pointerEvents := "none",
          svg.className     := "reflect-copy-skeleton",
          svg.line(
            svg.x1              := (a.x - ux * axisGuideExtent).toString,
            svg.y1              := (a.y - uy * axisGuideExtent).toString,
            svg.x2              := (a.x + ux * axisGuideExtent).toString,
            svg.y2              := (a.y + uy * axisGuideExtent).toString,
            svg.stroke <-- EditorState.overlayPreviewStrokeColor,
            svg.strokeWidth     := "1",
            svg.strokeDashArray := "4,4",
            svg.opacity         := "0.7"
          ),
          svg.g(
            svg.transform       := skeletonTransform,
            drag.facePoints.map: (_, pointsStr) =>
              svg.polygon(
                svg.points          := pointsStr,
                svg.fill            := "none",
                svg.stroke <-- EditorState.overlayPreviewStrokeColor,
                svg.strokeWidth     := "2",
                svg.strokeDashArray := "5,5",
                svg.opacity         := "0.9"
              )
          )
        )
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
