package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.{ColorRGB, SettingsStorage}
import munit.FunSuite
import org.scalajs.dom

class SettingsOperationsSpec extends FunSuite with EditorStateFixture:

  private def clearPersistedSettings(): Unit =
    dom.window.localStorage.removeItem("tessella.settings.defaultFillColor")
    dom.window.localStorage.removeItem("tessella.settings.perimeterEdgeColor")
    dom.window.localStorage.removeItem("tessella.settings.boundaryEdgeWidth")
    dom.window.localStorage.removeItem("tessella.settings.polygonEdgeColor")
    dom.window.localStorage.removeItem("tessella.settings.polygonEdgeWidth")
    dom.window.localStorage.removeItem("tessella.settings.reduceMotion")

  override def beforeEach(context: BeforeEach): Unit =
    super.beforeEach(context)
    clearPersistedSettings()

  override def afterEach(context: AfterEach): Unit =
    clearPersistedSettings()
    super.afterEach(context)

  test("applySettings writes defaultStartFillColor, fillColor, perimeterEdgeColor and persists both") {
    val fill      = ColorRGB(11, 22, 33)
    val perimeter = ColorRGB(44, 55, 66)
    EditorState.colorState.update(_.copy(
      tempDefaultFillColor = fill,
      tempPerimeterEdgeColor = perimeter
    ))

    SettingsOperations.applySettings()

    val color = EditorState.colorState.now()
    assertEquals(color.defaultStartFillColor, fill)
    assertEquals(color.fillColor, fill)
    assertEquals(color.perimeterEdgeColor, perimeter)

    assertEquals(SettingsStorage.loadDefaultStartFillColor(), Some(fill))
    assertEquals(SettingsStorage.loadPerimeterEdgeColor(), Some(perimeter))
  }

  test("resetFillColorToDefault copies defaultStartFillColor into fillColor") {
    val baseline = ColorRGB(7, 7, 7)
    EditorState.colorState.update(_.copy(
      defaultStartFillColor = baseline,
      fillColor = ColorRGB(200, 200, 200)
    ))

    SettingsOperations.resetFillColorToDefault()

    assertEquals(EditorState.colorState.now().fillColor, baseline)
  }

  test("refreshSettingsTempValues syncs temp colors from saved values and hides settings picker") {
    val savedFill      = ColorRGB(10, 20, 30)
    val savedPerimeter = ColorRGB(40, 50, 60)
    EditorState.colorState.update(_.copy(
      defaultStartFillColor = savedFill,
      perimeterEdgeColor = savedPerimeter,
      tempDefaultFillColor = ColorRGB(1, 2, 3),
      tempPerimeterEdgeColor = ColorRGB(4, 5, 6),
      tempSettingsPickerColor = ColorRGB(7, 8, 9)
    ))
    EditorState.popupState.update(_.copy(showSettingsColorPicker = true))

    SettingsOperations.refreshSettingsTempValues()

    val color = EditorState.colorState.now()
    assertEquals(color.tempDefaultFillColor, savedFill)
    assertEquals(color.tempPerimeterEdgeColor, savedPerimeter)
    assertEquals(color.tempSettingsPickerColor, savedFill)
    assertEquals(EditorState.popupState.now().showSettingsColorPicker, false)
  }

  test("applySettings round-trips through SettingsStorage back into a rebuilt ColorState.initial") {
    val fill      = ColorRGB(123, 45, 67)
    val perimeter = ColorRGB(9, 99, 199)
    EditorState.colorState.update(_.copy(
      tempDefaultFillColor = fill,
      tempPerimeterEdgeColor = perimeter
    ))

    SettingsOperations.applySettings()

    // A fresh ColorState.initial reads from SettingsStorage — our persisted values must come back.
    val rebuilt = io.github.scala_tessella.editor.models.ColorState.initial
    assertEquals(rebuilt.defaultStartFillColor, fill)
    assertEquals(rebuilt.fillColor, fill)
    assertEquals(rebuilt.perimeterEdgeColor, perimeter)
  }
