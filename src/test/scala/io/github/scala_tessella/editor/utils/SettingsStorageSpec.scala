package io.github.scala_tessella.editor.utils

import munit.FunSuite
import org.scalajs.dom

class SettingsStorageSpec extends FunSuite:

  private def clearKeys(): Unit =
    try
      dom.window.localStorage.removeItem("tessella.settings.defaultFillColor")
      dom.window.localStorage.removeItem("tessella.settings.perimeterEdgeColor")
    catch
      case _: Throwable => ()

  private def isLocalStorageAvailable: Boolean =
    try
      dom.window.localStorage.setItem("__tessella_test__", "1")
      dom.window.localStorage.removeItem("__tessella_test__")
      true
    catch
      case _: Throwable => false

  override def beforeEach(context: BeforeEach): Unit =
    if isLocalStorageAvailable then clearKeys()

  override def afterEach(context: AfterEach): Unit =
    if isLocalStorageAvailable then clearKeys()

  test("load returns None when no values are stored") {
    assume(isLocalStorageAvailable, "localStorage not available in this test environment")
    assert(SettingsStorage.loadDefaultStartFillColor().isEmpty)
    assert(SettingsStorage.loadPerimeterEdgeColor().isEmpty)
  }

  test("save and load round-trip settings colors") {
    assume(isLocalStorageAvailable, "localStorage not available in this test environment")
    val fill = ColorRGB(10, 20, 30)
    val edge = ColorRGB(250, 128, 64)

    SettingsStorage.saveDefaultStartFillColor(fill)
    SettingsStorage.savePerimeterEdgeColor(edge)

    assertEquals(SettingsStorage.loadDefaultStartFillColor(), Some(fill))
    assertEquals(SettingsStorage.loadPerimeterEdgeColor(), Some(edge))
  }
