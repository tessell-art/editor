package art.tessell.editor.components

import art.tessell.editor.models.Anchor
import art.tessell.editor.utils.geo.Point
import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.TilingDCEL
import art.tessell.editor.utils.geo.TessellationGeometry.*

/** Sizing/colour constants for anchor markers — see ADR-014. Kept in one place so the palette and radii no
  * longer drift across renderers.
  */
object MarkerStyle:

  /** Base radius (canvas-view units); every shape's extent derives from it scaled by the marker state. */
  val baseR: Double = 5.0

  /** Mid-edge ellipse, elongated along its edge (~2:1). */
  val ellipseRx: Double = 7.5
  val ellipseRy: Double = 3.5

  /** Centroid ring: a coloured stroke over a darker backing so it reads on any background. */
  val ringR: Double        = 6.0
  val ringStrokeW: Double  = 2.4
  val ringBackingW: Double = ringStrokeW + 1.6

  val edgeStroke: String  = "black"
  val edgeStrokeW: String = "1"

/** The editor's anchor symbology (ADR-014): two independent visual channels.
  *
  *   - **Shape encodes the anchor *type*.** Vertex → solid disc, mid-edge → ellipse aligned to its edge,
  *     centroid → hollow ring. All three are curves, so no straight side ever competes with the tiling edges.
  *   - **Colour + size encode the *role/state*.** Idle (available), Active (snapped/picked), and the
  *     measurement Start/End. Type never leaks into colour, and role never leaks into shape.
  */
object AnchorMarker:

  /** Interactive role of a marker; drives colour and size, never shape. */
  enum MarkerState:
    case Idle         // an available clickable point, or a passive Add-Copy dot
    case Active       // the snapped / picked anchor under the current gesture
    case MeasureStart // first point of a measurement
    case MeasureEnd // second / current point of a measurement

  import MarkerState.*

  private case class Look(colour: String, scale: Double, opacity: String)

  /** Role → colour/size. Start/End use the Okabe–Ito blue↔vermillion pair (colour-blind safe), replacing the
    * former green/red; their order is additionally cued by the measurement line's arrowhead.
    */
  private def look(state: MarkerState): Look = state match
    case Idle         => Look("#ff9500", 0.8, "0.9") // orange, small  — available
    case Active       => Look("#34c759", 1.4, "1.0") // green, enlarged — snapped / picked
    case MeasureStart => Look("#0a84ff", 1.0, "1.0") // blue
    case MeasureEnd   => Look("#d55e00", 1.0, "1.0") // vermillion

  /** Orientation (degrees, canvas-view) of a mid-edge anchor's edge; `None` for vertex / centroid. */
  def edgeAngleDeg(tiling: TilingDCEL, anchor: Anchor, toCanvasPoint: Point => Point): Option[Double] =
    anchor match
      case Anchor.MidPoint(a, b) =>
        for
          ca <- tiling.findVertex(a).toOption.map(_.coords.toPoint)
          cb <- tiling.findVertex(b).toOption.map(_.coords.toPoint)
        yield
          val pa = toCanvasPoint(ca)
          val pb = toCanvasPoint(cb)
          math.toDegrees(math.atan2(pb.y - pa.y, pb.x - pa.x))
      case _                     => None

  // ---- glyphs (the visible shapes) --------------------------------------------

  private def disc(p: Point, l: Look): Element =
    val r          = MarkerStyle.baseR * l.scale
    svg.circle(
      svg.cx          := p.x.toString,
      svg.cy          := p.y.toString,
      svg.r           := r.toString,
      svg.fill        := l.colour,
      svg.stroke      := MarkerStyle.edgeStroke,
      svg.strokeWidth := MarkerStyle.edgeStrokeW
    )

  /** Ellipse drawn as a path (two arcs) so it depends only on `svg.path` — major axis along the edge, rotated
    * about the anchor by `angleDeg`.
    */
  private def ellipse(p: Point, angleDeg: Double, l: Look): Element =
    val rx           = MarkerStyle.ellipseRx * l.scale
    val ry           = MarkerStyle.ellipseRy * l.scale
    val d            =
      s"M ${p.x - rx} ${p.y} a $rx $ry 0 1 0 ${2 * rx} 0 a $rx $ry 0 1 0 ${-2 * rx} 0 Z"
    svg.path(
      svg.d           := d,
      svg.transform   := s"rotate($angleDeg ${p.x} ${p.y})",
      svg.fill        := l.colour,
      svg.stroke      := MarkerStyle.edgeStroke,
      svg.strokeWidth := MarkerStyle.edgeStrokeW
    )

  private def ring(p: Point, l: Look): Element =
    val r = MarkerStyle.ringR * l.scale
    svg.g(
      svg.circle( // dark backing
        svg.cx          := p.x.toString,
        svg.cy          := p.y.toString,
        svg.r           := r.toString,
        svg.fill        := "none",
        svg.stroke      := MarkerStyle.edgeStroke,
        svg.strokeWidth := (MarkerStyle.ringBackingW * l.scale).toString
      ),
      svg.circle( // coloured ring carries the state colour
        svg.cx          := p.x.toString,
        svg.cy          := p.y.toString,
        svg.r           := r.toString,
        svg.fill        := "none",
        svg.stroke      := l.colour,
        svg.strokeWidth := (MarkerStyle.ringStrokeW * l.scale).toString
      )
    )

  private def glyph(p: Point, anchor: Anchor, angleDeg: Option[Double], l: Look): Element =
    anchor match
      case Anchor.Vertex(_)      => disc(p, l)
      case Anchor.MidPoint(_, _) => ellipse(p, angleDeg.getOrElse(0.0), l)
      case Anchor.Center(_)      => ring(p, l)

  /** Radius of the transparent hit-area for an interactive marker (covers the hollow ring's interior too). */
  private def hitRadius(anchor: Anchor, scale: Double): Double =
    val base = anchor match
      case Anchor.Vertex(_)      => MarkerStyle.baseR
      case Anchor.MidPoint(_, _) => MarkerStyle.ellipseRx
      case Anchor.Center(_)      => MarkerStyle.ringR
    base * scale

  // ---- public render entry points ---------------------------------------------

  /** A non-interactive marker (`pointer-events: none`) — the Add-Copy overlay dots. */
  def renderPassive(
      p: Point,
      anchor: Anchor,
      state: MarkerState,
      angleDeg: Option[Double],
      className: String
  ): Element =
    val l     = look(state)
    svg.g(
      svg.className     := className,
      svg.pointerEvents := "none",
      svg.opacity       := l.opacity,
      glyph(p, anchor, angleDeg, l)
    )

  /** An interactive marker — clickable / measurement points. A transparent hit-disc makes the whole marker
    * area clickable (necessary for the hollow centroid ring); `mods` carry the click/cursor wiring.
    */
  def renderInteractive(
      p: Point,
      anchor: Anchor,
      state: MarkerState,
      angleDeg: Option[Double],
      className: String,
      mods: Mod[SvgElement]*
  ): Element =
    val l = look(state)
    svg
      .g(
        svg.className := className,
        svg.opacity   := l.opacity,
        svg.circle( // transparent hit-area
          svg.cx            := p.x.toString,
          svg.cy            := p.y.toString,
          svg.r             := hitRadius(anchor, l.scale).toString,
          svg.fill          := "transparent",
          svg.pointerEvents := "all"
        ),
        glyph(p, anchor, angleDeg, l)
      )
      .amend(mods*)
