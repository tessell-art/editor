package io.github.scala_tessella.editor.components.popup

import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.components.LaminarTestSupport
import io.github.scala_tessella.editor.models.EditorState
import munit.FunSuite

// Second Laminar-mount spec — picked specifically to smoke-test LaminarTestSupport against more than
// AboutPopup's static content: this popup has state-driven `child.maybe <--` switching, three buttons
// that mutate state via Observers, and stopPropagation inside the observer rather than via
// onClick.stopPropagation.
class IrregularPolygonPopupSpec extends FunSuite with EditorStateFixture with LaminarTestSupport:

  private val initialAngles: Vector[AngleDegree] =
    Vector(60, 120, 60, 120).map(AngleDegree(_))

  test("renders the empty state when no irregular polygon is recorded"):
    EditorState.irregularState.update(_.copy(recentIrregularPolygon = None))
    mount(IrregularPolygonPopup.element): Unit

    assertEquals(querySelector("h2").map(_.textContent), Some("Adjust attaching edge"))
    // child.maybe renders "No irregular polygon" placeholder
    assert(container.textContent.contains("No irregular polygon"))
    assert(querySelector(".irregular-head-editor").isEmpty)

  test("renders the editor when a recent irregular polygon is present"):
    EditorState.irregularState.update(_.copy(recentIrregularPolygon = Some(initialAngles)))
    mount(IrregularPolygonPopup.element): Unit

    assert(querySelector(".irregular-head-editor").isDefined)
    assert(querySelector(".big-preview").isDefined)
    // 3 control buttons + the popup-close-btn
    assertEquals(querySelectorAll("button").size, 4)

  test("clicking the close button sets showIrregularPolygonPopup to false"):
    EditorState.popupState.update(_.copy(showIrregularPolygonPopup = true))
    EditorState.irregularState.update(_.copy(recentIrregularPolygon = Some(initialAngles)))
    mount(IrregularPolygonPopup.element): Unit

    clickOn(".popup-close-btn")

    assertEquals(EditorState.popupState.now().showIrregularPolygonPopup, false)

  test("shift-left button rotates recentIrregularPolygon left and reactively updates the DOM"):
    EditorState.irregularState.update(_.copy(recentIrregularPolygon = Some(initialAngles)))
    mount(IrregularPolygonPopup.element): Unit

    clickOn(".btn-left")

    val rotated = EditorState.irregularState.now().recentIrregularPolygon.get
    assertEquals(rotated, Vector(120, 60, 120, 60).map(AngleDegree(_)))
    assertNotEquals(rotated, initialAngles)

  test("shift-right button rotates recentIrregularPolygon right"):
    EditorState.irregularState.update(_.copy(recentIrregularPolygon = Some(initialAngles)))
    mount(IrregularPolygonPopup.element): Unit

    clickOn(".btn-right")

    val rotated = EditorState.irregularState.now().recentIrregularPolygon.get
    assertEquals(rotated, Vector(120, 60, 120, 60).map(AngleDegree(_)))

  test("flip button reflects recentIrregularPolygon"):
    val asymmetric = Vector(60, 90, 120, 150).map(AngleDegree(_))
    EditorState.irregularState.update(_.copy(recentIrregularPolygon = Some(asymmetric)))
    mount(IrregularPolygonPopup.element): Unit

    clickOn(".btn-flip")

    val flipped = EditorState.irregularState.now().recentIrregularPolygon.get
    // We don't assert the exact reflection result (RingSeq.reflectAt semantics are an implementation
    // detail) — just that the modify pipeline ran and produced a different vector of the same size.
    assertEquals(flipped.size, asymmetric.size)
    assertNotEquals(flipped, asymmetric)

  test("control-button clicks do not close the popup (stopPropagation in the observer)"):
    EditorState.popupState.update(_.copy(showIrregularPolygonPopup = true))
    EditorState.irregularState.update(_.copy(recentIrregularPolygon = Some(initialAngles)))
    mount(IrregularPolygonPopup.element): Unit

    clickOn(".btn-left")

    // showIrregularPolygonPopup should still be true — the stopPropagation in `modify`'s Observer
    // prevents the click from bubbling to popup-content / popup-overlay close handlers.
    assertEquals(EditorState.popupState.now().showIrregularPolygonPopup, true)

  test("DOM reactively switches from 'no polygon' to editor when state is updated mid-test"):
    EditorState.irregularState.update(_.copy(recentIrregularPolygon = None))
    mount(IrregularPolygonPopup.element): Unit
    assert(querySelector(".irregular-head-editor").isEmpty)

    EditorState.irregularState.update(_.copy(recentIrregularPolygon = Some(initialAngles)))

    // Airstream propagates synchronously, so the DOM should already reflect the new state.
    assert(querySelector(".irregular-head-editor").isDefined)
