package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{AppState, EditorMode, EditorState, Tool, ViewTransform}

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.tessella.IncrementalTiling.Strictness

import scala.math.{max, min}

object CanvasControlComponent:

  def element: Element =
    div(
      div(
        className := "canvas-controls",
        div(
          className := "control-group",
          button(
            "Fill ",
            svg.svg(
              svg.width := "24",
              svg.height := "24",
              svg.rect(
                svg.width := "18",
                svg.height := "18",
                svg.x := "2",
                svg.y := "2",
                svg.fill <-- EditorState.fillColor.signal.map { case (r, g, b) => f"rgb($r,$g,$b)" }
              )
            ),
            onClick --> { _ =>
              // Use shared state from EditorState
              EditorState.tempColor.set(EditorState.fillColor.now())
              EditorState.showColorPicker.set(true)
            },
            className := "fill-color-btn"
          ),
          button(
            IconsSVG.eyeDropperIcon,
//            "Pick Color",
            className <-- EditorState.activeTool.signal.map {
              case Some(Tool.ColorPicker) => "toggle-btn active"
              case _                      => "toggle-btn"
            },
            styleAttr := "display: inline-flex; align-items: center; gap: 0.3em;",
            onClick --> { _ =>
              EditorState.activeTool.update {
                case Some(Tool.ColorPicker) => None
                case _ =>
                  AppState.clearMeasurements()
                  Some(Tool.ColorPicker)
              }
            },
            title := "Activate color picker to select a color from an existing polygon"
          ),
          button(
            IconsSVG.selectByColorIcon,
//            "Select By Color",
            className <-- EditorState.activeTool.signal.map {
              case Some(Tool.SelectByColor) => "toggle-btn active"
              case _                        => "toggle-btn"
            },
            styleAttr := "display: inline-flex; align-items: center; gap: 0.3em;",
            onClick --> { _ =>
              EditorState.activeTool.update {
                case Some(Tool.SelectByColor) => None
                case _ =>
                  AppState.clearMeasurements()
                  Some(Tool.SelectByColor)
              }
            },
            title := "Activate selector to select all polygons with the same color"
          ),
          button(
            IconsSVG.rulerIcon,
            className := "toggle-btn",
            className <-- EditorState.activeTool.signal.map {
              case Some(Tool.Measurement) => "toggle-btn active"
              case _                      => "toggle-btn"
            },
            styleAttr := "display: inline-flex; align-items: center; gap: 0.3em;",
            onClick --> { _ =>
              EditorState.activeTool.update {
                case Some(Tool.Measurement) =>
                  AppState.clearMeasurements()
                  None
                case _ => Some(Tool.Measurement)
              }
            },
            title := "Activate measure mode to calculate the distance between two points"
          ),
          button(
            child.text <-- EditorState.editorMode.signal.map {
              case EditorMode.Select => "Mode: Select"
              case EditorMode.Delete => "Mode: Delete"
            },
            className <-- EditorState.editorMode.signal.map {
              case EditorMode.Select => "toggle-btn mode-select"
              case EditorMode.Delete => "toggle-btn mode-delete active"
            },
            onClick --> { _ => AppState.toggleEditorMode() },
            title <-- EditorState.editorMode.signal.map {
              case EditorMode.Select => "Click to switch to Delete mode"
              case EditorMode.Delete => "Click to switch to Select mode"
            }
          ),
          //      ),
          //      div(
          //        className := "visualization-controls",
          button(
            child.text <-- EditorState.showNodeLabels.signal.map(show =>
              if show then "Labels: ON" else "Labels: OFF"
            ),
            className <-- EditorState.showNodeLabels.signal.map(show =>
              if show then "toggle-btn active responsive-control" else "toggle-btn responsive-control"
            ),
            onClick --> { _ => AppState.toggleNodeLabels() },
            title <-- EditorState.showNodeLabels.signal.map { show =>
              if show then "Click to hide the node labels" else "Click to show the node labels"
            }
          ),
          button(
            child.text <-- EditorState.strictness.signal.map(s => s"Validation: ${if s == Strictness.STRICT then "ON" else "OFF"}"),
            className <-- EditorState.strictness.signal.map {
              case Strictness.STRICT => "toggle-btn active responsive-control"
              case _                 => "toggle-btn responsive-control"
            },
            onClick --> { _ => AppState.toggleStrictness() },
            title <-- EditorState.strictness.signal.map {
              case Strictness.STRICT => "Validation ON: adding a polygon touching and crossing the edges of the perimeter is not allowed. Click to switch OFF."
              case _                 => "Validation OFF: without validation it is possible to add a polygon that would invalidate the tessellation. Click to switch ON."
            }
          ),
          UndoComponent.element
        )
      )
    )
