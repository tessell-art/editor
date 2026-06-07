package art.tessell.editor.operations

import art.tessell.editor.models.Anchor
import art.tessell.editor.utils.geo.Point
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.structure.VertexId
import art.tessell.editor.utils.geo.TessellationGeometry.{tilingPointToCanvasView, toPoint}

/** ADR-015 — sound, fast pre-filter that hides Add Copy anchors/snaps proven to yield an invalid weld.
  *
  * The sole authority on validity is `maybeAdd…Copy`; this is a **necessary condition only** (one-sided
  * error): it may return `false` (hide) ONLY when an overlap is *guaranteed*, and MUST return `true` (show)
  * whenever uncertain. Never tighten it into a sufficiency test — hiding a candidate that would actually weld
  * is the one unacceptable failure, so every tolerance errs toward "show".
  *
  * Mechanism — at the gesture's contact, overlay the original's *vertex figure* (incident edge rays + the
  * occupied arcs of its convex faces) with the transformed figure of the copy; an edge ray strictly inside an
  * occupied face arc means an edge pierces a face ⇒ partial overlap ⇒ invalid. Coincident rays (within `eps`)
  * and rays in the exterior gap are fine (superposition-with-coincidence is a valid weld). Contacts handled:
  * vertices (all tools), edge midpoints (a half-plane figure, for Reflect/Glide axes and the Rotate 180°
  * point-symmetry test); face centres carry no edges so the local test is vacuous and they are always shown.
  * All angles are canvas-view (scale+translate of tiling coords — angle-preserving), matching the gesture
  * data. The per-tiling geometry is precomputed once and memoized by tiling reference.
  */
