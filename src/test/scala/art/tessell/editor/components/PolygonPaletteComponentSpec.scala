package art.tessell.editor.components

import io.github.scala_tessella.dcel.geometry.AngleDegree
import munit.FunSuite

class PolygonPaletteComponentSpec extends FunSuite:

  // --- validateSides ---

  test("validateSides parses an integer in range and returns it unchanged"):
    assertEquals(PolygonPaletteComponent.validateSides("3"), 3)
    assertEquals(PolygonPaletteComponent.validateSides("11"), 11)
    assertEquals(PolygonPaletteComponent.validateSides("100"), 100)

  test("validateSides clamps below the minimum of 3"):
    assertEquals(PolygonPaletteComponent.validateSides("0"), 3)
    assertEquals(PolygonPaletteComponent.validateSides("2"), 3)
    assertEquals(PolygonPaletteComponent.validateSides("-99"), 3)

  test("validateSides clamps above the maximum of 100"):
    assertEquals(PolygonPaletteComponent.validateSides("101"), 100)
    assertEquals(PolygonPaletteComponent.validateSides("9999"), 100)

  test("validateSides falls back to 3 on non-numeric input"):
    assertEquals(PolygonPaletteComponent.validateSides(""), 3)
    assertEquals(PolygonPaletteComponent.validateSides("abc"), 3)
    assertEquals(PolygonPaletteComponent.validateSides("3.5"), 3)

  // --- polygonTooltip ---

  test("polygonTooltip formats as '$sides-sided polygon (name)'"):
    val triangleTip = PolygonPaletteComponent.polygonTooltip(3)
    assert(triangleTip.startsWith("3-sided polygon ("))
    assert(triangleTip.endsWith(")"))

    val customTip = PolygonPaletteComponent.polygonTooltip(42)
    assert(customTip.startsWith("42-sided polygon ("))

  // --- irregularPolygonLabel ---

  test("irregularPolygonLabel returns 'Irregular' when no shape is recorded"):
    assertEquals(PolygonPaletteComponent.irregularPolygonLabel(None), "Irregular")

  test("irregularPolygonLabel returns 'N≠' with N = angle count"):
    val triangle = Vector(60, 60, 60).map(AngleDegree(_))
    assertEquals(PolygonPaletteComponent.irregularPolygonLabel(Some(triangle)), "3≠")

    val pentagon = Vector(108, 108, 108, 108, 108).map(AngleDegree(_))
    assertEquals(PolygonPaletteComponent.irregularPolygonLabel(Some(pentagon)), "5≠")

  // --- polygonButtonClasses ---

  test("polygonButtonClasses returns the base classes when neither selected nor processing"):
    assertEquals(
      PolygonPaletteComponent.polygonButtonClasses("polygon-btn", selected = false, processing = false),
      "polygon-btn"
    )

  test("polygonButtonClasses appends 'selected' when selected"):
    assertEquals(
      PolygonPaletteComponent.polygonButtonClasses("polygon-btn", selected = true, processing = false),
      "polygon-btn selected"
    )

  test("polygonButtonClasses appends 'disabled' when processing"):
    assertEquals(
      PolygonPaletteComponent.polygonButtonClasses("polygon-btn", selected = false, processing = true),
      "polygon-btn disabled"
    )

  test("polygonButtonClasses appends 'selected disabled' when both flags are set"):
    assertEquals(
      PolygonPaletteComponent.polygonButtonClasses("polygon-btn", selected = true, processing = true),
      "polygon-btn selected disabled"
    )
