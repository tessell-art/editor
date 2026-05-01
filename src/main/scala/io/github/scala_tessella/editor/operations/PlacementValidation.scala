package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.dcel.structure.VertexId
import io.github.scala_tessella.editor.models.VertexCoord

import scala.math.Ordering.Implicits.infixOrderingOps

/** Cheap pre-flight check for "would this polygon even fit on this boundary edge by the angle-at-endpoints
  * test?".
  *
  * For a polygon described by `angles` attached to a boundary edge `(vA, vB)`, the polygon's interior angle
  * at vA is `angles(0)` and at vB is `angles(1)` — see `BigLineSegment.unitPath` in the dcel library: it
  * walks the polygon starting with `[p1, p2]` and uses `angles(i)` as the interior angle at vertex i, so V_0 =
  * vA carries angles(0) and V_1 = vB carries angles(1).
  *
  * The check is a *necessary* condition: if either polygon corner exceeds the free wedge at its vertex, the
  * placement is geometrically guaranteed to overlap. Pre-rejecting these saves the user from clicking
  * dead-ends. Other failure modes (the new polygon's far edges crossing distant boundary segments) still need
  * the full DCEL validation downstream — `attemptPolygonAddition` remains the safety net.
  */
object PlacementValidation:

  /** Returns false only when the placement is guaranteed-invalid by the angle check. Returns true when the
    * polygon might fit (could still fail later for other reasons) or when the wedge cannot be computed (don't
    * pre-reject in degenerate cases — let the DCEL check arbitrate).
    */
  def fitsAtEdge(
      tiling: TilingDCEL,
      edge: (VertexCoord, VertexCoord),
      angles: Vector[AngleDegree]
  ): Boolean =
    if angles.size < 3 then true
    else
      val angleAtStart = angles(0)
      val angleAtEnd   = angles(1)
      val fitStart     = freeBoundaryWedge(tiling, edge._1.id).forall(angleAtStart <= _)
      val fitEnd       = freeBoundaryWedge(tiling, edge._2.id).forall(angleAtEnd <= _)
      fitStart && fitEnd

  private def freeBoundaryWedge(tiling: TilingDCEL, vertexId: VertexId): Option[AngleDegree] =
    tiling.getInnerAnglesAtVertex(vertexId).toOption.map(_.sumExact.conjugate)
