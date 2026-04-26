package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.geometry.{AngleDegree, RegularPolygon}
import io.github.scala_tessella.dcel.structure.{FaceId, Vertex}
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.models.{
  AddSubmode, EditorState, FailedPolygonPlacement, Tool, VertexCoord
}
import io.github.scala_tessella.editor.operations.OperationGuard.gate
import io.github.scala_tessella.editor.operations.PlacementOperations
import io.github.scala_tessella.editor.operations.TessellationOperations.toCoords
import io.github.scala_tessella.editor.utils.ColorRGB.*
import io.github.scala_tessella.editor.utils.SvgDsl.lineCoords
import io.github.scala_tessella.editor.utils.geo.{LineSegment, Point}
import io.github.scala_tessella.ring_seq.RingSeq.slidingO
import org.scalajs.dom.EndingType.transparent

object TessellationEdgeRenderer:

  private type PreviewState =
    (Option[Int], Option[Vector[AngleDegree]], TilingDCEL)

  private val previewStateSignal: Signal[PreviewState] =
    EditorState.toolState.signal.map(_.selectedPolygon).distinct
      .combineWith(EditorState.irregularState.signal.map(_.selectedShape).distinct)
      .combineWith(EditorState.tessellationState.signal.map(_.currentTiling).distinct)

  /** Snapshot helper: true only when the active tool is AddPolygon in its Outside sub-mode — the one mode
    * where hovering a perimeter edge should paint the dotted placement preview.
    */
  private def isAddOutsideMode: Boolean =
    val tools = EditorState.toolState.now()
    tools.activeTool == Tool.AddPolygon && tools.addSubmode == AddSubmode.Outside

  /** During a drag-from-palette gesture, the gesture itself owns `previewState.previewPlacement` (snapping to
    * the nearest valid edge). Hover handlers stand down to avoid fighting the snap.
    */
  private def isPaletteDragActive: Boolean =
    EditorState.uiState.now().isPaletteDragActive

  private def buildFailedPlacement(
      edgeIndex: Int,
      edge: (VertexCoord, VertexCoord),
      maybeSides: Option[Int],
      selectedIrregular: Option[Vector[AngleDegree]],
      tiling: TilingDCEL,
      intoFace: Option[FaceId] = None
  ): Option[FailedPolygonPlacement] =
    selectedIrregular match
      case Some(angles) =>
        Some(FailedPolygonPlacement(edgeIndex, angles, edge, tiling, intoFace = intoFace))
      case None         =>
        maybeSides.filter(_ >= 3).map: sides =>
          FailedPolygonPlacement(
            edgeIndex,
            RegularPolygon(sides).angles,
            edge,
            tiling,
            intoFace = intoFace
          )

  def renderPerimeterEdges(
      tiling: TilingDCEL,
      toCanvasPoint: Point => Point
  ): List[Element] =
    tiling.boundaryVertices.toOption.get
      .map(_.toCoords)
      .slidingO(2).toList.zipWithIndex
      .map: (vs, index) =>
        renderPerimeterEdge((vs(0), vs(1)), index, s"perimeter-edge-$index", toCanvasPoint)

  def renderInteriorEdgesForFace(
      tiling: TilingDCEL,
      faceId: FaceId,
      toCanvasPoint: Point => Point
  ): List[Element] =
    if tiling.isEmpty then Nil
    else
      tiling.findInnerFaceVertices(faceId).toOption match
        case Some(vertices) => rawRender(faceId, vertices, toCanvasPoint)
        case None           => Nil

  private def rawRender(
      faceId: FaceId,
      vertices: List[Vertex],
      toCanvasPoint: Point => Point
  ): List[Element] =
    val edges = vertices.map(_.toCoords).slidingO(2).toList
    edges.zipWithIndex.map:
      case (pair, idx) =>
        renderInteriorEdge((pair(0), pair(1)), faceId, idx, toCanvasPoint)

  private def renderInteriorEdge(
      edge: (VertexCoord, VertexCoord),
      faceId: FaceId,
      edgeIndex: Int,
      toCanvasPoint: Point => Point
  ): Element =
    val point1 = toCanvasPoint(edge._1.point)
    val point2 = toCanvasPoint(edge._2.point)

    val interactionArea = svg.line(
      lineCoords(LineSegment(point1, point2)),
      svg.stroke        := transparent,
      svg.strokeWidth   := "10",
      svg.strokeLineCap := "round",
      svg.className     := "interior-edge-transparent",
      // Show inner preview oriented into this face
      onMouseEnter.compose(stream =>
        gate(stream).withCurrentValueOf(previewStateSignal)
      ) --> {
        case (
              _,
              maybeSides: Option[Int],
              selectedIrregular: Option[Vector[AngleDegree]],
              tiling: TilingDCEL
            ) =>
          if !isPaletteDragActive then
            val placementOpt =
              buildFailedPlacement(
                edgeIndex = edgeIndex,
                edge = edge,
                maybeSides = maybeSides,
                selectedIrregular = selectedIrregular,
                tiling = tiling,
                intoFace = Some(faceId)
              )
            EditorState.previewState.update(_.copy(previewPlacement = placementOpt))
      },
      onMouseLeave.compose(gate) --> { _ =>

        if !isPaletteDragActive then
          EditorState.previewState.update(_.copy(previewPlacement = None))
      },
      // Trigger insertion directly when clicking the highlighted interior edge
      onClick.preventDefault.compose(stream =>
        gate(stream).withCurrentValueOf(EditorState.isAddInsideActive)
      ) --> {
        case (_, true)  =>
          PlacementOperations.attemptPolygonInsertion(edge._1.id, edge._2.id)
          EditorState.previewState.update(_.copy(previewPlacement = None))
        case (_, false) => ()
      }
    )

    val visibleLine = svg.line(
      lineCoords(LineSegment(point1, point2)),
      svg.stroke        := "#20A4BE",
      // Match the boundary edge width so the highlighted face's edges read with the same
      // visual weight as the perimeter — they are, after all, the candidate placement edges.
      svg.strokeWidth   := "4.5",
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
      id: String,
      toCanvasPoint: Point => Point
  ): Element =
    val vertex1 = edge._1.point
    val vertex2 = edge._2.point

    val point1 = toCanvasPoint(vertex1)
    val point2 = toCanvasPoint(vertex2)

    val interactionArea = svg.line(
      lineCoords(LineSegment(point1, point2)),
      svg.stroke        := transparent,
      svg.strokeWidth   := "12",
      svg.strokeLineCap := "round",
      svg.className     := "perimeter-edge-transparent",
      svg.pointerEvents <--
        EditorState.measurementState.signal.map(_.highlightedPolygonId).distinct.map(_.fold("visiblePainted")(
          _ =>
            "none"
        )),
      // Enhanced visual feedback and click handling. The dotted-polygon preview only makes
      // sense in AddPolygon + Outside, where the click would actually grow the tiling — gate
      // the mouse-enter handler accordingly so other tools don't paint a misleading preview.
      onMouseEnter.compose(stream =>
        gate(stream).withCurrentValueOf(previewStateSignal)
      ) --> {
        case (
              _,
              maybeSides: Option[Int],
              selectedIrregular: Option[Vector[AngleDegree]],
              tiling: TilingDCEL
            ) =>
          if isAddOutsideMode && !isPaletteDragActive then
            val placementOpt =
              buildFailedPlacement(
                edgeIndex = edgeIndex,
                edge = edge,
                maybeSides = maybeSides,
                selectedIrregular = selectedIrregular,
                tiling = tiling
              )
            EditorState.previewState.update(_.copy(previewPlacement = placementOpt))
      },
      onMouseLeave.compose(gate) --> { _ =>

        if !isPaletteDragActive then
          EditorState.previewState.update(_.copy(previewPlacement = None))
      },
      onClick.compose(gate) --> { _ =>

        AppState.handlePerimeterEdgeClick(id, edgeIndex)
      }
    )

    val visibleLine = svg.line(
      lineCoords(LineSegment(point1, point2)),
      svg.stroke <-- EditorState.colorState.signal.map(_.perimeterEdgeColor).distinct.map:
        _.toRgb
      ,
      svg.strokeWidth <--
        EditorState.settingsState.signal.map(_.boundaryEdgeWidth).distinct.map(_.toString),
      svg.strokeLineCap := "round",
      svg.className     := "perimeter-edge",
      svg.pointerEvents := "none"
    )

    svg.g(
      visibleLine,
      interactionArea
    )
