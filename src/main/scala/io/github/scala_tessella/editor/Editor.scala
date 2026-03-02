package io.github.scala_tessella.editor

import com.raquo.laminar.api.L._
import io.github.scala_tessella.editor.components.{
  ColorPickerPopupComponent,
  EditorCanvasComponent,
  MenuBarComponent,
  PolygonPaletteComponent
}
import io.github.scala_tessella.editor.interactions.KeyboardEventHandler
import io.github.scala_tessella.editor.models.{EditorState, Theme}
import io.github.scala_tessella.editor.utils.Logger
import org.scalajs.dom

@main
def Editor(): Unit =
  // Initialize logging based on environment (dev vs prod)
  Logger.initFromEnvironment()
  Logger.info("Editor starting up")
  renderOnDomContentLoaded(
    dom.document.getElementById("app"),
    EditorApp.element
  )

object EditorApp:
  def element: Element =
    div(
      onMountCallback: ctx =>
        EditorState.effectiveTheme.foreach { theme =>
          val body = dom.document.body
          body.classList.toggle("light-mode", theme == Theme.Light): Unit
          body.classList.toggle("dark-mode", theme == Theme.Dark): Unit
        }(using ctx.owner): Unit,
      //      h1("Polygon Shape Editor"),
      // Add the Menu Bar at the top, passing the theme signal and the state Var to update
      MenuBarComponent.element(EditorState.effectiveTheme, EditorState.userThemePreference),
      div(
        className := "editor-layout",
        PolygonPaletteComponent.element,
        EditorCanvasComponent.element
      ),
      // Global keyboard event handlers
      KeyboardEventHandler.keyboardEventHandlers,
      // Render the Color Picker Popup at the top level, controlled by shared state
      child.maybe <-- EditorState.showColorPicker.signal.map: show =>
        if show then
          Some(ColorPickerPopupComponent.element(EditorState.showColorPicker, EditorState.tempColor))
        else None
    )
