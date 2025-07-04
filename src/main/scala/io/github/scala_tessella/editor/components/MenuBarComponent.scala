package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{AppState, EditorMode, EditorState, ViewTransform}
import io.github.scala_tessella.editor.operations.{TessellationOperations, ViewOperations}
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
        img(
          src := "tessella-logo.svg",
          alt := "Tessella Logo",
          className := "menu-bar-logo"
        ),
        // Hamburger button for small screens
        button(
          className := "menu-toggle",
          onClick --> { _ => isMenuOpen.update(!_) },
          aria.label := "Toggle navigation menu",
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
      guidePopup(),
      shortcutsPopup(),
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
      dropdownLink("New", () =>
        AppState.clearTiling()
        EditorState.currentFileName.set(None)
        UndoManager.clearHistory()
        EditorState.viewTransform.set(ViewTransform())
      ),
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
//      dropdownLink("Import from .DOT...", () => {
//        // Placeholder for Import functionality
//        println("Import from .DOT... clicked")
//      }),
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
      dropdownLink("↶ Undo", () => AppState.undo(), AppState.canUndo, shortcut = Some("Ctrl+Z")),
      dropdownLink("↷ Redo", () => AppState.redo(), AppState.canRedo, shortcut = Some("Shift+Ctrl+Z")),
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
          case Strictness.STRICT   => "Switch Validation OFF"
          case _                   => "Switch Validation ON"
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
      dropdownLink("Rotate Left", () => ViewOperations.rotateView(-30), shortcut = Some("E")),
      dropdownLink("Rotate Right", () => ViewOperations.rotateView(30), shortcut = Some("R"))
    )

  private def helpMenu(): Element =
    menuItem("Help",
      dropdownLink("Guide...", () => { EditorState.showGuidePopup.set(true) }),
      dropdownLink("Keyboard shortcuts...", () => { EditorState.showShortcutsPopup.set(true) }),
      div(className := "menu-separator"),
      dropdownLink("About...", () => { EditorState.showAboutPopup.set(true) }),
    )

  private def guidePopup(): Element =
    div(
      className := "popup-overlay",
      display <-- EditorState.showGuidePopup.signal.map(if (_) "flex" else "none"),
      onClick --> (_ => EditorState.showGuidePopup.set(false)),
      div(
        className := "popup-content",
        onClick.stopPropagation --> {}, // Prevents clicks from closing the popup
        h2("Guide"),
        div(
          className := "popup-text-scrollable",
          h3("Creating a Tiling"),
          ul(
            li(
              "To start a new tiling, select a polygon shape from the palette on the left. ",
              "It will be placed on the canvas."
            ),
            li("To add new polygons, first select the desired shape, then click on any highlighted perimeter edge.")
          ),
          h3("Validation"),
          ul(
            li(
              "If Validation is ON, the editor will check that the added polygon ",
              "is not touching or crossing the perimeter (boundary), ",
              "ensuring you are building a proper finite tessellation."
            ),
            li(
              "You can switch Validation OFF for more freedom of expression.",
            ),
            li(
              "In any case, when removing a polygon, the editor will always check that the resulting tiling ",
              "has one perimeter (boundary) only, made of one graph cycle only."
            )
          ),
          h3("Selecting & Deleting"),
          ul(
            li("Select Mode (default): click on any polygon to select it."),
            li("Delete Mode: click on any perimeter polygon to remove it."),
            li("Use ", kbd("Esc"), " to deselect everything."),
            li("Use the Select button at the bottom of the palette to select all the polygons with the same shape."),
            li(
              "Use the ",
              IconsSVG.selectByColorIcon,
              " ", i("Select by color"), " tool to select all the polygons with the same color.")
          ),
          h3("Navigating the Canvas"),
          ul(
            li("Pan: click and drag the canvas background to move the view."),
            li("Zoom: use the mouse wheel, or the ", kbd('+'), " and ", kbd('-'), " keys."),
            li("Rotate: use the ", kbd('E'), " (left) and ", kbd('R'), " (right) keys."),
            li("Fit: use the 'View' -> 'Fit to canvas' menu option to automatically adjust the view to see the entire tiling."),
            li("Reset: use 'View' -> 'Reset View' to return to the default position, zoom, and rotation.")
          ),
          h3("Styling"),
          ul(
            li("To change the color of polygons, select one or more polygons, then go to 'Edit' -> 'Fill Color...' to open the color picker."),
            li("The new color will be applied to all currently selected polygons and will be used for any new polygons you add."),
            li(
              "Use the ",
              IconsSVG.eyeDropperIcon,
              " ", i("Color picker"), " tool to select a fill color from an existing polygon.")
          ),
          h3("Measurement"),
          ul(
            li("By constraint, each regular polygon side in the tiling has unit length, that lenght equal to 1."),
            li(
              "Use the ",
              IconsSVG.rulerIcon,
              " ", i("Measurement"), " tool to calculate the unit distance between two key points (vertex, mid-side, center) of the polygons."
            ),
            li(
              "When you click on a polygon the key points will be highlighted, ",
              "click one to choose the (green) start and repeat to choose the (red) end. ",
              "The unit distance will be displayed above the top right corner of the canvas."
            )
          ),
          h3("Saving & Loading"),
          ul(
            li("Use the 'File' menu to save your work as an SVG file ('Save SVG' or 'Save SVG as...')."),
            li("You can also load a previously saved SVG tiling."),
            li("The tiling's graph structure can be exported to a .DOT file, which is compatible with Graphviz.")

          )
        ),
        p(
          className := "popup-close-hint",
          "Click outside of this popup to close it."
        )
      )
    )

  private def shortcutsPopup(): Element =
    div(
      className := "popup-overlay",
      display <-- EditorState.showShortcutsPopup.signal.map(if (_) "flex" else "none"),
      onClick --> (_ => EditorState.showShortcutsPopup.set(false)),
      div(
        className := "popup-content",
        onClick.stopPropagation --> {}, // Prevents clicks from closing the popup
        h2("Editor Commands & Guide"),
        div(
          className := "popup-text-scrollable",
          h3("Keyboard Shortcuts"),
          table(
            className := "shortcuts-table",
            thead(
              tr(
                th("Action"),
                th("Shortcut")
              )
            ),
            tbody(
              tr(
                td("Undo"),
                td(kbd("Ctrl"), " + ", kbd("Z"))
              ),
              tr(
                td("Redo"),
                td(kbd("Ctrl"), " + ", kbd("Shift"), " + ", kbd("Z"))
              ),
              tr(
                td("Save"),
                td(kbd("Ctrl"), " + ", kbd("S"))
              ),
              tr(
                td("Deselect All"),
                td(kbd("Esc"))
              ),
              tr(
                td("Zoom In"),
                td(kbd("+"))
              ),
              tr(
                td("Zoom Out"),
                td(kbd("-"))
              ),
              tr(
                td("Rotate Left"),
                td(kbd("E"))
              ),
              tr(
                td("Rotate Right"),
                td(kbd("R"))
              )
            )
          )
        ),
        p(
          className := "popup-close-hint",
          "Click outside of this popup to close it."
        )
      )
    )

  private def helpPopup(): Element =
    div(
      className := "popup-overlay",
      display <-- EditorState.showAboutPopup.signal.map(if (_) "flex" else "none"),
      onClick --> (_ => EditorState.showAboutPopup.set(false)),
      div(
        className := "popup-content",
        onClick.stopPropagation --> {}, // Prevents clicks from closing the popup
        img(
          src := "tessella-logo.svg",
          alt := "Tessella Logo",
          className := "popup-logo"
        ),

        h1("Tessella"),
        h2("Regular polygon tessellation editor"),
        p(
          className := "popup-text",
          "Allows you to interactively create, view, and manipulate edge-to-edge regular polygon tessellations of the plane.",
        ),
        p(
          className := "popup-text",
          "The editor it's built on top of the ", b("scala-tessella/tessella"), " library. For more information, and to contribute, please visit the official ",
          a(
            href := "https://github.com/scala-tessella/tessella",
            target := "_blank", // Opens in a new tab
            rel := "noopener noreferrer",
            "GitHub repository"
          ),
          "."
        ),
        p(
          className := "popup-close-hint",
          "Click outside of this popup to close it."
        )
      )
    )
