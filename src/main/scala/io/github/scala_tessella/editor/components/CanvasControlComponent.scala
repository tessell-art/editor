package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.editor.models.{AppState, ViewTransform}
import scala.math.{max, min}

object CanvasControls:
  def element: Element =
    div(
      className := "canvas-controls",
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
      }),
      transformInfo()
    )

  private def transformInfo(): Element =
    div(
      className := "transform-info",
      child.text <-- AppState.viewTransform.signal.map(t =>
        f"Scale: ${t.scale}%.2f | Rotation: ${t.rotationDegrees}%.0f° | Pan: (${t.panX}%.0f, ${t.panY}%.0f)"
      )
    )