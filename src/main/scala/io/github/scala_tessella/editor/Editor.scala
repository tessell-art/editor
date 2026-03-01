package io.github.scala_tessella.editor

import com.raquo.laminar.api.L._
import io.github.scala_tessella.editor.components.{
  ColorPickerPopupComponent,
  EditorCanvasComponent,
  MenuBarComponent,
  PolygonPaletteComponent
}
import io.github.scala_tessella.editor.interactions.KeyboardEventHandler
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.Logger
import org.scalajs.dom

@main
def Editor(): Unit =
  // Initialize logging based on environment (dev vs prod)
  Logger.initFromEnvironment()
  Logger.info("Editor starting up")
  // This observer will update the body's class list whenever the theme changes.
  // We use unsafeWindowOwner because this is a global setting for the app's lifetime.
  val _ = EditorState.effectiveTheme.foreach { theme =>
    dom.document.body.classList.remove("light-mode")
    dom.document.body.classList.remove("dark-mode")
    dom.document.body.classList.add(theme.modeClass)
  }(using unsafeWindowOwner)

  renderOnDomContentLoaded(
    dom.document.getElementById("app"),
    EditorApp.element
  )

object EditorApp:
  def element: Element =
    div(
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
