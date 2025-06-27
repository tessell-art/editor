package io.github.scala_tessella.editor.interactions

import io.github.scala_tessella.editor.models.{AppState, EditorState}
import io.github.scala_tessella.editor.operations.SelectionOperations.clearAllSelections
import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.editor.operations.ViewOperations
import org.scalajs.dom
import org.scalajs.dom.KeyboardEvent

import scala.math.{max, min}

object KeyboardEventHandler:
  def keyboardEventHandlers: Binder[HtmlElement] =
    windowEvents(_.onKeyDown)
      .filter(_ => !EditorState.isProcessing.now())
      --> handleKeyDown

  def handleKeyDown(event: KeyboardEvent): Unit =
    val targetIsInput = Option(event.target).exists {
      case _: dom.html.Input => true
      case _: dom.html.TextArea => true
      case _: dom.html.Select => true
      case el: dom.html.Element if el.isContentEditable => true
      case _ => false
    }

    if !targetIsInput then
      event.key match
        case "r" | "R" =>
          event.preventDefault()
          ViewOperations.rotateView(+15)
        case "e" | "E" =>
          event.preventDefault()
          ViewOperations.rotateView(-15)
        case "Z" if event.ctrlKey && event.shiftKey =>
          event.preventDefault()
          AppState.redo()
        case "z" if event.ctrlKey =>
          event.preventDefault()
          AppState.undo()
        case "+" | "=" =>
          event.preventDefault()
          EditorState.viewTransform.update(t => t.copy(scale = min(t.scale * 1.1, 5.0)))
        case "-" | "_" =>
          event.preventDefault()
          EditorState.viewTransform.update(t => t.copy(scale = max(t.scale / 1.1, 0.1)))
        case "Escape" =>
          event.preventDefault()
          clearAllSelections()
        case "Delete" | "Backspace" =>
          event.preventDefault()
          // Future deletion logic can be added here
          ()
        case _ => ()