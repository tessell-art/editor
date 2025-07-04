package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.EditorState

import com.raquo.laminar.api.L.{*, given}

object LoadingIndicatorComponent:
  def element: Element =
    div(
      className := "loading-overlay",
      display <-- EditorState.isProcessing.signal.map(loading => if loading then "flex" else "none"),
      div(
        className := "loading-content",
        div(
          className := "loading-spinner",
          // CSS-based spinner animation
        ),
        div(
          className := "loading-message",
//          child.text <-- AppState.loadingMessage.signal.map(_.getOrElse("Processing..."))
        )
      )
    )