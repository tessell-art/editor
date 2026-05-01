package io.github.scala_tessella.editor.components.popup

import com.raquo.laminar.api.L._
import io.github.scala_tessella.editor.components.IconsSVG
import io.github.scala_tessella.editor.i18n.I18n
import io.github.scala_tessella.editor.models.EditorState

object GuidePopup:

  import PopupCommons._

  private val titleId = "guide-popup-title"

  private val closeGuide: Observer[org.scalajs.dom.MouseEvent] =
    closePopup(EditorState.popupState.update(_.copy(showGuidePopup = false)))

  def element: Element =
    popupOverlay(closeGuide)(
      popupContent(closeGuide)(
        aria.labelledBy := titleId,
        h2(idAttr    := titleId, child.text <-- I18n.t("popup.guide.title")),
        // TODO i18n long-form: section titles + body paragraphs below remain English in v1.
        // Mixed inline icons / kbd / bold elements per list item make per-fragment keys clumsy;
        // translate when the i18n pipeline gains rich-text support.
        div(
          className  := "popup-text-scrollable",
          tabIndex   := 0,
          aria.label := "Guide content",
          h3("Creating a tiling"),
          ul(
            li(
              "The editor has two adding modes. ",
              em("Add (outside)"),
              " grows the tiling by attaching polygons to its boundary; ",
              em("Add (inside)"),
              " fills an existing polygon with smaller regular ones."
            ),
            li(
              "To start a new tiling, pick a polygon shape from the palette. ",
              "It is placed on the canvas."
            )
          ),
          h3("Adding regular polygons (outside)"),
          ul(
            li(
              "Pick a shape from the palette and drag it onto the canvas, ",
              "or click any boundary edge to attach the shape there."
            )
          ),
          h3("Adding regular polygons (inside)"),
          ul(
            li(
              "Click the ",
              IconsSVG.plusIcon,
              " icon to enter ",
              em("Add (inside)"),
              " mode."
            ),
            li(
              "Drag a shape from the palette onto an existing polygon, ",
              "or click a polygon to highlight its edges, then click an edge to attach the chosen shape."
            )
          ),
          h3("Adding irregular polygons"),
          ul(
            li("Pick the irregular shape from the palette, just like a regular one."),
            li(
              "Click the ↺ button on the top-right corner of the palette item to shift the attaching edge."
            ),
            li(
              "Use the ",
              IconsSVG.eyeDropperPentagonIcon,
              " ",
              em("Shape and color picker"),
              " tool to copy the shape (and fill colour) of an existing irregular polygon."
            )
          ),
          h3("Fanning"),
          ul(
            li(
              "Use the ",
              IconsSVG.fanIcon,
              " ",
              em("Fan"),
              " tool to add rotated copies of the tiling around a boundary vertex."
            ),
            li(
              "Click a polygon to highlight its boundary vertices, then click one. ",
              "As many copies as fit are added, up to a full circle."
            )
          ),
          h3("Selecting"),
          ul(
            li(
              "In ",
              em("Add (outside)"),
              " mode, click any polygon to select it."
            ),
            li(
              "Use the ",
              IconsSVG.selectionGridFilledIcon,
              " ",
              em("Select all"),
              " button to select every polygon."
            ),
            li(
              "Use the ",
              IconsSVG.selectionGridEmptyIcon,
              " ",
              em("Deselect all"),
              " button — or press ",
              kbd("Esc"),
              " — to clear the selection."
            ),
            li(
              "Use the ",
              em("Select"),
              " button at the bottom of the palette to select all regular polygons of the same shape."
            ),
            li(
              "Use the ",
              IconsSVG.selectByColorIcon,
              " ",
              em("Select by color"),
              " tool to select all polygons sharing the same fill colour."
            )
          ),
          h3("Deleting"),
          ul(
            li(
              "Use the ",
              IconsSVG.eraserIcon,
              " ",
              em("Eraser"),
              " tool to remove a vertex, an edge, or a whole polygon."
            ),
            li(
              "Click a polygon to highlight its vertices, mid-edge points, and centre, ",
              "then click the item you want to remove."
            )
          ),
          h3("Doubling and mirroring"),
          ul(
            li(
              "Use ",
              strong("Edit → Double (to infinite)"),
              " or press ",
              kbd('D'),
              " to double the entire tiling. ",
              "This works only when the boundary is a parallelogon ",
              "(a polygon whose opposite sides are equal and parallel), ",
              "so the doubled copies could tile the infinite plane."
            ),
            li(
              "Use ",
              strong("Edit → Mirror"),
              " to switch to a mirror image of the tiling."
            )
          ),
          h3("Styling"),
          ul(
            li(
              "In ",
              em("Add (outside)"),
              " mode, select one or more polygons, then click the colour button or use ",
              strong("Edit → Fill Color…"),
              " to open the colour picker."
            ),
            li(
              "The chosen colour applies to the current selection and to any polygon you add next."
            ),
            li(
              "Use the ",
              IconsSVG.eyeDropperIcon,
              " ",
              em("Color picker"),
              " tool to copy the fill colour from an existing polygon."
            ),
            li(
              "Use the ",
              IconsSVG.eyeDropperPentagonIcon,
              " ",
              em("Shape and color picker"),
              " tool to copy both the shape and the fill colour."
            )
          ),
          h3("Visual options"),
          ul(
            li(
              strong("View → Show Node Labels"),
              " shows the node label (unique number) of each vertex of the underlying graph."
            ),
            li(
              strong("View → Show Uniformity"),
              " marks nodes that share the same adjacent pattern."
            ),
            li(
              strong("View → Show Rotational Symmetry"),
              " shows the rotation axes that divide the tiling into identical rotated parts."
            ),
            li(
              strong("View → Show Reflectional Symmetry"),
              " shows the reflection axes that divide the tiling into mirrored halves."
            )
          ),
          h3("Measurement"),
          ul(
            li(
              "By design, every polygon side has unit length 1 (or an integer multiple of it)."
            ),
            li(
              "Use the ",
              IconsSVG.rulerIcon,
              " ",
              em("Measurement"),
              " tool to measure unit distances and angles between key points (vertex, mid-edge, centre)."
            ),
            li(
              "Click a polygon to highlight its key points; click one to set the (green) start, ",
              "and click another to set the (red) end. ",
              "The unit distance is displayed above the top-right corner of the canvas."
            ),
            li(
              "Click another key point to choose a new (red) end. ",
              "The angle between the previous and current end points is shown as an arc, ",
              "with its measure in radians."
            )
          ),
          h3("Navigating the canvas"),
          ul(
            li("Pan: click and drag the canvas background (or drag with one finger on touch devices)."),
            li(
              "Zoom: scroll the mouse wheel, pinch on a touch device, or press ",
              kbd('+'),
              " / ",
              kbd('-'),
              "."
            ),
            li("Rotate: press ", kbd('E'), " (left) or ", kbd('R'), " (right)."),
            li(
              "Fit: use the ",
              IconsSVG.maximizeIcon,
              " ",
              em("Fit"),
              " button or press ",
              kbd('F'),
              " to fit the entire tiling in view."
            ),
            li(
              "Reset: ",
              strong("View → Reset View"),
              " returns to the default position, zoom, and rotation."
            )
          ),
          h3("Saving and loading"),
          ul(
            li(
              "Use the ",
              strong("File"),
              " menu to save your work as an SVG (",
              strong("Save SVG"),
              " or ",
              strong("Save SVG as…"),
              ") or to load a previously saved SVG tiling."
            ),
            li(
              "The tiling's topological structure can also be exported as a DOT graph, in the Graphviz .gv format."
            )
          ),
          h3("Validation rules"),
          ul(
            li(
              "An added polygon must not cross the boundary or another polygon, ",
              "so the result stays a proper edge-to-edge finite tessellation ",
              "(every edge is shared by exactly one or two polygons, with no overlaps)."
            ),
            li(
              "When you remove a polygon, the editor checks that the remaining tiling still has ",
              "a single, non-self-intersecting boundary."
            )
          )
        )
      )
    )
