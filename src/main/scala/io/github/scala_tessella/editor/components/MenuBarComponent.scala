package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{AppState, EditorMode, EditorState, ViewTransform}
import io.github.scala_tessella.editor.operations.TessellationOperations
import io.github.scala_tessella.editor.utils.{DotExporter, SvgExporter, SvgImporter, UndoManager}

import com.raquo.laminar.api.L.{*, given}
import com.raquo.laminar.api.features.unitArrows
import io.github.scala_tessella.tessella.IncrementalTiling.Strictness

import scala.math.{max, min}

object MenuBarComponent:

  // This state is for the mobile view, to toggle the hamburger menu
  private val isMenuOpen = Var(false)

  def element: Element =
    div(
      navTag(
        className := "menu-bar",
        // Hamburger button for small screens
        button(
          className := "menu-toggle",
          onClick --> { _ => isMenuOpen.update(!_) },
          "☰"
        ),
        // The menu itself
        div(
          className <-- isMenuOpen.signal.map(open => if (open) "menu-items-container open" else "menu-items-container"),
          fileMenu(),
          editMenu(),
          viewMenu(),
          helpMenu()
        )
      ),
      helpPopup()
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
  private def dropdownLink(title: String, action: () => Unit, enabled: Signal[Boolean] = Val(true), shortcut: Option[String] = None): Element =
    a(
      href := "#",
      onClick.preventDefault.map(_ => action()) --> { _ => isMenuOpen.set(false) }, // close menu on action
      className.toggle("disabled") <-- enabled.map(!_),
      span(title),
      shortcut.map(s => span(className := "shortcut", s))
    )

  // A helper for creating a dropdown link with dynamic text
  private def dropdownLinkDynamic[T](title: Signal[String], action: () => Unit, enabled: Signal[Boolean] = Val(true), shortcut: Option[String] = None): Element =
    a(
      href := "#",
      onClick.preventDefault.map(_ => action()) --> { _ => isMenuOpen.set(false) }, // close menu on action
      className.toggle("disabled") <-- enabled.map(!_),
      span(child.text <-- title),
      shortcut.map(s => span(className := "shortcut", s))
    )

  private def fileMenu(): Element =
    val isTilingEmpty = EditorState.currentTiling.signal.map(_.isEmpty)
    val hasFileName = EditorState.currentFileName.signal.map(_.isDefined)
    menuItem("File",
      dropdownLink("Load SVG...", () => SvgImporter.trigger()),
      dropdownLink(
        "Save SVG",
        () => SvgExporter.saveTilingToSVG(),
        enabled = hasFileName
      ),
      dropdownLink(
        "Save SVG as...",
        () => SvgExporter.exportTilingToSVG(),
        enabled = isTilingEmpty.map(!_)
      ),
      div(className := "menu-separator"),
      dropdownLink("Import from .DOT...", () => {
        // Placeholder for Import functionality
        println("Import from .DOT... clicked")
      }),
      dropdownLink(
        "Export to .DOT...",
        () => DotExporter.exportTilingToDOT(),
        enabled = isTilingEmpty.map(!_)
      )
    )

  private def editMenu(): Element =
    val isTilingEmpty = EditorState.currentTiling.signal.map(_.isEmpty)
    val hasSelection = EditorState.selectedTilingPolygons.signal
      .combineWith(EditorState.selectedPerimeterEdges.signal)
      .map((polys, edges) => polys.nonEmpty || edges.nonEmpty)

    menuItem("Edit",
      dropdownLink("Undo", () => AppState.undo(), AppState.canUndo, shortcut = Some("Ctrl+Z")),
      dropdownLink("Redo", () => AppState.redo(), AppState.canRedo, shortcut = Some("Shift+Ctrl+Z")),
      div(className := "menu-separator"),
      dropdownLink("Clear tiling", () => AppState.clearTiling()),
      div(className := "menu-separator"),
      dropdownLinkDynamic(
        EditorState.editorMode.signal.map {
          case EditorMode.Select => "Switch to Delete Mode"
          case EditorMode.Delete => "Switch to Select Mode"
        },
        () => AppState.toggleEditorMode()
      ),
      div(className := "menu-separator"),
      dropdownLink("Select All", () => AppState.selectAll(), isTilingEmpty.map(!_)),
      dropdownLink("Deselect All", () => AppState.deselectAll(), hasSelection, shortcut = Some("Esc")),
      div(className := "menu-separator"),
      a(
        href := "#",
        "Fill Color...",
        onClick.preventDefault --> { _ =>
          EditorState.tempColor.set(EditorState.fillColor.now())
          EditorState.showColorPicker.set(true)
          isMenuOpen.set(false)
        }
      ),
      div(className := "menu-separator"),
      dropdownLinkDynamic(
        EditorState.strictness.signal.map {
          case Strictness.STRICT   => "Switch to Touching Strictness"
          case Strictness.TOUCHING => "Switch to Crossing Strictness"
          case Strictness.CROSSING => "Switch to Strict Strictness"
        },
        () => AppState.toggleStrictness()
      )
    )

  private def viewMenu(): Element =
    val isTilingEmpty = EditorState.currentTiling.signal.map(_.isEmpty)
    menuItem("View",
      dropdownLinkDynamic(
        EditorState.showNodeLabels.signal.map(show => if (show) "Hide Node Labels" else "Show Node Labels"),
        () => AppState.toggleNodeLabels()
      ),
      div(className := "menu-separator"),
      dropdownLink("Fit to canvas", () => AppState.fitTilingToCanvas(), enabled = isTilingEmpty.map(!_)),
      dropdownLink("Reset View", () => EditorState.viewTransform.set(ViewTransform())),
      div(className := "menu-separator"),
      dropdownLink("Zoom In", () => EditorState.viewTransform.update(t => t.copy(scale = min(t.scale * 1.2, 5.0))), shortcut = Some("+")),
      dropdownLink("Zoom Out", () => EditorState.viewTransform.update(t => t.copy(scale = max(t.scale / 1.2, 0.1))), shortcut = Some("-")),
      dropdownLink("Rotate Left", () => EditorState.viewTransform.update(t => t.withRotation(t.rotationDegrees - 30)), shortcut = Some("E")),
      dropdownLink("Rotate Right", () => EditorState.viewTransform.update(t => t.withRotation(t.rotationDegrees + 30)), shortcut = Some("R"))
    )

  private def helpMenu(): Element =
    menuItem("Help",
      dropdownLink("About...", () => {
        EditorState.showAboutPopup.set(true)
      }),
    )

  private def helpPopup(): Element =
    div(
      className := "popup-overlay",
      display <-- EditorState.showAboutPopup.signal.map(if (_) "flex" else "none"),
      onClick --> (_ => EditorState.showAboutPopup.set(false)),
      div(
        className := "popup-content",
        onClick.stopPropagation --> {}, // Prevents clicks from closing the popup
        h2("About Scala-Tessella Editor"),
        p(
          "This editor allows you to create, view, and manipulate tessellations interactively. ",
          "It's built with Scala.js and the UI is powered by the ",
          a(
            href := "https://laminar.dev/",
            target := "_blank",
            rel := "noopener noreferrer",
            "Laminar"
          ),
          " library."
        ),
        p(
          "For more information, tutorials, and to contribute, please visit the official ",
          a(
            href := "https://github.com/scala-tessella/scala-tessella-editor",
            target := "_blank", // Opens in a new tab
            rel := "noopener noreferrer",
            "GitHub repository"
          ),
          "."
        ),
        p(
          className := "popup-close-hint",
          "Click on the gray area to close this popup."
        )
      )
    )
