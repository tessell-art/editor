package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.editor.models.{AppState, ViewTransform}
import scala.math.{max, min}

object CanvasControlComponent:
  def element: Element =
    div(
      className := "canvas-controls",
      div(
        className := "control-group",
        button("Reset View", onClick --> { _ => AppState.viewTransform.set(ViewTransform()) }),
        button("Zoom In", onClick --> { _ =>
          AppState.viewTransform.update(t => t.copy(scale = min(t.scale * 1.2, 5.0)))
        }),
        button("Zoom Out", onClick --> { _ =>
          AppState.viewTransform.update(t => t.copy(scale = max(t.scale / 1.2, 0.1)))
        }),
        button("Rotate Left", onClick --> { _ =>
          AppState.viewTransform.update(t => t.withRotation(t.rotationDegrees - 30))
        }),
        button("Rotate Right", onClick --> { _ =>
          AppState.viewTransform.update(t => t.withRotation(t.rotationDegrees + 30))
        })
      ),
      div(
        className := "visualization-controls",
        button(
          child.text <-- AppState.showNodeLabels.signal.map(show =>
            if (show) "Hide Node Labels" else "Show Node Labels"
          ),
          className <-- AppState.showNodeLabels.signal.map(show =>
            if (show) "toggle-btn active" else "toggle-btn"
          ),
          onClick --> { _ => AppState.toggleNodeLabels() }
        )
      ),
      transformInfo()
    )

  private def transformInfo(): Element =
    div(
      className := "transform-info",
      child.text <-- AppState.viewTransform.signal.map(t =>
        f"Zoom: ${t.scale*100}%.0f${'%'} | Rotation: ${t.rotationDegrees}%.0f°"
      )
    )