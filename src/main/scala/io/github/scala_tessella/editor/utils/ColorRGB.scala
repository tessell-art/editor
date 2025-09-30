package io.github.scala_tessella.editor.utils

import scala.scalajs.js.RegExp
import scala.util.Try

opaque type ColorRGB = (red: Int, green: Int, blue: Int)

object ColorRGB:

  inline def apply(red: Int, green: Int, blue: Int): ColorRGB =
    (red, green, blue)

  def parseColor(colorStr: String): Option[ColorRGB] =
    Option(colorStr).flatMap { s =>
      val rgbRegex = new RegExp("rgb\\((\\d+),\\s*(\\d+),\\s*(\\d+)\\)")
      Option(rgbRegex.exec(s)).flatMap { result =>

        if result.length == 4 then
          for
            rStr <- result(1).toOption
            gStr <- result(2).toOption
            bStr <- result(3).toOption
            r    <- Try(rStr.toInt).toOption
            g    <- Try(gStr.toInt).toOption
            b    <- Try(bStr.toInt).toOption
          yield ColorRGB(r, g, b)
        else None
      }
    }

  extension (color: ColorRGB)

    inline def r: Int =
      color.red

    inline def g: Int =
      color.green

    inline def b: Int =
      color.blue

    def toRgb: String =
      s"rgb($r,$g,$b)"

    def toRgba: String =
      s"rgba($r,$g,$b,1)"

    def toHex: String =
      f"#$r%02x$g%02x$b%02x"
