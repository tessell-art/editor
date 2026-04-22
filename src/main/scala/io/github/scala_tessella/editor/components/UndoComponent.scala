package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L._
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.operations.UndoManager

object UndoComponent:
  def element: Element =
    div(
      className := "undo-controls",
      button(
        className := "undo-button",
        disabled <-- UndoManager.canUndo.signal.map(
          !_
        ).combineWith(EditorState.uiState.signal.map(_.isProcessing).distinct).map(_ || _),
        title <-- UndoManager.canUndo.signal.map(isUndoable =>
          if isUndoable then UndoManager.getUndoPreview.getOrElse("Undo") else "No actions to undo"
        ),
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
        title <-- UndoManager.canRedo.signal.map(isRedoable =>
          if (isRedoable) UndoManager.getRedoPreview.getOrElse("Redo") else "No actions to redo"
        ),
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
