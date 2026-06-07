package art.tessell.editor.operations

import munit.FunSuite

/** Soundness-critical unit tests for the ADR-015 pre-filter primitives. Synthetic vertex figures keep the
  * geometry exact and independent of any tiling/coordinate frame.
  */
class AddCopyValiditySpec extends FunSuite:

  import AddCopyValidity.*

  private val twoPi          = 2.0 * math.Pi
  private def deg(d: Double) = math.toRadians(d)

  // A 4-valent interior vertex (square grid): edges at 0/90/180/270°, four 90° faces filling the disk.
  private val interior =
    Figure(
      rays = Vector(0.0, deg(90), deg(180), deg(270)),
      occupied = Vector((0.0, deg(90)), (deg(90), deg(90)), (deg(180), deg(90)), (deg(270), deg(90)))
    )

  // A boundary 90° corner: edges at 0° and 90°, one 90° face between them, 270° exterior gap.
  private val corner = Figure(rays = Vector(0.0, deg(90)), occupied = Vector((0.0, deg(90))))

  test("minorArc picks the interior (< π) side, regardless of argument order"):
    assert(minorArc(0.0, deg(90)).exists((s, w) => math.abs(s) < 1e-9 && math.abs(w - deg(90)) < 1e-9))
    assert(minorArc(deg(90), 0.0).exists((s, w) => math.abs(s) < 1e-9 && math.abs(w - deg(90)) < 1e-9))

  test("minorArc returns None for a straight (π) configuration"):
    assert(minorArc(0.0, math.Pi).isEmpty)

  test("a figure is compatible with itself (exact coincidence)"):
    assert(compatible(interior, interior))
    assert(compatible(corner, corner))

  test("interior figure is compatible under a 90° rotation (grid symmetry)"):
    assert(compatible(interior, rotated(interior, deg(90))))

  test("interior figure is NOT compatible under a 45° rotation (edge pierces a face)"):
    assert(!compatible(interior, rotated(interior, deg(45))))

  test("an interior vertex with no symmetric rotation is hidden by the candidate-angle scan"):
    // skew interior star: edges at 0/100/180/260°; no edge-alignment angle is a symmetry.
    val skew       = Figure(
      rays = Vector(0.0, deg(100), deg(180), deg(260)),
      occupied = Vector((0.0, deg(100)), (deg(100), deg(80)), (deg(180), deg(80)), (deg(260), deg(100)))
    )
    val candidates =
      (for r1 <- skew.rays; r2 <- skew.rays if r1 != r2 yield ((r2 - r1) % twoPi + twoPi) % twoPi)
        .filter(d => d > 1e-6 && d < twoPi - 1e-6)
    assert(candidates.nonEmpty)
    assert(!candidates.exists(theta => compatible(skew, rotated(skew, theta))))

  test("corner reflected across its bisector (45°) is compatible (mirror symmetry)"):
    assert(compatible(corner, reflected(corner, deg(45))))

  test("corner reflected across its own edge (0°) is compatible (adjacent fold into the gap)"):
    assert(compatible(corner, reflected(corner, 0.0)))

  test("corner reflected across an axis cutting the face interior (22.5°) is NOT compatible"):
    assert(!compatible(corner, reflected(corner, deg(22.5))))

  test("rotating the corner 180° lands its face entirely in the exterior gap (compatible)"):
    assert(compatible(corner, rotated(corner, deg(180))))

  test("pierces detects an edge ray strictly inside an occupied arc"):
    assert(pierces(Vector(deg(45)), Vector((0.0, deg(90)))))
    assert(!pierces(Vector(0.0, deg(90)), Vector((0.0, deg(90))))) // boundary rays coincide, not pierce
    assert(!pierces(Vector(deg(180)), Vector((0.0, deg(90))))) // ray in the exterior gap

  // A boundary edge-midpoint: edge along the x-axis (rays 0 and π), one π-wide face in the upper half.
  private val boundaryMid = Figure(rays = Vector(0.0, math.Pi), occupied = Vector((0.0, math.Pi)))

  test("boundary edge-midpoint: reflecting across the edge line folds the face to the empty side"):
    assert(compatible(boundaryMid, reflected(boundaryMid, 0.0)))

  test("boundary edge-midpoint: reflecting across the perpendicular keeps the face in place"):
    assert(compatible(boundaryMid, reflected(boundaryMid, math.Pi / 2)))

  test("boundary edge-midpoint: reflecting across a diagonal axis cuts the face (NOT compatible)"):
    assert(!compatible(boundaryMid, reflected(boundaryMid, math.Pi / 4)))

  // An interior edge-midpoint: both half-planes occupied (full disk).
  private val interiorMid =
    Figure(rays = Vector(0.0, math.Pi), occupied = Vector((0.0, math.Pi), (math.Pi, math.Pi)))

  test("interior edge-midpoint: an axis along the edge does not pierce, a diagonal one does"):
    assert(compatible(interiorMid, reflected(interiorMid, 0.0)))
    assert(!compatible(interiorMid, reflected(interiorMid, math.Pi / 4)))
