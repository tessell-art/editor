package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.ValidationError
import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.{ColorRGB, TilingBuilders, UndoManager}
import munit.FunSuite

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise

class OperationRunnerSpec extends FunSuite with EditorStateFixture:

  test("runTilingOp should not save undo when tiling is unchanged") {
    val tiling = TilingBuilders.freshSquare()
    EditorState.currentTiling.set(tiling)
    UndoManager.clearHistory()

    val done = Promise[Unit]()
    OperationRunner.runTilingOp(() => Right(tiling))(
      onSuccess = done.success(()),
      onFailure = err => done.failure(new RuntimeException(err.message))
    )

    done.future.map { _ =>
      assertEquals(UndoManager.undoCount.now(), 0)
      assertEquals(EditorState.currentTiling.now(), tiling)
    }
  }

  test("runTilingOp should save undo and assign colors when tiling changes") {
    val newTiling = TilingBuilders.freshSquare()
    val fill      = ColorRGB(9, 8, 7)
    EditorState.fillColor.set(fill)
    EditorState.currentTiling.set(TilingBuilders.freshTriangle())
    UndoManager.clearHistory()

    val done = Promise[Unit]()
    OperationRunner.runTilingOp(() => Right(newTiling))(
      onSuccess = done.success(()),
      onFailure = err => done.failure(new RuntimeException(err.message))
    )

    done.future.map { _ =>
      val colors = EditorState.polygonColors.now()
      val ids    = newTiling.innerFaces.map(_.id)
      assertEquals(UndoManager.undoCount.now(), 1)
      assertEquals(EditorState.currentTiling.now(), newTiling)
      assert(ids.nonEmpty)
      assert(ids.forall(id => colors.get(id).contains(fill)))
    }
  }

  test("runTilingOp should not change tiling or undo on failure") {
    val tiling = TilingBuilders.freshSquare()
    EditorState.currentTiling.set(tiling)
    UndoManager.clearHistory()

    val done = Promise[Unit]()
    OperationRunner.runTilingOp(() => Left(ValidationError("boom")))(
      onSuccess = done.success(()),
      onFailure = _ => done.success(())
    )

    done.future.map { _ =>
      assertEquals(UndoManager.undoCount.now(), 0)
      assertEquals(EditorState.currentTiling.now(), tiling)
    }
  }
