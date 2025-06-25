package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.operations.ErrorOperations.clearError

import com.raquo.laminar.api.L.{*, given}

object ErrorMessageComponent:
  def element: Element =
    div(
      className := "error-container",
      child.maybe <-- EditorState.errorMessage.signal.map(_.map(message =>
        div(
          className := "error-message",
          span(className := "error-icon", "⚠️"),
          span(className := "error-text", message),
          button(
            className := "error-close",
            "×",
            onClick --> { _ => clearError() } // This now clears both error and wireframe
          )
        )
      ))
    )