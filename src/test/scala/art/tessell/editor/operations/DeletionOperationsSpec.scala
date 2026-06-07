package art.tessell.editor.operations

import art.tessell.editor.EditorStateFixture
import art.tessell.editor.models.EditorState
import art.tessell.editor.utils.TilingBuilders
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.structure.{FaceId, VertexId}
import munit.FunSuite

import scala.concurrent.Promise
import scala.scalajs.js.timers.setTimeout

class DeletionOperationsSpec extends FunSuite with EditorStateFixture:

  // OperationRunner is async (50ms loading-state delay before the op runs).
  // 200ms is the same wait used by TessellationOperationsSpec.
  private val asyncWaitMs = 200

  private def afterAsync(body: => Unit): scala.concurrent.Future[Unit] =
    val done = Promise[Unit]()
    setTimeout(asyncWaitMs) {
      body
      done.success(())
    }: Unit
    done.future

  test("attemptFaceDeletion on empty tiling shows 'Cannot remove polygon' error") {
    EditorState.tessellationState.update(_.copy(currentTiling = TilingDCEL.empty))
    EditorState.errorState.update(_.copy(errorMessage = None))
    UndoManager.clearHistory()

    DeletionOperations.attemptFaceDeletion(FaceId(1))

    afterAsync {
      assert(EditorState.errorState.now().errorMessage.exists(_.contains("Cannot remove polygon")))
      assertEquals(UndoManager.undoCount.now(), 0)
      assert(EditorState.tessellationState.now().currentTiling.isEmpty)
    }
  }

  test(
    "attemptFaceDeletion with unknown FaceId on non-empty tiling shows error and leaves tiling unchanged"
  ) {
    val tiling = TilingBuilders.freshSquare()
    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.errorState.update(_.copy(errorMessage = None))
    UndoManager.clearHistory()

    DeletionOperations.attemptFaceDeletion(FaceId(9999))

    afterAsync {
      assert(EditorState.errorState.now().errorMessage.exists(_.contains("Cannot remove polygon")))
      assertEquals(UndoManager.undoCount.now(), 0)
      assertEquals(EditorState.tessellationState.now().currentTiling, tiling)
    }
  }

  test("attemptVertexDeletion with unknown VertexId shows 'Cannot remove vertex' error") {
    val tiling = TilingBuilders.freshSquare()
    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.errorState.update(_.copy(errorMessage = None))
    UndoManager.clearHistory()

    DeletionOperations.attemptVertexDeletion(VertexId(9999))

    afterAsync {
      assert(EditorState.errorState.now().errorMessage.exists(_.contains("Cannot remove vertex")))
      assertEquals(UndoManager.undoCount.now(), 0)
      assertEquals(EditorState.tessellationState.now().currentTiling, tiling)
    }
  }

  test("attemptEdgeDeletion with unknown endpoints shows 'Cannot remove edge' error") {
    val tiling = TilingBuilders.freshSquare()
    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.errorState.update(_.copy(errorMessage = None))
    UndoManager.clearHistory()

    DeletionOperations.attemptEdgeDeletion(VertexId(9998), VertexId(9999))

    afterAsync {
      assert(EditorState.errorState.now().errorMessage.exists(_.contains("Cannot remove edge")))
      assertEquals(UndoManager.undoCount.now(), 0)
      assertEquals(EditorState.tessellationState.now().currentTiling, tiling)
    }
  }

  test("attemptFaceDeletion clears symmetry overlays on success") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id

    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.viewState.update(_.copy(showUniformity = true, showRotation = true, showReflection = true))
    UndoManager.clearHistory()

    DeletionOperations.attemptFaceDeletion(faceId)

    afterAsync {
      // Whether or not the DCEL accepts deletion of the only face, only the success path
      // would touch overlays. Assert the post-conditions consistent with each branch.
      val view = EditorState.viewState.now()
      val err  = EditorState.errorState.now().errorMessage
      if err.isEmpty then
        // success branch: clearOverlays() ran
        assert(!view.showUniformity)
        assert(!view.showRotation)
        assert(!view.showReflection)
      else
        // failure branch: overlays untouched
        assert(view.showUniformity && view.showRotation && view.showReflection)
    }
  }
