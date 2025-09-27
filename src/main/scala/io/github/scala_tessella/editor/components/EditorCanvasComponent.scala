package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L._
import io.github.scala_tessella.editor.interactions.{MouseEventHandler, TouchEventHandler}
import io.github.scala_tessella.editor.models.EditorState

object EditorCanvasComponent:

  private def distanceString(distance: Double): String =
    f"Distance: $distance%.6f units"

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
          child.text <-- EditorState.currentFileName.signal.combineWith(
            EditorState.measurementResult.signal
          ).map { (maybeName, maybeDistance) =>

            maybeDistance match
              case Some(_) => ""
              case None    => maybeName.getOrElse("untitled")
          }
        ),
        div(
          className := "measurement-result",
          child <-- EditorState.measurementResult.signal
            .combineWith(EditorState.measurementAngle.signal)
            .combineWith(EditorState.isAngleShownInRad.signal)
            .map {
              case (None, _, _)                         => ""
              case (Some(distance), None, _)            => distanceString(distance)
              case (Some(distance), Some(angle), isRad) =>
                val angleText    =
                  if isRad then f"Angle: $angle%.6f rad" else f"Angle: ${angle * 180 / Math.PI}%.2f°"
                val distancePart = distanceString(distance)
                span(
                  span(
                    onClick --> { _ =>

                      EditorState.isAngleShownInRad.update(!_)
                    },
                    title     := "Click to toggle radians/degrees",
                    className := "angle-toggle",
                    angleText
                  ),
                  span(s" · $distancePart")
                )
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
          svg.viewBox   := "0 0 800 600",
          svg.tabIndex  := "0",

          // Store reference to the canvas element
          onMountCallback(ctx => EditorState.canvasElementRef.set(Some(ctx.thisNode.ref))),

          // Dynamic cursor and interactivity derived as a Signal
          {
            val canvasInteractivityStyle: Signal[String] =
              EditorState.isProcessing.signal.map { isProcessing =>

                if isProcessing then
                  "cursor: wait; pointer-events: none;"
                else
                  "cursor: default;"
              }
            svg.style <-- canvasInteractivityStyle
          },

          // Add grid pattern definition here
          GridRenderer.patternDef,

          // Background
          //          background(),

          // Main content group with transforms
          contentGroup(),

          // Disable mouse events when processing, without using .now()
          onMouseDown.compose(
            _.withCurrentValueOf(EditorState.isProcessing.signal)
              .collect { case (e, false) =>
                e
              }
          ) --> MouseEventHandler.handleMouseDown,
          onMouseMove.compose(
            _.withCurrentValueOf(EditorState.isProcessing.signal)
              .collect { case (e, false) =>
                e
              }
          ) --> MouseEventHandler.handleMouseMove,
          onMouseUp.compose(
            _.withCurrentValueOf(EditorState.isProcessing.signal)
              .collect { case (e, false) =>
                e
              }
          ) --> MouseEventHandler.handleMouseUp,
          onWheel.compose(
            _.withCurrentValueOf(EditorState.isProcessing.signal)
              .collect { case (e, false) =>
                e
              }
          ) --> MouseEventHandler.handleWheel,

          // Touch events for mobile support (also gated)
          onTouchStart.compose(
            _.withCurrentValueOf(EditorState.isProcessing.signal)
              .collect { case (e, false) =>
                e
              }
          ) --> TouchEventHandler.handleTouchStart,
          onTouchMove.compose(
            _.withCurrentValueOf(EditorState.isProcessing.signal)
              .collect { case (e, false) =>
                e
              }
          ) --> TouchEventHandler.handleTouchMove,
          onTouchEnd.compose(
            _.withCurrentValueOf(EditorState.isProcessing.signal)
              .collect { case (e, false) =>
                e
              }
          ) --> TouchEventHandler.handleTouchEnd,
          onTouchCancel.compose(
            _.withCurrentValueOf(EditorState.isProcessing.signal)
              .collect { case (e, false) =>
                e
              }
          ) --> TouchEventHandler.handleTouchCancel
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
      svg.x           := "0",
      svg.y           := "0",
      svg.width       := "800",
      svg.height      := "600",
      svg.fill        := "#1a1a1a",
      svg.stroke      := "#333",
      svg.strokeWidth := "2"
    )

  private def contentGroup(): Element =
    svg.g(
      svg.transform <-- EditorState.viewTransform.signal.map(transform =>
        s"translate(${transform.pan.xx}, ${transform.pan.yy}) scale(${transform.scale}) rotate(${transform.rotationDegrees} 400 300)"
      ),

      // Grid pattern
      GridRenderer.element,

      // Render tessellation if available
      child <-- EditorState.currentTiling.signal.map {
        tiling =>

          TessellationRenderer.renderTiling(tiling)
      },

      // Show message when no tessellation is available
      child.maybe <-- EditorState.currentTiling.signal.map { tiling =>

        if tiling.isEmpty then
          Some(noTessellationMessage())
        else if tiling.innerFaces.size == 1 && tiling.innerFaces.head.hasEqualAngles.toOption.get then
          Some(onePolygonMessage())
        else None
      }
    )

  private def createSvgText(x: String, y: String, fontSize: String, fill: String, content: String): Element =
    svg.text(
      svg.x          := x,
      svg.y          := y,
      svg.fontSize   := fontSize,
      svg.fill       := fill,
      svg.textAnchor := "middle",
      svg.fontFamily := "Arial, sans-serif",
      content
    )

  private def canvasMessage(title: String, subTitle: String): Element =
    svg.g(
      createSvgText("425", "250", "18", "#888", title),
      createSvgText("425", "280", "14", "#666", subTitle)
    )

  private def noTessellationMessage(): Element =
    canvasMessage("Empty tessellation", "Select a polygon to start a tessellation")

  private def onePolygonMessage(): Element =
    canvasMessage("Add the next polygon", "Click on any perimeter edge to grow the tessellation")
