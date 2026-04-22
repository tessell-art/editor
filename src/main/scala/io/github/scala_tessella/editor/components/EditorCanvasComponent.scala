package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.interactions.{MouseEventHandler, TouchEventHandler}
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.geo.{LineSegment, Point}
import io.github.scala_tessella.editor.utils.SvgDsl.{rectCoords, textCoords}

object EditorCanvasComponent:

  private def distanceString(distance: Double): String =
    f"Distance: $distance%.6f units"

  def element: Element =
    val fileNameDisplaySignal =
      EditorState.fileState.signal.map(_.currentFileName).distinct.combineWith(EditorState.measurementState.signal.map(_.measurementResult).distinct).map:
        (maybeName, maybeDistance) =>
          maybeDistance match
            case Some(_) => ""
            case None    => maybeName.getOrElse("untitled")

    val measurementDisplaySignal =
      EditorState.measurementState.signal.map(_.measurementResult).distinct
        .combineWith(EditorState.measurementState.signal.map(_.measurementAngle).distinct, EditorState.measurementState.signal.map(_.isAngleShownInRad).distinct)
        .map:
          case (None, _, _)                         => None
          case (Some(distance), None, _)            => Some(span(distanceString(distance)))
          case (Some(distance), Some(angle), isRad) =>
            val angleText    =
              if isRad then f"Angle: ${angle.toDouble}%.6f rad" else f"Angle: ${angle.toDegrees}%.2f°"
            val distancePart = distanceString(distance)
            Some(span(
              span(
                onClick --> { _ =>

                  EditorState.measurementState.update(s => s.copy(isAngleShownInRad = !s.isAngleShownInRad))
                },
                title     := "Click to toggle radians/degrees",
                className := "angle-toggle",
                angleText
              ),
              span(s" · $distancePart")
            ))

    div(
      className := "canvas-container",
      //      h2("Canvas"),
      CanvasControlComponent.element,
      // Loading indicator
      child.maybe <-- EditorState.uiState.signal.map(_.isProcessing).distinct.map: processing =>
        if processing then Some(loadingIndicator()) else None,
      div(
        className := "file-and-measurement-container",
        div(
          className := "file-name",
          child.text <-- fileNameDisplaySignal
        ),
        div(
          className := "measurement-result",
          child.maybe <-- measurementDisplaySignal
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
          onMountCallback: ctx =>
            EditorState.uiState.update(_.copy(canvasElementRef = Some(ctx.thisNode.ref))),
          onUnmountCallback: _ =>
            EditorState.uiState.update(_.copy(canvasElementRef = None)),

          // Dynamic cursor and interactivity derived as a Signal
          {
            val canvasInteractivityStyle: Signal[String] =
              EditorState.uiState.signal.map(_.isProcessing).distinct.map: isProcessing =>
                if isProcessing then
                  "cursor: wait; pointer-events: none;"
                else
                  "cursor: default;"
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
            _.withCurrentValueOf(EditorState.uiState.signal.map(_.isProcessing).distinct)
              .collect:
                case (e, false) => e
          ) --> MouseEventHandler.handleMouseDown,
          onMouseMove.compose(
            _.withCurrentValueOf(EditorState.uiState.signal.map(_.isProcessing).distinct)
              .collect:
                case (e, false) => e
          ) --> MouseEventHandler.handleMouseMove,
          onMouseUp.compose(
            _.withCurrentValueOf(EditorState.uiState.signal.map(_.isProcessing).distinct)
              .collect:
                case (e, false) => e
          ) --> MouseEventHandler.handleMouseUp,
          onWheel.compose(
            _.withCurrentValueOf(EditorState.uiState.signal.map(_.isProcessing).distinct)
              .collect:
                case (e, false) => e
          ) --> MouseEventHandler.handleWheel,

          // Touch events for mobile support (also gated)
          onTouchStart.compose(
            _.withCurrentValueOf(EditorState.uiState.signal.map(_.isProcessing).distinct)
              .collect:
                case (e, false) => e
          ) --> TouchEventHandler.handleTouchStart,
          onTouchMove.compose(
            _.withCurrentValueOf(EditorState.uiState.signal.map(_.isProcessing).distinct)
              .collect:
                case (e, false) => e
          ) --> TouchEventHandler.handleTouchMove,
          onTouchEnd.compose(
            _.withCurrentValueOf(EditorState.uiState.signal.map(_.isProcessing).distinct)
              .collect:
                case (e, false) => e
          ) --> TouchEventHandler.handleTouchEnd,
          onTouchCancel.compose(
            _.withCurrentValueOf(EditorState.uiState.signal.map(_.isProcessing).distinct)
              .collect:
                case (e, false) => e
          ) --> TouchEventHandler.handleTouchCancel
        ),
        // HTML placeholder text is now inside the wrapper
        div(
          child.text <-- EditorState.viewState.signal.map(_.viewTransform).distinct.map(t =>
            f"Zoom: ${t.scale * 100}%.0f${'%'} · Rotation: ${t.rotationDegrees}°"
          ),
          className := "zoom-rotation"
        )
      )
    )

  private def loadingIndicator(): Element =
    div(
      className := "loading-indicator",
      div(
        className := "loading-content",
        div(className := "spinner"),
        p(
          child.text <-- EditorState.uiState.signal.map(_.loadingMessage).distinct.map:
            _.getOrElse("Processing tessellation...")
        )
      )
    )

  private def background(): Element =
    svg.rect(
      rectCoords(LineSegment(Point(0, 0), Point(800, 600))),
      svg.fill        := "#1a1a1a",
      svg.stroke      := "#333",
      svg.strokeWidth := "2"
    )

  private def contentGroup(): Element =
    val animationAndTilingSignal =
      EditorState.animationState.signal.map(_.mirrorAnimation).distinct
        .combineWith(
          EditorState.animationState.signal.map(_.doublingAnimation).distinct,
          EditorState.animationState.signal.map(_.fanAnimation).distinct,
          EditorState.tessellationState.signal.map(_.currentTiling).distinct
        )

    svg.g(
      svg.transform <-- EditorState.viewState.signal.map(_.viewTransform).distinct.map(transform =>
        s"translate(${transform.pan.x}, ${transform.pan.y}) scale(${transform.scale}) rotate(${transform.rotationDegrees} 400 300)"
      ),

      // Grid pattern
      GridRenderer.element,

      // Render tessellation (or animations, if active)
      child <-- animationAndTilingSignal.map: (mirrorOpt, doubleOpt, fanOpt, tiling) =>
        mirrorOpt match
          case Some(animation) => TessellationRenderer.renderMirrorAnimation(animation)
          case None            =>
            doubleOpt match
              case Some(animation) => TessellationRenderer.renderDoublingAnimation(animation)
              case None            =>
                fanOpt match
                  case Some(animation) => TessellationRenderer.renderFanAnimation(animation)
                  case None            => TessellationRenderer.renderTiling(tiling),

      // Show message when no tessellation is available
      child.maybe <-- animationAndTilingSignal.map: (mirrorOpt, doubleOpt, fanOpt, tiling) =>
        if mirrorOpt.isDefined || doubleOpt.isDefined || fanOpt.isDefined then None
        else if tiling.isEmpty then
          Some(noTessellationMessage())
        else if tiling.innerFaces.size == 1 && tiling.innerFaces.head.hasEqualAngles.toOption.contains(true) then
          Some(onePolygonMessage())
        else None
    )

  private def createSvgText(point: Point, fontSize: Int, fill: String, content: String): Element =
    svg.text(
      textCoords(point),
      svg.fontSize   := fontSize.toString,
      svg.fill       := fill,
      svg.textAnchor := "middle",
      svg.fontFamily := "Arial, sans-serif",
      content
    )

  private def canvasMessage(title: String, subTitle: String): Element =
    svg.g(
      createSvgText(Point(425, 250), 18, "#888", title),
      createSvgText(Point(425, 280), 14, "#666", subTitle)
    )

  private def noTessellationMessage(): Element =
    canvasMessage("Empty tessellation", "Select a polygon to start a tessellation")

  private def onePolygonMessage(): Element =
    canvasMessage("Add the next polygon", "Click on any perimeter edge to grow the tessellation")
