package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.editor.models.AppState

object ErrorMessage:
  def element: Element =
    div(
      className := "error-container",
      child.maybe <-- AppState.errorMessage.signal.map(_.map(message =>
        div(
          className := "error-message",
          span(className := "error-icon", "⚠️"),
          span(className := "error-text", message),
          button(
            className := "error-close",
            "×",
            onClick --> { _ => AppState.clearError() }
          )
        )
      ))
    )