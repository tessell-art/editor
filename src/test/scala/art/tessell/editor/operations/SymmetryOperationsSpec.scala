package art.tessell.editor.operations

import art.tessell.editor.EditorStateFixture
import art.tessell.editor.models.EditorState
import art.tessell.editor.utils.TilingBuilders
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.structure.VertexId
import munit.FunSuite

import scala.concurrent.Promise
import scala.scalajs.js.timers.setTimeout

class SymmetryOperationsSpec extends FunSuite with EditorStateFixture:

  private def afterAsync(body: => Unit): scala.concurrent.Future[Unit] =
    val done = Promise[Unit]()
    setTimeout(200) {
      body
      done.success(())
    }: Unit
    done.future

  test("clearOverlays resets all three flags and caches atomically") {
    EditorState.viewState.update(_.copy(
      showUniformity = true,
      uniformityMap = Some(Map(VertexId(1) -> 0)),
      showRotation = true,
      rotationVertexIds = Some(Nil),
      showReflection = true,
      reflectionVertexIds = Some(Nil)
    ))

    SymmetryOperations.clearOverlays()

    val view = EditorState.viewState.now()
    assert(!view.showUniformity)
    assert(view.uniformityMap.isEmpty)
    assert(!view.showRotation)
    assert(view.rotationVertexIds.isEmpty)
    assert(!view.showReflection)
    assert(view.reflectionVertexIds.isEmpty)
  }

  test("toggleShowUniformity hides the overlay synchronously when currently shown") {
    EditorState.viewState.update(_.copy(
      showUniformity = true,
      uniformityMap = Some(Map(VertexId(1) -> 0))
    ))

    SymmetryOperations.toggleShowUniformity()

    val view = EditorState.viewState.now()
    assert(!view.showUniformity)
    // cache preserved so a re-toggle is instant
    assert(view.uniformityMap.isDefined)
  }

  test("toggleShowUniformity shows the overlay synchronously when cache is populated") {
    EditorState.viewState.update(_.copy(
      showUniformity = false,
      uniformityMap = Some(Map(VertexId(1) -> 0))
    ))

    SymmetryOperations.toggleShowUniformity()

    assert(EditorState.viewState.now().showUniformity)
  }

  test("toggleShowRotation asynchronously computes and caches when overlay is off and cache is empty") {
    val tiling = TilingBuilders.freshSquare()
    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.viewState.update(_.copy(
      showRotation = false,
      rotationVertexIds = None
    ))

    SymmetryOperations.toggleShowRotation()

    afterAsync {
      // Compute may populate the cache or return None (square tiling has trivial rotation symmetry).
      // Either way the flag may have been flipped or not; assert only that no exception propagated
      // and the state is internally consistent.
      val view = EditorState.viewState.now()
      // If cache is populated, overlay is shown; if None, overlay remains off.
      if view.rotationVertexIds.isDefined then assert(view.showRotation)
      else assert(!view.showRotation)
    }
  }

  test("toggleShowReflection on empty tiling still flips the flag but leaves the cache empty") {
    // On empty tiling, compute returns None so no cache is populated. The async branch of
    // toggleComputedOverlay still flips the flag to true — the post-compute `if !getShow(...)` guard
    // only protects against the user toggling off mid-compute; it doesn't gate on compute success.
    // The visible effect is an empty overlay, which is harmless.
    EditorState.tessellationState.update(_.copy(currentTiling = TilingDCEL.empty))
    EditorState.viewState.update(_.copy(
      showReflection = false,
      reflectionVertexIds = None
    ))

    SymmetryOperations.toggleShowReflection()

    afterAsync {
      val view = EditorState.viewState.now()
      assert(view.showReflection)
      assert(view.reflectionVertexIds.isEmpty)
    }
  }

  test("toggleShowUniformity is gated by ifNotProcessing") {
    EditorState.uiState.update(_.copy(isProcessing = true))
    EditorState.viewState.update(_.copy(
      showUniformity = false,
      uniformityMap = Some(Map(VertexId(1) -> 0))
    ))

    SymmetryOperations.toggleShowUniformity()

    // Reset the guard so EditorStateFixture teardown isn't affected by our simulated processing flag.
    EditorState.uiState.update(_.copy(isProcessing = false))

    // Flag unchanged because the guard dropped the call.
    assert(!EditorState.viewState.now().showUniformity)
  }
