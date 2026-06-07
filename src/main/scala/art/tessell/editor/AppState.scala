package art.tessell.editor

import art.tessell.editor.models.{
  ClickablePoint, EditorMode, EditorState, FailedPolygonDeletion, FailedPolygonPlacement, Tool
}
import art.tessell.editor.operations.{
  AddCopyOperations, ColorOperations, DirtyTracker, ErrorOperations, MeasurementOperations,
  SelectionOperations, SettingsOperations, SymmetryOperations, TessellationOperations, TransformOperations,
  UndoManager, ViewOperations
}
import art.tessell.editor.utils.file.SvgImporter
import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.dcel.structure.FaceId
import art.tessell.editor.models.*
import art.tessell.editor.operations.*
import art.tessell.editor.operations.OperationGuard.ifNotProcessing
import art.tessell.editor.utils.{ColorRGB, FirstRunStorage}

/** AppState object provides a higher-level interface to the editor state. It exports all members of
  * EditorState and provides methods to manipulate the state.
  */
object AppState:
  // Expose state for components to read
  export EditorState.*

  // Expose undo/redo capabilities for UI components
  /** Signal indicating whether the undo operation is available */
  val canUndo: Signal[Boolean] = UndoManager.canUndo.signal

  /** Signal indicating whether the redo operation is available */
  val canRedo: Signal[Boolean] = UndoManager.canRedo.signal

  // Observers for UI wiring (Laminar-idiomatic: views do --> AppState.XObserver)
  val undoObserver: Observer[Boolean] =
    Observer: isProcessing =>
      ifNotProcessing(isProcessing):
        UndoManager.undo()

  val redoObserver: Observer[Boolean] =
    Observer: isProcessing =>
      ifNotProcessing(isProcessing):
        UndoManager.redo()

  // Simple UI operations

  /** Toggles between Select and Delete editor modes. Does nothing if the editor is currently processing an
    * operation.
    */
  def toggleEditorMode(): Unit =
    ifNotProcessing:
      toolState.update: s =>
        s.copy(editorMode = s.editorMode match
          case EditorMode.Select => EditorMode.Delete
          case EditorMode.Delete => EditorMode.Select)

  /** Toggles the visibility of node labels. Does nothing if the editor is currently processing an operation.
    */
  def toggleNodeLabels(): Unit =
    ifNotProcessing:
      viewState.update(s => s.copy(showNodeLabels = !s.showNodeLabels))

  /** Toggles the visibility of the uniformity data. Does nothing if the editor is currently processing an
    * operation.
    */
  def toggleShowUniformity(): Unit =
    SymmetryOperations.toggleShowUniformity()

  /** Toggles the visibility of the rotation axes. Does nothing if the editor is currently processing an
    * operation.
    */
  def toggleShowRotation(): Unit =
    SymmetryOperations.toggleShowRotation()

  /** Toggles the visibility of the reflection axes. Does nothing if the editor is currently processing an
    * operation.
    */
  def toggleShowReflection(): Unit =
    SymmetryOperations.toggleShowReflection()

  /** Toggles the visibility of the Tiling-info panel. Independent of `isProcessing` — the panel just reads
    * derived state, so it's safe to toggle anytime.
    */
  def toggleShowTilingInfo(): Unit =
    viewState.update(s => s.copy(showTilingInfo = !s.showTilingInfo))

  /** Opens the template gallery popup. */
  def openTemplateGallery(): Unit =
    popupState.update(_.copy(showTemplateGallery = true))

  /** Opens the recent-files panel popup. */
  def openRecentFilesPanel(): Unit =
    popupState.update(_.copy(showRecentFilesPanel = true))

  /** Opens the Print-to-PDF popup. */
  def openPrintPopup(): Unit =
    popupState.update(_.copy(showPrintPopup = true))

  /** Dismisses the first-run welcome overlay and persists the "seen" flag so it doesn't reappear. */
  def dismissFirstRun(): Unit =
    uiState.update(_.copy(showFirstRunOverlay = false))
    FirstRunStorage.markSeenFirstRun()

  /** Checks if the current tiling is empty.
    * @return
    *   true if the tiling is empty, false otherwise
    */
  def isTilingEmpty: Boolean = tessellationState.now().currentTiling.isEmpty

  /** True when at least one polygon or perimeter edge is selected in the current state snapshot. */
  private def hasSelectionNow: Boolean =
    val t = tessellationState.now()
    t.selectedTilingPolygons.nonEmpty || t.selectedPerimeterEdges.nonEmpty

  /** Runs a mutating selection operation only when not processing and a tiling exists, saving undo first. */
  private def withUndoOnNonEmptyTiling(op: => Unit): Unit =
    ifNotProcessing:
      if !isTilingEmpty then
        UndoManager.saveState()
        op

  /** Runs a mutating selection operation only when not processing and there is an active selection. */
  private def withUndoOnSelection(op: => Unit): Unit =
    ifNotProcessing:
      if hasSelectionNow then
        UndoManager.saveState()
        op

  // Delegate to operation objects

  /** Selects a polygon with the specified number of sides.
    * @param sides
    *   The number of sides of the polygon to select
    */
  def selectPolygon(sides: Int): Unit =
    TessellationOperations.selectPolygon(sides)

  /** Clears the current tiling and all measurements.
    */
  def doubleTiling(): Unit =
    clearMeasurements()
    TransformOperations.attemptDoubling()

  def mirrorTiling(): Unit =
    clearMeasurements()
    TransformOperations.attemptMirroring()

  /** Activates the Fan tool. The user then clicks a polygon and one of its boundary vertices to trigger the
    * fan animation. Does nothing if processing or if the tiling is empty.
    */
  def enterFanMode(): Unit =
    ifNotProcessing:
      if !isTilingEmpty then
        clearMeasurements()
        toolState.update(_.copy(activeTool = Tool.Fan))

  /** Activates the Add Copy ▸ Translate tool. The tiling's vertices then show as clipping-point dots; the
    * user drags the dashed skeleton and releases on a vertex to weld a translated copy. Does nothing if
    * processing or if the tiling is empty.
    */
  def enterTranslateCopyMode(): Unit =
    AddCopyOperations.enterTranslateCopyMode()

  /** Activates the Add Copy ▸ Rotate tool. Rotation centres (vertices, edge midpoints, symmetric face
    * centres) show as dots; the user presses one and drags around it to set the angle, releasing on a snap to
    * weld a rotated copy. Does nothing if processing or if the tiling is empty.
    */
  def enterRotateCopyMode(): Unit =
    AddCopyOperations.enterRotateCopyMode()

  /** Activates the Add Copy ▸ Reflect tool. Anchors (vertices, edge midpoints, face centres) show as dots;
    * the user presses one and drags to another to define a mirror axis, releasing to weld a reflected copy.
    * Does nothing if processing or if the tiling is empty.
    */
  def enterReflectCopyMode(): Unit =
    AddCopyOperations.enterReflectCopyMode()

  /** Activates the Add Copy ▸ Glide reflect tool. Same two-anchor axis gesture as Reflect, but the copy is
    * also slid along the axis by the vector B − A. Does nothing if processing or if the tiling is empty.
    */
  def enterGlideReflectCopyMode(): Unit =
    AddCopyOperations.enterGlideReflectCopyMode()

  /** Activates the Measurement tool. The user then clicks a polygon to expose its clickable points (vertices,
    * edge midpoints, center). Does nothing if processing or if the tiling is empty.
    */
  def enterMeasureMode(): Unit =
    ifNotProcessing:
      if !isTilingEmpty then
        clearMeasurements()
        toolState.update(_.copy(activeTool = Tool.Measurement))

  /** Clears the current tiling and all measurements. The user explicitly chose to wipe the canvas, so the
    * resulting empty state is treated as a fresh document — `DirtyTracker.resetBaseline()` drops the
    * previously-saved tiling reference so subsequent destructive actions (New, Load, Template…) don't
    * spuriously prompt for unsaved changes.
    */
  def clearTiling(): Unit =
    clearMeasurements()
    TessellationOperations.clearTiling()
    DirtyTracker.resetBaseline()

  /** Handles a click on a tiling polygon. The behavior depends on the current editor mode and active tool.
    * @param faceId
    *   The ID of the clicked polygon
    */
  def handleTilingPolygonClick(faceId: FaceId): Unit =
    SelectionOperations.handleTilingPolygonClick(faceId)

  /** Handles a click on a perimeter edge.
    * @param edgeId
    *   The ID of the clicked edge
    * @param edgeIndex
    *   The index of the clicked edge
    */
  def handlePerimeterEdgeClick(edgeId: String, edgeIndex: Int): Unit =
    SelectionOperations.handlePerimeterEdgeClick(edgeId, edgeIndex)

  /** Handles a click on a point for measurement.
    * @param point
    *   The clicked point
    */
  def handlePointClickForMeasurement(point: ClickablePoint): Unit =
    SelectionOperations.handlePointClickForMeasurement(point)

  /** Handles a click on a point for multiplying tilind around a vertex..
    * @param point
    *   The clicked point
    */
  def handlePointClickForFan(point: ClickablePoint): Unit =
    SelectionOperations.handlePointClickForFan(point)

  /** Handles a click on a point for deletion.
    *
    * @param point
    *   The clicked point
    */
  def handlePointClickForDeletion(point: ClickablePoint): Unit =
    SelectionOperations.handlePointClickForDeletion(point)

  /** Handles a click on an edge point for insertion.
    *
    * @param point
    *   The clicked edge point
    */
  def handlePointClickForInsertion(point: ClickablePoint): Unit =
    SelectionOperations.handlePointClickForInsertion(point)

  /** Applies the specified color to all selected polygons.
    * @param color
    *   The color to apply (RGB tuple)
    */
  def applyColorToSelectedPolygons(color: ColorRGB): Unit =
    ColorOperations.applyColorToSelectedPolygons(color)

  /** Gets the color for a polygon if it exists. */
  def getPolygonColor(faceId: FaceId): Option[ColorRGB] =
    ColorOperations.getPolygonColor(faceId)

  /** Sets the color for a polygon explicitly. */
  def setPolygonColor(faceId: FaceId, color: ColorRGB): Unit =
    ColorOperations.setPolygonColor(faceId, color)

  /** Syncs temporary settings values from current saved settings. */
  def refreshSettingsTempValues(): Unit =
    SettingsOperations.refreshSettingsTempValues()

  /** Commits the popup's temp settings (colors, boundary edge width, reduce-motion) and persists. */
  def applySettings(): Unit =
    SettingsOperations.applySettings()

  /** Resets the current fill color to the default start fill color. */
  def resetFillColorToDefault(): Unit =
    SettingsOperations.resetFillColorToDefault()

  /** Shows an error message with optional details about failed operations.
    * @param message
    *   The error message to display
    * @param placement
    *   Optional details about a failed polygon placement
    * @param deletion
    *   Optional details about a failed polygon deletion
    */
  def showError(
      message: String,
      placement: Option[FailedPolygonPlacement] = None,
      deletion: Option[FailedPolygonDeletion] = None
  ): Unit =
    ErrorOperations.showError(message, placement, deletion)

  /** Clears the current error message and details.
    */
  def clearError(): Unit =
    ErrorOperations.clearError()

  /** Selects all polygons with the specified number of sides. Does nothing if the editor is processing or if
    * the tiling is empty.
    * @param sides
    *   The number of sides of the polygons to select
    */
  def selectPolygonsBySides(sides: Int): Unit =
    withUndoOnNonEmptyTiling:
      SelectionOperations.selectPolygonsBySides(sides)

  /** Selects all polygons with the same shape. Does nothing if the editor is processing or if the tiling is
    * empty.
    *
    * @param angles
    *   The interior angles of the polygons to select
    */
  def selectPolygonsByShape(angles: Vector[AngleDegree]): Unit =
    withUndoOnNonEmptyTiling:
      SelectionOperations.selectPolygonsByShape(angles)

  /** Selects all polygons in the tiling. Does nothing if the editor is processing or if the tiling is empty.
    */
  def selectAll(): Unit =
    withUndoOnNonEmptyTiling:
      SelectionOperations.selectAllPolygons()

  /** Deselects all selected polygons and edges. Does nothing if the editor is processing or if nothing is
    * selected.
    */
  def deselectAll(): Unit =
    withUndoOnSelection:
      SelectionOperations.clearAllSelections()

  /** Adjusts the view to fit the entire tiling in the canvas.
    */
  def fitTilingToCanvas(): Unit =
    ViewOperations.fitTilingToCanvas()

  /** Clears all measurement-related state.
    */
  def clearMeasurements(): Unit =
    MeasurementOperations.clearAll()

  /** Clears symmetry overlays and their cached data. */
  def clearSymmetryOverlays(): Unit =
    SymmetryOperations.clearOverlays()

  /** Starts a new, empty tiling. Clears the current tiling and measurements, forgets the current file name,
    * wipes undo history, resets the view transform, and restores the default fill color. Routed through
    * `DirtyTracker.confirmIfDirty` so a dirty document prompts the user before being thrown away.
    */
  def newTiling(): Unit =
    DirtyTracker.confirmIfDirty(() => doNewTiling())

  private def doNewTiling(): Unit =
    clearTiling()
    EditorState.fileState.update(_.copy(currentFileName = None))
    UndoManager.clearHistory()
    ViewOperations.resetView()
    resetFillColorToDefault()
    // Empty canvas, no file → clean baseline.
    DirtyTracker.resetBaseline()

  /** Opens the file picker for an SVG, guarding the current tiling behind the unsaved-changes prompt. */
  def loadSvgFile(): Unit =
    DirtyTracker.confirmIfDirty(() => SvgImporter.trigger())
