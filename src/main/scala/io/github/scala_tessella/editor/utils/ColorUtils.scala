package io.github.scala_tessella.editor.utils

object ColorUtils:
  def rgbToString(color: (Int, Int, Int)): String =
    val (r, g, b) = color
    s"rgb($r,$g,$b)"

  extension (color: (Int, Int, Int))
    def toRgbString: String = rgbToString(color)
