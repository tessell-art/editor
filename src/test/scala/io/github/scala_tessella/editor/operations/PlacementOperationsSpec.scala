package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.{EditorState, IrregularState}
import io.github.scala_tessella.editor.utils.{ColorRGB, Logger, TilingBuilders}
import munit.FunSuite

import scala.concurrent.Promise
import scala.scalajs.js.timers.setTimeout

class PlacementOperationsSpec extends FunSuite with EditorStateFixture:

  private def afterAsync(body: => Unit): scala.concurrent.Future[Unit] =
    val done = Promise[Unit]()
    setTimeout(200) {
      body
      done.success(())
    }: Unit
    done.future

  // Silences Logger around a block. Used for the two tests that deliberately trigger the
  // "should not happen" branches in PlacementOperations.resolvePolygonPlacementKind — the
  // emitted WARN/ERROR lines are intentional coverage, not a real error, so we keep them out
  // of the CI log.
  private def silenced(body: => Unit): Unit =
    val prev = Logger.getLevel
    Logger.setLevel(Logger.Level.Off)
    try body
    finally Logger.setLevel(prev)

  test("attemptPolygonAddition successfully grows the tiling on a valid perimeter edge") {
    val tiling     = TilingBuilders.freshSquare()
    val faceCount0 = tiling.innerFaces.size
    val fill       = ColorRGB(40, 60, 80)

    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.toolState.update(_.copy(selectedPolygon = Some(4)))
    EditorState.irregularState.update(_.copy(isIrregularSelected = false))
    EditorState.colorState.update(_.copy(fillColor = fill))
    EditorState.errorState.update(_.copy(errorMessage = None))
    UndoManager.clearHistory()

    PlacementOperations.attemptPolygonAddition("edge-0", 0)

    afterAsync {
      val newTiling = EditorState.tessellationState.now().currentTiling
      assert(newTiling.innerFaces.size > faceCount0)
      assertEquals(EditorState.errorState.now().errorMessage, None)
      assertEquals(UndoManager.undoCount.now(), 1)

      // The newly added face should have inherited the current fill color.
      val colors = EditorState.colorState.now().polygonColors
      assert(newTiling.innerFaces.forall(face => colors.get(face.id).contains(fill)))
    }
  }

  test("attemptPolygonAddition with edgeIndex out of range surfaces 'Invalid edge index'") {
    val tiling = TilingBuilders.freshSquare()
    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.toolState.update(_.copy(selectedPolygon = Some(3)))
    EditorState.irregularState.update(_.copy(isIrregularSelected = false))
    EditorState.errorState.update(_.copy(errorMessage = None))
    UndoManager.clearHistory()

    PlacementOperations.attemptPolygonAddition("edge-bogus", edgeIndex = 999)

    afterAsync {
      assertEquals(EditorState.tessellationState.now().currentTiling, tiling)
      assertEquals(UndoManager.undoCount.now(), 0)
      assert(EditorState.errorState.now().errorMessage.exists(_.contains("Invalid edge index")))
    }
  }

  test("attemptPolygonAddition with no polygon selection is a silent no-op (no error, no growth)") {
    val tiling = TilingBuilders.freshSquare()
    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.toolState.update(_.copy(selectedPolygon = None))
    EditorState.irregularState.update(_.copy(isIrregularSelected = false))
    EditorState.errorState.update(_.copy(errorMessage = None))
    UndoManager.clearHistory()

    // Emits a WARN ("Both regular polygon and irregular polygon unselected") by design.
    silenced(PlacementOperations.attemptPolygonAddition("edge-0", 0))

    afterAsync {
      assertEquals(EditorState.tessellationState.now().currentTiling, tiling)
      assertEquals(UndoManager.undoCount.now(), 0)
      assertEquals(EditorState.errorState.now().errorMessage, None)
    }
  }

  test("attemptPolygonAddition with both regular and irregular selected is a silent no-op") {
    val tiling = TilingBuilders.freshSquare()
    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.toolState.update(_.copy(selectedPolygon = Some(4)))
    EditorState.irregularState.update(_.copy(
      isIrregularSelected = true,
      recentIrregularPolygon = Some(IrregularState.initialShape)
    ))
    EditorState.errorState.update(_.copy(errorMessage = None))
    UndoManager.clearHistory()

    // Emits an ERROR ("Should not happen: both regular polygon and irregular polygon selected")
    // by design — we want coverage on this defensive branch even though the UI should prevent it.
    silenced(PlacementOperations.attemptPolygonAddition("edge-0", 0))

    afterAsync {
      assertEquals(EditorState.tessellationState.now().currentTiling, tiling)
      assertEquals(UndoManager.undoCount.now(), 0)
      assertEquals(EditorState.errorState.now().errorMessage, None)
    }
  }

  test("attemptPolygonInsertion on empty tiling shows 'No tiling available for insertion'") {
    EditorState.tessellationState.update(_.copy(currentTiling =
      io.github.scala_tessella.dcel.TilingDCEL.empty
    ))
    EditorState.toolState.update(_.copy(selectedPolygon = Some(4)))
    EditorState.irregularState.update(_.copy(isIrregularSelected = false))
    EditorState.errorState.update(_.copy(errorMessage = None))

    PlacementOperations.attemptPolygonInsertion(
      io.github.scala_tessella.dcel.structure.VertexId(1),
      io.github.scala_tessella.dcel.structure.VertexId(2)
    )

    assert(EditorState.errorState.now().errorMessage.exists(_.contains("No tiling available for insertion")))
  }
