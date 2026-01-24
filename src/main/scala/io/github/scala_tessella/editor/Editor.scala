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
  val _ = EditorApp.effectiveTheme.foreach { theme =>
    dom.document.body.classList.remove("light-mode")
    dom.document.body.classList.remove("dark-mode")
    dom.document.body.classList.add(s"$theme-mode")
  }(using unsafeWindowOwner)

  renderOnDomContentLoaded(
    dom.document.getElementById("app"),
    EditorApp.element
  )

object EditorApp:

  // 1. Create a reactive signal for the system's theme preference (no Var needed).
  private val lightMediaQuery = dom.window.matchMedia("(prefers-color-scheme: light)")

  // Use an EventBus to bridge the JS event into Airstream
  private val systemThemeBus              = new EventBus[String]
  private val systemTheme: Signal[String] =
    systemThemeBus.events.startWith(if (lightMediaQuery.matches) "light" else "dark")

  // Attach the listener once to push updates into the bus
  lightMediaQuery.addEventListener(
    "change",
    (_: dom.Event) =>
      systemThemeBus.writer.onNext(if (lightMediaQuery.matches) "light" else "dark")
  )

  // 2. The effective theme is the user's preference, or the system theme if none is set.
  val effectiveTheme: Signal[String] =
    EditorState.userThemePreference.signal
      .combineWith(systemTheme)
      .map: (userChoiceOpt, systemPref) =>
        userChoiceOpt.getOrElse(systemPref)

  // 3. The toggleTheme function is no longer needed here; the logic is moved into the component.

  def element: Element =
    div(
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
