package io.github.scala_tessella.editor

import models.AppState
import components.{PolygonPaletteComponent, EditorCanvasComponent}
import interactions.KeyboardEventHandler

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom

@main
def Editor(): Unit =
  renderOnDomContentLoaded(
    dom.document.getElementById("app"),
    EditorApp.element
  )

object EditorApp:
  def element: Element =
    div(
      h1("Polygon Shape Editor"),
      div(
        className := "editor-layout",
        PolygonPaletteComponent.element,
        EditorCanvasComponent.element
      ),
      // Global keyboard event handlers
      KeyboardEventHandler.keyboardEventHandlers
    )