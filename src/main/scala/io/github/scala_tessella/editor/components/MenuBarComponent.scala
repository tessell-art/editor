package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{AppState, EditorMode, EditorState, ViewTransform}
import io.github.scala_tessella.tessella.IncrementalTiling.Strictness
import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.editor.operations.TessellationOperations.clearTiling
import io.github.scala_tessella.editor.utils.UndoManager

import scala.math.{max, min}

object MenuBarComponent {

  // This state is for the mobile view, to toggle the hamburger menu
  private val isMenuOpen = Var(false)

  def element: Element = {
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
        optionsMenu()
      )
    )
  }

  // A helper to create a top-level menu item like "File", "Edit"
  private def menuItem(title: String, children: Mod[HtmlElement]*): Element = {
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
  }

  // A helper for creating a clickable link in a dropdown
  private def dropdownLink(title: String, action: () => Unit, enabled: Signal[Boolean] = Val(true)): Element = {
    a(
      href := "#",
      title,
      onClick.preventDefault.map(_ => action()) --> { _ => isMenuOpen.set(false) }, // close menu on action
      disabled <-- enabled.map(!_)
    )
  }

  // A helper for creating a dropdown link with dynamic text
  private def dropdownLinkDynamic[T](title: Signal[String], action: () => Unit, enabled: Signal[Boolean] = Val(true)): Element = {
    a(
      href := "#",
      child.text <-- title,
      onClick.preventDefault.map(_ => action()) --> { _ => isMenuOpen.set(false) }, // close menu on action
      disabled <-- enabled.map(!_)
    )
  }

  private def fileMenu(): Element = {
    menuItem("File",
      dropdownLink("Import from .DOT...", () => {
        // Placeholder for Import functionality
        println("Import from .DOT... clicked")
      }),
      dropdownLink("Export to .DOT...", () => {
        // Placeholder for Export functionality
        println("Export to .DOT... clicked")
      })
    )
  }

  private def editMenu(): Element = {
    // NOTE: I'm assuming AppState has undo/redo logic like:
    // undo(): Unit, redo(): Unit, canUndo: Signal[Boolean], canRedo: Signal[Boolean]
    menuItem("Edit",
      dropdownLink("Undo", () => AppState.undo(), AppState.canUndo),
      dropdownLink("Redo", () => AppState.redo(), AppState.canRedo),
      div(className := "menu-separator"),
      dropdownLink("Clear tiling", () => clearTiling()),
      div(className := "menu-separator"),
      dropdownLinkDynamic(
        EditorState.editorMode.signal.map {
          case EditorMode.Select => "Switch to Delete Mode"
          case EditorMode.Delete => "Switch to Select Mode"
        },
        () => AppState.toggleEditorMode()
      ),
      div(className := "menu-separator"),
      a(
        href := "#",
        "Fill Color...",
        onClick.preventDefault --> { _ =>
          EditorState.tempColor.set(EditorState.fillColor.now())
          EditorState.showColorPicker.set(true)
          isMenuOpen.set(false)
        }
      )
    )
  }

  private def viewMenu(): Element = {
    menuItem("View",
      dropdownLinkDynamic(
        EditorState.showNodeLabels.signal.map(show => if (show) "Hide Node Labels" else "Show Node Labels"),
        () => AppState.toggleNodeLabels()
      ),
      div(className := "menu-separator"),
      dropdownLink("Reset View", () => EditorState.viewTransform.set(ViewTransform())),
      dropdownLink("Zoom In", () => EditorState.viewTransform.update(t => t.copy(scale = min(t.scale * 1.2, 5.0)))),
      dropdownLink("Zoom Out", () => EditorState.viewTransform.update(t => t.copy(scale = max(t.scale / 1.2, 0.1)))),
      dropdownLink("Rotate Left", () => EditorState.viewTransform.update(t => t.withRotation(t.rotationDegrees - 30))),
      dropdownLink("Rotate Right", () => EditorState.viewTransform.update(t => t.withRotation(t.rotationDegrees + 30)))
    )
  }

  private def optionsMenu(): Element = {
    menuItem("Options",
      dropdownLinkDynamic(
        EditorState.strictness.signal.map {
          case Strictness.STRICT   => "Switch to Touching Strictness"
          case Strictness.TOUCHING => "Switch to Crossing Strictness"
          case Strictness.CROSSING => "Switch to Strict Strictness"
        },
        () => AppState.toggleStrictness()
      )
    )
  }
}
