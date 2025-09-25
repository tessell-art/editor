package io.github.scala_tessella.editor.operations

import com.raquo.airstream.core.EventStream
import io.github.scala_tessella.editor.models.EditorState

object OperationGuard:
  def ifNotProcessing(op: => Unit): Unit =
    if !EditorState.isProcessing.now() then op

  // Gate any event stream by the processing flag: drop events while processing = true
  def gate[A](stream: EventStream[A]): EventStream[A] =
    stream
      .withCurrentValueOf(EditorState.isProcessing.signal)
      .collect { case (a, false) =>
        a
      }
