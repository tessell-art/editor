
package io.github.scala_tessella.editor

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.tessella.Tiling
import io.github.scala_tessella.tessella.Topology.{Edge, Node => TilingNode}
import io.github.scala_tessella.tessella.RegularPolygon.{Polygon, *}
import io.github.scala_tessella.tessella.creation.Reticulate

import scala.scalajs.js
import scala.scalajs.js.annotation.*

import org.scalajs.dom
import org.scalajs.dom.{MouseEvent, WheelEvent, KeyboardEvent}

import scala.math.{cos, Pi, sin, max, min}
import scala.util.Random

// import javascriptLogo from "/javascript.svg"
@js.native @JSImport("/javascript.svg", JSImport.Default)
val javascriptLogo: String = js.native

case class Point(x: Double, y: Double)
case class CanvasPolygon(id: String, sides: Int, center: Point, radius: Double, rotation: Double = 0.0)
case class CanvasText(id: String, text: String, position: Point, fontSize: Double = 14.0)
case class ViewTransform(scale: Double = 1.0, rotationDegrees: Double = 0.0, panX: Double = 0.0, panY: Double = 0.0) {
  // Helper method to get rotation in radians
  def rotationRadians: Double = math.toRadians(rotationDegrees)

  // Helper method to normalize rotation to 0-359 degrees
  def normalizeRotation(degrees: Double): Double = {
    val normalized = degrees % 360
    if (normalized < 0) normalized + 360 else normalized
  }

  // Method to update rotation and keep it normalized
  def withRotation(newRotationDegrees: Double): ViewTransform =
    this.copy(rotationDegrees = normalizeRotation(newRotationDegrees))
}

@main
def Editor(): Unit =
  renderOnDomContentLoaded(
    dom.document.getElementById("app"),
    Main.appElement()
  )

