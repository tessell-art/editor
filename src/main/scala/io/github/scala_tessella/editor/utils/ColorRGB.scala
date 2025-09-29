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
      s"rgb($r,$g,$b)"

    def toRgba: String =
      s"rgba($r,$g,$b,1)"

    def toHex: String =
      f"#$r%02x$g%02x$b%02x"
