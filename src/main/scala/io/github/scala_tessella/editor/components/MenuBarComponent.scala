package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.components.popup.*
import io.github.scala_tessella.editor.models.{AppState, EditorState, ViewTransform}
import io.github.scala_tessella.editor.operations.ViewOperations
import io.github.scala_tessella.editor.utils.PolygonNameGenerator.*
import io.github.scala_tessella.editor.utils.file.{DotExporter, SvgExporter, SvgImporter, TemplateLoader}
import io.github.scala_tessella.editor.utils.UndoManager

import scala.math.{max, min}

object MenuBarComponent:

  // This state is for the mobile view, to toggle the hamburger menu
  private val isMenuOpen = Var(false)

  // The signature is updated to accept the theme preference Var directly
  def element(effectiveTheme: Signal[String], userThemePreference: Var[Option[String]]): Element =
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
            onClick --> { _ =>

              isMenuOpen.update(!_)
            },
            aria.label := "Toggle navigation menu",
            "☰"
          ),
          // The menu itself
          div(
            className <-- isMenuOpen.signal.map(open =>
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
      IrregularPolygonPopup.element,
      GuidePopup.element,
      ShortcutsPopup.element,
      AboutPopup.element
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

  // A helper for creating a clickable link in a dropdown
  private def dropdownLink(
      title: String,
      action: () => Unit,
      enabled: Signal[Boolean] = Val(true),
      shortcut: Option[String] = None
  ): Element =
    a(
      href := "#",
      onClick.preventDefault.map(_ => action()) --> { _ =>

        isMenuOpen.set(false)
      }, // close menu on action
      className("disabled") <-- enabled.map(!_),
      span(title),
      shortcut.map(s => span(className := "shortcut", s))
    )

  // A helper for creating a dropdown link with dynamic text
  private def dropdownLinkDynamic(
      title: Signal[String],
      action: () => Unit,
      enabled: Signal[Boolean] = Val(true),
      shortcut: Option[String] = None
  ): Element =
    a(
      href := "#",
      onClick.preventDefault.map(_ => action()) --> { _ =>

        isMenuOpen.set(false)
      }, // close menu on action
      className("disabled") <-- enabled.map(!_),
      span(child.text <-- title),
      shortcut.map(s => span(className := "shortcut", s))
    )

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
    val isTilingEmpty = EditorState.currentTiling.signal.map(_.isEmpty)
    val hasFileName   = EditorState.currentFileName.signal.map(_.isDefined)
    menuItem(
      "File",
      dropdownLink(
        "New",
        () =>
          AppState.clearTiling()
          EditorState.currentFileName.set(None)
          UndoManager.clearHistory()
          EditorState.viewTransform.set(ViewTransform())
      ),
      templatesMenu(),
      div(className := "menu-separator"),
      dropdownLink("Load SVG...", () => SvgImporter.trigger()),
      dropdownLink(
        "Save SVG",
        () => SvgExporter.saveTilingToSVG(),
        enabled = hasFileName.combineWith(isTilingEmpty).map(_ && !_),
        shortcut = Some("Ctrl+S")
      ),
      dropdownLink(
        "Save SVG as...",
        () => SvgExporter.saveAsTilingToSVG(),
        enabled = isTilingEmpty.map(!_)
      ),
      div(className := "menu-separator"),
      dropdownLink(
        "Export to DOT...",
        () => DotExporter.exportTilingToDOT(),
        enabled = isTilingEmpty.map(!_)
      )
    )

  private def editMenu(): Element =
    val isTilingEmpty = EditorState.currentTiling.signal.map(_.isEmpty)
    val hasSelection  = EditorState.selectedTilingPolygons.signal
      .combineWith(EditorState.selectedPerimeterEdges.signal)
      .map((polys, edges) => polys.nonEmpty || edges.nonEmpty)

    menuItem(
      "Edit",
      dropdownLink("↶ Undo", () => AppState.undoObserver: Unit, AppState.canUndo, shortcut = Some("Ctrl+Z")),
      dropdownLink(
        "↷ Redo",
        () => AppState.redoObserver: Unit,
        AppState.canRedo,
        shortcut = Some("Shift+Ctrl+Z")
      ),
      div(className := "menu-separator"),
      dropdownLink("Clear Tiling", () => AppState.clearTiling()),
//      div(className := "menu-separator"),
//      dropdownLinkDynamic(
//        EditorState.editorMode.signal.map {
//          case EditorMode.Select => "Switch to Delete Mode"
//          case EditorMode.Delete => "Switch to Select Mode"
//        },
//        () => AppState.toggleEditorMode()
//      ),
      div(className := "menu-separator"),
      dropdownLink("Select All", () => AppState.selectAll(), isTilingEmpty.map(!_)),
      dropdownLink("Deselect All", () => AppState.deselectAll(), hasSelection, shortcut = Some("Esc")),
      div(className := "menu-separator"),
      a(
        href        := "#",
        "Fill Color...",
        onClick.preventDefault --> { _ =>
          EditorState.tempColor.set(EditorState.fillColor.now())
          EditorState.showColorPicker.set(true)
          isMenuOpen.set(false)
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
    val isTilingEmpty = EditorState.currentTiling.signal.map(_.isEmpty)
    menuItem(
      "View",
      dropdownLinkDynamic(
        EditorState.showNodeLabels.signal.map(show => if (show) "Hide Node Labels" else "Show Node Labels"),
        () => AppState.toggleNodeLabels()
      ),
      dropdownLinkDynamic(
        EditorState.showUniformity.signal.map(if (_) "Hide Uniformity" else "Show Uniformity"),
        () => AppState.toggleShowUniformity()
      ),
      div(className := "menu-separator"),
      dropdownLink("Fit to Canvas", () => AppState.fitTilingToCanvas(), enabled = isTilingEmpty.map(!_)),
      dropdownLink("Reset View", () => EditorState.viewTransform.set(ViewTransform())),
      div(className := "menu-separator"),
      dropdownLink(
        "Zoom In",
        () => EditorState.viewTransform.update(t => t.copy(scale = min(t.scale * 1.2, 5.0))),
        shortcut = Some("+")
      ),
      dropdownLink(
        "Zoom Out",
        () => EditorState.viewTransform.update(t => t.copy(scale = max(t.scale / 1.2, 0.1))),
        shortcut = Some("-")
      ),
      dropdownLink("Rotate Left", () => ViewOperations.rotateView(-30), shortcut = Some("E")),
      dropdownLink("Rotate Right", () => ViewOperations.rotateView(30), shortcut = Some("R"))
    )

  private def helpMenu(): Element =
    menuItem(
      "Help",
      dropdownLink("Guide...", () => EditorState.showGuidePopup.set(true)),
      dropdownLink("Keyboard Shortcuts...", () => EditorState.showShortcutsPopup.set(true)),
      div(className := "menu-separator"),
      dropdownLink("About...", () => EditorState.showAboutPopup.set(true))
    )

  // This now handles the theme update logic directly
  private def themeSwitcher(
      effectiveTheme: Signal[String],
      userThemePreference: Var[Option[String]]
  ): Element =
    button(
      className := "theme-toggle-button",
      title <-- effectiveTheme.map {
        case "dark"  => "Switch to Light Mode"
        case "light" => "Switch to Dark Mode"
      },
      // Safely get the current theme on click and update the state
      onClick.compose(_.withCurrentValueOf(effectiveTheme)) --> { case (_, currentTheme) =>
        val nextTheme = if (currentTheme == "light") "dark" else "light"
        userThemePreference.set(Some(nextTheme))
      },
      child <-- effectiveTheme.map {
        case "dark"  => IconsSVG.sunIcon  // Sun icon for dark mode, to switch to light
        case "light" => IconsSVG.moonIcon // Moon icon for light mode, to switch to dark
      }
    )
