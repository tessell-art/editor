package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.models.{AddSubmode, EditorState, Tool}
import io.github.scala_tessella.editor.operations.OperationGuard.gate
import io.github.scala_tessella.editor.operations.{ColorOperations, ToolActions, UndoManager}

/** Phone-portrait bottom toolbar — replaces the desktop CanvasControlComponent at narrow widths.
  *
  * Two rows:
  *   1. Six icon-only mode buttons (Add, Eraser, Color, Shape+Color, SelByCol, Measure).
  *   2. Undo · Redo · Fit.
  *
  * Visibility is CSS-driven: this component is always rendered, hidden at desktop widths via `@media` rules
  * in the stylesheet. Reuses [[ToolActions]] for mode-switching semantics so desktop and mobile stay in sync.
  *
  * The Add Polygon button cycles `Outside ↔ Inside` on tap when already active; same as the desktop tool
  * strip. A long-press popover for explicit sub-mode selection is a Phase 5 polish item.
  */
object MobileBottomToolbar:

  def element: Element =
    div(
      className := "mobile-bottom-toolbar",
      div(
        className := "mobile-bottom-toolbar-modes",
        addModeButton(),
        modeButton(Tool.Eraser, "Eraser", IconsSVG.eraserIcon),
        modeButton(Tool.ColorPicker, "Color", IconsSVG.eyeDropperIcon),
        modeButton(Tool.ShapeAndColorPicker, "Shape & Color", IconsSVG.eyeDropperPentagonIcon),
        modeButton(Tool.SelectByColor, "Select by color", IconsSVG.selectByColorIcon)
        // Measurement is reachable from the Edit menu on phone widths to keep the row compact.
      ),
      div(
        className := "mobile-bottom-toolbar-aux",
        fillButton(),
        undoButton(),
        redoButton(),
        fitButton()
      )
    )

  private def addModeButton(): Element =
    val isActive = EditorState.toolState.signal.map(_.activeTool == Tool.AddPolygon).distinct

    val tooltipText: Signal[String] =
      isActive.combineWith(EditorState.toolState.signal.map(_.addSubmode).distinct)
        .map: (active, sub) =>

          val subText = sub match
            case AddSubmode.Outside => "outside"
            case AddSubmode.Inside  => "inside"
          if active then s"Add ($subText) — tap to switch sub-mode"
          else "Activate Add Polygon"

    button(
      className := "mobile-bottom-toolbar-btn",
      cls("active") <-- isActive,
      title <-- tooltipText,
      disabled <-- EditorState.uiState.signal.map(_.isProcessing).distinct,
      onClick.compose(gate) --> { _ =>

        ToolActions.cycleOrActivateAddPolygon()
      },
      IconsSVG.plusIcon
    )

  private def modeButton(tool: Tool, titleText: String, icon: Element): Element =
    button(
      className := "mobile-bottom-toolbar-btn",
      cls("active") <-- EditorState.toolState.signal.map(_.activeTool == tool).distinct,
      title     := titleText,
      disabled <-- EditorState.uiState.signal.map(_.isProcessing).distinct,
      onClick.compose(gate) --> { _ =>

        ToolActions.toggleTool(tool)
      },
      icon
    )

  /** Compact swatch showing the current fill color. Click opens the color picker; OK applies to the current
    * selection (or sets the default fill if nothing is selected). Mirrors the desktop fill swatch.
    */
  private def fillButton(): Element =
    button(
      className := "mobile-bottom-toolbar-btn aux fill-swatch",
      title     := "Fill color — tap to change",
      div(
        className := "fill-swatch-chip",
        backgroundColor <-- EditorState.colorState.signal.map(_.fillColor).distinct.map {
          case (r, g, b) => f"rgb($r,$g,$b)"
        }
      ),
      onClick.compose(gate) --> { _ =>

        ColorOperations.openFillColorPicker()
      }
    )

  private def undoButton(): Element =
    button(
      className := "mobile-bottom-toolbar-btn aux",
      title     := "Undo",
      disabled <-- AppState.canUndo.map(!_),
      onClick.compose(gate) --> { _ =>

        UndoManager.undo()
      },
      "↶"
    )

  private def redoButton(): Element =
    button(
      className := "mobile-bottom-toolbar-btn aux",
      title     := "Redo",
      disabled <-- AppState.canRedo.map(!_),
      onClick.compose(gate) --> { _ =>

        UndoManager.redo()
      },
      "↷"
    )

  private def fitButton(): Element =
    button(
      className := "mobile-bottom-toolbar-btn aux",
      title     := "Fit tiling to canvas",
      disabled <-- EditorState.canMutateTilingSignal.map(!_),
      onClick.compose(gate) --> { _ =>

        AppState.fitTilingToCanvas()
      },
      "Fit"
    )
