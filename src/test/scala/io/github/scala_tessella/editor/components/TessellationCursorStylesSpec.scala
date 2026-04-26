package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{AddSubmode, EditorMode, Tool}
import munit.FunSuite

class TessellationCursorStylesSpec extends FunSuite:

  test("polygon cursor uses pointer in select mode when AddPolygon Outside is active"):
    val css =
      TessellationCursorStyles.polygonCursorCss(EditorMode.Select, Tool.AddPolygon, AddSubmode.Outside)
    assertEquals(css, "cursor: pointer;")

  test("polygon cursor uses delete icon in delete mode when AddPolygon Outside is active"):
    val css =
      TessellationCursorStyles.polygonCursorCss(EditorMode.Delete, Tool.AddPolygon, AddSubmode.Outside)
    assert(css.startsWith("cursor: url("))
    assert(css.endsWith(", auto;"))

  test("polygon cursor maps shape-and-color picker to same icon as color picker"):
    val colorPicker =
      TessellationCursorStyles.polygonCursorCss(EditorMode.Select, Tool.ColorPicker, AddSubmode.Outside)
    val shapePicker =
      TessellationCursorStyles.polygonCursorCss(
        EditorMode.Delete,
        Tool.ShapeAndColorPicker,
        AddSubmode.Outside
      )
    assertEquals(shapePicker, colorPicker)
    assert(colorPicker.startsWith("cursor: url("))

  test("clickable point cursor follows measurement / fan / AddPolygon-Inside / default mapping"):
    assertEquals(
      TessellationCursorStyles.clickablePointCursorCss(Tool.Measurement, AddSubmode.Outside),
      "cursor: crosshair;"
    )
    assertEquals(
      TessellationCursorStyles.clickablePointCursorCss(Tool.Fan, AddSubmode.Outside),
      "cursor: pointer;"
    )
    assertEquals(
      TessellationCursorStyles.clickablePointCursorCss(Tool.AddPolygon, AddSubmode.Inside),
      "cursor: pointer;"
    )
    val fallback =
      TessellationCursorStyles.clickablePointCursorCss(Tool.AddPolygon, AddSubmode.Outside)
    assert(fallback.startsWith("cursor: url("))
    assert(fallback.endsWith(", auto;"))
