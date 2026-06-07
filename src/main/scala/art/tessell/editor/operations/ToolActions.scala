package art.tessell.editor.operations

import art.tessell.editor.models.{AddSubmode, EditorState, Tool}

/** Tool-strip / mode-switcher behaviour shared between the desktop CanvasControlComponent and the mobile
  * MobileBottomToolbar.
  *
  * Kept in the operations layer (instead of a single component) because both components need identical
  * mode-switching semantics and clearing of measurement state.
  */
object ToolActions:

  /** Toggle a non-default tool. Click the active tool to return to the default mode (`AddPolygon + Outside`);
    * click an inactive tool to activate it. Tools that own clickable-points (Measurement, Eraser) clear
    * measurements on both activation and deactivation.
    */
  def toggleTool(tool: Tool): Unit =
    EditorState.toolState.update: s =>
      if s.activeTool == tool then
        if tool == Tool.Measurement || tool == Tool.Eraser then
          MeasurementOperations.clearAll()
        s.copy(activeTool = Tool.AddPolygon, addSubmode = AddSubmode.Outside)
      else
        MeasurementOperations.clearAll()
        s.copy(activeTool = tool)

  /** Add Polygon button click. Activates `Tool.AddPolygon` (defaulting to Outside) when not active; cycles
    * `Outside ↔ Inside` when already active.
    */
  def cycleOrActivateAddPolygon(): Unit =
    EditorState.toolState.update: s =>
      if s.activeTool == Tool.AddPolygon then
        val nextSub = s.addSubmode match
          case AddSubmode.Outside => AddSubmode.Inside
          case AddSubmode.Inside  => AddSubmode.Outside
        MeasurementOperations.clearAll()
        s.copy(addSubmode = nextSub)
      else
        MeasurementOperations.clearAll()
        s.copy(activeTool = Tool.AddPolygon, addSubmode = AddSubmode.Outside)
