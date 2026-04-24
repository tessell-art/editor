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
import io.github.scala_tessella.editor.platform.desktop.DesktopMenuBridge
import io.github.scala_tessella.editor.utils.{ColorRGB, Logger}
import org.scalajs.dom

import scala.scalajs.js

@main
def Editor(): Unit =
  // Initialize logging based on environment (dev vs prod)
  Logger.initFromEnvironment()
  Logger.info("Editor starting up")
  // Register the e2e test-hook object on globalThis. See ADR-004 + TestHooks.scala for context.
  // Lands in production bundles too; payload is tiny.
  TestHooks.install()
  // Subscribe to Tauri native-menu events. No-op when running in a web browser
  // (window.__TAURI__ undefined). See ADR-008 + DesktopMenuBridge.scala.
  DesktopMenuBridge.install()
  renderOnDomContentLoaded(
    dom.document.getElementById("app"),
    EditorApp.element
  )

object EditorApp:
  private val applyThemeToBodyObserver: Observer[Theme] =
    Observer: theme =>

      val body = dom.document.body
      body.classList.toggle("light-mode", theme == Theme.Light): Unit
      body.classList.toggle("dark-mode", theme == Theme.Dark): Unit

  def element: Element =
    div(
      onMountCallback: ctx =>

        val owner     = ctx.owner
        val windowDyn = js.Dynamic.global.selectDynamic("window")
        if js.typeOf(windowDyn) != "undefined" && js.typeOf(windowDyn.matchMedia) == "function" then
          val mediaQuery =
            windowDyn.matchMedia("(prefers-color-scheme: light)").asInstanceOf[dom.MediaQueryList]
          val initial    = if mediaQuery.matches then Theme.Light else Theme.Dark
          val changes    =
            DomEventStream(mediaQuery, "change")
              .map: _ =>
                if mediaQuery.matches then Theme.Light else Theme.Dark
          val _          =
            changes
              .startWith(initial)
              .addObserver(Observer[Theme](t => EditorState.themeState.update(_.copy(systemTheme = t))))(using
                owner
              )
        else
          EditorState.themeState.update(_.copy(systemTheme = Theme.Light))

        val _ =
          EditorState.effectiveTheme.addObserver(applyThemeToBodyObserver)(using
            owner
          )
      ,
      //      h1("Polygon Shape Editor"),
      // Add the Menu Bar at the top, passing the theme signal and the state Var to update
      MenuBarComponent.element(
        EditorState.effectiveTheme,
        Observer[Option[Theme]](pref => EditorState.themeState.update(_.copy(userThemePreference = pref)))
      ),
      div(
        className := "editor-layout",
        PolygonPaletteComponent.element,
        EditorCanvasComponent.element
      ),
      // Global keyboard event handlers
      KeyboardEventHandler.keyboardEventHandlers,
      // Render the Color Picker Popup at the top level, controlled by shared state
      child.maybe <-- EditorState.colorState.signal.map(_.showColorPicker).distinct.map: show =>
        if show then
          Some(
            ColorPickerPopupComponent.element(
              tempColorSignal = EditorState.colorState.signal.map(_.tempColor).distinct,
              tempColorObserver = Observer[ColorRGB](c =>
                EditorState.colorState.update(_.copy(tempColor = c))
              ),
              close = () => EditorState.colorState.update(_.copy(showColorPicker = false))
            )
          )
        else None
    )
