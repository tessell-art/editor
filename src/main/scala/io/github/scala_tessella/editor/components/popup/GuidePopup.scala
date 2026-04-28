package io.github.scala_tessella.editor.components.popup

import com.raquo.laminar.api.L._
import io.github.scala_tessella.editor.components.IconsSVG
import io.github.scala_tessella.editor.i18n.I18n
import io.github.scala_tessella.editor.models.EditorState

object GuidePopup:

  import PopupCommons._

  private val closeGuide: Observer[org.scalajs.dom.MouseEvent] =
    closePopup(EditorState.popupState.update(_.copy(showGuidePopup = false)))

  def element: Element =
    popupOverlay(closeGuide)(
      popupContent(closeGuide)(
        h2(child.text <-- I18n.t("popup.guide.title")),
        // TODO i18n long-form: section titles + body paragraphs below remain English in v1.
        // Mixed inline icons / kbd / bold elements per list item make per-fragment keys clumsy;
        // translate when the i18n pipeline gains rich-text support.
        div(
          className := "popup-text-scrollable",
          h3("Creating a Tiling"),
          ul(
            li(
              "To start a new tiling, select a polygon shape from the palette. ",
              "It will be placed on the canvas."
            ),
            li(
              "To add new polygons, first select the desired shape from the palette and then drag it into the canvas. ",
              "You can also click on any boundary edge."
            )
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
              "has one boundary only, without intersections (made of one graph cycle only)."
            )
          ),
          h3("Selecting & Deleting"),
          ul(
            li(
              "Click on any polygon to select it, while you are in the ",
              i("Add (outside)"),
              " mode."
            ),
            li("Use the ", IconsSVG.selectionGridFilledIcon, " button to select all."),
            li("Use the ", IconsSVG.selectionGridEmptyIcon, " button to deselect all."),
            li("Use ", kbd("Esc"), " from the keyboard to deselect all as well."),
            li(
              "Use the ",
              i("Select"),
              " button at the bottom of the palette to select all regular polygons with the same shape."
            ),
            li(
              "Use the ",
              IconsSVG.selectByColorIcon,
              " ",
              i("Select by color"),
              " tool to select all polygons with the same color."
            )
          ),
          h3("Adding interior polygons"),
          ul(
            li(
              "Toggle the ",
              IconsSVG.plusIcon,
              " to select the ",
              i("Add (inside)"),
              " mode and add a regular polygon to the interior of an existing one."
            ),
            li(
              "You can select and drag from the palette. ",
              "Or when you click on a polygon its edges will be highlighted, ",
              "click one to add the selected regular polygon."
            )
          ),
          h3("Adding irregular polygons"),
          ul(
            li("Select the irregular shape in the palette, just like a regular one.")
          ),
          ul(
            li("Click the '↺' button on the top right corner of the button to shift the attaching edge.")
          ),
          ul(
            li(
              "Use the ",
              IconsSVG.eyeDropperPentagonIcon,
              " ",
              i("Shape and color picker"),
              " tool to select the shape (and the fill color) of an existing irregular polygon."
            )
          ),
          h3("Fanning"),
          ul(
            li(
              "Use the ",
              IconsSVG.fanIcon,
              " ",
              i("Fan"),
              " tool to add copies of the tiling rotating around a boundary vertex."
            ),
            li(
              "When you click on a polygon the boundary vertices will be highlighted, ",
              "click one to fan the tiling around the selected vertex. ",
              "As many copies as possible will be added, up to a full circle."
            )
          ),
          h3("Doubling and mirroring"),
          ul(
            li(
              "Use the ",
              b("Edit → Double (to infinite)"),
              " menu option or the ",
              kbd('D'),
              " key to double the entire tiling. ",
              "This works only when the boundary is a parallelogon, ",
              "so that the whole infinite planar space could be covered."
            ),
            li(
              "Use the ",
              b("Edit → Mirror"),
              " menu option to switch to a mirror image of the tiling."
            )
          ),
          h3("Deleting"),
          ul(
            li(
              "Use the ",
              IconsSVG.eraserIcon,
              " ",
              i("Eraser"),
              " tool to delete a vertex, an edge or a whole polygon from the tiling."
            ),
            li(
              "When you click on a polygon the vertices, edges (at midpoint) and face (at center) will be highlighted, ",
              "click one to delete the selected item."
            )
          ),
          h3("Navigating the Canvas"),
          ul(
            li("Pan: click and drag the canvas background to move the view."),
            li("Zoom: use the mouse wheel, or the ", kbd('+'), " and ", kbd('-'), " keys."),
            li("Rotate: use the ", kbd('E'), " (left) and ", kbd('R'), " (right) keys."),
            li(
              "Fit: use the ",
              IconsSVG.maximizeIcon,
              " button or the ",
              kbd('F'),
              " key to automatically adjust the view to see the entire tiling."
            ),
            li(
              "Reset: use ",
              b("View → Reset View"),
              " to return to the default position, zoom, and rotation."
            )
          ),
          h3("Visual options"),
          ul(
            li(
              "You can switch ",
              b("Labels: ON"),
              " to show the node labels (numbers) of the underlying graph, each node a vertex."
            ),
            li(
              "You can use ",
              b("View → Show Uniformity"),
              " to show dots marking nodes with the same adjacent pattern."
            ),
            li(
              "You can use ",
              b("View → Show Rotational Symmetry"),
              " to show the rotation axes dividing the tiling in identical rotated parts."
            ),
            li(
              "You can use ",
              b("View → Show Reflectional Symmetry"),
              " to show the reflection axes dividing the tiling in mirrored halves."
            )
          ),
          h3("Styling"),
          ul(
            li(
              "To change polygons' color, select one or more polygons, while you are in the ",
              i("Add (outside)"),
              " mode, then click on the colour button or go to ",
              b("Edit → Fill Color..."),
              " to open the color picker."
            ),
            li(
              "The new color will be applied to all currently selected polygons and will be used for any new polygon you add."
            ),
            li(
              "Use the ",
              IconsSVG.eyeDropperIcon,
              " ",
              i("Color picker"),
              " tool to select the fill color from an existing polygon."
            ),
            li(
              "Use the ",
              IconsSVG.eyeDropperPentagonIcon,
              " ",
              i("Shape and color picker"),
              " tool to select both the shape and the fill color from an existing polygon."
            )
          ),
          h3("Measurement"),
          ul(
            li(
              "By constraint, each polygon side in the tiling has unit length, that is length equal to 1, or an integer multiple of it."
            ),
            li(
              "Use the ",
              IconsSVG.rulerIcon,
              " ",
              i("Measurement"),
              " tool to calculate unit distances and angles for the key points (vertex, mid-side, center) of the polygons."
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
            li(
              "Use the ",
              b("File"),
              " menu to save your work as an SVG file (",
              b("Save SVG"),
              " or ",
              b("Save SVG as..."),
              ")."
            ),
            li("You can also load a previously saved SVG tiling."),
            li(
              "The tiling's topological structure can be exported as a DOT graph, in the Graphviz .gv file format."
            )
          )
        )
      )
    )
