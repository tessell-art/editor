package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.models.{AddSubmode, EditorState, Tool}
import io.github.scala_tessella.editor.operations.OperationGuard.gate
import io.github.scala_tessella.editor.utils.geo.{LineSegment, Point}
import io.github.scala_tessella.editor.utils.SvgDsl.*

object CanvasControlComponent:

  // Toggling a non-default tool: clicking it again returns to the default mode
  // (AddPolygon + Outside). Tools that own clickable-points clear measurements on
  // both activation and deactivation.
  private def toggleTool(tool: Tool): Unit =
    EditorState.toolState.update: s =>
      if s.activeTool == tool then
        if tool == Tool.Measurement || tool == Tool.Eraser then
          AppState.clearMeasurements()
        s.copy(activeTool = Tool.AddPolygon, addSubmode = AddSubmode.Outside)
      else
        AppState.clearMeasurements()
        s.copy(activeTool = tool)

  // Toggle between AddPolygon's Outside (default) and Inside sub-modes. Replaces
  // the former Tool.Inserter button.
  private def toggleAddInside(): Unit =
    EditorState.toolState.update: s =>
      if s.isAddInside then
        s.copy(addSubmode = AddSubmode.Outside)
      else
        AppState.clearMeasurements()
        s.copy(activeTool = Tool.AddPolygon, addSubmode = AddSubmode.Inside)

  private def createToolButton(tool: Tool, titleText: String, icon: Element): Element =
    button(
      icon,
      className := "toggle-btn",
      cls("active") <-- EditorState.toolState.signal.map(_.activeTool == tool).distinct,
      onClick.compose(gate) --> { _ =>

        toggleTool(tool)
      },
      title     := titleText
    )

  private def createAddInsideButton(): Element =
    button(
      IconsSVG.inserterIcon,
      className := "toggle-btn",
      cls("active") <-- EditorState.isAddInsideActive,
      onClick.compose(gate) --> { _ =>

        toggleAddInside()
      },
      title     := "Activate insertion mode to add interior polygons"
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
                svg.fill <-- EditorState.colorState.signal.map(_.fillColor).distinct.map { case (r, g, b) =>
                  f"rgb($r,$g,$b)"
                }
              )
            ),
            onClick.compose(stream =>
              gate(stream).withCurrentValueOf(EditorState.colorState.signal.map(_.fillColor).distinct)
            ) --> { case (_, color) =>
              // Use shared state from EditorState
              EditorState.colorState.update(_.copy(tempColor = color))
              EditorState.colorState.update(_.copy(showColorPicker = true))
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
          createAddInsideButton(),
          createToolButton(
            Tool.Measurement,
            "Activate measure mode to calculate the distance between two points",
            IconsSVG.rulerIcon
          ),
          button(
            child.text <-- EditorState.viewState.signal.map(_.showNodeLabels).distinct.map(show =>
              if show then "Labels: ON" else "Labels: OFF"
            ),
            className := "toggle-btn responsive-control",
            cls("active") <-- EditorState.viewState.signal.map(_.showNodeLabels).distinct.map(identity),
            onClick --> { _ =>

              AppState.toggleNodeLabels()
            },
            title <-- EditorState.viewState.signal.map(_.showNodeLabels).distinct.map { show =>

              if show then "Click to hide the node labels" else "Click to show the node labels"
            }
          ),
          UndoComponent.element
        )
      )
    )
