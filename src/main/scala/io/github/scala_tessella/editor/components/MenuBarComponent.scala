package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.components.popup.*
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.models.{EditorConfig, EditorState, Theme, ViewTransform}
import io.github.scala_tessella.editor.operations.ViewOperations
import io.github.scala_tessella.editor.utils.PolygonNameGenerator.*
import io.github.scala_tessella.editor.utils.file.{DotExporter, SvgExporter, SvgImporter, TemplateLoader}
import io.github.scala_tessella.editor.utils.UndoManager

object MenuBarComponent:

  // The signature is updated to accept the theme preference Var directly
  def element(effectiveTheme: Signal[Theme], userThemePreference: Var[Option[Theme]]): Element =
    div(
      navTag(
        className := "menu-bar",
        // Group logo, hamburger, and menu items on the left
        div(
          className := "menu-left-section",
          img(
            src        := "tessella-logo.svg",
            alt        := "Tessella Logo",
            className  := "menu-bar-logo"
          ),
          // Hamburger button for small screens
          button(
            className  := "menu-toggle",
            onClick --> (_ => EditorState.uiState.update(s => s.copy(isMenuOpen = !s.isMenuOpen))),
            aria.label := "Toggle navigation menu",
            "☰"
          ),
          // The menu itself
          div(
            className <-- EditorState.uiState.signal.map(_.isMenuOpen).distinct.map(open =>
              if (open) "menu-items-container open" else "menu-items-container"
            ),
            fileMenu(),
            editMenu(),
            viewMenu(),
            helpMenu()
          )
        ),
        // Pass both the signal and the Var to the switcher
        themeSwitcher(effectiveTheme, userThemePreference)
      ),
      child.maybe <-- EditorState.popupState.signal.map(_.showIrregularPolygonPopup).distinct.map: show =>
        if show then Some(IrregularPolygonPopup.element) else None,
      child.maybe <-- EditorState.popupState.signal.map(_.showGuidePopup).distinct.map: show =>
        if show then Some(GuidePopup.element) else None,
      child.maybe <-- EditorState.popupState.signal.map(_.showShortcutsPopup).distinct.map: show =>
        if show then Some(ShortcutsPopup.element) else None,
      child.maybe <-- EditorState.popupState.signal.map(_.showAboutPopup).distinct.map: show =>
        if show then Some(AboutPopup.element) else None,
      child.maybe <-- EditorState.popupState.signal.map(_.showSettingsPopup).distinct.map: show =>
        if show then Some(SettingsPopup.element) else None
    )

  // A helper to create a top-level menu item like "File", "Edit"
  private def menuItem(title: String, children: Mod[HtmlElement]*): Element =
    div(
      className := "menu-item",
      button(
        className := "menu-button",
        title
      ),
      div(
        className := "dropdown-content",
        children
      )
    )

  private def dropdownLinkBase(
      title: Mod[HtmlElement],
      action: () => Unit,
      enabled: Signal[Boolean],
      shortcut: Option[String]
  ): Element =
    a(
      href := "#",
      onClick.preventDefault.compose(
        _.withCurrentValueOf(enabled).collect:
          case (_, true) => ()
      ) --> { _ =>

        action()
        EditorState.uiState.update(_.copy(isMenuOpen = false))
      }, // close menu on action
      className("disabled") <-- enabled.map(!_),
      span(title),
      shortcut.map(s => span(className := "shortcut", s))
    )

  // A helper for creating a clickable link in a dropdown
  private def dropdownLink(
      title: String,
      action: () => Unit,
      enabled: Signal[Boolean] = Val(true),
      shortcut: Option[String] = None
  ): Element =
    dropdownLinkBase(title, action, enabled, shortcut)

  // A helper for creating a dropdown link with dynamic text
  private def dropdownLinkDynamic(
      title: Signal[String],
      action: () => Unit,
      enabled: Signal[Boolean] = Val(true),
      shortcut: Option[String] = None
  ): Element =
    dropdownLinkBase(child.text <-- title, action, enabled, shortcut)

  // Helper for a menu item that opens a submenu
  private def subMenuItem(title: String, children: Mod[HtmlElement]*): Element =
    div(
      className := "submenu-item",
      a(
        href := "#",
        onClick.preventDefault --> { _ =>

          ()
        }, // Prevent navigation, allows hover/focus
        span(title),
        span(className := "submenu-arrow", "▸")
      ),
      div(
        className := "submenu-content",
        children
      )
    )

  private def dropdownLinks(directory: String, templates: List[Template]): List[Element] =
    templates.map(template =>
      dropdownLink(
        s"${template.name} ${template.pattern}",
        () => TemplateLoader.loadTemplate(directory, template.filename)
      )
    )

  private def templatesMenu(): Element =
    subMenuItem(
      "New from Template...",
      // Regular tilings
      regularMenu(),
      div(className := "menu-separator"),
      // Semiregular tilings
      semiRegularMenu(),
      div(className := "menu-separator"),
      // Aperiodic tilings
      aperiodicMenu()
    )

  private def regularMenu(): Element =
    subMenuItem(
      "Regular...",
      // Regular tilings
      dropdownLinks("regular", regularNames)
    )

  private def semiRegularMenu(): Element =
    subMenuItem(
      "Semi Regular...",
      // Semi regular tilings
      dropdownLinks("semiregular", semiRegularNames)
    )

  private def aperiodicMenu(): Element =
    subMenuItem(
      "Aperiodic...",
      // Aperiodic tilings
      dropdownLinks("aperiodic", irregularNames)
    )

  private def fileMenu(): Element =
    menuItem(
      "File",
      dropdownLink(
        "New",
        () =>

          AppState.clearTiling()
          EditorState.currentFileName.set(None)
          UndoManager.clearHistory()
          EditorState.viewState.update(_.copy(viewTransform = ViewTransform()))
          AppState.resetFillColorToDefault()
      ),
      templatesMenu(),
      div(className := "menu-separator"),
      dropdownLink("Load SVG...", () => SvgImporter.trigger()),
      dropdownLink(
        "Save SVG",
        () => SvgExporter.saveTilingToSVG(),
        enabled = EditorState.canSaveCurrentFileWhenIdleSignal,
        shortcut = Some("Ctrl+S")
      ),
      dropdownLink(
        "Save SVG as...",
        () => SvgExporter.saveAsTilingToSVG(),
        enabled = EditorState.canMutateTilingSignal
      ),
      div(className := "menu-separator"),
      dropdownLink(
        "Export to DOT...",
        () => DotExporter.exportTilingToDOT(),
        enabled = EditorState.canMutateTilingSignal
      ),
      div(className := "menu-separator"),
      dropdownLink("Settings...", () => EditorState.popupState.update(_.copy(showSettingsPopup = true)))
    )

  private def editMenu(): Element =
    menuItem(
      "Edit",
      dropdownLink("↶ Undo", () => UndoManager.undo(), AppState.canUndo, shortcut = Some("Ctrl+Z")),
      dropdownLink("↷ Redo", () => UndoManager.redo(), AppState.canRedo, shortcut = Some("Shift+Ctrl+Z")),
      div(className := "menu-separator"),
      dropdownLink("Clear Tiling", () => AppState.clearTiling()),
      dropdownLink(
        "Double (to infinite)",
        () => AppState.doubleTiling(),
        enabled = EditorState.canMutateTilingSignal,
        shortcut = Some("D")
      ),
      dropdownLink("Mirror", () => AppState.mirrorTiling(), enabled = EditorState.canMutateTilingSignal),
//      div(className := "menu-separator"),
//      dropdownLinkDynamic(
//        EditorState.editorMode.signal.map {
//          case EditorMode.Select => "Switch to Delete Mode"
//          case EditorMode.Delete => "Switch to Select Mode"
//        },
//        () => AppState.toggleEditorMode()
//      ),
      div(className := "menu-separator"),
      dropdownLink("Select All", () => AppState.selectAll(), EditorState.canMutateTilingSignal),
      dropdownLink(
        "Deselect All",
        () => AppState.deselectAll(),
        EditorState.canDeselectAllSignal,
        shortcut = Some("Esc")
      ),
      div(className := "menu-separator"),
      a(
        href        := "#",
        "Fill Color...",
        onClick.preventDefault.compose(
          _.withCurrentValueOf(EditorState.colorState.signal.map(_.fillColor).distinct)
            .map((_, color) => color)
        ) --> { color =>

          EditorState.colorState.update(_.copy(tempColor = color))
          EditorState.colorState.update(_.copy(showColorPicker = true))
          EditorState.uiState.update(_.copy(isMenuOpen = false))
        }
      )
//      div(className := "menu-separator"),
//      dropdownLinkDynamic(
//        EditorState.strictness.signal.map {
//          case Strictness.STRICT   => "Switch Validation OFF"
//          case _                   => "Switch Validation ON"
//        },
//        () => AppState.toggleStrictness()
//      )
    )

  private def viewMenu(): Element =
    menuItem(
      "View",
      dropdownLinkDynamic(
        EditorState.viewState.signal.map(_.showNodeLabels).distinct.map(show =>
          if (show) "Hide Node Labels" else "Show Node Labels"
        ),
        () => AppState.toggleNodeLabels()
      ),
      dropdownLinkDynamic(
        EditorState.viewState.signal.map(_.showUniformity).distinct.map(if (_) "Hide Uniformity"
        else "Show Uniformity"),
        () => AppState.toggleShowUniformity()
      ),
      dropdownLinkDynamic(
        EditorState.viewState.signal.map(_.showRotation).distinct.map(if (_) "Hide Rotational Symmetry"
        else "Show Rotational Symmetry"),
        () => AppState.toggleShowRotation()
      ),
      dropdownLinkDynamic(
        EditorState.viewState.signal.map(_.showReflection).distinct.map(if (_) "Hide Reflectional Symmetry"
        else "Show Reflectional Symmetry"),
        () => AppState.toggleShowReflection()
      ),
      div(className := "menu-separator"),
      dropdownLink(
        "Fit to Canvas",
        () => AppState.fitTilingToCanvas(),
        enabled = EditorState.canMutateTilingSignal,
        shortcut = Some("F")
      ),
      dropdownLink("Reset View", () => EditorState.viewState.update(_.copy(viewTransform = ViewTransform()))),
      div(className := "menu-separator"),
      dropdownLink(
        "Zoom In",
        () =>
          EditorState.viewState.update: s =>

            val vt = s.viewTransform
            s.copy(viewTransform =
              vt.copy(scale = ViewOperations.clampViewScale(vt.scale * EditorConfig.menuZoomFactor))
            )
        ,
        shortcut = Some("+")
      ),
      dropdownLink(
        "Zoom Out",
        () =>
          EditorState.viewState.update: s =>

            val vt = s.viewTransform
            s.copy(viewTransform =
              vt.copy(scale = ViewOperations.clampViewScale(vt.scale / EditorConfig.menuZoomFactor))
            )
        ,
        shortcut = Some("-")
      ),
      dropdownLink("Rotate Left", () => ViewOperations.rotateView(-30), shortcut = Some("E")),
      dropdownLink("Rotate Right", () => ViewOperations.rotateView(30), shortcut = Some("R"))
    )

  private def helpMenu(): Element =
    menuItem(
      "Help",
      dropdownLink("Guide...", () => EditorState.popupState.update(_.copy(showGuidePopup = true))),
      dropdownLink(
        "Keyboard Shortcuts...",
        () => EditorState.popupState.update(_.copy(showShortcutsPopup = true))
      ),
      div(className := "menu-separator"),
      dropdownLink("About...", () => EditorState.popupState.update(_.copy(showAboutPopup = true)))
    )

  // This now handles the theme update logic directly
  private def themeSwitcher(
      effectiveTheme: Signal[Theme],
      userThemePreference: Var[Option[Theme]]
  ): Element =
    button(
      className := "theme-toggle-button",
      title <-- effectiveTheme.map {
        case Theme.Dark  => "Switch to Light Mode"
        case Theme.Light => "Switch to Dark Mode"
      },
      // Safely get the current theme on click and update the state
      onClick.compose(_.withCurrentValueOf(effectiveTheme)) --> { case (_, currentTheme) =>
        userThemePreference.set(Some(currentTheme.toggle))
      },
      child <-- effectiveTheme.map {
        case Theme.Dark  => IconsSVG.sunIcon  // Sun icon for dark mode, to switch to light
        case Theme.Light => IconsSVG.moonIcon // Moon icon for light mode, to switch to dark
      }
    )
