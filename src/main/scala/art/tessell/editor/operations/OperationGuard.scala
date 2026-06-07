package art.tessell.editor.operations

import art.tessell.editor.models.EditorState
import com.raquo.airstream.core.EventStream

object OperationGuard:
  def ifNotProcessing(isProcessing: Boolean)(op: => Unit): Unit =
    if !isProcessing then op

  def ifNotProcessing(op: => Unit): Unit =
    ifNotProcessing(EditorState.uiState.now().isProcessing)(op)

  // Gate any event stream by the processing flag: drop events while processing = true
  def gate[A](stream: EventStream[A]): EventStream[A] =
    stream
      .withCurrentValueOf(EditorState.uiState.signal.map(_.isProcessing).distinct)
      .collect { case (a, false) =>
        a
      }
