package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.editor.models.AppState
import io.github.scala_tessella.editor.utils.UndoManager

object UndoComponent:
  def element: Element =
    div(
      className := "undo-controls",
      button(
        className := "undo-button",
        disabled <-- UndoManager.canUndo.signal.map(!_).combineWith(AppState.isProcessing.signal).map(_ || _),
        title := UndoManager.getUndoPreview.getOrElse("No actions to undo"),
        onClick --> { _ => AppState.undo() },
        span(className := "icon", "↶"), // Undo arrow icon
        span(
          className := "button-text",
          child.text <-- UndoManager.undoCount.signal.map { count =>
            if count > 0 then s"Undo ($count)" else "Undo"
          }
        )
      )
    )