package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.i18n.I18n
import io.github.scala_tessella.editor.interactions.{MouseEventHandler, TouchEventHandler}
import io.github.scala_tessella.editor.models.{AddSubmode, EditorState, Tool}

object EditorCanvasComponent:

  def element: Element =
    div(
      className := "canvas-container",
      CanvasControlComponent.element,
      // Loading indicator
      child.maybe <-- EditorState.uiState.signal.map(_.isProcessing).distinct.map: processing =>
        if processing then Some(loadingIndicator()) else None,
      // A new wrapper for the SVG and its overlays
      div(
        className := "editor-canvas-wrapper",
        ErrorMessageComponent.element,
        ModeBadgeComponent.element,
        // Empty-state card shown when no tessellation exists
        child.maybe <-- EditorState.isTilingEmptySignal.map: isEmpty =>
          if isEmpty then Some(EmptyStateCardComponent.element) else None,
        svg.svg(
          svg.className <-- canvasClassNameSignal,
          // The fixed width and height have been removed to allow CSS to control the size
          svg.viewBox  := "0 0 800 600",
          svg.tabIndex := "0",

          // Store reference to the canvas element
          onMountCallback: ctx =>
            EditorState.uiState.update(_.copy(canvasElementRef = Some(ctx.thisNode.ref))),
          onUnmountCallback: _ =>
            EditorState.uiState.update(_.copy(canvasElementRef = None)),

          // While processing, the canvas freezes — wait cursor, no pointer events. The per-mode
          // cursor (driven by `canvasClassNameSignal` above) takes over once processing clears.
          svg.style <-- EditorState.uiState.signal.map(_.isProcessing).distinct.map: isProcessing =>
            if isProcessing then "cursor: wait; pointer-events: none;" else "",

          // Add grid pattern definition here
          GridRenderer.patternDef,

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
        // Zoom / rotation status chip — mirrors ModeBadgeComponent's structure (label + value spans)
        // so ZoomRotation.css can render an identical-looking badge in the opposite corner.
        div(
          className := "zoom-rotation",
          span(className := "zoom-rotation-label", "Zoom:"),
          span(
            className    := "zoom-rotation-value",
            child.text <--
              EditorState.viewState.signal.map(_.viewTransform.scale).distinct
                .map(s => f"${s * 100}%.0f${'%'}")
          ),
          span(className := "zoom-rotation-sep", "·"),
          span(className := "zoom-rotation-label", "Rotation:"),
          span(
            className    := "zoom-rotation-value",
            child.text <--
              EditorState.viewState.signal.map(_.viewTransform.rotationDegrees).distinct
                .map(r => s"$r°")
          )
        ),
        // Tiling info side panel — slides in from the right edge when toggled. Lives inside the
        // canvas wrapper so it floats over the canvas (overlay, not a separate grid column).
        TilingInfoPanel.element
      ),
      // Status row pinned to the bottom of the canvas area
      StatusRowComponent.element
    )

  /** Class string applied to the canvas SVG. Always `editor-canvas`; suffixed with a `tool-…` modifier
    * tracking the active tool so CSS can swap the cursor per mode (`tool-add-outside`, `tool-eraser`,
    * `tool-color-picker`, …). The processing/wait state is layered on top via inline `style`.
    */
  private val canvasClassNameSignal: Signal[String] =
    EditorState.toolState.signal.map(s => (s.activeTool, s.addSubmode)).distinct.map { case (tool, sub) =>
      val toolClass = tool match
        case Tool.AddPolygon          =>
          sub match
            case AddSubmode.Outside => "tool-add-outside"
            case AddSubmode.Inside  => "tool-add-inside"
        case Tool.Eraser              => "tool-eraser"
        case Tool.ColorPicker         => "tool-color-picker"
        case Tool.ShapeAndColorPicker => "tool-shape-color-picker"
        case Tool.SelectByColor       => "tool-select-by-color"
        case Tool.Measurement         => "tool-measurement"
        case Tool.Fan                 => "tool-fan"
      s"editor-canvas $toolClass"
    }

  private def loadingIndicator(): Element =
    div(
      className := "loading-indicator",
      div(
        className := "loading-content",
        div(className := "spinner"),
        p(
          child.text <--
            EditorState.uiState.signal.map(_.loadingMessage).distinct
              .combineWith(EditorState.localeState.signal)
              .map: (msg, _) =>
                msg.getOrElse(I18n.tNow("loading.default"))
        )
      )
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
                  case None            => TessellationRenderer.renderTiling(tiling)
    )
