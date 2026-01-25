package io.github.scala_tessella.editor.models

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.dcel.structure.{FaceId, VertexId}
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.TilingSymmetry.{reflectionalVertexIds, rotationalVertexIds}
import io.github.scala_tessella.editor.operations.OperationGuard.ifNotProcessing
import io.github.scala_tessella.editor.operations.TessellationOperations.VertexCoord
import io.github.scala_tessella.editor.operations.*
import io.github.scala_tessella.editor.utils.geo.Point
import io.github.scala_tessella.editor.utils.{AsyncUtils, ColorRGB, Logger, SettingsStorage, UndoManager}

import scala.concurrent.ExecutionContext.Implicits.global

// Case class to represent a failed polygon placement
case class FailedPolygonPlacement(
    edgeIndex: Int,
    angles: Vector[AngleDegree],
    edge: (VertexCoord, VertexCoord),
    tiling: TilingDCEL,
    intoFace: Option[FaceId] = None
)

// Case class to represent a failed polygon deletion
case class FailedPolygonDeletion(faceId: FaceId, polygonNodes: Vector[VertexId])

enum Anchor:

  case Vertex(vertexId: VertexId)
  case Center(faceId: FaceId)
  case MidPoint(startVertexId: VertexId, endVertexId: VertexId)

case class ClickablePoint(point: Point, anchor: Anchor)

// Editor mode enumeration
enum EditorMode:
  case Select, Delete

