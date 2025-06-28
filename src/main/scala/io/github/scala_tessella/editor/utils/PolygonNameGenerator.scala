package io.github.scala_tessella.editor.utils

object PolygonNameGenerator:
  def polygonName(sides: Int): String = sides match
    case 3 => "Triangle"
    case 4 => "Square"
    case 5 => "Pentagon"
    case 6 => "Hexagon"
    case 7 => "Heptagon"
    case 8 => "Octagon"
    case 9 => "Enneagon"
    case 10 => "Decagon"
    case 11 => "Hendecagon"
    case 12 => "Dodecagon"
    case 15 => "Pentadecagon"
    case 18 => "Octadecagon"
    case 20 => "Icosagon"
    case 24 => "Icositetragon"
    case _ => s"$sides-gon"