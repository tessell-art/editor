package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.i18n.I18n
import io.github.scala_tessella.editor.models.EditorState

/** Tiling-info panel — collapsible right-side rail / pull-up sheet that surfaces basic facts about the
  * current tiling.
  *
  * Contents:
  *   - Vertex count
  *   - Face (polygon) count
  *   - Edge count
  *   - Distinct full-vertex configurations (e.g. `3.6.3.6 — 12 vertices`), filtered to exclude non-360°
  *     boundary vertices and configurations containing a 180° term.
  *
  * Visibility is driven by `viewState.showTilingInfo` — toggled from the View menu and from a tool-strip
  * icon. The panel is always rendered (no `child.maybe`) so its open/close transition stays smooth; CSS
  * controls visibility via the `--open` modifier class.
  */
object TilingInfoPanel:

  def element: Element =
    div(
      className <-- EditorState.viewState.signal.map(_.showTilingInfo).distinct.map: open =>
        if open then "tiling-info-panel tiling-info-panel--open" else "tiling-info-panel",
      header(),
      counts(),
      vertexConfigurations()
    )

  private def header(): Element =
    div(
      className := "tiling-info-header",
      span(className := "tiling-info-title", child.text <-- I18n.t("info.title")),
      button(
        className    := "tiling-info-close",
        title <-- I18n.t("info.close"),
        onClick --> { _ =>

          AppState.toggleShowTilingInfo()
        },
        "✕"
      )
    )

  private def counts(): Element =
    div(
      className := "tiling-info-counts",
      countRow("info.vertices", EditorState.vertexCountSignal),
      countRow("info.faces", EditorState.faceCountSignal),
      countRow("info.edges", EditorState.edgeCountSignal)
    )

  private def countRow(labelKey: String, valueSignal: Signal[Int]): Element =
    div(
      className := "tiling-info-row",
      span(className := "tiling-info-label", child.text <-- I18n.t(labelKey)),
      span(className := "tiling-info-value", child.text <-- valueSignal.map(_.toString))
    )

  private def vertexConfigurations(): Element =
    div(
      className := "tiling-info-vertex-types",
      div(className := "tiling-info-section-title", child.text <-- I18n.t("info.vertexTypes")),
      div(
        className   := "tiling-info-vertex-list",
        children <-- EditorState.fullVertexConfigurationsSignal.map { configs =>

          if configs.isEmpty then List(emptyConfigPlaceholder())
          else
            configs.map { case (config, count) =>
              configRow(config, count)
            }
        }
      )
    )

  private def configRow(config: String, count: Int): Element =
    div(
      className := "tiling-info-row",
      span(className := "tiling-info-config", config),
      span(className := "tiling-info-count", s"× $count")
    )

  private def emptyConfigPlaceholder(): Element =
    div(
      className := "tiling-info-empty",
      child.text <-- I18n.t("info.vertexConfigs.empty")
    )
