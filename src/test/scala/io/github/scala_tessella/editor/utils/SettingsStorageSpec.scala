package io.github.scala_tessella.editor.utils

import munit.FunSuite
import org.scalajs.dom

class SettingsStorageSpec extends FunSuite:

  private def clearKeys(): Unit =
    dom.window.localStorage.removeItem("tessella.settings.defaultFillColor")
    dom.window.localStorage.removeItem("tessella.settings.perimeterEdgeColor")

  override def beforeEach(context: BeforeEach): Unit = clearKeys()
  override def afterEach(context: AfterEach): Unit   = clearKeys()

  test("load returns None when no values are stored") {
    assert(SettingsStorage.loadDefaultStartFillColor().isEmpty)
    assert(SettingsStorage.loadPerimeterEdgeColor().isEmpty)
  }

  test("save and load round-trip settings colors") {
    val fill = ColorRGB(10, 20, 30)
    val edge = ColorRGB(250, 128, 64)

    SettingsStorage.saveDefaultStartFillColor(fill)
    SettingsStorage.savePerimeterEdgeColor(edge)

    assertEquals(SettingsStorage.loadDefaultStartFillColor(), Some(fill))
    assertEquals(SettingsStorage.loadPerimeterEdgeColor(), Some(edge))
  }
