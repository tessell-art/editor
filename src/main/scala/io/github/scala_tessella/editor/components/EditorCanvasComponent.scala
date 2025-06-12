package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.editor.models.AppState
import io.github.scala_tessella.editor.interactions.{KeyboardHandler, MouseHandler}

object EditorCanvasComponent:
  def element: Element =
    div(
      className := "canvas-container",
      h2("Canvas"),
      CanvasControlComponent.element,
      ErrorMessage.element, // Add error message display
      svg.svg(
        svg.className := "editor-canvas",
        svg.width := "800",
        svg.height := "600",
        svg.viewBox := "0 0 800 600",
        svg.tabIndex := "0", // Make focusable for keyboard events

        // Store reference to the canvas element
        onMountCallback(ctx => AppState.canvasElementRef.set(Some(ctx.thisNode.ref))),

        // Background
        background(),

        // Main content group with transforms
        contentGroup(),

        onMouseDown --> MouseHandler.handleMouseDown,
        onMouseMove --> MouseHandler.handleMouseMove,
        onMouseUp --> MouseHandler.handleMouseUp,
        onWheel --> MouseHandler.handleWheel,
        onKeyDown --> KeyboardHandler.handleKeyDown
      )
    )

  private def background(): Element =
    svg.rect(
      svg.x := "0", svg.y := "0",
      svg.width := "800", svg.height := "600",
      svg.fill := "#1a1a1a",
      svg.stroke := "#333",
      svg.strokeWidth := "2"
    )

  private def contentGroup(): Element =
    svg.g(
      svg.transform <-- AppState.viewTransform.signal.map(transform =>
        s"translate(${transform.panX}, ${transform.panY}) scale(${transform.scale}) rotate(${transform.rotationDegrees} 400 300)"
      ),

      // Grid pattern
      GridRenderer.element,

      // Render tessellation if available
      child.maybe <-- AppState.currentTiling.signal.map(_.map(TessellationRenderer.renderTiling)),

      // Render individual polygons (if not showing tessellation)
      children <-- Signal.combine(AppState.currentTiling.signal, AppState.canvasPolygons.signal).map {
        case (tiling, polygons) =>
          if (tiling.isEmpty) polygons.map(PolygonRenderer.renderCanvasPolygon) else List.empty
      },

      // Render texts
      children <-- AppState.canvasTexts.signal.map(_.map(PolygonRenderer.renderCanvasText)),

      // Render connection points for individual polygons
      children <-- Signal.combine(AppState.currentTiling.signal, AppState.canvasPolygons.signal).map {
        case (tiling, polygons) =>
          if (tiling.isEmpty) polygons.flatMap(PolygonRenderer.renderPolygonPoints) else List.empty
      }
    )