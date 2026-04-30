package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.ValidationError
import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.operations.UndoManager
import io.github.scala_tessella.editor.utils.{ColorRGB, TilingBuilders}
import munit.FunSuite

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise

class OperationRunnerSpec extends FunSuite with EditorStateFixture:

  test("runTilingOp should not save undo when tiling is unchanged") {
    val tiling = TilingBuilders.freshSquare()
    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    UndoManager.clearHistory()

    val done = Promise[Unit]()
    OperationRunner.runTilingOp(() => Right(tiling))(
      onSuccess = done.success(()),
      onFailure = err => done.failure(new RuntimeException(err.message))
    )

    done.future.map { _ =>

      assertEquals(UndoManager.undoCount.now(), 0)
      assertEquals(EditorState.tessellationState.now().currentTiling, tiling)
    }
  }

  test("runTilingOp should save undo and assign colors when tiling changes") {
    val newTiling = TilingBuilders.freshSquare()
    val fill      = ColorRGB(9, 8, 7)
    EditorState.colorState.update(_.copy(fillColor = fill))
    EditorState.tessellationState.update(_.copy(currentTiling = TilingBuilders.freshTriangle()))
    UndoManager.clearHistory()

    val done = Promise[Unit]()
    OperationRunner.runTilingOp(() => Right(newTiling))(
      onSuccess = done.success(()),
      onFailure = err => done.failure(new RuntimeException(err.message))
    )

    done.future.map { _ =>

      val colors = EditorState.colorState.now().polygonColors
      val ids    = newTiling.innerFaces.map(_.id)
      assertEquals(UndoManager.undoCount.now(), 1)
      assertEquals(EditorState.tessellationState.now().currentTiling, newTiling)
      assert(ids.nonEmpty)
      assert(ids.forall(id => colors.get(id).contains(fill)))
    }
  }

  test("runTilingOp should not change tiling or undo on failure") {
    val tiling = TilingBuilders.freshSquare()
    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    UndoManager.clearHistory()

    val done = Promise[Unit]()
    OperationRunner.runTilingOp(() => Left(ValidationError("boom")))(
      onSuccess = done.success(()),
      onFailure = _ => done.success(())
    )

    done.future.map { _ =>

      assertEquals(UndoManager.undoCount.now(), 0)
      assertEquals(EditorState.tessellationState.now().currentTiling, tiling)
    }
  }

  // ---------- safely ----------

  test("safely passes a Right through unchanged") {
    val result = OperationRunner.safely("ctx")(Right(42))
    assertEquals(result, Right(42))
  }

  test("safely passes a Left through unchanged (does not re-wrap an existing TilingError)") {
    val original = Left(ValidationError("upstream failure"))
    val result   = OperationRunner.safely[Int]("ctx")(original)
    assertEquals(result, original)
  }

  test("safely converts a thrown exception into Left(ValidationError) with the context prefix") {
    val result = OperationRunner.safely[Int]("Error doubling")(throw RuntimeException("kapow"))
    result match
      case Left(ValidationError(msg)) =>
        assertEquals(msg, "Error doubling: kapow")
      case other                      =>
        fail(s"expected Left(ValidationError(...)), got $other")
  }

  test("safely lets fatal throwables propagate (only NonFatal is caught)") {
    // `Try` catches only `NonFatal` — `InterruptedException`, `VirtualMachineError`, `ThreadDeath`
    // and `LinkageError` are excluded and propagate. The original sites' `case e: Exception`
    // would have swallowed `InterruptedException` (it extends Exception); this test locks in the
    // fix. Use plain try/catch rather than `intercept[T]` because munit's intercept routes fatal
    // throwables back as test failures rather than intercepted results.
    var propagated = false
    try OperationRunner.safely[Int]("ctx")(throw new InterruptedException("synthetic"))
    catch case _: InterruptedException => propagated = true
    assert(propagated, "InterruptedException should have propagated through safely, not been wrapped")
  }

  test("runTilingOp should drop colors for faces not present in new tiling") {
    val oldTiling = TilingBuilders.freshSquare()
    val newTiling = TilingBuilders.freshTriangle()
    val fill      = ColorRGB(9, 8, 7)
    val newFaces  = newTiling.innerFaces.map(_.id).toSet
    val staleId   = io.github.scala_tessella.dcel.structure.FaceId(9999)

    EditorState.tessellationState.update(_.copy(currentTiling = oldTiling))
    EditorState.colorState.update(_.copy(fillColor = fill))
    EditorState.colorState.update(_.copy(polygonColors = Map(staleId -> ColorRGB(1, 2, 3))))
    UndoManager.clearHistory()

    val done = Promise[Unit]()
    OperationRunner.runTilingOp(() => Right(newTiling))(
      onSuccess = done.success(()),
      onFailure = err => done.failure(new RuntimeException(err.message))
    )

    done.future.map { _ =>

      val colors = EditorState.colorState.now().polygonColors
      assert(newFaces.nonEmpty)
      assertEquals(colors.keySet, newFaces)
      assert(newFaces.forall(id => colors.get(id).contains(fill)))
      assert(!colors.contains(staleId))
    }
  }
