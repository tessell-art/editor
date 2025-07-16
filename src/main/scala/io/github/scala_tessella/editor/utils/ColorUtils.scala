package io.github.scala_tessella.editor.utils

object ColorUtils:
  def rgbToString(color: (Int, Int, Int)): String =
    val (r, g, b) = color
    s"rgb($r,$g,$b)"

  def rgbaToString(color: (Int, Int, Int)): String =
    val (r, g, b) = color
    s"rgba($r,$g,$b,1)"

  extension (color: (Int, Int, Int))

    def toRgbString: String = rgbToString(color)
    
    def toRgbaString: String = rgbaToString(color)
