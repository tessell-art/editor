package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.i18n.I18n
import io.github.scala_tessella.editor.models.MenuShortcuts.MenuAction
import io.github.scala_tessella.editor.models.{AddSubmode, EditorState, MenuShortcuts, Tool}
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
  *   7. Add Copy (chevron flyout → Translate / Rotate / Reflect / Glide reflect)
  *   8. Spacer
  *   9. Labels toggle
  *   10. Undo / Redo
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

  /** True while any of the four Add-Copy tools is active; drives the trigger's `.active` highlight. */
  private val isAddCopyToolActive: Signal[Boolean] =
    EditorState.toolState.signal.map { s =>

      s.activeTool == Tool.TranslateCopy || s.activeTool == Tool.RotateCopy ||
      s.activeTool == Tool.ReflectCopy || s.activeTool == Tool.GlideReflectCopy
    }.distinct

  /** Add-Copy flyout: a trigger button whose chevron toggles a vertical submenu of the four grow-by-isometry
    * tools. Mirrors the Edit ▸ Add Copy menu submenu; each option is disabled while the tiling can't be
    * mutated. Selecting an option activates that copy mode and closes the flyout; clicking outside it (or the
    * trigger again) also closes it.
    */
  private def addCopyButton(): Element =
    val isOpen = Var(false)

    def option(labelKey: String, shortcut: Option[String], action: => Unit): Element =
      button(
        className := "tool-submenu-option",
        tpe       := "button",
        span(child.text <-- I18n.t(labelKey)),
        shortcut.map(s => span(className := "tool-submenu-shortcut", s)),
        disabled <-- EditorState.canMutateTilingSignal.map(!_),
        onClick.compose(gate) --> { _ =>

          action
          isOpen.set(false)
        }
      )

    div(
      className := "tool-submenu",
      cls("open") <-- isOpen.signal,
      // Close when a click lands outside the flyout (the trigger toggles; options close on select).
      inContext: root =>
        documentEvents(_.onClick) --> { ev =>

          if isOpen.now() && !root.ref.contains(ev.target.asInstanceOf[org.scalajs.dom.Node]) then
            isOpen.set(false)
        },
      button(
        className := "toggle-btn tool-submenu-trigger",
        tpe       := "button",
        cls("active") <-- isAddCopyToolActive,
        title <-- I18n.t("tool.addCopy.title"),
        onClick --> { _ =>

          isOpen.update(!_)
        },
        IconsSVG.quadrupleIcon,
        span(className := "tool-button-label", child.text <-- I18n.t("tool.addCopy")),
        span(className := "tool-submenu-chevron", "▾")
      ),
      div(
        className := "tool-submenu-content",
        option(
          "menu.edit.addCopy.translate",
          Some(MenuShortcuts.labelOf(MenuAction.EditAddCopyTranslate)),
          AppState.enterTranslateCopyMode()
        ),
        option(
          "menu.edit.addCopy.rotate",
          Some(MenuShortcuts.labelOf(MenuAction.EditAddCopyRotate)),
          AppState.enterRotateCopyMode()
        ),
        option(
          "menu.edit.addCopy.reflect",
          Some(MenuShortcuts.labelOf(MenuAction.EditAddCopyReflect)),
          AppState.enterReflectCopyMode()
        ),
        option("menu.edit.addCopy.glide", None, AppState.enterGlideReflectCopyMode())
      )
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
          createToolButton(
            Tool.Measurement,
            "tool.measurement",
            "tool.measurement.title",
            IconsSVG.rulerIcon
          ),
          addCopyButton()
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
//          labelsToggleButton(),
          UndoComponent.element
        )
      )
    )
