package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.models.EditorState
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js

object AsyncUtils:
  
  private def delay(milliseconds: Int): Future[Unit] = {
    val p = Promise[Unit]()
    js.timers.setTimeout(milliseconds) {
      p.success(())
    }
    p.future
  }

  def withLoadingState[T](operation: () => T): Future[T] = {
    EditorState.isProcessing.set(true)

    delay(50).map { _ =>
      try {
        operation()
      } finally {
        EditorState.isProcessing.set(false)
      }
    }
  }