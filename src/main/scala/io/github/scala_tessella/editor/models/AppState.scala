package io.github.scala_tessella.editor.models

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.dcel.structure.{FaceId, VertexId}
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.TilingSymmetry.{reflectionalVertexIds, rotationalVertexIds}
import io.github.scala_tessella.editor.operations.OperationGuard.ifNotProcessing
import io.github.scala_tessella.editor.operations.TessellationOperations.VertexCoord
import io.github.scala_tessella.editor.operations.*
import io.github.scala_tessella.editor.utils.geo.{Point, Radian}
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

// Case class to represent a fan animation overlay
case class FanAnimation(
    facePoints: List[(FaceId, String)],
    pivot: Point,
    copies: Int,
    stepAngle: Radian,
    durationMs: Int,
    staggerMs: Int
)

case class DoublingAnimation(
    facePoints: List[(FaceId, String)],
    delta: Point,
    durationMs: Int
)

case class MirrorAnimation(
    facePoints: List[(FaceId, String)],
    axisY: Double,
    durationMs: Int
)

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
  case ColorPicker, ShapeAndColorPicker, SelectByColor, Eraser, Inserter, Measurement, Fan

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
      editorMode.update:
        case EditorMode.Select => EditorMode.Delete
        case EditorMode.Delete => EditorMode.Select

  /** Toggles the visibility of node labels. Does nothing if the editor is currently processing an operation.
    */
  def toggleNodeLabels(): Unit =
    ifNotProcessing:
      showNodeLabels.update(!_)

  private def displaySizeInfo(size: Int, dataType: String): Unit =
    ErrorOperations.info(
      s"$dataType data computed: $size ${if size == 1 then "class" else "classes"} found"
    )

  private def toggleComputedOverlay[T](
      showFlag: Var[Boolean],
      cache: Var[Option[T]],
      dataType: String,
      sizeOf: Option[T] => Int
  )(compute: TilingDCEL => Option[T]): Unit =
    val currentlyShown = showFlag.now()
    if currentlyShown then
      showFlag.set(false)
    else
      val existing = cache.now()
      if existing.nonEmpty then
        showFlag.set(true)
      else
        val tiling = currentTiling.now()
        AsyncUtils
          .withLoadingState { () =>

            if tiling.isEmpty then None
            else compute(tiling)
          }
          .foreach { computed =>
            // Only apply if the user still intends to show (hasn't toggled off meanwhile)
            if !showFlag.now() then
              cache.set(computed)
              showFlag.set(true)
              displaySizeInfo(sizeOf(computed), dataType)
          }

  /** Toggles the visibility of the uniformity data. Does nothing if the editor is currently processing an
    * operation.
    */
  def toggleShowUniformity(): Unit =
    ifNotProcessing:
      toggleComputedOverlay(
        showFlag = showUniformity,
        cache = uniformityMap,
        dataType = "Uniformity",
        sizeOf = _.map(_.values.toSet.size).getOrElse(0)
      ): tiling =>
        val classes  = tiling.uniformityTree.flattenLeaves
        val indexMap = classes.zipWithIndex.flatMap((vertexIds, index) => vertexIds.map((_, index))).toMap
        Some(indexMap)

  /** Toggles the visibility of the rotation axes. Does nothing if the editor is currently processing an
    * operation.
    */
  def toggleShowRotation(): Unit =
    ifNotProcessing:
      toggleComputedOverlay(
        showFlag = showRotation,
        cache = rotationVertexIds,
        dataType = "Rotation",
        sizeOf = _.map(_.size).getOrElse(1)
      ): tiling =>
        val vs = tiling.rotationalVertexIds
        if vs.size > 1 then
          Logger.debug(s"Rotation vertices: $vs")
          Some(vs)
        else
          None

  /** Toggles the visibility of the reflection axes. Does nothing if the editor is currently processing an
    * operation.
    */
  def toggleShowReflection(): Unit =
    ifNotProcessing:
      toggleComputedOverlay(
        showFlag = showReflection,
        cache = reflectionVertexIds,
        dataType = "Reflection",
        sizeOf = _.map(_.size).getOrElse(0)
      ): tiling =>
        val vs = tiling.reflectionalVertexIds
        if vs.nonEmpty then
          Logger.debug(s"Reflection vertices: $vs")
          Some(vs)
        else
          None

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

  /** Resets the current fill color to the default start fill color. */
  def resetFillColorToDefault(): Unit =
    EditorState.fillColor.set(EditorState.defaultStartFillColor.now())

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
