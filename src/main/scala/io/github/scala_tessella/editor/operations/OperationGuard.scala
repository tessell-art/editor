package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.models.EditorState

object OperationGuard:
  def ifNotProcessing(op: => Unit): Unit =
    if !EditorState.isProcessing.now() then op
