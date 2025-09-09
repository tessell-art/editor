package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.models.{AppState, EditorMode, EditorState, Tool}

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.tessella.IncrementalTiling.Strictness

object CanvasControlComponent:

  private def toggleTool(tool: Tool): Unit =
    EditorState.activeTool.update {
      case Some(t) if t == tool =>
        if t == Tool.Measurement || t == Tool.Eraser then AppState.clearMeasurements()
        None // Deactivate if it's the current tool
      case _ =>
        AppState.clearMeasurements() // Clear measurements when switching
        Some(tool) // Activate the new tool
    }

  private def createToolButton(tool: Tool, titleText: String, icon: Element): Element =
    button(
      icon,
      className := "toggle-btn",
      cls.toggle("active") <-- EditorState.activeTool.signal.map(_.contains(tool)),
      onClick --> { _ => toggleTool(tool) },
      title := titleText
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
              svg.width := "24",
              svg.height := "24",
              svg.rect(
                svg.width := "18",
                svg.height := "18",
                svg.x := "2",
                svg.y := "2",
                svg.fill <-- EditorState.fillColor.signal.map { case (r, g, b) => f"rgb($r,$g,$b)" }
              )
            ),
            onClick --> { _ =>
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
            IconsSVG.rulerIcon
          ),
          createToolButton(
            Tool.Measurement,
            "Activate measure mode to calculate the distance between two points",
            IconsSVG.rulerIcon
          ),
          button(
            child.text <-- EditorState.editorMode.signal.map {
              case EditorMode.Select => "Mode: Select"
              case EditorMode.Delete => "Mode: Delete"
            },
            className := "toggle-btn",
            cls.toggle("mode-select") <-- EditorState.editorMode.signal.map(_ == EditorMode.Select),
            cls.toggle("mode-delete") <-- EditorState.editorMode.signal.map(_ == EditorMode.Delete),
            cls.toggle("active") <-- EditorState.editorMode.signal.map(_ == EditorMode.Delete),
            onClick --> { _ => AppState.toggleEditorMode() },
            title <-- EditorState.editorMode.signal.map {
              case EditorMode.Select => "Click to switch to Delete mode"
              case EditorMode.Delete => "Click to switch to Select mode"
            }
          ),
          button(
            child.text <-- EditorState.showNodeLabels.signal.map(show =>
              if show then "Labels: ON" else "Labels: OFF"
            ),
            className := "toggle-btn responsive-control",
            cls.toggle("active") <-- EditorState.showNodeLabels.signal.map(identity),
            onClick --> { _ => AppState.toggleNodeLabels() },
            title <-- EditorState.showNodeLabels.signal.map { show =>
              if show then "Click to hide the node labels" else "Click to show the node labels"
            }
          ),
          button(
            child.text <-- EditorState.strictness.signal.map(s => s"Validation: ${if s == Strictness.STRICT then "ON" else "OFF"}"),
            className := "toggle-btn responsive-control",
            cls.toggle("active") <-- EditorState.strictness.signal.map(_ == Strictness.STRICT),
            onClick --> { _ => AppState.toggleStrictness() },
            title <-- EditorState.strictness.signal.map {
              case Strictness.STRICT => "Validation ON: adding a polygon touching and crossing the edges of the perimeter is not allowed. Click to switch OFF."
              case _                 => "Validation OFF: without validation it is possible to add a polygon that would invalidate the tessellation. Click to switch ON."
            }
          ),
          UndoComponent.element
        )
      )
    )
