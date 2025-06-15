package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.models.{EditorState, FailedPolygonPlacement, FailedPolygonDeletion}
import org.scalajs.dom
import scala.scalajs.js
import scala.util.Try

object ErrorOperations:

  def showError(message: String, placement: Option[FailedPolygonPlacement] = None, deletion: Option[FailedPolygonDeletion] = None): Unit =
    EditorState.errorMessage.set(Some(message))
    EditorState.failedPlacement.set(placement)
    EditorState.failedDeletion.set(deletion)

    Try {
      if (js.typeOf(js.Dynamic.global.window) != "undefined") {
        dom.window.setTimeout(() => {
          EditorState.errorMessage.set(None)
          EditorState.failedPlacement.set(None)
          EditorState.failedDeletion.set(None)
        }, 3000)
      }
    }.recover {
      case _ => // Ignore errors in test environment
    }

  def clearError(): Unit =
    EditorState.errorMessage.set(None)
    EditorState.failedPlacement.set(None)
    EditorState.failedDeletion.set(None)