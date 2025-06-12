package io.github.scala_tessella.editor

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import io.github.scala_tessella.editor.models.AppState
import io.github.scala_tessella.editor.components.{PolygonPaletteComponent, EditorCanvasComponent}

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
      )
    )