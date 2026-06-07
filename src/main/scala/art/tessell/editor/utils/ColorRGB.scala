package art.tessell.editor.utils

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

  def parseHex(colorStr: String): Option[ColorRGB] =
    Option(colorStr).map(_.trim).flatMap: s =>

      val hex = if s.startsWith("#") then s.drop(1) else s
      if hex.length == 6 && hex.forall: x =>
          x.isDigit || "abcdefABCDEF".contains(x)
      then
        val r = Integer.parseInt(hex.substring(0, 2), 16)
        val g = Integer.parseInt(hex.substring(2, 4), 16)
        val b = Integer.parseInt(hex.substring(4, 6), 16)
        Some(ColorRGB(r, g, b))
      else None

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
