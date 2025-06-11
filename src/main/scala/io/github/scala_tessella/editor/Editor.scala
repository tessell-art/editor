package io.github.scala_tessella.editor

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.tessella.Tiling
import io.github.scala_tessella.tessella.Topology.Edge
import io.github.scala_tessella.tessella.RegularPolygon.Polygon

import scala.scalajs.js
import scala.scalajs.js.annotation.*

import org.scalajs.dom

import scala.math.{cos, Pi, sin}

// import javascriptLogo from "/javascript.svg"
@js.native @JSImport("/javascript.svg", JSImport.Default)
val javascriptLogo: String = js.native

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
      polygonPalette(),
      div(className := "card",
        counterButton(),
      ),
    )

  val polygonSides: List[Int] = List(3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 18, 20, 24, 42)
  val selectedPolygon: Var[Option[Int]] = Var[Option[Int]](None)

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

  def counterButton(): Element =
    val counter = Var(0)
    button(
      tpe := "button",
      "count is ",
      child.text <-- counter,
      onClick --> { event => counter.update(_ + 1) },
    )
