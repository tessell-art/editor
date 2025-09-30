package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.utils.ColorRGB.parseColor
import munit.FunSuite

class SvgImporterSpec extends FunSuite:

  test("parseColor handles valid rgb triplets and invalid inputs") {
    assert(parseColor("rgb(10, 20, 30)").contains((10, 20, 30)))
    assert(parseColor("rgb(0,0,0)").contains((0, 0, 0)))
    assert(parseColor("rgb(255,255,255)").contains((255, 255, 255)))
    assert(parseColor("rgba(1,2,3,0.5)").isEmpty)
    assert(parseColor("").isEmpty)
    assert(parseColor(null.asInstanceOf[String]).isEmpty)
  }
