package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.{ColorRGB, UndoManager, TilingBuilders}
import munit.FunSuite

import scala.concurrent.Promise
import scala.scalajs.js.timers.setTimeout

class TessellationOperationsSpec extends FunSuite with EditorStateFixture:

  test("selectPolygon should save undo and assign default colors on empty tiling") {
    EditorState.currentTiling.set(io.github.scala_tessella.dcel.TilingDCEL.empty)
    EditorState.fillColor.set(ColorRGB(10, 20, 30))
    UndoManager.clearHistory()

    TessellationOperations.selectPolygon(4)

    val tiling = EditorState.currentTiling.now()
    assert(!tiling.isEmpty)
    assertEquals(UndoManager.undoCount.now(), 1)
    assert(tiling.innerFaces.nonEmpty)
    assert(tiling.innerFaces.forall(face =>
      EditorState.polygonColors.now().get(face.id).contains(EditorState.fillColor.now())
    ))
  }

  test("initializeWithIrregularIfEmpty should save undo and assign colors") {
    val fill = ColorRGB(7, 8, 9)
    EditorState.fillColor.set(fill)
    EditorState.currentTiling.set(io.github.scala_tessella.dcel.TilingDCEL.empty)
    EditorState.recentIrregularPolygon.set(Some(EditorState.initialShape))
    UndoManager.clearHistory()

    TessellationOperations.initializeWithIrregularIfEmpty()

    val tiling = EditorState.currentTiling.now()
    assert(!tiling.isEmpty)
    assertEquals(UndoManager.undoCount.now(), 1)
    assert(tiling.innerFaces.nonEmpty)
    assert(tiling.innerFaces.forall(face =>
      EditorState.polygonColors.now().get(face.id).contains(fill)
    ))
  }

  test("attemptPolygonAddition should show error when tiling is empty") {
    EditorState.currentTiling.set(io.github.scala_tessella.dcel.TilingDCEL.empty)
    EditorState.selectedPolygon.set(Some(4))
    EditorState.errorMessage.set(None)

    TessellationOperations.attemptPolygonAddition("edge-1", 0)

    assert(EditorState.errorMessage.now().exists(_.contains("No tiling available to grow")))
  }

  test("attemptFanning should replicate colors across fan copies") {
    val tiling        = TilingBuilders.freshSquare()
    val faceIds       = tiling.innerFaces.map(_.id)
    val originalColor = ColorRGB(200, 100, 50)
    val fillColor     = ColorRGB(1, 2, 3)
    val vertexId      = tiling.boundaryVertices.head.id

    EditorState.currentTiling.set(tiling)
    EditorState.fillColor.set(fillColor)
    EditorState.polygonColors.set(faceIds.map(_ -> originalColor).toMap)

    val done = Promise[Unit]()

    TessellationOperations.attemptFanning(vertexId)

    setTimeout(200) {
      val newFaceIds = EditorState.currentTiling.now().innerFaces.map(_.id)
      val colors     = EditorState.polygonColors.now()

      assert(newFaceIds.size > faceIds.size)
      assert(newFaceIds.forall(id => colors.get(id).contains(originalColor)))
      done.success(())
    }: Unit

    done.future
  }
