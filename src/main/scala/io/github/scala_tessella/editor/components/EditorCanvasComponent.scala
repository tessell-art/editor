package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{EditorMode, EditorState}
import io.github.scala_tessella.editor.interactions.{KeyboardEventHandler, MouseEventHandler}

import com.raquo.laminar.api.L.{*, given}

object EditorCanvasComponent:
  def element: Element =
    div(
      className := "canvas-container",
//      h2("Canvas"),
      CanvasControlComponent.element,
      ErrorMessageComponent.element,
      // Loading indicator
      loadingIndicator(),
      svg.svg(
        svg.className := "editor-canvas",
        svg.width := "800",
        svg.height := "600",
        svg.viewBox := "0 0 800 600",
        svg.tabIndex := "0",

        // Store reference to the canvas element
        onMountCallback(ctx => EditorState.canvasElementRef.set(Some(ctx.thisNode.ref))),

        // Dynamic cursor based on loading state and editor mode
        svg.style <-- EditorState.isProcessing.signal.combineWith(EditorState.editorMode.signal).map {
          case (isProcessing, mode) =>
            if isProcessing then
              "cursor: wait; pointer-events: none;"
            else 
              "cursor: default;"
        },

        // Add grid pattern definition here
        GridRenderer.patternDef,

        // Background
        background(),

        // Main content group with transforms
        contentGroup(),

        // Disable mouse events when processing
        onMouseDown.filter(_ => !EditorState.isProcessing.now()) --> MouseEventHandler.handleMouseDown,
        onMouseMove.filter(_ => !EditorState.isProcessing.now()) --> MouseEventHandler.handleMouseMove,
        onMouseUp.filter(_ => !EditorState.isProcessing.now()) --> MouseEventHandler.handleMouseUp,
        onWheel.filter(_ => !EditorState.isProcessing.now()) --> MouseEventHandler.handleWheel
      )
    )

  private def loadingIndicator(): Element =
    div(
      className := "loading-indicator",
      display <-- EditorState.isProcessing.signal.map(processing => if processing then "block" else "none"),
      div(
        className := "loading-content",
        div(className := "spinner"),
        p("Processing tessellation...")
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
      svg.transform <-- EditorState.viewTransform.signal.map(transform =>
        s"translate(${transform.panX}, ${transform.panY}) scale(${transform.scale}) rotate(${transform.rotationDegrees} 400 300)"
      ),

      // Grid pattern
      GridRenderer.element,

      // Render tessellation if available
      child <-- EditorState.currentTiling.signal.map {
        tiling => TessellationRenderer.renderTiling(tiling)
      },

      // Show message when no tessellation is available
      child.maybe <-- EditorState.currentTiling.signal.map { tiling =>
        if (tiling.isEmpty) Some(noTessellationMessage()) else None
      }
    )

  private def noTessellationMessage(): Element =
    svg.g(
      svg.text(
        svg.x := "400",
        svg.y := "280",
        svg.fontSize := "18",
        svg.fill := "#888",
        svg.textAnchor := "middle",
        svg.fontFamily := "Arial, sans-serif",
        "Empty tessellation"
      ),
      svg.text(
        svg.x := "400",
        svg.y := "310",
        svg.fontSize := "14",
        svg.fill := "#666",
        svg.textAnchor := "middle",
        svg.fontFamily := "Arial, sans-serif",
        "Select a polygon to start a tessellation"
      )
    )