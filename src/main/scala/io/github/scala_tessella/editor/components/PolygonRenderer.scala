package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.tessella.Tiling
import io.github.scala_tessella.tessella.Topology.{Edge, Node => TilingNode}
import io.github.scala_tessella.editor.models.{AppState, Point}

object PolygonRenderer:
  def renderCanvasPolygon(polygon: io.github.scala_tessella.editor.models.CanvasPolygon): Element =
    import scala.math.{cos, Pi, sin}

    val points = (0 until polygon.sides).map { i =>
      val angle = (2 * Pi * i / polygon.sides) - (Pi / 2) + polygon.rotation
      val x = polygon.center.x + polygon.radius * cos(angle)
      val y = polygon.center.y + polygon.radius * sin(angle)
      s"$x,$y"
    }.mkString(" ")

    val isSelected = AppState.selectedElements.signal.map(_.contains(polygon.id))

    svg.g(
      svg.polygon(
        svg.points := points,
        svg.fill := "rgba(100, 108, 255, 0.3)",
        svg.stroke <-- isSelected.map(selected => if (selected) "#ff6b6b" else "#646cff"),
        svg.strokeWidth <-- isSelected.map(selected => if (selected) "3" else "2"),
        svg.className := "clickable-polygon",
        onClick --> { _ => AppState.toggleSelection(polygon.id) }
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

  def renderCanvasText(text: io.github.scala_tessella.editor.models.CanvasText): Element =
    val isSelected = AppState.selectedElements.signal.map(_.contains(text.id))

    svg.text(
      svg.x := text.position.x.toString,
      svg.y := text.position.y.toString,
      svg.fontSize := text.fontSize.toString,
      svg.fill <-- isSelected.map(selected => if (selected) "#ff6b6b" else "#ccc"),
      svg.fontWeight <-- isSelected.map(selected => if (selected) "bold" else "normal"),
      svg.textAnchor := "middle",
      svg.className := "clickable-text",
      text.text,
      onClick --> { _ => AppState.toggleSelection(text.id) }
    )

  def renderPolygonPoints(polygon: io.github.scala_tessella.editor.models.CanvasPolygon): List[Element] =
    import scala.math.{cos, Pi, sin}

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