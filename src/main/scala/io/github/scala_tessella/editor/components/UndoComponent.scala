package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L._
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.operations.UndoManager

object UndoComponent:

  // Tooltip mappers — when the action is available, show the preview (or the bare verb if no preview);
  // otherwise show the disabled-state explanation.
  private[components] def undoTitle(canUndo: Boolean, preview: Option[String]): String =
    if canUndo then preview.getOrElse("Undo") else "No actions to undo"

  private[components] def redoTitle(canRedo: Boolean, preview: Option[String]): String =
    if canRedo then preview.getOrElse("Redo") else "No actions to redo"

  def element: Element =
    div(
      className := "undo-controls",
      button(
        className := "undo-button",
        disabled <-- UndoManager.canUndo.signal.map(
          !_
        ).combineWith(EditorState.uiState.signal.map(_.isProcessing).distinct).map(_ || _),
        title <-- UndoManager.canUndo.signal.map(canUndo => undoTitle(canUndo, UndoManager.getUndoPreview)),
        onClick.compose(
          _.withCurrentValueOf(EditorState.uiState.signal.map(_.isProcessing).distinct).map(_._2)
        ) --> AppState.undoObserver,
        span(className := "icon", "↶")
//        span(className := "button-text", " "),
//        span(
//          className := "button-text",
//          child.text <-- UndoManager.undoCount.signal.map { count =>
//            if count > 0 then s"Undo ($count)" else "Undo"
//          }
//        )
      ),
      button(
        className := "redo-button",
        disabled <-- UndoManager.canRedo.signal.map(
          !_
        ).combineWith(EditorState.uiState.signal.map(_.isProcessing).distinct).map(_ || _),
        title <-- UndoManager.canRedo.signal.map(canRedo => redoTitle(canRedo, UndoManager.getRedoPreview)),
        onClick.compose(
          _.withCurrentValueOf(EditorState.uiState.signal.map(_.isProcessing).distinct).map(_._2)
        ) --> AppState.redoObserver,
        span(className := "icon", "↷") // Redo arrow icon
//        span(className := "button-text", " "),
//        span(
//          className := "button-text",
//          child.text <-- UndoManager.redoCount.signal.map { count =>
//            if count > 0 then s"Redo ($count)" else "Redo"
//          }
//        )
      )
    )
