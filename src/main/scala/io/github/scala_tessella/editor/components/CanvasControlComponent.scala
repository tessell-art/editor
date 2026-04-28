package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.i18n.I18n
import io.github.scala_tessella.editor.models.{AddSubmode, EditorState, Tool}
import io.github.scala_tessella.editor.operations.OperationGuard.gate
import io.github.scala_tessella.editor.operations.{ColorOperations, ToolActions}

/** Canvas tool strip — sits above the canvas on desktop / tablet.
  *
  * Order (left to right):
  *   1. Add Polygon (with inline Outside / Inside cycle)
  *   2. Eraser
  *   3. Color Picker
  *   4. Shape & Color Picker
  *   5. Select-by-Color
  *   6. Measurement
  *   7. Spacer
  *   8. Labels toggle
  *   9. Undo / Redo
  *
  * Mobile bottom-toolbar is a separate component (Phase 3).
  */
object CanvasControlComponent:

  private def addPolygonButton(): Element =
    val isActive = EditorState.toolState.signal.map(_.activeTool == Tool.AddPolygon).distinct

    val labelText: Signal[String] =
      isActive
        .combineWith(
          EditorState.toolState.signal.map(_.addSubmode).distinct,
          EditorState.localeState.signal
        )
        .map: (active, sub, _) =>

          val subKey = sub match
            case AddSubmode.Outside => "tool.addPolygon.outside"
            case AddSubmode.Inside  => "tool.addPolygon.inside"
          if active then I18n.tNow("tool.addPolygon.activeFmt", I18n.tNow(subKey))
          else I18n.tNow("tool.addPolygon")

    val tooltipText: Signal[String] =
      isActive.combineWith(EditorState.localeState.signal).map: (active, _) =>
        if active then I18n.tNow("tool.addPolygon.tooltip.active")
        else I18n.tNow("tool.addPolygon.tooltip.inactive")

    button(
      className := "toggle-btn add-polygon-btn",
      cls("active") <-- isActive,
      title <-- tooltipText,
      disabled <-- EditorState.uiState.signal.map(_.isProcessing).distinct,
      onClick.compose(gate) --> { _ =>

        ToolActions.cycleOrActivateAddPolygon()
      },
      IconsSVG.plusIcon,
      span(className := "tool-button-label", child.text <-- labelText)
    )

  private def createToolButton(tool: Tool, labelKey: String, titleKey: String, icon: Element): Element =
    button(
      icon,
      span(className := "tool-button-label", child.text <-- I18n.t(labelKey)),
      className := "toggle-btn",
      cls("active") <-- EditorState.toolState.signal.map(_.activeTool == tool).distinct,
      onClick.compose(gate) --> { _ =>

        ToolActions.toggleTool(tool)
      },
      title <-- I18n.t(titleKey)
    )

  /** Compact swatch showing the current fill color. Click opens the color picker; OK applies to the current
    * selection (or sets the default fill if nothing is selected) — same as Edit → Fill Color…
    */
  private def fillButton(): Element =
    button(
      className := "fill-swatch",
      tpe       := "button",
      title <-- I18n.t("tool.fill.title"),
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

  private def labelsToggleButton(): Element =
    val showSignal = EditorState.viewState.signal.map(_.showNodeLabels).distinct
    button(
      span(
        className := "tool-button-label",
        child.text <-- showSignal.combineWith(EditorState.localeState.signal).map { case (show, _) =>
          I18n.tNow(if show then "tool.labels.on" else "tool.labels.off")
        }
      ),
      className := "toggle-btn responsive-control labels-toggle",
      cls("active") <-- showSignal,
      onClick --> { _ =>

        AppState.toggleNodeLabels()
      },
      title <-- showSignal.combineWith(EditorState.localeState.signal).map { case (show, _) =>
        I18n.tNow(if show then "tool.labels.hide" else "tool.labels.show")
      }
    )

  private def infoToggleButton(): Element =
    val showSignal = EditorState.viewState.signal.map(_.showTilingInfo).distinct
    button(
      className := "toggle-btn info-toggle",
      cls("active") <-- showSignal,
      onClick --> { _ =>

        AppState.toggleShowTilingInfo()
      },
      title <-- showSignal.combineWith(EditorState.localeState.signal).map { case (show, _) =>
        I18n.tNow(if show then "tool.info.hide" else "tool.info.show")
      },
      "ⓘ"
    )

  /** True when the tiling has at least one polygon and every one is selected. Drives the select-toggle
    * button's action branch and icon swap. Mirrors [[MobileBottomToolbar]].
    */
  private val isAllSelectedSignal: Signal[Boolean] =
    EditorState.tessellationState.signal
      .map { t =>

        val faceCount = t.currentTiling.innerFaces.size
        faceCount > 0 && t.selectedTilingPolygons.size == faceCount
      }
      .distinct

  private def fitButton(): Element =
    button(
      className := "toggle-btn",
      title <-- I18n.t("menu.view.fitToCanvas"),
      disabled <-- EditorState.canMutateTilingSignal.map(!_),
      onClick.compose(gate) --> { _ =>

        AppState.fitTilingToCanvas()
      },
      IconsSVG.maximizeIcon
    )

  private def selectToggleButton(): Element =
    button(
      className := "toggle-btn",
      title <-- isAllSelectedSignal.combineWith(EditorState.localeState.signal).map { case (allSelected, _) =>
        I18n.tNow(if allSelected then "menu.edit.deselectAll" else "menu.edit.selectAll")
      },
      disabled <-- EditorState.canMutateTilingSignal.map(!_),
      onClick.preventDefault.compose(stream =>
        gate(stream).withCurrentValueOf(isAllSelectedSignal)
      ) --> { case (_, allSelected) =>
        if allSelected then AppState.deselectAll()
        else AppState.selectAll()
      },
      child <-- isAllSelectedSignal.map { allSelected =>

        if allSelected then IconsSVG.selectionGridEmptyIcon
        else IconsSVG.selectionGridFilledIcon
      }
    )

  def element: Element =
    div(
      className := "canvas-controls",
      div(
        className := "tool-strip",
        // Mode buttons
        div(
          className   := "tool-strip-modes",
          addPolygonButton(),
          createToolButton(Tool.Eraser, "tool.eraser", "tool.eraser.title", IconsSVG.eraserIcon),
          createToolButton(
            Tool.ColorPicker,
            "tool.colorPicker",
            "tool.colorPicker.title",
            IconsSVG.eyeDropperIcon
          ),
          createToolButton(
            Tool.ShapeAndColorPicker,
            "tool.shapeColor",
            "tool.shapeColor.title",
            IconsSVG.eyeDropperPentagonIcon
          ),
          createToolButton(
            Tool.SelectByColor,
            "tool.selectByColor",
            "tool.selectByColor.title",
            IconsSVG.selectByColorIcon
          ),
          createToolButton(Tool.Measurement, "tool.measurement", "tool.measurement.title", IconsSVG.rulerIcon)
        ),
        // Spacer pushes ancillary controls to the right
        div(className := "tool-strip-spacer"),
        // Ancillary controls
        div(
          className   := "tool-strip-aux",
          fillButton(),
//          infoToggleButton(),
          fitButton(),
          selectToggleButton(),
          labelsToggleButton(),
          UndoComponent.element
        )
      )
    )
