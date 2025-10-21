package io.github.scala_tessella.editor.utils

object PolygonNameGenerator:

  private val names: Map[Int, String] = Map(
    3  -> "Triangle",
    4  -> "Square",
    5  -> "Pentagon",
    6  -> "Hexagon",
    7  -> "Heptagon",
    8  -> "Octagon",
    9  -> "Enneagon",
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

  type Template = (name: String, pattern: String, filename: String)

  def regularNames: List[Template] =
    List(
      ("Triangular", "(3.3.3.3.3.3)", "regular_3-3-3-3-3-3.svg"),
      ("Square", "(4.4.4.4)", "regular_4-4-4-4.svg"),
      ("Hexagonal", "(6.6.6)", "regular_6-6-6.svg")
    )

  def semiRegularNames: List[Template] =
    List(
      ("Snub square", "(3.3.4.3.4)", "semiregular_3-3-4-3-4.svg"),
      ("Elongated triangular", "(3.3.3.4.4)", "semiregular_3-3-3-4-4.svg"),
      ("Snub hexagonal", "(3.3.3.3.6)", "semiregular_3-3-3-3-6.svg"),
      ("Truncated triangular", "(3.6.3.6)", "semiregular_3-6-3-6.svg"),
      ("Rhombitrihexagonal", "(3.4.6.4)", "semiregular_3-4-6-4.svg"),
      ("Truncated square", "(4.8.8)", "semiregular_4-8-8.svg"),
      ("Truncated trihexagonal", "(4.6.12)", "semiregular_4-6-12.svg"),
      ("Truncated hexagonal", "(3.12.12)", "semiregular_3-12-12.svg")
    )

  def irregularNames: List[Template] =
    List(
      ("Ammann-Beenker", "A5", "ammann_A5.svg"),
      ("Penrose", "P1", "penrose_P1.svg"),
      ("Penrose", "P3", "penrose_P3.svg")
    )
