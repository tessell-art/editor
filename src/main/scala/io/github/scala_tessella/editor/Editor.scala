package io.github.scala_tessella.editor

import io.github.scala_tessella.editor.components.{ColorPickerPopupComponent, EditorCanvasComponent, MenuBarComponent, PolygonPaletteComponent}
import io.github.scala_tessella.editor.interactions.KeyboardEventHandler
import io.github.scala_tessella.editor.models.EditorState

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom

@main
def Editor(): Unit =
  renderOnDomContentLoaded(
    dom.document.getElementById("app"),
    EditorApp.element
  )

object EditorApp:

  // 1. Create a reactive variable for the system's theme preference.
  private val lightMediaQuery = dom.window.matchMedia("(prefers-color-scheme: light)")
  private val systemTheme = Var(if (lightMediaQuery.matches) "light" else "dark")

  // 2. Listen for changes in the system theme preference.
  // We use lightMediaQuery.matches directly to avoid the problematic cast.
  lightMediaQuery.addEventListener("change", (_: dom.Event) => {
    systemTheme.set(if (lightMediaQuery.matches) "light" else "dark")
  })

  // 3. The effective theme is the user's preference, or the system theme if none is set.
  val effectiveTheme: Signal[String] = EditorState.userThemePreference.signal.combineWith(systemTheme.signal).map {
    case (Some(userChoice), _) => userChoice // User's choice takes precedence
    case (None, systemPref)    => systemPref   // Otherwise, follow system
  }

  // 4. The toggleTheme function is no longer needed here; the logic is moved into the component.

  def element: Element =
    div(
      className <-- effectiveTheme.map(theme => s"$theme-mode"),
      //      h1("Polygon Shape Editor"),
      // Add the Menu Bar at the top, passing the theme signal and the state Var to update
      MenuBarComponent.element(effectiveTheme, EditorState.userThemePreference),
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