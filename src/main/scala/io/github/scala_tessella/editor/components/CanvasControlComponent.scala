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
            styleAttr := "margin-left: 8px; border: 1px solid #888; background: #222; color: white; display: inline-flex; align-items: center; gap: 0.3em; padding: 0.3em 0.9em; border-radius: 5px"
          ),
          button(
            svg.svg(
              svg.width := "1em",
              svg.height := "1em",
              svg.viewBox := "0 0 56 56",
              svg.path(
                svg.fill := "currentColor",
                svg.d := "M 39.6485 28.9024 L 40.3048 28.1758 C 41.4532 26.9805 41.5001 25.5742 40.2813 24.3555 L 39.5782 23.6758 C 43.1641 20.4649 47.1485 20.0195 49.6095 17.5117 C 53.1015 13.9961 51.9533 9.0742 49.5159 6.6133 C 47.0780 4.1289 42.2032 3.0742 38.6172 6.5195 C 36.0860 8.9571 35.6641 12.9649 32.4532 16.5508 L 31.7735 15.8476 C 30.5548 14.6289 29.1485 14.6758 27.9532 15.8242 L 27.2266 16.4805 C 25.7969 17.8633 26.0548 19.0820 27.2969 20.3242 L 28.2813 21.3086 L 10.5860 39.0274 C 3.3438 46.2695 6.8360 45.1445 2.8985 50.6992 L 4.9845 52.9258 C 10.3750 49.0117 9.6485 52.8555 16.9845 45.5195 L 34.7969 27.8242 L 35.8048 28.8320 C 37.0469 30.0742 38.2657 30.3320 39.6485 28.9024 Z M 10.1172 46.1289 C 9.2501 45.1914 9.4141 44.3008 10.3516 43.3633 L 30.2969 23.3242 L 32.8516 25.8789 L 12.8126 45.8945 C 11.9923 46.7383 10.9141 46.9961 10.1172 46.1289 Z"
              )
            ),
//            "Pick Color",
            className <-- EditorState.activeTool.signal.map {
              case Some(Tool.ColorPicker) => "toggle-btn active"
              case _                      => "toggle-btn"
            },
            styleAttr := "display: inline-flex; align-items: center; gap: 0.3em;",
            onClick --> { _ =>
              EditorState.activeTool.update {
                case Some(Tool.ColorPicker) => None
                case _                      => Some(Tool.ColorPicker)
              }
            },
            title := "Activate color picker to select a color from an existing polygon"
          ),
          button(
            svg.svg(
              svg.width := "1em",
              svg.height := "1em",
              svg.viewBox := "0 0 37.643265 44.674143",
              svg.fill := "currentColor",
              svg.path(
                svg.d := "M 15.302,0 C 6.85,0 0,6.309 0,14.09 c 0,7.781 6.85,14.092 15.302,14.092 1.519,-8.259 4.996,-9.012 8.362,-9.012 0.751,0 1.497,0.038 2.214,0.038 2.521,0 4.687,-0.463 5.502,-4.646 C 32.744,7.586 23.752,0 15.302,0 Z m 14.335958,14.790305 c -0.744518,2.094393 -0.955291,2.261786 -3.024775,2.620009 -0.933269,0.161547 -0.832255,0.05748 -1.541035,-0.01983 -0.399,-0.01 -1.037565,-0.119539 -1.441565,-0.119539 -3.879,0 -7.639278,1.034464 -9.861278,8.777464 C 6.2285932,24.929856 1.9315932,19.753102 1.9315932,14.357102 c 0,-6.1150003 4.4505932,-12.4255088 13.5039578,-12.5590596 4.028562,-0.059427 9.877508,3.1268559 12.564508,6.3888559 0.901,1.0939997 2.079899,4.3374067 1.637899,6.6034067 z"
              ),
              svg.path(
                svg.d := "m 10.26,15.943 c -1.565,0 -2.839,1.273 -2.839,2.839 0,1.566 1.273,2.839 2.839,2.839 1.564,0 2.838,-1.273 2.838,-2.839 0,-1.566 -1.273,-2.839 -2.838,-2.839 z m 0,4.178 c -0.738,0 -1.339,-0.602 -1.339,-1.339 0,-0.738 0.601,-1.339 1.339,-1.339 0.737,0 1.338,0.602 1.338,1.339 0,0.737 -0.6,1.339 -1.338,1.339 z"
              ),
              svg.circle(svg.cx := "8.467", svg.cy := "11.012", svg.r := "2.0880001"),
              svg.circle(svg.cx := "13.296", svg.cy := "7.2950001", svg.r := "2.089"),
              svg.circle(svg.cx := "19.381001", svg.cy := "8.7869997", svg.r := "2.089"),
              svg.circle(svg.cx := "24.089001", svg.cy := "12.497", svg.r := "2.089"),
              svg.g(
                svg.transform := "matrix(0.09071207,0,0,0.09071207,11.351823,13.156144)",
                svg.polygon(
                  svg.points := "57.617,303.138 123.48,224.061 181.017,347.451 244.459,317.867 186.921,194.478 289.834,194.854 57.617,0 "
                )
              )
            ),
//            "Select By Color",
            className <-- EditorState.activeTool.signal.map {
              case Some(Tool.SelectByColor) => "toggle-btn active"
              case _                        => "toggle-btn"
            },
            styleAttr := "display: inline-flex; align-items: center; gap: 0.3em;",
            onClick --> { _ =>
              EditorState.activeTool.update {
                case Some(Tool.SelectByColor) => None
                case _                        => Some(Tool.SelectByColor)
              }
            },
            title := "Activate selector to select all polygons with the same color"
          ),
          button(
            svg.svg(
              svg.width := "1em",
              svg.height := "1em",
              svg.viewBox := "-871 1129 256 256",
              svg.fill := "currentColor",
              svg.path(
                svg.d := "M-871,1185.5l199.2,199.7l56.8-56.7l-199.2-199.7L-871,1185.5z M-627,1328.5l-36.3,36.3l-187.3-187.7l36.4-36.2l25.4,25.4 l-11.2,11.2l6,6l11.2-11.2l12,12l-17.2,17.2l6,6l17.2-17.2l12,12l-11.2,11.2l6,6l11.2-11.2l12,12l-17.2,17.2l6,6l17.2-17.2l12,12 l-11.2,11.2l6,6l11.2-11.2l12,12l-17.2,17.2l6,6l17.2-17.2l12,12l-11.2,11.2l6,6l11.2-11.2l12,12l-17.2,17.2l6,6l17.2-17.2l12,12 l-11.2,11.2l6,6l11.2-11.2L-627,1328.5z M-820.3,1165.2c3.1,3,3.2,8,0.2,11.2c-3,3.1-8,3.2-11.2,0.2c-3.1-3-3.2-8-0.2-11.2 C-828.5,1162.3-823.5,1162.2-820.3,1165.2z"
              )
            ),
            className := "toggle-btn",
            className <-- EditorState.activeTool.signal.map {
              case Some(Tool.Measurement) => "toggle-btn active"
              case _                      => "toggle-btn"
            },
            styleAttr := "display: inline-flex; align-items: center; gap: 0.3em;",
            onClick --> { _ =>
              EditorState.activeTool.update {
                case Some(Tool.Measurement) =>
                  EditorState.clickablePoints.set(Nil)
                  EditorState.measurementStartNode.set(None)
                  EditorState.highlightedPolygonId.set(None)
                  EditorState.measurementResult.set(None)
                  None
                case _ => Some(Tool.Measurement)
              }
            },
            title := "Activate measure mode"
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
              if show then "Hide Labels" else "Show Labels"
            ),
            className <-- EditorState.showNodeLabels.signal.map(show =>
              if show then "toggle-btn active" else "toggle-btn"
            ),
            onClick --> { _ => AppState.toggleNodeLabels() }
          ),
          button(
            child.text <-- EditorState.strictness.signal.map(s => s"Validation: ${if s == Strictness.STRICT then "ON" else "OFF"}"),
            className <-- EditorState.strictness.signal.map {
              case Strictness.STRICT => "toggle-btn"
              case _                 => "toggle-btn active"
            },
            onClick --> { _ => AppState.toggleStrictness() },
            title <-- EditorState.strictness.signal.map {
              case Strictness.STRICT => "Current mode is STRICT: prevents adding a polygon that would invalidate the tessellation. Click to switch to CROSSING."
              case _                 => "Current mode is CROSSING: allows adding a polygon touching and crossing the edges of the perimeter. Click to switch to STRICT."
            }
          ),
          UndoComponent.element
        )
      )
    )
