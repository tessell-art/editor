package io.github.scala_tessella.editor.utils

import munit.FunSuite

class ColorRGBSpec extends FunSuite:

  test("apply constructs color and accessors r/g/b expose channels") {
    val c = ColorRGB(12, 34, 56)
    assert(c.r == 12)
    assert(c.g == 34)
    assert(c.b == 56)
  }

  test("toRgb renders css rgb without spaces") {
    val c = ColorRGB(1, 2, 3)
    assert(c.toRgb == "rgb(1,2,3)")
  }

  test("toRgba renders css rgba with alpha fixed to 1") {
    val c = ColorRGB(9, 8, 7)
    assert(c.toRgba == "rgba(9,8,7,1)")
  }

  test("toHex renders lowercase #RRGGBB with zero padding") {
    val c1 = ColorRGB(0, 0, 0)
    assert(c1.toHex == "#000000")

    val c2 = ColorRGB(255, 255, 255)
    assert(c2.toHex == "#ffffff")

    val c3 = ColorRGB(16, 32, 48) // 0x10, 0x20, 0x30
    assert(c3.toHex == "#102030")
  }

  test("parseColor accepts rgb with optional spaces and valid integers") {
    assert(ColorRGB.parseColor("rgb(0,0,0)").contains(ColorRGB(0, 0, 0)))
    assert(ColorRGB.parseColor("rgb(255, 128, 64)").contains(ColorRGB(255, 128, 64)))
    assert(ColorRGB.parseColor("rgb( 10 , 20 , 30 )").isEmpty)
  }

  test("parseColor rejects invalid strings") {
    // not rgb()
    assert(ColorRGB.parseColor("").isEmpty)
    assert(ColorRGB.parseColor("rgba(1,2,3,1)").isEmpty)
    assert(ColorRGB.parseColor("rgb(1,2)").isEmpty)
    assert(ColorRGB.parseColor("rgb(1,2,3,4)").isEmpty)
    // non-numeric
    assert(ColorRGB.parseColor("rgb(a,b,c)").isEmpty)
    // out of range still parses (current behavior), but keep a check example for clarity
    // if behavior changes to validate range, update this test accordingly
    assert(ColorRGB.parseColor("rgb(999,999,999)").contains(ColorRGB(999, 999, 999)))
  }
