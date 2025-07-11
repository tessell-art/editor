package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{EditorMode, EditorState}
import io.github.scala_tessella.editor.interactions.{KeyboardEventHandler, MouseEventHandler, TouchEventHandler}

import com.raquo.laminar.api.L.{*, given}

object EditorCanvasComponent:
  def element: Element =
    div(
      className := "canvas-container",
      //      h2("Canvas"),
      CanvasControlComponent.element,
      // Loading indicator
      loadingIndicator(),
      div(
        className := "file-and-measurement-container",
        div(
          className := "file-name",
          child.text <-- EditorState.currentFileName.signal.combineWith(EditorState.measurementResult.signal).map { (maybeName, maybeDistance) =>
            maybeDistance match
              case Some(_) => ""
              case None => maybeName.getOrElse("untitled")
          }
        ),
        div(
          className := "measurement-result",
          child.text <-- EditorState.measurementResult.signal.combineWith(EditorState.measurementAngle.signal).map { (maybeDistance, maybeAngle) =>
            maybeDistance match
              case None => ""
              case Some(distance) => f"Distance: $distance%.6f units" + {
                maybeAngle match
                  case None => ""
                  case Some(angle) => f" · Angle: $angle%.6f rad"
              }
          }
        )
      ),
        // A new wrapper for the SVG and its overlays
      div(
        className := "editor-canvas-wrapper",
        ErrorMessageComponent.element,
        svg.svg(
          svg.className := "editor-canvas",
          // The fixed width and height have been removed to allow CSS to control the size
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
//          background(),

          // Main content group with transforms
          contentGroup(),

          // Disable mouse events when processing
          onMouseDown.filter(_ => !EditorState.isProcessing.now()) --> MouseEventHandler.handleMouseDown,
          onMouseMove.filter(_ => !EditorState.isProcessing.now()) --> MouseEventHandler.handleMouseMove,
          onMouseUp.filter(_ => !EditorState.isProcessing.now()) --> MouseEventHandler.handleMouseUp,
          onWheel.filter(_ => !EditorState.isProcessing.now()) --> MouseEventHandler.handleWheel,

          // Touch events for mobile support
          onTouchStart.filter(_ => !EditorState.isProcessing.now()) --> TouchEventHandler.handleTouchStart,
          onTouchMove.filter(_ => !EditorState.isProcessing.now()) --> TouchEventHandler.handleTouchMove,
          onTouchEnd.filter(_ => !EditorState.isProcessing.now()) --> TouchEventHandler.handleTouchEnd,
          onTouchCancel.filter(_ => !EditorState.isProcessing.now()) --> TouchEventHandler.handleTouchCancel
        ),
        // HTML placeholder text is now inside the wrapper
        div(
          child.text <-- EditorState.viewTransform.signal.map(t =>
            f"Zoom: ${t.scale * 100}%.0f${'%'} · Rotation: ${t.rotationDegrees}%.0f°"
          ),
          className := "zoom-rotation"
        )
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
        if tiling.isEmpty then Some(noTessellationMessage())
        else if tiling.orientedPolygons.size == 1 then Some(onePolygonMessage())
        else  None
      }
    )

  private def canvasMessage(title: String, subTitle: String): Element =
    svg.g(
      svg.text(
        svg.x := "425",
        svg.y := "250",
        svg.fontSize := "18",
        svg.fill := "#888",
        svg.textAnchor := "middle",
        svg.fontFamily := "Arial, sans-serif",
        title
      ),
      svg.text(
        svg.x := "425",
        svg.y := "280",
        svg.fontSize := "14",
        svg.fill := "#666",
        svg.textAnchor := "middle",
        svg.fontFamily := "Arial, sans-serif",
        subTitle
      )
    )

  private def noTessellationMessage(): Element =
    canvasMessage("Empty tessellation", "Select a polygon to start a tessellation")

  private def onePolygonMessage(): Element =
    canvasMessage("Add the next polygon", "Click on any perimeter edge to grow the tessellation")
