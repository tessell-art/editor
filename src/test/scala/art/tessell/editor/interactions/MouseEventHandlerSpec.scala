package art.tessell.editor.interactions

import art.tessell.editor.EditorStateFixture
import art.tessell.editor.models.{EditorConfig, EditorState, ViewTransform}
import art.tessell.editor.utils.geo.Point
import munit.FunSuite
import org.scalajs.dom

import scala.scalajs.js

class MouseEventHandlerSpec extends FunSuite with EditorStateFixture:

  private def worldPoint(screen: Point, transform: ViewTransform): Point =
    (screen - transform.pan) / transform.scale

  private def mouseEvent(eventType: String, clientX: Double, clientY: Double): dom.MouseEvent =
    val init = js.Dynamic
      .literal(clientX = clientX, clientY = clientY, bubbles = true, cancelable = true)
      .asInstanceOf[dom.MouseEventInit]
    new dom.MouseEvent(eventType, init)

  private def wheelEvent(clientX: Double, clientY: Double, deltaY: Double): dom.WheelEvent =
    val init = js.Dynamic
      .literal(clientX = clientX, clientY = clientY, deltaY = deltaY, bubbles = true, cancelable = true)
      .asInstanceOf[dom.WheelEventInit]
    new dom.WheelEvent("wheel", init)

  // --- pure calculateZoomTransform helper ---

  test("calculateZoomTransform keeps the world point under the cursor") {
    val transform = ViewTransform(scale = 1.0, rotationDegrees = 0, pan = Point.origin)
    val cursor    = Point(400, 300)
    val before    = worldPoint(cursor, transform)

    val afterTransform =
      MouseEventHandler.calculateZoomTransform(transform, cursor, scaleFactor = 1.1)
    val after          = worldPoint(cursor, afterTransform)

    assertEquals(before.x, after.x)
    assertEquals(before.y, after.y)
  }

  test("calculateZoomTransform clamps scale to min and max") {
    val cursor = Point(10, 10)
    val minT   = ViewTransform(scale = EditorConfig.minViewScale, rotationDegrees = 0, pan = Point.origin)
    val maxT   = ViewTransform(scale = EditorConfig.maxViewScale, rotationDegrees = 0, pan = Point.origin)

    val zoomOut = MouseEventHandler.calculateZoomTransform(minT, cursor, scaleFactor = 0.5)
    val zoomIn  = MouseEventHandler.calculateZoomTransform(maxT, cursor, scaleFactor = 2.0)

    assertEquals(zoomOut.scale, EditorConfig.minViewScale)
    assertEquals(zoomIn.scale, EditorConfig.maxViewScale)
  }

  // --- handleMouseDown / handleMouseMove / handleMouseUp ---

  test("handleMouseDown sets isDragging and stores the drag start position") {
    EditorState.uiState.update(_.copy(isDragging = false, dragStart = None))

    MouseEventHandler.handleMouseDown(mouseEvent("mousedown", 100, 200))

    val ui = EditorState.uiState.now()
    assert(ui.isDragging)
    assertEquals(ui.dragStart, Some(Point(100, 200)))
  }

  test("handleMouseMove pans the view by the cursor delta when dragging") {
    val initialPan = Point(50, 50)
    EditorState.uiState.update(_.copy(isDragging = true, dragStart = Some(Point(100, 200))))
    EditorState.viewState.update(s => s.copy(viewTransform = s.viewTransform.copy(pan = initialPan)))

    MouseEventHandler.handleMouseMove(mouseEvent("mousemove", 130, 240))

    val pan = EditorState.viewState.now().viewTransform.pan
    assertEquals(pan, initialPan + Point(30, 40))
    // dragStart was advanced to the latest cursor position.
    assertEquals(EditorState.uiState.now().dragStart, Some(Point(130, 240)))
  }

  test("handleMouseMove is a no-op when not dragging") {
    val initialPan = Point(11, 22)
    EditorState.uiState.update(_.copy(isDragging = false, dragStart = None))
    EditorState.viewState.update(s => s.copy(viewTransform = s.viewTransform.copy(pan = initialPan)))

    MouseEventHandler.handleMouseMove(mouseEvent("mousemove", 999, 999))

    assertEquals(EditorState.viewState.now().viewTransform.pan, initialPan)
    assertEquals(EditorState.uiState.now().dragStart, None)
  }

  test("handleMouseUp clears isDragging and dragStart") {
    EditorState.uiState.update(_.copy(isDragging = true, dragStart = Some(Point(1, 2))))

    MouseEventHandler.handleMouseUp(mouseEvent("mouseup", 0, 0))

    val ui = EditorState.uiState.now()
    assert(!ui.isDragging)
    assertEquals(ui.dragStart, None)
  }

  // --- handleWheel (exercises private getCanvasRelativePosition) ---

  test("handleWheel zooms in when deltaY is negative and a canvas element is registered") {
    val canvas = dom.document.createElement("div")
    EditorState.uiState.update(_.copy(canvasElementRef = Some(canvas)))
    EditorState.viewState.update(s =>
      s.copy(viewTransform =
        ViewTransform(
          scale = 1.0,
          rotationDegrees = 0,
          pan = Point.origin
        )
      )
    )

    MouseEventHandler.handleWheel(wheelEvent(clientX = 100, clientY = 50, deltaY = -1))

    val newScale = EditorState.viewState.now().viewTransform.scale
    assert(newScale > 1.0, s"expected zoom-in (>1.0), got $newScale")
    assertEquals(newScale, EditorConfig.mouseWheelZoomInFactor)
  }

  test("handleWheel zooms out when deltaY is positive") {
    val canvas = dom.document.createElement("div")
    EditorState.uiState.update(_.copy(canvasElementRef = Some(canvas)))
    EditorState.viewState.update(s =>
      s.copy(viewTransform =
        ViewTransform(
          scale = 1.0,
          rotationDegrees = 0,
          pan = Point.origin
        )
      )
    )

    MouseEventHandler.handleWheel(wheelEvent(clientX = 100, clientY = 50, deltaY = 1))

    val newScale = EditorState.viewState.now().viewTransform.scale
    assert(newScale < 1.0, s"expected zoom-out (<1.0), got $newScale")
    assertEquals(newScale, EditorConfig.mouseWheelZoomOutFactor)
  }

  test("handleWheel is a no-op when no canvas element is registered") {
    EditorState.uiState.update(_.copy(canvasElementRef = None))
    val before = ViewTransform(scale = 1.0, rotationDegrees = 0, pan = Point.origin)
    EditorState.viewState.update(_.copy(viewTransform = before))

    MouseEventHandler.handleWheel(wheelEvent(clientX = 100, clientY = 50, deltaY = -1))

    assertEquals(EditorState.viewState.now().viewTransform, before)
  }
