package art.tessell.editor.operations

import io.github.scala_tessella.dcel.geometry.{AngleDegree, RegularPolygon}
import io.github.scala_tessella.dcel.structure.{FaceId, VertexId}
import io.github.scala_tessella.dcel.{TilingDCEL, ValidationError}
import TessellationOperations.toCoords
import art.tessell.editor.models.{EditorState, FailedPolygonPlacement}
import art.tessell.editor.utils.Logger
import art.tessell.editor.utils.PolygonNameGenerator.polygonName
import io.github.scala_tessella.ring_seq.RingSeq.slidingO

/** Placement operations: adding a polygon onto a perimeter edge or inserting one inside a face.
  *
  * Each operation resolves a [[PolygonPlacementKind]] (regular vs irregular, from the current
  * palette/irregular-state selection) and runs the DCEL mutation through [[OperationRunner]].
  *
  * `attempt…` naming: these return `Unit` — on validation failure a `FailedPolygonPlacement` overlay is
  * stored for the renderer and an error is surfaced via [[ErrorOperations]].
  */
object PlacementOperations:

  sealed private trait PolygonPlacementKind:
    def angles: Vector[AngleDegree]

  private case class RegularPlacement(sides: Int) extends PolygonPlacementKind:
    override val angles: Vector[AngleDegree] = RegularPolygon(sides).angles

  private case class IrregularPlacement(override val angles: Vector[AngleDegree])
      extends PolygonPlacementKind

  private case class PolygonPlacementContext(tiling: TilingDCEL, placement: PolygonPlacementKind)

  private def resolvePolygonPlacementKind(
      maybeSides: Option[Int],
      selectedIrregular: Option[Vector[AngleDegree]]
  ): Option[PolygonPlacementKind] =
    (maybeSides, selectedIrregular) match
      case (None, None)         =>
        Logger.warn("Both regular polygon and irregular polygon unselected")
        None
      case (Some(_), Some(_))   =>
        Logger.error("Should not happen: both regular polygon and irregular polygon selected")
        None
      case (Some(sides), None)  => Some(RegularPlacement(sides))
      case (None, Some(angles)) =>
        // The palette queue holds both regulars and irregulars; a regular created via the factory
        // ends up here with `selectedIndex` set, not `selectedPolygon`. Detect by all-equal angles
        // and route through the regular placement path so the tiling lib gets the regular optimization.
        if angles.size >= 3 && angles.forall(_ == angles.head) then
          Some(RegularPlacement(angles.size))
        else
          Some(IrregularPlacement(angles))

  private def currentPolygonPlacementContext(emptyTilingMessage: String): Option[PolygonPlacementContext] =
    val tiling            = EditorState.tessellationState.now().currentTiling
    val maybeSides        = EditorState.toolState.now().selectedPolygon
    val selectedIrregular = EditorState.irregularState.now().selectedShape
    if tiling.isEmpty then
      ErrorOperations.showError(emptyTilingMessage)
      None
    else
      resolvePolygonPlacementKind(maybeSides, selectedIrregular).map: placement =>
        PolygonPlacementContext(tiling, placement)

  /** Find the inner face whose boundary contains the directed edge (v1 -> v2). */
  private def findFaceContainingEdge(tiling: TilingDCEL, v1: VertexId, v2: VertexId): Option[FaceId] =
    tiling.innerFaces.find { face =>

      tiling
        .findInnerFaceVertices(face.id)
        .toOption
        .exists(vertices =>
          vertices.map(_.id).toVector.slidingO(2).exists(pair => pair(0) == v1 && pair(1) == v2)
        )
    }.map(_.id)

  /** Add a regular (or irregular) polygon onto a perimeter edge, growing the tiling outward. */
  def attemptPolygonAddition(edgeId: String, edgeIndex: Int): Unit =
    currentPolygonPlacementContext("No tiling available to grow").foreach: context =>

      val tiling         = context.tiling
      val perimeterEdges = tiling.boundaryVertices.toOption.get.map(_.toCoords).slidingO(2).toList
      val op             = () =>
        OperationRunner.safely("Error growing edge"):
          if edgeIndex < perimeterEdges.length then
            val selectedEdge = perimeterEdges(edgeIndex)
            context.placement match
              case RegularPlacement(sides)    =>
                tiling.maybeAddRegularPolygonToBoundary(selectedEdge.head.id, RegularPolygon(sides))
              case IrregularPlacement(angles) =>
                tiling.maybeAddSimplePolygonToBoundary(selectedEdge.head.id, angles)
          else
            Left(ValidationError("Invalid edge index"))

      OperationRunner.runTilingOp(op)(
        onSuccess = {
          TessellationOperations.clearStaleAfterMutation()
          TessellationOperations.recordPlacedShape(context.placement.angles)
        },
        onFailure = err =>
          if edgeIndex < perimeterEdges.length then
            val selectedEdge = perimeterEdges(edgeIndex)
            val angles       = context.placement.angles
            val placement    =
              FailedPolygonPlacement(edgeIndex, angles, (selectedEdge(0), selectedEdge(1)), tiling)
            val truncated    = err.message
            context.placement match
              case RegularPlacement(sides) =>
                ErrorOperations.showError(
                  s"Growing ${polygonName(sides)}s on this perimeter edge is invalid. $truncated",
                  Some(placement)
                )
              case IrregularPlacement(_)   =>
                ErrorOperations.showError(
                  s"Growing the given ${angles.size}-sides irregular polygon on this perimeter edge is invalid. $truncated",
                  Some(placement)
                )
          else
            ErrorOperations.showError(err.message)
      )

  /** Insert a regular (or irregular) polygon inside the tiling along the given interior edge. */
  def attemptPolygonInsertion(startVertexId: VertexId, endVertexId: VertexId): Unit =
    currentPolygonPlacementContext("No tiling available for insertion").foreach: context =>

      val tiling = context.tiling
      val op     = () =>
        OperationRunner.safely("Error inserting polygon"):
          context.placement match
            case RegularPlacement(sides)    =>
              tiling.maybeAddRegularPolygon(startVertexId, endVertexId, RegularPolygon(sides))
            case IrregularPlacement(angles) =>
              tiling.maybeAddSimplePolygon(startVertexId, endVertexId, angles)

      OperationRunner.runTilingOp(op)(
        onSuccess = {
          TessellationOperations.clearStaleAfterMutation()
          TessellationOperations.recordPlacedShape(context.placement.angles)
        },
        onFailure = error => {
          val curr           = EditorState.tessellationState.now().currentTiling
          val maybeFaceId    = findFaceContainingEdge(curr, startVertexId, endVertexId)
          val startCoordsOpt = tiling.findVertex(startVertexId).toOption.map(_.toCoords)
          val endCoordsOpt   = tiling.findVertex(endVertexId).toOption.map(_.toCoords)
          val edgeOpt        =
            for
              startCoords <- startCoordsOpt
              endCoords   <- endCoordsOpt
            yield (startCoords, endCoords)

          val placementOpt = edgeOpt.map: edge =>
            FailedPolygonPlacement(
              edgeIndex = 0, // not needed for interior wireframe
              angles = context.placement.angles,
              edge = edge,
              tiling = curr,
              intoFace = maybeFaceId
            )

          context.placement match
            case RegularPlacement(_)   =>
              ErrorOperations.showError(
                s"Cannot insert regular polygon: ${error.message}",
                placement = placementOpt
              )
            case IrregularPlacement(_) =>
              ErrorOperations.showError(
                s"Cannot insert irregular polygon: ${error.message}",
                placement = placementOpt
              )
        }
      )
