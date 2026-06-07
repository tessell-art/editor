package art.tessell.editor.models

import art.tessell.editor.utils.ColorRGB
import art.tessell.editor.utils.geo.{LineSegment, Point}

object EditorConfig:
  // Canvas ViewBox dimensions
  val canvasViewBoxWidth  = 800
  val canvasViewBoxHeight = 600
  val canvasEnd: Point    = Point(canvasViewBoxWidth, canvasViewBoxHeight)

  // The scale factor from tiling coordinates to SVG coordinates
  val canvasScale = 50.0

  // Derived canvas center
  val canvasCenter: Point = LineSegment(Point.origin, canvasEnd).midPoint

  // View zoom limits
  val minViewScale: Double = 0.1
  val maxViewScale: Double = 20.0

  // Below this view scale, node labels auto-hide to reduce visual noise (level-of-detail).
  // Uniformity dots and symmetry axes are exempt — they describe the tiling's global
  // structure and stay visible at any zoom.
  val lodMinScale: Double = 0.5

  // View zoom factors by input modality
  val mouseWheelZoomInFactor: Double  = 1.1
  val mouseWheelZoomOutFactor: Double = 0.9
  val keyboardZoomFactor: Double      = 1.1
  val menuZoomFactor: Double          = 1.2

  // Polygon palette configuration. The shape queue is initially seeded with these regular polygons;
  // anything outside this list (e.g. 7, 9, 10, 11, 15, 18, 20, 24, 42) is reachable via the
  // "Regular polygon" factory.
  val polygonSides: List[Int] = List(3, 4, 5, 6, 8, 12)

  // Maximum number of shapes (regular + irregular, mixed) remembered in the palette queue.
  val paletteShapeQueueSize: Int = 12

  // Default polygon fill color when no explicit color is assigned
  val defaultPolygonColor: ColorRGB = ColorRGB(76, 175, 80)

  // Default perimeter edge color for the editor canvas
  val defaultPerimeterEdgeColor: ColorRGB = ColorRGB(255, 149, 0)

  // Default stroke width (px) for the perimeter edge in the canvas. User-overridable in Settings.
  val defaultBoundaryEdgeWidth: Double = 4.0
  val minBoundaryEdgeWidth: Double     = 1.0
  val maxBoundaryEdgeWidth: Double     = 12.0

  // Default stroke colour and width (px) for the polygon outline in normal Select mode.
  // User-overridable in Settings. Selected and Delete-mode strokes intentionally keep their
  // state-specific overrides — those signal interaction, not styling.
  val defaultPolygonEdgeColor: ColorRGB = ColorRGB(100, 108, 255)
  val defaultPolygonEdgeWidth: Double   = 1.5
  val minPolygonEdgeWidth: Double       = 0.0
  val maxPolygonEdgeWidth: Double       = 2.0

  // Fan animation duration (milliseconds)
  val fanAnimationDurationMs: Int = 3000
  val fanAnimationStaggerMs: Int  = 60
