package io.github.scala_tessella.editor.utils

opaque type ColorRGB = (red: Int, green: Int, blue: Int)

object ColorRGB:

  def apply(red: Int, green: Int, blue: Int): ColorRGB =
    (red, green, blue)

  extension (color: ColorRGB)

    def r: Int =
      color.red

    def g: Int =
      color.green

    def b: Int =
      color.blue

    def toRgb: String =
      s"rgb(${color.r},${color.g},${color.b})"

    def toRgba: String =
      s"rgba(${color.r},${color.g},${color.b},1)"

    def toHex: String =
      f"#${color.r}%02x${color.g}%02x${color.b}%02x"
