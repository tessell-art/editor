package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.structure.VertexId
import io.github.scala_tessella.editor.models.Anchor
import io.github.scala_tessella.editor.utils.geo.Point
import io.github.scala_tessella.editor.utils.geo.TessellationGeometry.{tilingPointToCanvasView, toPoint}

/** ADR-015 — sound, fast pre-filter that hides Add Copy anchors/snaps proven to yield an invalid weld.
  *
  * The sole authority on validity is `maybeAdd…Copy`; this is a **necessary condition only** (one-sided
  * error): it may return `false` (hide) ONLY when an overlap is *guaranteed* at the contact vertex, and MUST
  * return `true` (show) whenever uncertain. Never tighten it into a sufficiency test — hiding a candidate
  * that would actually weld is the one unacceptable failure.
  *
  * Mechanism (vertex contacts only in this increment; mid-edge / face-centre contacts conservatively show):
  * at the contact vertex, overlay the original's "vertex figure" (incident edge rays + the occupied arcs of
  * its convex faces) with the gesture-transformed figure of the copy. If any edge ray of one lies *strictly*
  * inside an occupied face arc of the other, an edge pierces a face ⇒ partial overlap ⇒ invalid. Coincident
  * rays (within `eps`) and rays falling in the exterior gap are fine (superposition-with-coincidence is a
  * valid weld). All angles are in the canvas-view frame (scale+translate of tiling coords —
  * angle-preserving), matching the gesture data.
  */
