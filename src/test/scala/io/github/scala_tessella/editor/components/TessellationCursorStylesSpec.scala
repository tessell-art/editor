package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{EditorMode, Tool}
import munit.FunSuite

class TessellationCursorStylesSpec extends FunSuite:

  test("polygon cursor uses pointer in select mode when no tool is active"):
    val css = TessellationCursorStyles.polygonCursorCss(EditorMode.Select, None)
    assertEquals(css, "cursor: pointer;")

  test("polygon cursor uses delete icon in delete mode when no tool is active"):
    val css = TessellationCursorStyles.polygonCursorCss(EditorMode.Delete, None)
    assert(css.startsWith("cursor: url("))
    assert(css.endsWith(", auto;"))

  test("polygon cursor maps shape-and-color picker to same icon as color picker"):
    val colorPicker = TessellationCursorStyles.polygonCursorCss(EditorMode.Select, Some(Tool.ColorPicker))
    val shapePicker = TessellationCursorStyles.polygonCursorCss(
      EditorMode.Delete,
      Some(Tool.ShapeAndColorPicker)
    )
    assertEquals(shapePicker, colorPicker)
    assert(colorPicker.startsWith("cursor: url("))

  test("clickable point cursor follows measurement/fan/inserter/default mapping"):
    assertEquals(
      TessellationCursorStyles.clickablePointCursorCss(Some(Tool.Measurement)),
      "cursor: crosshair;"
    )
    assertEquals(TessellationCursorStyles.clickablePointCursorCss(Some(Tool.Fan)), "cursor: pointer;")
    assertEquals(
      TessellationCursorStyles.clickablePointCursorCss(Some(Tool.Inserter)),
      "cursor: pointer;"
    )
    val fallback = TessellationCursorStyles.clickablePointCursorCss(None)
    assert(fallback.startsWith("cursor: url("))
    assert(fallback.endsWith(", auto;"))
