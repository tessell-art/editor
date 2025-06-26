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
            "Fill Color ",
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
            "Pick Color",
            className <-- EditorState.isEyedropperActive.signal.map(active =>
              if (active) "toggle-btn active" else "toggle-btn"
            ),
            onClick --> { _ =>
              EditorState.isEyedropperActive.update(!_)
            },
            title := "Activate color picker to select a color from an existing polygon"
          ),
          //      ),
          //      div(
          //        className := "visualization-controls",
          button(
            child.text <-- EditorState.showNodeLabels.signal.map(show =>
              if (show) "Hide Node Labels" else "Show Node Labels"
            ),
            className <-- EditorState.showNodeLabels.signal.map(show =>
              if (show) "toggle-btn active" else "toggle-btn"
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