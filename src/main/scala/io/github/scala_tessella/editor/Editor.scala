package io.github.scala_tessella.editor

import com.raquo.airstream.web.DomEventStream
import com.raquo.laminar.api.L.*
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

import scala.scalajs.js

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
        val owner     = ctx.owner
        val windowDyn = js.Dynamic.global.selectDynamic("window")
        val _         =
          if js.typeOf(windowDyn) != "undefined" && js.typeOf(windowDyn.matchMedia) == "function" then
            val mediaQuery =
              windowDyn.matchMedia("(prefers-color-scheme: light)").asInstanceOf[dom.MediaQueryList]
            val initial    = if mediaQuery.matches then Theme.Light else Theme.Dark
            val changes    =
              DomEventStream(mediaQuery, "change")
                .map: _ =>
                  if mediaQuery.matches then Theme.Light else Theme.Dark
            changes.startWith(initial).foreach(EditorState.systemTheme.set)(using owner)
          else
            EditorState.systemTheme.set(Theme.Light)

        EditorState.effectiveTheme.foreach { theme =>
          val body = dom.document.body
          body.classList.toggle("light-mode", theme == Theme.Light): Unit
          body.classList.toggle("dark-mode", theme == Theme.Dark): Unit
        }(using owner): Unit
      ,
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
