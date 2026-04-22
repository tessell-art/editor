package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.geometry.{AngleDegree, RegularPolygon}
import io.github.scala_tessella.dcel.structure.{FaceId, Vertex}
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.models.{EditorState, FailedPolygonPlacement, Tool, VertexCoord}
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
    (Option[Int], Boolean, Option[Vector[AngleDegree]], TilingDCEL)

  private val previewStateSignal: Signal[PreviewState] =
    EditorState.toolState.signal.map(_.selectedPolygon).distinct
      .combineWith(EditorState.irregularState.signal.map(_.isIrregularSelected).distinct)
      .combineWith(EditorState.irregularState.signal.map(_.recentIrregularPolygon).distinct)
      .combineWith(EditorState.tessellationState.signal.map(_.currentTiling).distinct)
      .map:
        case (maybeSides, isIrregular, maybeAngles, tiling) =>
          (maybeSides, isIrregular, maybeAngles, tiling)

  private def buildFailedPlacement(
      edgeIndex: Int,
      edge: (VertexCoord, VertexCoord),
      maybeSides: Option[Int],
      isIrregular: Boolean,
      maybeAngles: Option[Vector[AngleDegree]],
      tiling: TilingDCEL,
      intoFace: Option[FaceId] = None
  ): Option[FailedPolygonPlacement] =
    if isIrregular then
      maybeAngles.map: angles =>
        FailedPolygonPlacement(
          edgeIndex,
          angles,
          edge,
          tiling,
          intoFace = intoFace
        )
    else
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
              isIrregular: Boolean,
              maybeAngles: Option[Vector[AngleDegree]],
              tiling: TilingDCEL
            ) =>
          val placementOpt =
            buildFailedPlacement(
              edgeIndex = edgeIndex,
              edge = edge,
              maybeSides = maybeSides,
              isIrregular = isIrregular,
              maybeAngles = maybeAngles,
              tiling = tiling,
              intoFace = Some(faceId)
            )
          EditorState.previewState.update(_.copy(previewPlacement = placementOpt))
      },
      onMouseLeave.compose(gate) --> { _ =>

        EditorState.previewState.update(_.copy(previewPlacement = None))
      },
      // Trigger insertion directly when clicking the highlighted interior edge
      onClick.preventDefault.compose(stream =>
        gate(stream).withCurrentValueOf(EditorState.toolState.signal.map(_.activeTool).distinct)
      ) --> {
        case (_, Some(Tool.Inserter)) =>
          PlacementOperations.attemptPolygonInsertion(edge._1.id, edge._2.id)
          EditorState.previewState.update(_.copy(previewPlacement = None))
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
      // Enhanced visual feedback and click handling
      onMouseEnter.compose(stream =>
        gate(stream).withCurrentValueOf(previewStateSignal)
      ) --> {
        case (
              _,
              maybeSides: Option[Int],
              isIrregular: Boolean,
              maybeAngles: Option[Vector[AngleDegree]],
              tiling: TilingDCEL
            ) =>
          val placementOpt =
            buildFailedPlacement(
              edgeIndex = edgeIndex,
              edge = edge,
              maybeSides = maybeSides,
              isIrregular = isIrregular,
              maybeAngles = maybeAngles,
              tiling = tiling
            )
          EditorState.previewState.update(_.copy(previewPlacement = placementOpt))
      },
      onMouseLeave.compose(gate) --> { _ =>

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
      svg.strokeWidth   := "4",
      svg.strokeLineCap := "round",
      svg.className     := "perimeter-edge",
      svg.pointerEvents := "none"
    )

    svg.g(
      visibleLine,
      interactionArea
    )
