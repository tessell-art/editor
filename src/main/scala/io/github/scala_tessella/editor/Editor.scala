package io.github.scala_tessella.editor

import com.raquo.airstream.web.DomEventStream
import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.components.{
  AppShellComponent, ColorPickerPopupComponent, EditorCanvasComponent, MobileBottomToolbar,
  PolygonPaletteComponent, UpdateAvailableBanner
}
import io.github.scala_tessella.editor.interactions.KeyboardEventHandler
import io.github.scala_tessella.editor.models.{ColorPickerContext, EditorState, Theme}
import io.github.scala_tessella.editor.operations.{DirtyTracker, MotionPreferences, UpdateChecker}
import io.github.scala_tessella.editor.platform.desktop.DesktopMenuBridge
import io.github.scala_tessella.editor.utils.{ColorRGB, Logger}
import org.scalajs.dom

import scala.scalajs.js

@main
def Editor(): Unit =
  // Initialize logging based on environment (dev vs prod)
  Logger.initFromEnvironment()
  Logger.info("Editor starting up")
  // Register the e2e test-hook object on globalThis. See TestHooks.scala for context.
  // Lands in production bundles too; payload is tiny.
  TestHooks.install()
  // Subscribe to Tauri native-menu events. No-op when running in a web browser
  // (window.__TAURI__ undefined). See DesktopMenuBridge.scala.
  DesktopMenuBridge.install()
  // Poll the deployed `version.json` for newer bundles. Web-only; skipped
  // inside Tauri / file:// loaded shells, which update through their own
  // packaging channel.
  UpdateChecker.install()
  // Native unsaved-changes warning before the tab/window unloads. The browser shows its own
  // dialog (no custom message — modern browsers ignore returnValue text); our in-app
  // confirm popup handles the same prompt for in-app navigations (New / Load / Template / Recent).
  installBeforeUnloadGuard()
  renderOnDomContentLoaded(
    dom.document.getElementById("app"),
    EditorApp.element
  )

private def installBeforeUnloadGuard(): Unit =
  // Modern browsers ignore the returned string; assigning it (or `event.returnValue`) is what
  // triggers the native prompt. Returning `null` when clean keeps the page unloading silently.
  dom.window.onbeforeunload = (event: dom.BeforeUnloadEvent) =>
    if DirtyTracker.isDirty then
      event.preventDefault()
      event.returnValue = ""
      ""
    else null

object EditorApp:
  private val applyThemeToBodyObserver: Observer[Theme] =
    Observer: theme =>

      val body = dom.document.body
      body.classList.toggle("light-mode", theme == Theme.Light): Unit
      body.classList.toggle("dark-mode", theme == Theme.Dark): Unit

  /** Toggles `body.reduce-motion` so CSS hover transforms / non-essential transitions can opt out across all
    * surfaces. Driven by `MotionPreferences.reducedMotionSignal`, which combines the Settings popup's
    * three-way preference with the OS-level `prefers-reduced-motion` media query.
    */
  private val applyReducedMotionToBodyObserver: Observer[Boolean] =
    Observer: reduced =>
      dom.document.body.classList.toggle("reduce-motion", reduced): Unit

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

          // Mirror the OS-level `prefers-reduced-motion` query into state so the same Auto/On/Off
          // resolver (`MotionPreferences`) drives both the dynamic animation paths AND the body class.
          val reducedMotionMq =
            windowDyn.matchMedia("(prefers-reduced-motion: reduce)").asInstanceOf[dom.MediaQueryList]
          EditorState.osPrefersReducedMotion.set(reducedMotionMq.matches)
          val _               =
            DomEventStream(reducedMotionMq, "change")
              .map(_ => reducedMotionMq.matches)
              .addObserver(Observer[Boolean](b => EditorState.osPrefersReducedMotion.set(b)))(using owner)
        else
          EditorState.themeState.update(_.copy(systemTheme = Theme.Light))

        val _ =
          EditorState.effectiveTheme.addObserver(applyThemeToBodyObserver)(using
            owner
          )
        val _ =
          MotionPreferences.reducedMotionSignal.addObserver(applyReducedMotionToBodyObserver)(using
            owner
          )
      ,
      //      h1("Polygon Shape Editor"),
      // App shell (top bar): logo, hamburger, menu items, language slot, theme toggle, popups
      AppShellComponent.element(
        EditorState.effectiveTheme,
        Observer[Option[Theme]](pref => EditorState.themeState.update(_.copy(userThemePreference = pref)))
      ),
      // Online-update banner. Renders nothing until UpdateChecker detects a
      // strictly-newer published version; sits above the editor layout so it
      // never overlaps the canvas.
      UpdateAvailableBanner.element,
      div(
        className := "editor-layout",
        PolygonPaletteComponent.element,
        EditorCanvasComponent.element
      ),
      // Mobile bottom toolbar — always rendered, CSS-hidden at desktop widths.
      MobileBottomToolbar.element,
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
              close = () =>
                EditorState.colorState.update(
                  _.copy(
                    showColorPicker = false,
                    colorPickerContext = ColorPickerContext.Default
                  )
                )
            )
          )
        else None
    )
