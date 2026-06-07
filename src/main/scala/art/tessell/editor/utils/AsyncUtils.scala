package art.tessell.editor.utils

import art.tessell.editor.models.EditorState
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.scalajs.js

object AsyncUtils:

  private def delay(milliseconds: Int): Future[Unit] =
    val p = Promise[Unit]()
    js.timers.setTimeout(milliseconds) {
      p.success(())
    }: Unit
    p.future

  def withLoadingState[T](operation: () => T, message: Option[String] = None): Future[T] =
    EditorState.uiState.update(_.copy(isProcessing = true))
    EditorState.uiState.update(_.copy(loadingMessage = message))

    delay(50).map: _ =>
      try
        operation()
      finally
        EditorState.uiState.update(_.copy(isProcessing = false))
        EditorState.uiState.update(_.copy(loadingMessage = None))

  def setLoadingMessage(message: String): Unit =
    EditorState.uiState.update(_.copy(loadingMessage = Some(message)))
