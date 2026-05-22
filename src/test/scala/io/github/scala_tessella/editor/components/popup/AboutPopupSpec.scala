package io.github.scala_tessella.editor.components.popup

import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.components.LaminarTestSupport
import io.github.scala_tessella.editor.models.EditorState
import munit.FunSuite

// First Laminar-in-JSDOM mount spec. AboutPopup is static content plus close wiring —
// ideal for crystallising the LaminarTestSupport pattern on a small surface.
class AboutPopupSpec extends FunSuite with EditorStateFixture with LaminarTestSupport:

  test("renders title, version line, and close button"):
    mount(AboutPopup.element): Unit

    assertEquals(querySelector("h1").map(_.textContent), Some("Tessella"))
    assert(querySelector(".about-version").exists(_.textContent.startsWith("Editor v")))
    assert(querySelector(".popup-close-btn").isDefined)

  test("clicking the close button sets showAboutPopup to false"):
    EditorState.popupState.update(_.copy(showAboutPopup = true))
    mount(AboutPopup.element): Unit

    clickOn(".popup-close-btn")

    assertEquals(EditorState.popupState.now().showAboutPopup, false)

  test("clicking the overlay sets showAboutPopup to false"):
    EditorState.popupState.update(_.copy(showAboutPopup = true))
    mount(AboutPopup.element): Unit

    clickOn(".popup-overlay")

    assertEquals(EditorState.popupState.now().showAboutPopup, false)

  test("clicking inside popup-content does not close (stopPropagation on content)"):
    EditorState.popupState.update(_.copy(showAboutPopup = true))
    mount(AboutPopup.element): Unit

    // Click a non-button descendant of popup-content — the click bubbles to popup-content
    // which stops propagation, so the overlay's close observer never fires.
    clickOn(".popup-content h1")

    assertEquals(EditorState.popupState.now().showAboutPopup, true)
