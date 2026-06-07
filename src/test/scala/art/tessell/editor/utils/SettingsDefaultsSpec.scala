package art.tessell.editor.utils

import art.tessell.editor.models.EditorConfig
import munit.FunSuite
import org.scalajs.dom

class SettingsDefaultsSpec extends FunSuite:

  private def isLocalStorageAvailable: Boolean =
    try
      dom.window.localStorage.setItem("__tessella_test__", "1")
      dom.window.localStorage.removeItem("__tessella_test__")
      true
    catch
      case _: Throwable => false

  test("tempDefaults returns editor default colors") {
    val (fill, perimeter, polygonEdge) = SettingsDefaults.tempDefaults
    assertEquals(fill, EditorConfig.defaultPolygonColor)
    assertEquals(perimeter, EditorConfig.defaultPerimeterEdgeColor)
    assertEquals(polygonEdge, EditorConfig.defaultPolygonEdgeColor)
  }

  test("tempDefaults does not persist or overwrite stored values") {
    assume(isLocalStorageAvailable, "localStorage not available in this test environment")

    val storedFill = ColorRGB(10, 20, 30)
    val storedEdge = ColorRGB(40, 50, 60)
    SettingsStorage.saveDefaultStartFillColor(storedFill)
    SettingsStorage.savePerimeterEdgeColor(storedEdge)

    SettingsDefaults.tempDefaults: Unit

    assertEquals(SettingsStorage.loadDefaultStartFillColor(), Some(storedFill))
    assertEquals(SettingsStorage.loadPerimeterEdgeColor(), Some(storedEdge))
  }
