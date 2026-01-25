package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.models.EditorState

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
    EditorState.isProcessing.set(true)
    EditorState.loadingMessage.set(message)

    delay(50).map: _ =>
      try
        operation()
      finally
        EditorState.isProcessing.set(false)
        EditorState.loadingMessage.set(None)

  def setLoadingMessage(message: String): Unit =
    EditorState.loadingMessage.set(Some(message))