object AddCopyValidity:

  private val eps    = 1e-6
  private val twoPi  = 2.0 * math.Pi
  private val symEps = 0.1 // canvas-view distance for the point-symmetry match (generous → errs to "show")

  private def norm(a: Double): Double =
    val m = a % twoPi
    if m < 0 then m + twoPi else m

  /** A vertex/contact figure: incident edge ray directions, and the occupied arcs of its faces as
    * `(startCcw, width)`. Convex-vertex arcs have `width < π`; an edge-midpoint half-plane has `width = π`.
    */
  final private[operations] case class Figure(rays: Vector[Double], occupied: Vector[(Double, Double)])

  /** Per-tiling geometry, precomputed once (ADR-015). */
  final private case class Geom(
      figures: Map[VertexId, Figure],
      vertexCv: Map[VertexId, Point],
      edgeFaces: Map[Set[VertexId], List[Vector[Point]]]
  )

  // Memoized by tiling reference — tilings are immutable and replaced on mutation, so `eq` is a valid key.
  private var memo: Option[(TilingDCEL, Geom)] = None

  private def geom(tiling: TilingDCEL): Geom =
    memo match
      case Some((t, g)) if t eq tiling => g
      case _                           =>
        val g = build(tiling)
        memo = Some((tiling, g))
        g

  private def build(tiling: TilingDCEL): Geom =
    val rays = scala.collection.mutable.Map.empty[VertexId, scala.collection.mutable.ArrayBuffer[Double]]
    val occ  =
      scala.collection.mutable.Map.empty[VertexId, scala.collection.mutable.ArrayBuffer[(Double, Double)]]
    val vcv  = scala.collection.mutable.Map.empty[VertexId, Point]
    val ef   =
      scala.collection.mutable.Map.empty[Set[VertexId], scala.collection.mutable.ArrayBuffer[Vector[Point]]]
    tiling.innerFacesVertices.foreach: (_, verts) =>

      val pts = verts.map(v => tilingPointToCanvasView(v.coords.toPoint)).toVector
      val ids = verts.map(_.id).toVector
      val n   = pts.size
      if n >= 3 then
        for i <- 0 until n do
          val p    = pts(i)
          val prev = pts((i - 1 + n) % n)
          val nxt  = pts((i + 1) % n)
          vcv(ids(i)) = p
          minorArc(math.atan2(prev.y - p.y, prev.x - p.x), math.atan2(nxt.y - p.y, nxt.x - p.x)).foreach:
            arc =>

              rays.getOrElseUpdate(ids(i), scala.collection.mutable.ArrayBuffer.empty) ++=
                Seq(norm(math.atan2(prev.y - p.y, prev.x - p.x)), norm(math.atan2(nxt.y - p.y, nxt.x - p.x)))
              occ.getOrElseUpdate(ids(i), scala.collection.mutable.ArrayBuffer.empty) += arc
          val j    = (i + 1) % n
          ef.getOrElseUpdate(Set(ids(i), ids(j)), scala.collection.mutable.ArrayBuffer.empty) += pts
    val figs = rays.keys.map { id =>

      id -> Figure(
        dedup(rays(id).toVector),
        occ.getOrElse(id, scala.collection.mutable.ArrayBuffer.empty).toVector
      )
    }.toMap
    Geom(figs, vcv.toMap, ef.view.mapValues(_.toList).toMap)

  /** Interior arc of a convex face between two incident edge directions; `None` for (near-)degenerate or
    * straight (≥ π) cases, treated conservatively (caller shows the candidate).
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

  /** Edge-midpoint figure: the edge line (two opposite rays) and a half-plane (`width = π`) per incident
    * face, on that face's side. Interior edge → both half-planes (full disk); boundary edge → one.
    */
  private def midEdgeFigure(g: Geom, a: VertexId, b: VertexId): Option[Figure] =
    for ca <- g.vertexCv.get(a); cb <- g.vertexCv.get(b)
    yield
      val phi = math.atan2(cb.y - ca.y, cb.x - ca.x)
      val m   = Point((ca.x + cb.x) / 2, (ca.y + cb.y) / 2)
      val occ = g.edgeFaces.getOrElse(Set(a, b), Nil).map: fpts =>

        val cx    = fpts.map(_.x).sum / fpts.size
        val cy    = fpts.map(_.y).sum / fpts.size
        val dirC  = math.atan2(cy - m.y, cx - m.x)
        val start = if norm(dirC - phi) < math.Pi then norm(phi) else norm(phi + math.Pi)
        (start, math.Pi)
      Figure(Vector(norm(phi), norm(phi + math.Pi)), occ.toVector)

  private def anchorFigure(g: Geom, anchor: Anchor): Option[Figure] =
    anchor match
      case Anchor.Vertex(id)     => g.figures.get(id)
      case Anchor.MidPoint(a, b) => midEdgeFigure(g, a, b)
      case Anchor.Center(_)      => None // a face centre carries no edges — the local test can't prune it

  /** True if rotating an interior edge's two faces 180° about the midpoint coincides (point-symmetric pair),
    * or the edge is on the boundary (≤ 1 face → 180° doubling welds). Errs to `true`.
    */
  private def midpointWeldable(g: Geom, a: VertexId, b: VertexId): Boolean =
    g.edgeFaces.getOrElse(Set(a, b), Nil) match
      case f1 :: f2 :: Nil =>
        (g.vertexCv.get(a), g.vertexCv.get(b)) match
          case (Some(ca), Some(cb)) =>
            val mx = (ca.x + cb.x) / 2
            val my = (ca.y + cb.y) / 2
            f1.size == f2.size &&
            f1.forall(v => f2.exists(w => sq(2 * mx - v.x - w.x) + sq(2 * my - v.y - w.y) < symEps * symEps))
          case _                    => true
      case _               => true // boundary edge (or unknown): 180° doubling can weld

  private def sq(x: Double): Double = x * x

  // ---- per-tool predicates: true = show (maybe valid); false = provably invalid ----

  /** Translate `from → to`: the source star lands on `to` unrotated. */
  def translateShows(tiling: TilingDCEL, fromId: VertexId, toId: VertexId): Boolean =
    val g = geom(tiling)
    (g.figures.get(fromId), g.figures.get(toId)) match
      case (Some(from), Some(to)) => compatible(to, from)
      case _                      => true

  /** Rotate centre shown iff *some* allowed angle is locally weldable. */
  def rotateCentreShows(tiling: TilingDCEL, centre: Anchor): Boolean =
    val g = geom(tiling)
    centre match
      case Anchor.Vertex(id)     =>
        g.figures.get(id).forall: f =>

          val candidates =
            (for r1 <- f.rays; r2 <- f.rays if r1 != r2
            yield norm(r2 - r1)).filter(d => d > eps && d < twoPi - eps)
          candidates.isEmpty || candidates.exists(theta => compatible(f, rotated(f, theta)))
      case Anchor.MidPoint(a, b) => midpointWeldable(g, a, b)
      case Anchor.Center(_)      => true

  /** Whether a specific rotation angle (degrees) at `centre` is locally weldable — used to prune the
    * snap-angle candidates at press time.
    */
  def rotateAngleShows(tiling: TilingDCEL, centre: Anchor, angleDeg: Double): Boolean =
    val g = geom(tiling)
    centre match
      case Anchor.Vertex(id)     =>
        g.figures.get(id).forall(f => compatible(f, rotated(f, math.toRadians(angleDeg))))
      case Anchor.MidPoint(a, b) => midpointWeldable(g, a, b)
      case Anchor.Center(_)      => true

  /** Reflect (or glide) across the axis through `aCv`–`bCv`. Tests the contact at A and B (reflect) or at the
    * B where A lands (glide). Face-centre anchors are shown (the local test can't prune them).
    */
  def reflectShows(
      tiling: TilingDCEL,
      aAnchor: Anchor,
      aCv: Point,
      bAnchor: Anchor,
      bCv: Point,
      glide: Boolean
  ): Boolean =
    val g   = geom(tiling)
    val phi = math.atan2(bCv.y - aCv.y, bCv.x - aCv.x)
    if glide then
      (anchorFigure(g, aAnchor), anchorFigure(g, bAnchor)) match
        case (Some(fa), Some(fb)) => compatible(fb, reflected(fa, phi))
        case _                    => true
    else
      anchorFigure(g, aAnchor).forall(f => compatible(f, reflected(f, phi))) &&
      anchorFigure(g, bAnchor).forall(f => compatible(f, reflected(f, phi)))
