package io.github.scala_tessella.editor.utils

object ColorUtils:
  def rgbToString(color: (Int, Int, Int)): String =
    val (r, g, b) = color
    s"rgb($r,$g,$b)"

  def rgbaToString(color: (Int, Int, Int)): String =
    val (r, g, b) = color
    s"rgba($r,$g,$b,1)"

  def rgbToHex(color: (Int, Int, Int)): String =
    val (r, g, b) = color
    f"#$r%02x$g%02x$b%02x"

  extension (color: (Int, Int, Int))

    def toRgbString: String = rgbToString(color)

    def toRgbaString: String = rgbaToString(color)

    def toHexString: String = rgbToHex(color)
