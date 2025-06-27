package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{AppState, EditorMode, EditorState, ViewTransform}

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.tessella.IncrementalTiling.Strictness

import scala.math.{max, min}

object CanvasControlComponent:
  // Local state for color picker is no longer needed here

  def element: Element =
    div(
      div(
        className := "top-bar-info",
        div(
          className := "current-file-name",
          child.text <-- EditorState.currentFileName.signal.map(_.getOrElse("untitled"))
        ),
        transformInfo()
      ),
      div(
        className := "canvas-controls",
        div(
          className := "control-group",
          //        button("Reset View", onClick --> { _ => EditorState.viewTransform.set(ViewTransform()) }),
          //        button("Zoom In", onClick --> { _ =>
          //          EditorState.viewTransform.update(t => t.copy(scale = min(t.scale * 1.2, 5.0)))
          //        }),
          //        button("Zoom Out", onClick --> { _ =>
          //          EditorState.viewTransform.update(t => t.copy(scale = max(t.scale / 1.2, 0.1)))
          //        }),
          //        button("Rotate Left", onClick --> { _ =>
          //          EditorState.viewTransform.update(t => t.withRotation(t.rotationDegrees - 30))
          //        }),
          //        button("Rotate Right", onClick --> { _ =>
          //          EditorState.viewTransform.update(t => t.withRotation(t.rotationDegrees + 30))
          //        }),
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
            "Pick Color",
            className <-- EditorState.isEyedropperActive.signal.map(active =>
              if active then "toggle-btn active" else "toggle-btn"
            ),
            styleAttr := "display: inline-flex; align-items: center; gap: 0.3em;",
            onClick --> { _ =>
              EditorState.isEyedropperActive.update(!_)
            },
            title := "Activate color picker to select a color from an existing polygon"
          ),
          button(
            "Select By Color",
            className <-- EditorState.isColorSelectorActive.signal.map(active =>
              if active then "toggle-btn active" else "toggle-btn"
            ),
            onClick --> { _ =>
              EditorState.isColorSelectorActive.update(!_)
            },
            title := "Activate selector to select all polygons with the same color"
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
            child.text <-- EditorState.strictness.signal.map(s => s"Strictness: ${s.toString.toLowerCase.capitalize}"),
            className := "toggle-btn",
            onClick --> { _ => AppState.toggleStrictness() },
            title <-- EditorState.strictness.signal.map {
              case Strictness.STRICT => "Current mode is STRICT: prevents adding a polygon that would invalidate the tessellation. Click to switch to TOUCHING."
              case Strictness.TOUCHING => "Current mode is TOUCHING: allows adding a polygon sharing non-continuous edges with the perimeter. Click to switch to CROSSING."
              case Strictness.CROSSING => "Current mode is CROSSING: allows adding a polygon crossing the edges of the perimeter. Click to switch to STRICT."
            }
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
          )
        ),
        UndoComponent.element
        // ColorPickerPopupComponent is no longer rendered here, it will be moved to the main view
      )
    )

  private def transformInfo(): Element =
    div(
      className := "transform-info",
      child.text <-- EditorState.viewTransform.signal.map(t =>
        f"Zoom: ${t.scale * 100}%.0f${'%'} | Rotation: ${t.rotationDegrees}%.0f°"
      )
    )