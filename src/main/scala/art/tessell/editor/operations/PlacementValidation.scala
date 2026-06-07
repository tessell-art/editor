package art.tessell.editor.operations

import art.tessell.editor.models.VertexCoord
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.dcel.structure.{FaceId, VertexId}
import io.github.scala_tessella.ring_seq.RingSeq.isRotationOrReflectionOf

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

  /** Inside-insertion analogue of `fitsAtEdge`. Two pre-emptive rejections:
    *
    *   1. *Same shape as receiving face* — an inserted polygon whose angle vector is a rotation or reflection
    *      of the face's would coincide with the face itself, so the insertion is degenerate. (Trivially the
    *      case for regular-into-regular of equal sides; for irregulars, also catches the palette
    *      re-orientations that match the face's shape.)
    *   2. *Corner doesn't fit the face's wedge* — the bound at each endpoint of the shared interior edge is
    *      the face's own interior angle there. If the polygon's angle at either endpoint exceeds it, the
    *      polygon's adjacent edge would punch through one of the face's existing edges.
    *
    * `face.angles` is aligned with `findInnerFaceVertices` (both follow `outerComponent`'s face traversal):
    * index `i` holds the interior angle at vertex `i`.
    */
  def fitsInFace(
      tiling: TilingDCEL,
      faceId: FaceId,
      edge: (VertexCoord, VertexCoord),
      angles: Vector[AngleDegree]
  ): Boolean =
    if angles.size < 3 then true
    else
      val faceData =
        for
          face     <- tiling.findInnerFace(faceId).toOption
          vertices <- tiling.findInnerFaceVertices(faceId).toOption
          interior <- face.angles.toOption
        yield (vertices.map(_.id).zip(interior).toMap, interior)
      faceData match
        case None                                 => true // permissive when face data unreadable
        case Some((angleByVertex, faceInteriors)) =>
          val sameShape =
            faceInteriors.size == angles.size && faceInteriors.isRotationOrReflectionOf(angles)
          if sameShape then false
          else
            val fitStart = angleByVertex.get(edge._1.id).forall(angles(0) <= _)
            val fitEnd   = angleByVertex.get(edge._2.id).forall(angles(1) <= _)
            fitStart && fitEnd