// Tool enumeration
enum Tool:
  case ColorPicker, ShapeAndColorPicker, SelectByColor, Eraser, Inserter, Measurement

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
  val undoObserver: Observer[Any] = Observer { _ =>

    ifNotProcessing:
      UndoManager.undo()
  }

  val redoObserver: Observer[Any] = Observer { _ =>

    ifNotProcessing:
      UndoManager.redo()
  }

  // Simple UI operations

  /** Toggles between Select and Delete editor modes. Does nothing if the editor is currently processing an
    * operation.
    */
  def toggleEditorMode(): Unit =
    ifNotProcessing:
      editorMode.update {
        case EditorMode.Select => EditorMode.Delete
        case EditorMode.Delete => EditorMode.Select
      }

  /** Toggles the visibility of node labels. Does nothing if the editor is currently processing an operation.
    */
  def toggleNodeLabels(): Unit =
    ifNotProcessing:
      showNodeLabels.update(!_)

  private def displaySizeInfo(size: Int, dataType: String): Unit =
    ErrorOperations.info(
      s"$dataType data computed: $size ${if size == 1 then "class" else "classes"} found"
    )

  /** Toggles the visibility of the uniformity data. Does nothing if the editor is currently processing an
    * operation.
    */
  def toggleShowUniformity(): Unit =
    ifNotProcessing:
      val currentlyShown = showUniformity.now()
      if currentlyShown then
        // Turning OFF: hide immediately
        showUniformity.set(false)
      else
        // Turning ON
        val existing = uniformityMap.now()
        if existing.nonEmpty then
          // Already computed: show immediately
          showUniformity.set(true)
        else
          // Compute first, then show
          val tiling = currentTiling.now()
          AsyncUtils
            .withLoadingState { () =>

              if tiling.isEmpty then None
              else
                val classes  = tiling.uniformityTree.flattenLeaves
                val indexMap =
                  classes.zipWithIndex.flatMap((vertexIds, index) => vertexIds.map((_, index))).toMap
//                Logger.debug(s"Computed uniformity map: $indexMap")
                Some(indexMap)
            }
            .foreach { computed =>
              // Only apply if the user still intends to show (hasn't toggled off meanwhile)
              if !showUniformity.now() then
                uniformityMap.set(computed)
                showUniformity.set(true)
                val size = computed.map(_.values.toSet.size).getOrElse(0)
                displaySizeInfo(size, "Uniformity")
            }

  /** Toggles the visibility of the rotation axes. Does nothing if the editor is currently processing an
    * operation.
    */
  def toggleShowRotation(): Unit =
    ifNotProcessing:
      val currentlyShown = showRotation.now()
      if currentlyShown then
        // Turning OFF: hide immediately
        showRotation.set(false)
      else
        // Turning ON
        val existing = rotationVertexIds.now()
        if existing.nonEmpty then
          // Already computed: show immediately
          showRotation.set(true)
        else
          // Compute first, then show
          val tiling = currentTiling.now()
          AsyncUtils
            .withLoadingState { () =>

              if tiling.isEmpty then None
              else
                val vs = tiling.rotationalVertexIds
                if vs.size > 1 then
                  Logger.debug(s"Rotation vertices: $vs")
                  Some(vs)
                else
                  None
            }
            .foreach { computed =>
              // Only apply if the user still intends to show (hasn't toggled off meanwhile)
              if !showRotation.now() then
                rotationVertexIds.set(computed)
                showRotation.set(true)
                val size = computed.map(_.size).getOrElse(1)
                displaySizeInfo(size, "Rotation")
            }

  /** Toggles the visibility of the reflection axes. Does nothing if the editor is currently processing an
    * operation.
    */
  def toggleShowReflection(): Unit =
    ifNotProcessing:
      val currentlyShown = showReflection.now()
      if currentlyShown then
        // Turning OFF: hide immediately
        showReflection.set(false)
      else
        // Turning ON
        val existing = reflectionVertexIds.now()
        if existing.nonEmpty then
          // Already computed: show immediately
          showReflection.set(true)
        else
          // Compute first, then show
          val tiling = currentTiling.now()
          AsyncUtils
            .withLoadingState { () =>

              if tiling.isEmpty then None
              else
                val vs = tiling.reflectionalVertexIds
                if vs.nonEmpty then
                  Logger.debug(s"Reflection vertices: $vs")
                  Some(vs)
                else
                  None
            }
            .foreach { computed =>
              // Only apply if the user still intends to show (hasn't toggled off meanwhile)
              if !showReflection.now() then
                reflectionVertexIds.set(computed)
                showReflection.set(true)
                val size = computed.map(_.size).getOrElse(0)
                displaySizeInfo(size, "Reflection")
            }

  /** Checks if the current tiling is empty.
    * @return
    *   true if the tiling is empty, false otherwise
    */
  def isTilingEmpty: Boolean = currentTiling.now().isEmpty

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
    TessellationOperations.attemptDoubling()

  def mirrorTiling(): Unit =
    clearMeasurements()
    TessellationOperations.attemptMirroring()

  /** Clears the current tiling and all measurements.
    */
  def clearTiling(): Unit =
    clearMeasurements()
    TessellationOperations.clearTiling()

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
    EditorState.tempDefaultFillColor.set(EditorState.defaultStartFillColor.now())
    EditorState.tempPerimeterEdgeColor.set(EditorState.perimeterEdgeColor.now())
    EditorState.tempSettingsPickerColor.set(EditorState.defaultStartFillColor.now())
    EditorState.showSettingsColorPicker.set(false)

  /** Applies editor settings and persists them. */
  def applySettings(defaultFill: ColorRGB, perimeterEdge: ColorRGB): Unit =
    EditorState.defaultStartFillColor.set(defaultFill)
    EditorState.fillColor.set(defaultFill)
    SettingsStorage.saveDefaultStartFillColor(defaultFill)
    EditorState.perimeterEdgeColor.set(perimeterEdge)
    SettingsStorage.savePerimeterEdgeColor(perimeterEdge)

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
    ifNotProcessing:
      if !isTilingEmpty then
        UndoManager.saveState()
        SelectionOperations.selectPolygonsBySides(sides)

  /** Selects all polygons with the same shape. Does nothing if the editor is processing or if the tiling is
    * empty.
    *
    * @param angles
    *   The interior angles of the polygons to select
    */
  def selectPolygonsByShape(angles: Vector[AngleDegree]): Unit =
    ifNotProcessing:
      if !isTilingEmpty then
        UndoManager.saveState()
        SelectionOperations.selectPolygonsByShape(angles)

  /** Selects all polygons in the tiling. Does nothing if the editor is processing or if the tiling is empty.
    */
  def selectAll(): Unit =
    ifNotProcessing:
      if !isTilingEmpty then
        UndoManager.saveState()
        SelectionOperations.selectAllPolygons()

  /** Deselects all selected polygons and edges. Does nothing if the editor is processing or if nothing is
    * selected.
    */
  def deselectAll(): Unit =
    ifNotProcessing:
      if selectedTilingPolygons.now().nonEmpty || selectedPerimeterEdges.now().nonEmpty then
        UndoManager.saveState()
        SelectionOperations.clearAllSelections()

  /** Adjusts the view to fit the entire tiling in the canvas.
    */
  def fitTilingToCanvas(): Unit =
    ViewOperations.fitTilingToCanvas()

  /** Clears all measurement-related state.
    */
  def clearMeasurements(): Unit =
    clickablePoints.set(Nil)
    measurementStartPoint.set(None)
    measurementEndPoint.set(None)
    measurementPreviousEndPoint.set(None)
    highlightedPolygonId.set(None)
    measurementResult.set(None)
    measurementAngle.set(None)

  /** Clears symmetry overlays and their cached data. */
  def clearSymmetryOverlays(): Unit =
    showUniformity.set(false)
    uniformityMap.set(None)
    showRotation.set(false)
    rotationVertexIds.set(None)
    showReflection.set(false)
    reflectionVertexIds.set(None)
