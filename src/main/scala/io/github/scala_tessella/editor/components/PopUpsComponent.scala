package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.EditorState

import com.raquo.laminar.api.L.{*, given}
import com.raquo.laminar.api.features.unitArrows

object PopUpsComponent:

  // Helper method to create a simple 'X' close icon
  private def closeIcon: Element =
    svg.svg(
      svg.width := "24",
      svg.height := "24",
      svg.viewBox := "0 0 24 24",
      svg.fill := "none",
      svg.stroke := "currentColor",
      svg.strokeWidth := "2",
//      svg.strokeLinecap := "round",
//      svg.strokeLinejoin := "round",
      svg.path(svg.d := "M 18 6 L 6 18"),
      svg.path(svg.d := "M 6 6 L 18 18")
    )

  private [components] def guidePopup(): Element =
    div(
      className := "popup-overlay",
      display <-- EditorState.showGuidePopup.signal.map(if (_) "flex" else "none"),
      onClick --> (_ => EditorState.showGuidePopup.set(false)),
      div(
        className := "popup-content",
        onClick.stopPropagation --> {}, // Prevents clicks from closing the popup
        button(
          className := "popup-close-btn",
          onClick --> (_ => EditorState.showGuidePopup.set(false)),
          closeIcon
        ),
        h2("Guide"),
        div(
          className := "popup-text-scrollable",
          h3("Creating a Tiling"),
          ul(
            li(
              "To start a new tiling, select a polygon shape from the palette. ",
              "It will be placed on the canvas."
            ),
            li("To add new polygons, first select the desired shape, then click on any boundary edge.")
          ),
          h3("Validation"),
          ul(
            li(
              "The editor will check that an added polygon ",
              "is not crossing the boundary or another polygon, ",
              "ensuring you are building a proper edge-to-edge finite tessellation."
            ),
            li(
              "And when removing a polygon, the editor will always check that the resulting tiling ",
              "has one boundary only, made of one graph cycle only."
            )
          ),
          h3("Selecting & Deleting"),
          ul(
            li("Click on any polygon to select it."),
            li("Use ", kbd("Esc"), " to deselect everything."),
            li("Use the ", i("Select"), " button at the bottom of the palette to select all regular polygons with the same shape."),
            li(
              "Use the ",
              IconsSVG.selectByColorIcon,
              " ", i("Select by color"), " tool to select all polygons with the same color.")
          ),
          h3("Adding interior polygons"),
          ul(
            li(
              "Use the ",
              IconsSVG.inserterIcon,
              " ", i("Insertion"), " tool to add a regular polygon to the interior of an existing one."
            ),
            li(
              "When you click on a polygon its edges will be highlighted, ",
              "click one to add the selected regular polygon."
            ),
          ),
          h3("Deleting"),
          ul(
            li(
              "Use the ",
              IconsSVG.eraserIcon,
              " ", i("Eraser"), " tool to delete a vertex, and edge or a whole polygon from the tiling."
            ),
            li(
              "When you click on a polygon the vertices, edges (at midpoint) and face (at center) will be highlighted, ",
              "click one to delete the selected item."
            ),
          ),
          h3("Navigating the Canvas"),
          ul(
            li("Pan: click and drag the canvas background to move the view."),
            li("Zoom: use the mouse wheel, or the ", kbd('+'), " and ", kbd('-'), " keys."),
            li("Rotate: use the ", kbd('E'), " (left) and ", kbd('R'), " (right) keys."),
            li("Fit: use the ", b("View → Fit to Canvas"), " menu option to automatically adjust the view to see the entire tiling."),
            li("Reset: use ", b("View → Reset View"), " to return to the default position, zoom, and rotation.")
          ),
          h3("Visual options"),
          ul(
            li(
              "You can switch ", b("Labels: ON"), " to show the node labels (numbers) of the underlying graph, each node a vertex."
            ),
//            li(
//              "Dual: use ", b("View → Show Dual"), " to show the dual of the tessellation."
//            )
          ),
          h3("Styling"),
          ul(
            li("To change polygons' color, select one or more polygons, then go to ", b("Edit → Fill Color..."), " to open the color picker."),
            li("The new color will be applied to all currently selected polygons and will be used for any new polygon you add."),
            li(
              "Use the ",
              IconsSVG.eyeDropperIcon,
              " ", i("Color picker"), " tool to select the fill color from an existing polygon."
            ),
            li(
              "Use the ",
              IconsSVG.eyeDropperPentagonIcon,
              " ", i("Shape and color picker"), " tool to select both the shape and the fill color from an existing regular polygon."
            )
          ),
          h3("Measurement"),
          ul(
            li("By constraint, each polygon side in the tiling has unit length, that is length equal to 1, or an integer multiple of it."),
            li(
              "Use the ",
              IconsSVG.rulerIcon,
              " ", i("Measurement"), " tool to calculate the unit distance between two key points (vertex, mid-side, center) of the polygons."
            ),
            li(
              "When you click on a polygon the key points will be highlighted, ",
              "click one to choose the (green) start and repeat to choose the (red) end. ",
              "The unit distance will be displayed above the top right corner of the canvas."
            ),
            li(
              "When you click on another key point to choose a different (red) end, ",
              "the angle between the current and the previous end points will be shown as an arc. ",
              "The angle measure in rad will be displayed too."
            )
          ),
          h3("Saving & Loading"),
          ul(
            li("Use the ", b("File"), " menu to save your work as an SVG file (", b("Save SVG"), " or ", b("Save SVG as..."), ")."),
            li("You can also load a previously saved SVG tiling."),
            li("The tiling's topological structure can be exported as a DOT graph, in the Graphviz .gv file format.")

          )
        )
      )
    )

  private [components] def shortcutsPopup(): Element =
    div(
      className := "popup-overlay",
      display <-- EditorState.showShortcutsPopup.signal.map(if (_) "flex" else "none"),
      onClick --> (_ => EditorState.showShortcutsPopup.set(false)),
      div(
        className := "popup-content",
        onClick.stopPropagation --> {}, // Prevents clicks from closing the popup
        button(
          className := "popup-close-btn",
          onClick --> (_ => EditorState.showShortcutsPopup.set(false)),
          closeIcon
        ),
        h2("Keyboard Shortcuts"),
        div(
          className := "popup-text-scrollable",
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
        )
      )
    )

  private [components] def helpPopup(): Element =
    div(
      className := "popup-overlay",
      display <-- EditorState.showAboutPopup.signal.map(if (_) "flex" else "none"),
      onClick --> (_ => EditorState.showAboutPopup.set(false)),
      div(
        className := "popup-content",
        onClick.stopPropagation --> {}, // Prevents clicks from closing the popup
        button(
          className := "popup-close-btn",
          onClick --> (_ => EditorState.showAboutPopup.set(false)),
          closeIcon
        ),
        img(
          src := "tessella-logo.svg",
          alt := "Tessella Logo",
          className := "popup-logo"
        ),
        h1("Tessella"),
        p(
          className := "about-version",
          "Editor v0.2.2"
        ),
        h2("Simple polygon tessellation editor"),
        div(
          className := "popup-text-scrollable",
          p(
            "Interactively create, view, and manipulate tessellations of the plane made of simple (regular and irregular) polygons.",
          ),
          p(
            "The editor depends on the ", b("scala-tessella/tessella"), " library. For more information, and to contribute, please visit the official ",
            a(
              href := "https://github.com/scala-tessella/tessella",
              target := "_blank", // Opens in a new tab
              rel := "noopener noreferrer",
              "GitHub repository"
            ),
            "."
          ),
          p(
            "Built with Scala.js and Laminar.",
          )
        )
      )
    )