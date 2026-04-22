package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.{DoublingAnimation, EditorConfig, EditorState, FanAnimation}
import io.github.scala_tessella.editor.utils.geo.TessellationGeometry.{tilingPointToCanvasView, toPoint}
import io.github.scala_tessella.editor.utils.geo.{Point, Radian}
import io.github.scala_tessella.editor.utils.{ColorRGB, UndoManager, TilingBuilders}
import io.github.scala_tessella.dcel.structure.VertexId
import munit.FunSuite

import scala.concurrent.Promise
import scala.scalajs.js.timers.setTimeout

class TessellationOperationsSpec extends FunSuite with EditorStateFixture:

  test("selectPolygon should save undo and assign default colors on empty tiling") {
    EditorState.tessellationState.update(_.copy(currentTiling =
      io.github.scala_tessella.dcel.TilingDCEL.empty
    ))
    EditorState.colorState.update(_.copy(fillColor = ColorRGB(10, 20, 30)))
    UndoManager.clearHistory()

    TessellationOperations.selectPolygon(4)

    val tiling = EditorState.tessellationState.now().currentTiling
    assert(!tiling.isEmpty)
    assertEquals(UndoManager.undoCount.now(), 1)
    assert(tiling.innerFaces.nonEmpty)
    assert(tiling.innerFaces.forall(face =>
      EditorState.colorState.now().polygonColors.get(face.id).contains(EditorState.colorState.now().fillColor)
    ))
  }

  test("initializeWithIrregularIfEmpty should save undo and assign colors") {
    val fill = ColorRGB(7, 8, 9)
    EditorState.colorState.update(_.copy(fillColor = fill))
    EditorState.tessellationState.update(_.copy(currentTiling =
      io.github.scala_tessella.dcel.TilingDCEL.empty
    ))
    EditorState.recentIrregularPolygon.set(Some(EditorState.initialShape))
    UndoManager.clearHistory()

    TessellationOperations.initializeWithIrregularIfEmpty()

    val tiling = EditorState.tessellationState.now().currentTiling
    assert(!tiling.isEmpty)
    assertEquals(UndoManager.undoCount.now(), 1)
    assert(tiling.innerFaces.nonEmpty)
    assert(tiling.innerFaces.forall(face =>
      EditorState.colorState.now().polygonColors.get(face.id).contains(fill)
    ))
  }

  test("attemptPolygonAddition should show error when tiling is empty") {
    EditorState.tessellationState.update(_.copy(currentTiling =
      io.github.scala_tessella.dcel.TilingDCEL.empty
    ))
    EditorState.toolState.update(_.copy(selectedPolygon = Some(4)))
    EditorState.errorMessage.set(None)

    TessellationOperations.attemptPolygonAddition("edge-1", 0)

    assert(EditorState.errorMessage.now().exists(_.contains("No tiling available to grow")))
  }

  test("attemptPolygonInsertion should not crash when edge vertices are missing") {
    val tiling = TilingBuilders.freshSquare()
    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.toolState.update(_.copy(selectedPolygon = Some(4)))
    EditorState.isIrregularSelected.set(false)
    EditorState.errorMessage.set(None)
    val done   = Promise[Unit]()

    TessellationOperations.attemptPolygonInsertion(VertexId(9999), VertexId(10000))

    setTimeout(200) {
      assert(EditorState.errorMessage.now().exists(_.contains("Cannot insert regular polygon")))
      done.success(())
    }: Unit

    done.future
  }

  test("attemptPolygonInsertion should not crash for irregular insertion when edge vertices are missing") {
    val tiling = TilingBuilders.freshSquare()
    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.toolState.update(_.copy(selectedPolygon = None))
    EditorState.isIrregularSelected.set(true)
    EditorState.recentIrregularPolygon.set(Some(EditorState.initialShape))
    EditorState.errorMessage.set(None)
    val done   = Promise[Unit]()

    TessellationOperations.attemptPolygonInsertion(VertexId(9999), VertexId(10000))

    setTimeout(200) {
      assert(EditorState.errorMessage.now().exists(_.contains("Cannot insert irregular polygon")))
      done.success(())
    }: Unit

    done.future
  }

  test("attemptDoubling should be a no-op on empty tiling") {
    EditorState.tessellationState.update(_.copy(currentTiling =
      io.github.scala_tessella.dcel.TilingDCEL.empty
    ))
    EditorState.colorState.update(_.copy(polygonColors = Map.empty))
    UndoManager.clearHistory()

    TessellationOperations.attemptDoubling()

    assert(EditorState.tessellationState.now().currentTiling.isEmpty)
    assertEquals(EditorState.colorState.now().polygonColors, Map.empty)
    assertEquals(UndoManager.undoCount.now(), 0)
  }

  test("attemptMirroring should be a no-op on empty tiling") {
    EditorState.tessellationState.update(_.copy(currentTiling =
      io.github.scala_tessella.dcel.TilingDCEL.empty
    ))
    EditorState.mirrorAnimation.set(None)
    UndoManager.clearHistory()

    TessellationOperations.attemptMirroring()

    assert(EditorState.tessellationState.now().currentTiling.isEmpty)
    assertEquals(EditorState.mirrorAnimation.now(), None)
    assertEquals(UndoManager.undoCount.now(), 0)
  }

  test("attemptMirroring should trigger mirror animation on non-empty tiling") {
    val tiling = TilingBuilders.freshSquare()
    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.fanAnimation.set(
      Some(FanAnimation(
        facePoints = Nil,
        pivot = Point.origin,
        copies = 2,
        stepAngle = Radian.fromDegrees(180),
        durationMs = 10,
        staggerMs = 0
      ))
    )
    EditorState.doublingAnimation.set(Some(DoublingAnimation(
      facePoints = Nil,
      delta = Point.origin,
      durationMs = 10
    )))
    EditorState.mirrorAnimation.set(None)

    val done = Promise[Unit]()

    TessellationOperations.attemptMirroring()

    setTimeout(200) {
      val mirrored        = EditorState.tessellationState.now().currentTiling
      assert(!mirrored.isEmpty)
      assertEquals(EditorState.fanAnimation.now(), None)
      assertEquals(EditorState.doublingAnimation.now(), None)
      val mirrorAnimation = EditorState.mirrorAnimation.now()
      assert(mirrorAnimation.nonEmpty)
      assertEquals(mirrorAnimation.get.durationMs, EditorConfig.fanAnimationDurationMs)
      done.success(())
    }: Unit

    done.future
  }

  test("attemptMirroring should use tiling vertical midpoint as mirror axis") {
    val squareAngles = Vector.fill(4)(AngleDegree(90))
    val tiling       = TilingDCEL.createSimplePolygon(squareAngles).toOption.get
    val ys           =
      tiling.innerFacesVertices.flatMap: (_, vertices) =>
        vertices.map: vertex =>
          tilingPointToCanvasView(vertex.coords.toPoint).y
    val expectedAxis = (ys.min + ys.max) / 2.0

    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.mirrorAnimation.set(None)
    val done = Promise[Unit]()

    TessellationOperations.attemptMirroring()

    setTimeout(200) {
      val mirrorAnimation = EditorState.mirrorAnimation.now()
      assert(mirrorAnimation.nonEmpty)
      assertEqualsDouble(mirrorAnimation.get.axisY, expectedAxis, 1e-9)
      assert(Math.abs(expectedAxis - EditorConfig.canvasCenter.y) > 1e-9)
      done.success(())
    }: Unit

    done.future
  }

  test("attemptFanning should replicate colors across fan copies") {
    val tiling        = TilingBuilders.freshSquare()
    val faceIds       = tiling.innerFaces.map(_.id)
    val originalColor = ColorRGB(200, 100, 50)
    val fillColor     = ColorRGB(1, 2, 3)
    val vertexId      = tiling.boundaryVertices.toOption.get.head.id

    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.colorState.update(_.copy(fillColor = fillColor))
    EditorState.colorState.update(_.copy(polygonColors = faceIds.map(_ -> originalColor).toMap))

    val done = Promise[Unit]()

    TessellationOperations.attemptFanning(vertexId)

    setTimeout(200) {
      val newFaceIds = EditorState.tessellationState.now().currentTiling.innerFaces.map(_.id)
      val colors     = EditorState.colorState.now().polygonColors

      assert(newFaceIds.size > faceIds.size)
      assert(newFaceIds.forall(id => colors.get(id).contains(originalColor)))
      done.success(())
    }: Unit

    done.future
  }

  private def assertEqualsDouble(obtained: Double, expected: Double, delta: Double): Unit =
    assert(
      Math.abs(obtained - expected) <= delta,
      s"obtained=$obtained expected=$expected delta=$delta"
    )
