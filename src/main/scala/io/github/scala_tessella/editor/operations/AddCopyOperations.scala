package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.structure.{FaceId, VertexId}
import io.github.scala_tessella.editor.models.{AddSubmode, EditorConfig, EditorState, Tool, TranslateCopyDrag}
import io.github.scala_tessella.editor.operations.OperationGuard.ifNotProcessing
import io.github.scala_tessella.editor.operations.PaletteDragOperations.screenSvgToCanvasView
import io.github.scala_tessella.editor.utils.geo.Point
import io.github.scala_tessella.editor.utils.geo.TessellationGeometry.{tilingPointToCanvasView, toPoint}

/** Operations for the **Edit ▸ Add Copy** family — growing a tiling by welding on a copy of itself under a
  * plane isometry (dcel 0.1.1). Only the **Translate** variant is wired today; rotate / reflect / glide
  * reflect are reserved for later iterations.
  *
  * Translate is a direct-manipulation gesture: the user drags a dashed skeleton of the whole tiling across
  * the canvas; the `from` endpoint is the vertex nearest the press and the `to` endpoint snaps to the vertex
  * nearest the release. Because both endpoints are exact tiling vertices (`vertex.coords`, exact
  * `BigPoint`s), the translation vector is exact and the copy can coincide with the existing composition —
  * the precondition `maybeAddTranslatedCopy` needs to accept the weld. The validation pipeline still runs; a
  * rejected copy surfaces via [[ErrorOperations]].
  */
object AddCopyOperations:

  /** Snap radius for the release endpoint, in canvas-view units (≈ 0.4 tiling units). Releasing farther than
    * this from any vertex cancels the drag rather than snapping to a distant vertex.
    */
  private val snapRadiusCv: Double = 0.4 * EditorConfig.canvasScale

  /** Activates the Translate-copy tool. The tiling's vertices then show as clipping-point dots; the user
    * drags the skeleton and releases on a vertex to weld a translated copy. No-op while processing or empty.
    */
  def enterTranslateCopyMode(): Unit =
    ifNotProcessing:
      if !EditorState.tessellationState.now().currentTiling.isEmpty then
        MeasurementOperations.clearAll()
        clearDrag()
        EditorState.toolState.update(_.copy(activeTool = Tool.TranslateCopy))

  /** Leaves the Translate-copy tool, returning to the default AddPolygon mode and dropping any in-flight
    * drag.
    */
  def exitMode(): Unit =
    clearDrag()
    EditorState.toolState.update(_.copy(activeTool = Tool.AddPolygon, addSubmode = AddSubmode.Outside))

  private def clearDrag(): Unit =
    EditorState.previewState.update(_.copy(translateCopyDrag = None))

  /** Distinct tiling vertices as `(id, canvas-view point)`, for dot rendering and snap hit-testing. */
  def vertexAnchorsCv(tiling: TilingDCEL): List[(VertexId, Point)] =
    if tiling.isEmpty then Nil
    else
      tiling.innerFacesVertices
        .flatMap((_, faceVertices) => faceVertices.map(v => v.id -> v.coords.toPoint))
        .toMap
        .view
        .mapValues(tilingPointToCanvasView)
        .toList

  /** Skeleton snapshot: each inner face as a canvas-view `"x1,y1 x2,y2 …"` points string. Mirrors
    * [[TransformOperations]]' face-point precompute so the dashed overlay matches the rendered polygons.
    */
  private def precomputeFacePoints(tiling: TilingDCEL): List[(FaceId, String)] =
    tiling.innerFacesVertices.map: (faceId, faceVertices) =>

      val pointStrings = faceVertices.map: vertex =>

        val p = tilingPointToCanvasView(vertex.coords.toPoint)
        s"${p.x},${p.y}"
      (faceId, pointStrings.mkString(" "))

  /** Pointer client pixels → canvas-view coords (the frame the content `<g>` paints into). */
  private def clientToCanvasView(clientX: Double, clientY: Double): Option[Point] =
    EraserProximityQuery
      .clientToSvg(clientX, clientY)
      .map(screenSvgToCanvasView(_, EditorState.viewState.now().viewTransform))

  private def nearest(target: Point, anchors: List[(VertexId, Point)]): Option[(VertexId, Point)] =
    anchors.minByOption { case (_, p) =>
      p.distanceTo(target)
    }

  /** Begin a skeleton drag: pick the source vertex nearest the press and snapshot the skeleton. No-op on an
    * empty tiling or when the pointer can't be projected onto the canvas.
    */
  def beginDrag(clientX: Double, clientY: Double): Unit =
    val tiling = EditorState.tessellationState.now().currentTiling
    if !tiling.isEmpty then
      clientToCanvasView(clientX, clientY).foreach: pressCv =>

        val anchors = vertexAnchorsCv(tiling)
        nearest(pressCv, anchors).foreach: (sourceId, sourceCv) =>
          EditorState.previewState.update(
            _.copy(translateCopyDrag =
              Some(
                TranslateCopyDrag(
                  facePoints = precomputeFacePoints(tiling),
                  sourceVertexId = sourceId,
                  sourcePointCv = sourceCv,
                  grabPointCv = pressCv,
                  deltaCv = Point(0, 0),
                  snapTarget = None
                )
              )
            )
          )

  /** Update the live drag: the skeleton tracks the cursor 1:1, and the vertex nearest the projected source
    * (within the snap radius, excluding the source itself) is highlighted as the prospective target.
    */
  def updateDrag(clientX: Double, clientY: Double): Unit =
    EditorState.previewState.now().translateCopyDrag.foreach: drag =>
      clientToCanvasView(clientX, clientY).foreach: currentCv =>

        val deltaCv    = currentCv - drag.grabPointCv
        val candidate  = drag.sourcePointCv + deltaCv
        val tiling     = EditorState.tessellationState.now().currentTiling
        val anchors    = vertexAnchorsCv(tiling).filterNot { case (id, _) =>
          id == drag.sourceVertexId
        }
        val snapTarget =
          nearest(candidate, anchors).filter { case (_, p) =>
            p.distanceTo(candidate) <= snapRadiusCv
          }
        EditorState.previewState.update(
          _.copy(translateCopyDrag = Some(drag.copy(deltaCv = deltaCv, snapTarget = snapTarget)))
        )

  /** Finish the drag: if a target vertex is snapped, weld a copy translated from the source vertex to the
    * target vertex (exact `BigPoint`s). Otherwise the drag is simply cancelled. The mode stays active so the
    * user can add further copies.
    */
  def endDrag(): Unit =
    val dragOpt = EditorState.previewState.now().translateCopyDrag
    clearDrag()
    for
      drag          <- dragOpt
      (targetId, _) <- drag.snapTarget
      tiling         = EditorState.tessellationState.now().currentTiling
      from          <- tiling.findVertex(drag.sourceVertexId).toOption.map(_.coords)
      to            <- tiling.findVertex(targetId).toOption.map(_.coords)
    do
      OperationRunner.runTilingOp(() =>
        OperationRunner.safely("Error adding translated copy")(tiling.maybeAddTranslatedCopy(from, to))
      )(
        onSuccess = TessellationOperations.clearStaleAfterMutation(),
        onFailure = err => ErrorOperations.showError(s"Cannot add translated copy: ${err.message}")
      )
