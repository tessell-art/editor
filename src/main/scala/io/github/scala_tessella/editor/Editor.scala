package io.github.scala_tessella.editor

import models.EditorState
import components.{ColorPickerPopupComponent, EditorCanvasComponent, MenuBarComponent, PolygonPaletteComponent}
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
//      h1("Polygon Shape Editor"),
      // Add the Menu Bar at the top
      MenuBarComponent.element,
      div(
        className := "editor-layout",
        PolygonPaletteComponent.element,
        EditorCanvasComponent.element
      ),
      // Global keyboard event handlers
      KeyboardEventHandler.keyboardEventHandlers,
      // Render the Color Picker Popup at the top level, controlled by shared state
      ColorPickerPopupComponent.element(EditorState.showColorPicker, EditorState.tempColor)
  )