object AddCopyValidity:

  private val eps   = 1e-6
  private val twoPi = 2.0 * math.Pi

  private def norm(a: Double): Double =
    val m = a % twoPi
    if m < 0 then m + twoPi else m

  /** A vertex's local figure: incident edge ray directions, and the occupied arcs of its (convex) faces as
    * `(startCcw, width)` with `0 < width < π`.
    */
  final private[operations] case class Figure(rays: Vector[Double], occupied: Vector[(Double, Double)])

  // Memoized by tiling reference — tilings are immutable and replaced on mutation, so `eq` is a valid key.
  private var memo: Option[(TilingDCEL, Map[VertexId, Figure])] = None

  def figures(tiling: TilingDCEL): Map[VertexId, Figure] =
    memo match
      case Some((t, m)) if t eq tiling => m
      case _                           =>
        val m = build(tiling)
        memo = Some((tiling, m))
        m

  private def build(tiling: TilingDCEL): Map[VertexId, Figure] =
    val rays = scala.collection.mutable.Map.empty[VertexId, scala.collection.mutable.ArrayBuffer[Double]]
    val occ  =
      scala.collection.mutable.Map.empty[VertexId, scala.collection.mutable.ArrayBuffer[(Double, Double)]]
    tiling.innerFacesVertices.foreach: (_, verts) =>

      val pts = verts.map(v => tilingPointToCanvasView(v.coords.toPoint)).toVector
      val ids = verts.map(_.id).toVector
      val n   = pts.size
      if n >= 3 then
        for i <- 0 until n do
          val p     = pts(i)
          val prev  = pts((i - 1 + n) % n)
          val nxt   = pts((i + 1) % n)
          val dPrev = math.atan2(prev.y - p.y, prev.x - p.x)
          val dNext = math.atan2(nxt.y - p.y, nxt.x - p.x)
          minorArc(dPrev, dNext).foreach: arc =>

            val id = ids(i)
            rays.getOrElseUpdate(id, scala.collection.mutable.ArrayBuffer.empty) ++=
              Seq(norm(dPrev), norm(dNext))
            occ.getOrElseUpdate(id, scala.collection.mutable.ArrayBuffer.empty) += arc
    rays.keys.map { id =>

      id -> Figure(
        dedup(rays(id).toVector),
        occ.getOrElse(id, scala.collection.mutable.ArrayBuffer.empty).toVector
      )
    }.toMap

  /** Interior arc of a convex face between two incident edge directions; `None` for (near-)degenerate or
    * straight (≥ π) cases, which the caller then treats conservatively (shows the candidate).
    */
  private[operations] def minorArc(d1: Double, d2: Double): Option[(Double, Double)] =
    val a1  = norm(d1)
    val ccw = norm(norm(d2) - a1)
    if ccw <= eps || ccw >= twoPi - eps then None
    else if math.abs(ccw - math.Pi) < eps then None
    else if ccw < math.Pi then Some((a1, ccw))
    else Some((norm(d2), twoPi - ccw))

  private def dedup(xs: Vector[Double]): Vector[Double] =
    xs.sorted.foldLeft(Vector.empty[Double]): (acc, x) =>
      if acc.exists(y => math.abs(norm(x - y)) < eps || math.abs(norm(x - y) - twoPi) < eps) then acc
      else acc :+ x

  private def strictlyInside(ray: Double, arc: (Double, Double)): Boolean =
    val off = norm(ray - arc._1)
    off > eps && off < arc._2 - eps

  private[operations] def pierces(rays: Vector[Double], occ: Vector[(Double, Double)]): Boolean =
    rays.exists(r => occ.exists(a => strictlyInside(r, a)))

  /** Sound compatibility at one contact: neither figure's edges pierce the other's face interiors. */
  private[operations] def compatible(orig: Figure, copy: Figure): Boolean =
    !pierces(copy.rays, orig.occupied) && !pierces(orig.rays, copy.occupied)

  private[operations] def rotated(f: Figure, theta: Double): Figure =
    Figure(f.rays.map(r => norm(r + theta)), f.occupied.map((s, w) => (norm(s + theta), w)))

  private[operations] def reflected(f: Figure, phi: Double): Figure =
    Figure(f.rays.map(r => norm(2 * phi - r)), f.occupied.map((s, w) => (norm(2 * phi - (s + w)), w)))

  // ---- per-tool predicates: true = show (maybe valid); false = provably invalid ----

  /** Translate `from → to`: the source star lands on `to` unrotated. */
  def translateShows(tiling: TilingDCEL, fromId: VertexId, toId: VertexId): Boolean =
    val figs = figures(tiling)
    (figs.get(fromId), figs.get(toId)) match
      case (Some(from), Some(to)) => compatible(to, from)
      case _                      => true

  /** Rotate centre: shown iff *some* candidate angle (an edge-alignment angle of the centre's own star) is
    * locally compatible. Hides interior centres whose star has no compatible rotation. Mid-edge / face-centre
    * centres are shown (the local test is trivial there).
    */
  def rotateCentreShows(tiling: TilingDCEL, centre: Anchor): Boolean =
    centre match
      case Anchor.Vertex(id) =>
        figures(tiling).get(id) match
          case Some(f) =>
            val candidates =
              (for r1 <- f.rays; r2 <- f.rays if r1 != r2
              yield norm(r2 - r1)).filter(d => d > eps && d < twoPi - eps)
            candidates.isEmpty || candidates.exists(theta => compatible(f, rotated(f, theta)))
          case None    => true
      case _                 => true

  /** Reflect (or glide) across the axis through `aCv`–`bCv`. Tests the contact at A and B (reflect) or at the
    * B where A lands (glide). Non-vertex anchors are shown (conservative).
    */
  def reflectShows(
      tiling: TilingDCEL,
      aAnchor: Anchor,
      aCv: Point,
      bAnchor: Anchor,
      bCv: Point,
      glide: Boolean
  ): Boolean =
    val figs                                 = figures(tiling)
    val phi                                  = math.atan2(bCv.y - aCv.y, bCv.x - aCv.x)
    def vfig(anchor: Anchor): Option[Figure] = anchor match
      case Anchor.Vertex(id) => figs.get(id)
      case _                 => None
    if glide then
      (vfig(aAnchor), vfig(bAnchor)) match
        case (Some(fa), Some(fb)) => compatible(fb, reflected(fa, phi))
        case _                    => true
    else
      vfig(aAnchor).forall(f => compatible(f, reflected(f, phi))) &&
      vfig(bAnchor).forall(f => compatible(f, reflected(f, phi)))
