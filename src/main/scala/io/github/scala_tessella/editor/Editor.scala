
package io.github.scala_tessella.editor

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.tessella.Tiling
import io.github.scala_tessella.tessella.Topology.Edge
import io.github.scala_tessella.tessella.RegularPolygon.Polygon

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
case class ViewTransform(scale: Double = 1.0, rotation: Double = 0.0, panX: Double = 0.0, panY: Double = 0.0)

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

  // Canvas interaction state
  val isDragging: Var[Boolean] = Var(false)
  val dragStart: Var[Option[Point]] = Var(None)

  def generateSamplePolygons(): List[CanvasPolygon] =
    List(
      CanvasPolygon("poly1", 6, Point(200, 150), 40),
      CanvasPolygon("poly2", 4, Point(350, 200), 35, Pi/4),
      CanvasPolygon("poly3", 8, Point(150, 300), 50),
      CanvasPolygon("poly4", 3, Point(400, 120), 30),
      CanvasPolygon("poly5", 5, Point(300, 350), 45, Pi/6)
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
      )
    )

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
            s"translate(${transform.panX}, ${transform.panY}) scale(${transform.scale}) rotate(${math.toDegrees(transform.rotation)} 400 300)"
          ),

          // Grid pattern
          gridPattern(),

          // Render polygons
          children <-- canvasPolygons.signal.map(_.map(renderCanvasPolygon)),

          // Render texts
          children <-- canvasTexts.signal.map(_.map(renderCanvasText)),

          // Render connection points
          children <-- canvasPolygons.signal.map(_.flatMap(renderPolygonPoints))
        ),

        // Mouse event handlers
        onMouseDown --> handleMouseDown,
        onMouseMove --> handleMouseMove,
        onMouseUp --> handleMouseUp,
        onWheel --> handleWheel,
        onKeyDown --> handleKeyDown
      )
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
        viewTransform.update(t => t.copy(rotation = t.rotation - Pi/6))
      }),
      button("Rotate Right", onClick --> { _ =>
        viewTransform.update(t => t.copy(rotation = t.rotation + Pi/6))
      }),
      div(
        className := "transform-info",
        child.text <-- viewTransform.signal.map(t =>
          f"Scale: ${t.scale}%.2f | Rotation: ${math.toDegrees(t.rotation)}%.0f° | Pan: (${t.panX}%.0f, ${t.panY}%.0f)"
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

  def handleWheel(event: WheelEvent): Unit =
    event.preventDefault()
    val scaleFactor = if (event.deltaY < 0) 1.1 else 0.9
    viewTransform.update(t => t.copy(
      scale = max(0.1, min(5.0, t.scale * scaleFactor))
    ))

  def handleKeyDown(event: KeyboardEvent): Unit =
    event.key match
      case "r" | "R" => viewTransform.update(t => t.copy(rotation = t.rotation + Pi/12))
      case "e" | "E" => viewTransform.update(t => t.copy(rotation = t.rotation - Pi/12))
      case "+" | "=" => viewTransform.update(t => t.copy(scale = min(t.scale * 1.1, 5.0)))
      case "-" | "_" => viewTransform.update(t => t.copy(scale = max(t.scale / 1.1, 0.1)))
      case "Escape" => selectedElements.set(Set.empty)
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