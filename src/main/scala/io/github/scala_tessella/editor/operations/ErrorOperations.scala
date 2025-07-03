package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.models.{EditorState, FailedPolygonPlacement, FailedPolygonDeletion}
import org.scalajs.dom
import scala.scalajs.js
import scala.util.Try

object ErrorOperations:

  private var messageTimeoutId: Option[Int] = None

  def showError(message: String, placement: Option[FailedPolygonPlacement] = None, deletion: Option[FailedPolygonDeletion] = None): Unit =
    // Cancel any existing timeout for the error message
    messageTimeoutId.foreach(id => dom.window.clearTimeout(id))

    EditorState.errorMessage.set(Some(message))
    EditorState.failedPlacement.set(placement)
    EditorState.failedDeletion.set(deletion)

    Try {
      if (js.typeOf(js.Dynamic.global.window) != "undefined") {
        // Timeout for the error message (10 seconds)
        val newTimeoutId = dom.window.setTimeout(() => {
          EditorState.errorMessage.set(None)
          messageTimeoutId = None
        }, 10000)
        messageTimeoutId = Some(newTimeoutId)

        // Timeout for the visual feedback (3 seconds)
        dom.window.setTimeout(() => {
          EditorState.failedPlacement.set(None)
          EditorState.failedDeletion.set(None)
        }, 3000)
      }
    }.recover {
      case _ => // Ignore errors in test environment
    }

  def clearError(): Unit =
    messageTimeoutId.foreach(id => dom.window.clearTimeout(id))
    messageTimeoutId = None
    EditorState.errorMessage.set(None)
    EditorState.failedPlacement.set(None)
    EditorState.failedDeletion.set(None)