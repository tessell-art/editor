package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{AppState, EditorState}
import io.github.scala_tessella.editor.utils.UndoManager

import com.raquo.laminar.api.L.{*, given}

object UndoComponent:
  def element: Element =
    div(
      className := "undo-controls",
      button(
        className := "undo-button",
        disabled <-- UndoManager.canUndo.signal.map(!_).combineWith(EditorState.isProcessing.signal).map(_ || _),
        title <-- UndoManager.canUndo.signal.map(isUndoable => if (isUndoable) UndoManager.getUndoPreview.getOrElse("Undo") else "No actions to undo"),
        onClick --> { _ => AppState.undo() },
        span(className := "icon", "↶"), // Undo arrow icon
        span(className := "button-text", " "),
        span(
          className := "button-text",
          child.text <-- UndoManager.undoCount.signal.map { count =>
            if count > 0 then s"Undo ($count)" else "Undo"
          }
        )
      ),
      button(
        className := "redo-button",
        disabled <-- UndoManager.canRedo.signal.map(!_).combineWith(EditorState.isProcessing.signal).map(_ || _),
        title <-- UndoManager.canRedo.signal.map(isRedoable => if (isRedoable) UndoManager.getRedoPreview.getOrElse("Redo") else "No actions to redo"),
        onClick --> { _ => AppState.redo() },
        span(className := "icon", "↷"), // Redo arrow icon
        span(className := "button-text", " "),
        span(
          className := "button-text",
          child.text <-- UndoManager.redoCount.signal.map { count =>
            if count > 0 then s"Redo ($count)" else "Redo"
          }
        )
      )
    )