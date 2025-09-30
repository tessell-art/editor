package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.models.{AppState, EditorState, Tool}
import io.github.scala_tessella.editor.operations.OperationGuard.gate
import io.github.scala_tessella.editor.utils.{LineSegment, Point}
import io.github.scala_tessella.editor.utils.SvgDsl.*

object CanvasControlComponent:

  private def toggleTool(tool: Tool): Unit =
    EditorState.activeTool.update {
      case Some(t) if t == tool =>
        if t == Tool.Measurement || t == Tool.Eraser || t == Tool.Inserter then AppState.clearMeasurements()
        None // Deactivate if it's the current tool
      case _                    =>
        AppState.clearMeasurements() // Clear measurements when switching
        Some(tool)                   // Activate the new tool
    }

  private def createToolButton(tool: Tool, titleText: String, icon: Element): Element =
    button(
      icon,
      className := "toggle-btn",
      cls("active") <-- EditorState.activeTool.signal.map(_.contains(tool)),
      onClick.compose(gate) --> { _ =>

        toggleTool(tool)
      },
      title     := titleText
    )

  def element: Element =
    div(
      div(
        className := "canvas-controls",
        div(
          className := "control-group",
          button(
            "Fill ",
            svg.svg(
              widthHeightCoords(Point(24, 24)),
              svg.rect(
                rectCoords(LineSegment(Point(2, 2), Point(18, 18))),
                svg.fill <-- EditorState.fillColor.signal.map { case (r, g, b) =>
                  f"rgb($r,$g,$b)"
                }
              )
            ),
            onClick.compose(gate) --> { _ =>
              // Use shared state from EditorState
              EditorState.tempColor.set(EditorState.fillColor.now())
              EditorState.showColorPicker.set(true)
            },
            className := "fill-color-btn"
          ),
          createToolButton(
            Tool.ColorPicker,
            "Activate color picker to select a color from an existing polygon",
            IconsSVG.eyeDropperIcon
          ),
          createToolButton(
            Tool.ShapeAndColorPicker,
            "Activate shape and color picker to select the shape and color from an existing polygon",
            IconsSVG.eyeDropperPentagonIcon
          ),
          createToolButton(
            Tool.SelectByColor,
            "Activate selector to select all polygons with the same color",
            IconsSVG.selectByColorIcon
          ),
          createToolButton(
            Tool.Eraser,
            "Activate deletion mode to delete polygons",
            IconsSVG.eraserIcon
          ),
          createToolButton(
            Tool.Inserter,
            "Activate insertion mode to add interior polygons",
            IconsSVG.inserterIcon
          ),
          createToolButton(
            Tool.Measurement,
            "Activate measure mode to calculate the distance between two points",
            IconsSVG.rulerIcon
          ),
          button(
            child.text <-- EditorState.showNodeLabels.signal.map(show =>
              if show then "Labels: ON" else "Labels: OFF"
            ),
            className := "toggle-btn responsive-control",
            cls("active") <-- EditorState.showNodeLabels.signal.map(identity),
            onClick --> { _ =>

              AppState.toggleNodeLabels()
            },
            title <-- EditorState.showNodeLabels.signal.map { show =>

              if show then "Click to hide the node labels" else "Click to show the node labels"
            }
          ),
          UndoComponent.element
        )
      )
    )