object Main:
  def appElement(): Element =
    div(
      h1("Polygon Shape Editor"),
      div(className := "editor-layout",
        polygonPalette(),
        editorCanvas()
      )
    )

  val polygonSides: List[Int] = List(3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 18, 20, 24, 42)
  val selectedPolygon: Var[Option[Int]] = Var[Option[Int]](None)

  // Canvas state
  val canvasPolygons: Var[List[CanvasPolygon]] = Var(generateSamplePolygons())
  val canvasTexts: Var[List[CanvasText]] = Var(generateSampleTexts())
  val viewTransform: Var[ViewTransform] = Var(ViewTransform())
  val selectedElements: Var[Set[String]] = Var(Set.empty)

  // Tessellation state
  val currentTiling: Var[Option[Tiling]] = Var(generateSampleTiling())
  val selectedPerimeterEdges: Var[Set[String]] = Var(Set.empty)
  val selectedTilingPolygons: Var[Set[String]] = Var(Set.empty)

  // Canvas interaction state
  val isDragging: Var[Boolean] = Var(false)
  val dragStart: Var[Option[Point]] = Var(None)

  // Canvas element reference for coordinate calculations
  val canvasElementRef: Var[Option[dom.Element]] = Var(None)

  def generateSampleTiling(): Option[Tiling] =
    try {
      // Create a simple tiling with hexagons and triangles
      val hexagon = Polygon(6)
      val triangle = Polygon(3)

      // Use Reticulate to create a sample tiling - adjust this based on actual API
      Tiling.pattern_2x33344_2x3446_3636(6, 6).toOption
    } catch {
      case _: Exception =>
        // Fallback to a simple single polygon tiling
        try {
          Tiling.pattern_2x33344_2x3446_3636(6, 6).toOption
        } catch {
          case _: Exception => None
        }
    }

  def generateSamplePolygons(): List[CanvasPolygon] =
    List(
      CanvasPolygon("poly1", 6, Point(200, 150), 40),
      CanvasPolygon("poly2", 4, Point(350, 200), 35, math.toRadians(45)), // 45 degrees in radians
      CanvasPolygon("poly3", 8, Point(150, 300), 50),
      CanvasPolygon("poly4", 3, Point(400, 120), 30),
      CanvasPolygon("poly5", 5, Point(300, 350), 45, math.toRadians(30)) // 30 degrees in radians
    )

  def generateSampleTexts(): List[CanvasText] =
    List(
      CanvasText("text1", "Hexagon", Point(200, 200)),
      CanvasText("text2", "Square", Point(350, 250)),
      CanvasText("text3", "Octagon", Point(150, 360)),
      CanvasText("text4", "Triangle", Point(400, 170)),
      CanvasText("text5", "Pentagon", Point(300, 410))
    )

  def polygonPalette(): Element =
    div(
      className := "polygon-palette",
      h2("Select a Polygon Shape"),
      div(
        className := "palette-grid",
        polygonSides.map(sides => polygonButton(sides))
      ),
      div(
        className := "selected-info",
        child.maybe <-- selectedPolygon.signal.map(_.map(sides =>
          p(s"Selected: ${sides}-sided polygon (${polygonName(sides)})")
        ))
      ),
      div(
        className := "tiling-controls",
        h3("Tessellation"),
        button("Generate Hexagon Tiling", onClick --> { _ => generateHexagonTiling() }),
        button("Generate Triangle Tiling", onClick --> { _ => generateTriangleTiling() }),
        button("Generate Mixed Tiling", onClick --> { _ => generateMixedTiling() }),
        button("Clear Tiling", onClick --> { _ => currentTiling.set(None) })
      )
    )

  def generateHexagonTiling(): Unit =
    try {
      val hexagon = Polygon(6)
      val tiling = Tiling.pattern_666(3, 3).toOption.get
      currentTiling.set(Some(tiling))
    } catch {
      case _: Exception => println("Failed to generate hexagon tiling")
    }

  def generateTriangleTiling(): Unit =
    try {
      val triangle = Polygon(3)
      val tiling = Tiling.pattern_333333(3, 3).toOption.get
      currentTiling.set(Some(tiling))
    } catch {
      case _: Exception => println("Failed to generate triangle tiling")
    }

  def generateMixedTiling(): Unit =
    try {
      val hexagon = Polygon(6)
      val triangle = Polygon(3)
      val tiling = Tiling.pattern_3464(3).toOption.get
      currentTiling.set(Some(tiling))
    } catch {
      case _: Exception => println("Failed to generate mixed tiling")
    }

  def editorCanvas(): Element =
    div(
      className := "canvas-container",
      h2("Canvas"),
      canvasControls(),
      svg.svg(
        svg.className := "editor-canvas",
        svg.width := "800",
        svg.height := "600",
        svg.viewBox := "0 0 800 600",
        svg.tabIndex := "0", // Make focusable for keyboard events

        // Store reference to the canvas element
        onMountCallback(ctx => canvasElementRef.set(Some(ctx.thisNode.ref))),

        // Background
        svg.rect(
          svg.x := "0", svg.y := "0",
          svg.width := "800", svg.height := "600",
          svg.fill := "#1a1a1a",
          svg.stroke := "#333",
          svg.strokeWidth := "2"
        ),

        // Main content group with transforms
        svg.g(
          svg.transform <-- viewTransform.signal.map(transform =>
            s"translate(${transform.panX}, ${transform.panY}) scale(${transform.scale}) rotate(${transform.rotationDegrees} 400 300)"
          ),

          // Grid pattern
          gridPattern(),

          // Render tessellation if available
          child.maybe <-- currentTiling.signal.map(_.map(renderTiling)),

          // Render individual polygons (if not showing tessellation)
          children <-- Signal.combine(currentTiling.signal, canvasPolygons.signal).map { case (tiling, polygons) =>
            if (tiling.isEmpty) polygons.map(renderCanvasPolygon) else List.empty
          },

          // Render texts
          children <-- canvasTexts.signal.map(_.map(renderCanvasText)),

          // Render connection points for individual polygons
          children <-- Signal.combine(currentTiling.signal, canvasPolygons.signal).map { case (tiling, polygons) =>
            if (tiling.isEmpty) polygons.flatMap(renderPolygonPoints) else List.empty
          }
        ),

        // Mouse event handlers
        onMouseDown --> handleMouseDown,
        onMouseMove --> handleMouseMove,
        onMouseUp --> handleMouseUp,
        onWheel --> handleWheel,
        onKeyDown --> handleKeyDown
      )
    )

  def renderTiling(tiling: Tiling): Element =
    val tilingPolygons = tiling.orientedPolygons.map(_.toPolygonPathNodes).zipWithIndex.map { case (nodes, index) =>
      renderTilingPolygon(tiling, nodes, s"tiling-poly-$index")
    }

    val perimeterEdges = tiling.perimeter.toRingEdges.zipWithIndex.map { case (edge, index) =>
      renderPerimeterEdge(tiling, edge, s"perimeter-edge-$index")
    }.toList

    svg.g(
      svg.className := "tessellation",
      // Render all polygons in the tiling
      tilingPolygons,
      // Render perimeter edges
      perimeterEdges
    )

  def renderTilingPolygon(tiling: Tiling, nodes: Vector[TilingNode], id: String): Element =
    val center = Point(0, 0)
    val isSelected = selectedTilingPolygons.signal.map(_.contains(id))

    // Convert tessella coordinates to canvas coordinates
    val canvasCenter = Point(center.x * 50 + 400, center.y * 50 + 300) // Scale and offset

    val points = nodes.map(tiling.coords).map { vertex =>
      val x = canvasCenter.x + vertex.x * 50
      val y = canvasCenter.y + vertex.y * 50
      s"$x,$y"
    }.mkString(" ")

    svg.polygon(
      svg.points := points,
      svg.fill <-- isSelected.map(selected =>
        if (selected) "rgba(255, 107, 107, 0.4)" else "rgba(100, 108, 255, 0.2)"
      ),
      svg.stroke <-- isSelected.map(selected =>
        if (selected) "#ff6b6b" else "#646cff"
      ),
      svg.strokeWidth <-- isSelected.map(selected => if (selected) "3" else "1.5"),
      svg.className := "tiling-polygon",
      onClick --> { _ => toggleTilingPolygonSelection(id) }
    )

  def renderPerimeterEdge(tiling: Tiling, edge: Edge, id: String): Element =
    val vertex1 = tiling.coords(edge.lesserNode)
    val vertex2 = tiling.coords(edge.greaterNode)
    val isSelected = selectedPerimeterEdges.signal.map(_.contains(id))

    // Convert tessella coordinates to canvas coordinates
    val x1 = vertex1.x * 50 + 400
    val y1 = vertex1.y * 50 + 300
    val x2 = vertex2.x * 50 + 400
    val y2 = vertex2.y * 50 + 300

    svg.line(
      svg.x1 := x1.toString,
      svg.y1 := y1.toString,
      svg.x2 := x2.toString,
      svg.y2 := y2.toString,
      svg.stroke <-- isSelected.map(selected =>
        if (selected) "#ff6b6b" else "#ff9500"
      ),
      svg.strokeWidth <-- isSelected.map(selected => if (selected) "4" else "3"),
      svg.className := "perimeter-edge",
      onClick --> { _ => togglePerimeterEdgeSelection(id) }
    )

  def toggleTilingPolygonSelection(id: String): Unit =
    selectedTilingPolygons.update(current =>
      if (current.contains(id)) current - id
      else current + id
    )

  def togglePerimeterEdgeSelection(id: String): Unit =
    selectedPerimeterEdges.update(current =>
      if (current.contains(id)) current - id
      else current + id
    )

  def canvasControls(): Element =
    div(
      className := "canvas-controls",
      button("Reset View", onClick --> { _ => viewTransform.set(ViewTransform()) }),
      button("Zoom In", onClick --> { _ =>
        viewTransform.update(t => t.copy(scale = min(t.scale * 1.2, 5.0)))
      }),
      button("Zoom Out", onClick --> { _ =>
        viewTransform.update(t => t.copy(scale = max(t.scale / 1.2, 0.1)))
      }),
      button("Rotate Left", onClick --> { _ =>
        viewTransform.update(t => t.withRotation(t.rotationDegrees - 30))
      }),
      button("Rotate Right", onClick --> { _ =>
        viewTransform.update(t => t.withRotation(t.rotationDegrees + 30))
      }),
      div(
        className := "transform-info",
        child.text <-- viewTransform.signal.map(t =>
          f"Scale: ${t.scale}%.2f | Rotation: ${t.rotationDegrees}%.0f° | Pan: (${t.panX}%.0f, ${t.panY}%.0f)"
        )
      )
    )

  def gridPattern(): Element =
    svg.g(
      svg.className := "grid-pattern",
      svg.opacity := "0.3",
      // Vertical lines
      (0 to 800 by 50).map(x =>
        svg.line(
          svg.x1 := x.toString, svg.y1 := "0",
          svg.x2 := x.toString, svg.y2 := "600",
          svg.stroke := "#444", svg.strokeWidth := "1"
        )
      ),
      // Horizontal lines
      (0 to 600 by 50).map(y =>
        svg.line(
          svg.x1 := "0", svg.y1 := y.toString,
          svg.x2 := "800", svg.y2 := y.toString,
          svg.stroke := "#444", svg.strokeWidth := "1"
        )
      )
    )

  def renderCanvasPolygon(polygon: CanvasPolygon): Element =
    val points = (0 until polygon.sides).map { i =>
      val angle = (2 * Pi * i / polygon.sides) - (Pi / 2) + polygon.rotation
      val x = polygon.center.x + polygon.radius * cos(angle)
      val y = polygon.center.y + polygon.radius * sin(angle)
      s"$x,$y"
    }.mkString(" ")

    val isSelected = selectedElements.signal.map(_.contains(polygon.id))

    svg.g(
      svg.polygon(
        svg.points := points,
        svg.fill := "rgba(100, 108, 255, 0.3)",
        svg.stroke <-- isSelected.map(selected => if (selected) "#ff6b6b" else "#646cff"),
        svg.strokeWidth <-- isSelected.map(selected => if (selected) "3" else "2"),
        svg.className := "clickable-polygon",
        onClick --> { _ => toggleSelection(polygon.id) }
      ),
      // Polygon center point
      svg.circle(
        svg.cx := polygon.center.x.toString,
        svg.cy := polygon.center.y.toString,
        svg.r := "3",
        svg.fill := "#ff6b6b",
        svg.className := "polygon-center"
      )
    )

  def renderCanvasText(text: CanvasText): Element =
    val isSelected = selectedElements.signal.map(_.contains(text.id))

    svg.text(
      svg.x := text.position.x.toString,
      svg.y := text.position.y.toString,
      svg.fontSize := text.fontSize.toString,
      svg.fill <-- isSelected.map(selected => if (selected) "#ff6b6b" else "#ccc"),
      svg.fontWeight <-- isSelected.map(selected => if (selected) "bold" else "normal"),
      svg.textAnchor := "middle",
      svg.className := "clickable-text",
      text.text,
      onClick --> { _ => toggleSelection(text.id) }
    )

  def renderPolygonPoints(polygon: CanvasPolygon): List[Element] =
    (0 until polygon.sides).map { i =>
      val angle = (2 * Pi * i / polygon.sides) - (Pi / 2) + polygon.rotation
      val x = polygon.center.x + polygon.radius * cos(angle)
      val y = polygon.center.y + polygon.radius * sin(angle)

      svg.circle(
        svg.cx := x.toString,
        svg.cy := y.toString,
        svg.r := "2",
        svg.fill := "#4ade80",
        svg.className := "polygon-point",
        onClick --> { _ => println(s"Clicked point $i of polygon ${polygon.id}") }
      )
    }.toList

  def toggleSelection(elementId: String): Unit =
    selectedElements.update(current =>
      if (current.contains(elementId)) current - elementId
      else current + elementId
    )

  def handleMouseDown(event: MouseEvent): Unit =
    event.preventDefault()
    isDragging.set(true)
    dragStart.set(Some(Point(event.clientX, event.clientY)))

  def handleMouseMove(event: MouseEvent): Unit =
    if (isDragging.now()) {
      dragStart.now().foreach { start =>
        val deltaX = event.clientX - start.x
        val deltaY = event.clientY - start.y
        viewTransform.update(t => t.copy(
          panX = t.panX + deltaX,
          panY = t.panY + deltaY
        ))
        dragStart.set(Some(Point(event.clientX, event.clientY)))
      }
    }

  def handleMouseUp(event: MouseEvent): Unit =
    isDragging.set(false)
    dragStart.set(None)

  def getCanvasRelativePosition(event: WheelEvent): Option[Point] =
    canvasElementRef.now().map { canvasElement =>
      val rect = canvasElement.getBoundingClientRect()
      Point(
        event.clientX - rect.left,
        event.clientY - rect.top
      )
    }

  def handleWheel(event: WheelEvent): Unit =
    event.preventDefault()

    getCanvasRelativePosition(event).foreach { mousePos =>
      val currentTransform = viewTransform.now()
      val scaleFactor = if (event.deltaY < 0) 1.1 else 0.9
      val newScale = max(0.1, min(5.0, currentTransform.scale * scaleFactor))

      // Calculate the world position that the mouse is pointing to before zoom
      val worldX = (mousePos.x - currentTransform.panX) / currentTransform.scale
      val worldY = (mousePos.y - currentTransform.panY) / currentTransform.scale

      // Calculate new pan to keep the world position under the mouse cursor
      val newPanX = mousePos.x - worldX * newScale
      val newPanY = mousePos.y - worldY * newScale

      viewTransform.set(currentTransform.copy(
        scale = newScale,
        panX = newPanX,
        panY = newPanY
      ))
    }

  def handleKeyDown(event: KeyboardEvent): Unit =
    event.key match
      case "r" | "R" => viewTransform.update(t => t.withRotation(t.rotationDegrees + 15))
      case "e" | "E" => viewTransform.update(t => t.withRotation(t.rotationDegrees - 15))
      case "+" | "=" => viewTransform.update(t => t.copy(scale = min(t.scale * 1.1, 5.0)))
      case "-" | "_" => viewTransform.update(t => t.copy(scale = max(t.scale / 1.1, 0.1)))
      case "Escape" =>
        selectedElements.set(Set.empty)
        selectedTilingPolygons.set(Set.empty)
        selectedPerimeterEdges.set(Set.empty)
      case "Delete" | "Backspace" => deleteSelectedElements()
      case _ => ()

  def deleteSelectedElements(): Unit =
    val selected = selectedElements.now()
    canvasPolygons.update(_.filterNot(p => selected.contains(p.id)))
    canvasTexts.update(_.filterNot(t => selected.contains(t.id)))
    selectedElements.set(Set.empty)

  def polygonButton(sides: Int): Element =
    button(
      className <-- selectedPolygon.signal.map(selected =>
        if (selected.contains(sides)) "polygon-btn selected" else "polygon-btn"
      ),
      tpe := "button",
      title := s"${sides}-sided polygon (${polygonName(sides)})",
      onClick --> { _ => selectedPolygon.set(Some(sides)) },
      polygonSvg(sides),
      div(className := "polygon-label", sides.toString)
    )

  def polygonSvg(sides: Int): Element =
    val size = 40
    val centerX = size / 2.0
    val centerY = size / 2.0
    val radius = size * 0.35

    val points = (0 until sides).map { i =>
      val angle = (2 * Pi * i / sides) - (Pi / 2) // Start from top
      val x = centerX + radius * cos(angle)
      val y = centerY + radius * sin(angle)
      s"$x,$y"
    }.mkString(" ")

    svg.svg(
      svg.width := size.toString,
      svg.height := size.toString,
      svg.viewBox := s"0 0 $size $size",
      svg.polygon(
        svg.points := points,
        svg.fill := "currentColor",
        svg.stroke := "currentColor",
        svg.strokeWidth := "1"
      )
    )

  def polygonName(sides: Int): String = sides match
    case 3 => "Triangle"
    case 4 => "Square"
    case 5 => "Pentagon"
    case 6 => "Hexagon"
    case 7 => "Heptagon"
    case 8 => "Octagon"
    case 9 => "Nonagon"
    case 10 => "Decagon"
    case 12 => "Dodecagon"
    case 15 => "Pentadecagon"
    case 18 => "Octadecagon"
    case 20 => "Icosagon"
    case 24 => "Icositetragon"
    case 42 => "Tetracontakaidigon"
    case _ => s"$sides-gon"