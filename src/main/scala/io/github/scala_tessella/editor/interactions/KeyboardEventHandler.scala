package io.github.scala_tessella.editor.interactions

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom.KeyboardEvent
import io.github.scala_tessella.editor.models.{AppState, EditorState}
//import io.github.scala_tessella.editor.operations.SelectionOperations
import scala.math.{max, min}

object KeyboardHandler:
  def keyboardEventHandlers: List[Modifier[?]] = List(
    onKeyDown --> handleKeyDown
  )

  def handleKeyDown(event: KeyboardEvent): Unit =
    event.key match
      case "r" | "R" => EditorState.viewTransform.update(t => t.withRotation(t.rotationDegrees + 15))
      case "e" | "E" => EditorState.viewTransform.update(t => t.withRotation(t.rotationDegrees - 15))
      case "z" if event.ctrlKey => event.preventDefault(); AppState.undo()
      case "+" | "=" => EditorState.viewTransform.update(t => t.copy(scale = min(t.scale * 1.1, 5.0)))
      case "-" | "_" => EditorState.viewTransform.update(t => t.copy(scale = max(t.scale / 1.1, 0.1)))
      case "Escape" => AppState.clearAllSelections()
      case "Delete" | "Backspace" => ???
      case _ => ()