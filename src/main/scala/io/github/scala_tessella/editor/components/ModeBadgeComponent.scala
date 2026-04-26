package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.i18n.I18n
import io.github.scala_tessella.editor.models.{AddSubmode, EditorState, Tool}

/** Persistent overlay chip in the canvas top-left that names the active mode (e.g.
  * `Mode: Add Polygon (outside)`, `Mode: Eraser`).
  *
  * Clicking the chip resets to the default mode (`AddPolygon + Outside`) — a one-click escape from any modal
  * tool. It also clears any in-progress measurement / clickable-point setup so the canvas returns to a clean
  * state.
  *
  * On phone widths, this surface is subsumed by the bottom toolbar's mode-switcher (Phase 3); on desktop it
  * floats over the top-left of the canvas.
  */
object ModeBadgeComponent:

  private def labelKey(tool: Tool, sub: AddSubmode): String =
    tool match
      case Tool.AddPolygon          =>
        sub match
          case AddSubmode.Outside => "modeBadge.add.outside"
          case AddSubmode.Inside  => "modeBadge.add.inside"
      case Tool.ColorPicker         => "modeBadge.colorPicker"
      case Tool.ShapeAndColorPicker => "modeBadge.shapeColor"
      case Tool.SelectByColor       => "modeBadge.selectByColor"
      case Tool.Eraser              => "modeBadge.eraser"
      case Tool.Measurement         => "modeBadge.measurement"
      case Tool.Fan                 => "modeBadge.fan"

  private val labelSignal: Signal[String] =
    EditorState.toolState.signal.map(_.activeTool).distinct
      .combineWith(
        EditorState.toolState.signal.map(_.addSubmode).distinct,
        EditorState.localeState.signal
      )
      .map((t, s, _) => I18n.tNow(labelKey(t, s)))

  private val isDefaultMode: Signal[Boolean] =
    EditorState.toolState.signal
      .map(s => s.activeTool == Tool.AddPolygon && s.addSubmode == AddSubmode.Outside)
      .distinct

  private def resetToDefault(): Unit =
    AppState.clearMeasurements()
    EditorState.toolState.update(_.copy(
      activeTool = Tool.AddPolygon,
      addSubmode = AddSubmode.Outside
    ))

  def element: Element =
    div(
      className := "mode-badge",
      cls("mode-badge--default") <-- isDefaultMode,
      title <-- I18n.t("modeBadge.reset.title"),
      onClick --> { _ =>

        resetToDefault()
      },
      span(className := "mode-badge-label", child.text <-- I18n.t("modeBadge.label")),
      span(className := "mode-badge-value", child.text <-- labelSignal)
    )
