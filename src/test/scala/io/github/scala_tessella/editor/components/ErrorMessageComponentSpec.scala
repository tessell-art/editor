package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.EditorState
import munit.FunSuite

// Laminar-mount spec for ErrorMessageComponent (ADR-003 Tier 1).
//
// The component is small (27 lines) but covers a pattern AboutPopup/IrregularPolygonPopup didn't:
// the entire content is wrapped in `child.maybe <-- signal.map(_.map(...))` so the inner div
// either exists or doesn't based on `errorState.errorMessage`. Tests here verify both branches
// switch reactively, plus that clicking the close button drives ErrorOperations.clearError.
class ErrorMessageComponentSpec extends FunSuite with EditorStateFixture with LaminarTestSupport:

  test("with no error message, the .error-container is empty (no .error-message child)"):
    EditorState.errorState.update(_.copy(errorMessage = None))
    mount(ErrorMessageComponent.element)

    // The outer .error-container is always present.
    assert(querySelector(".error-container").isDefined)
    // …but no inner .error-message div, because child.maybe collapses to None.
    assert(querySelector(".error-message").isEmpty)

  test("with an error message, the .error-message div renders icon + text + close"):
    EditorState.errorState.update(_.copy(errorMessage = Some("Boom")))
    mount(ErrorMessageComponent.element)

    val msg = querySelector(".error-message")
    assert(msg.isDefined)
    assertEquals(querySelector(".error-icon").map(_.textContent), Some("⚠️"))
    assertEquals(querySelector(".error-text").map(_.textContent), Some("Boom"))
    assert(querySelector(".error-close").isDefined)

  test("clicking the close button clears errorMessage and removes the .error-message div"):
    EditorState.errorState.update(_.copy(errorMessage = Some("Boom")))
    mount(ErrorMessageComponent.element)
    assert(querySelector(".error-message").isDefined)

    clickOn(".error-close")

    assertEquals(EditorState.errorState.now().errorMessage, None)
    // Reactive: the child.maybe should collapse synchronously.
    assert(querySelector(".error-message").isEmpty)

  test("the rendered content updates reactively when errorMessage changes mid-test"):
    EditorState.errorState.update(_.copy(errorMessage = None))
    mount(ErrorMessageComponent.element)
    assert(querySelector(".error-message").isEmpty)

    EditorState.errorState.update(_.copy(errorMessage = Some("First")))
    assertEquals(querySelector(".error-text").map(_.textContent), Some("First"))

    EditorState.errorState.update(_.copy(errorMessage = Some("Second")))
    assertEquals(querySelector(".error-text").map(_.textContent), Some("Second"))
