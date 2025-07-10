package io.github.scala_tessella.editor.utils

object PolygonNameGenerator:

  private val names: Map[Int, String] = Map(
    3 -> "Triangle",
    4 -> "Square",
    5 -> "Pentagon",
    6 -> "Hexagon",
    7 -> "Heptagon",
    8 -> "Octagon",
    9 -> "Enneagon",
    10 -> "Decagon",
    11 -> "Hendecagon",
    12 -> "Dodecagon",
    15 -> "Pentadecagon",
    18 -> "Octadecagon",
    20 -> "Icosagon",
    24 -> "Icositetragon"
  )

  def polygonName(sides: Int): String =
    names.getOrElse(sides, s"$sides-gon")
