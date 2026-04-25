package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.geometry.RegularPolygon
import io.github.scala_tessella.dcel.structure.Vertex
import io.github.scala_tessella.editor.models.{EditorState, VertexCoord}
import io.github.scala_tessella.editor.operations.ColorOperations.ensureColorsForFaces
import io.github.scala_tessella.editor.operations.OperationGuard.ifNotProcessing
import io.github.scala_tessella.editor.utils.geo.TessellationGeometry.toPoint
import io.github.scala_tessella.editor.operations.UndoManager

/** Tiling-lifecycle operations: create/clear the tiling and manage palette selection.
  *
  * Deletion lives in [[DeletionOperations]], placement in [[PlacementOperations]], and the three animated
  * tiling transformations (fan / double / mirror) in [[TransformOperations]].
  *
  * `attempt…` naming convention (applied by the sibling ops objects): methods that may fail return `Unit` and
  * surface an error via [[ErrorOperations]] rather than throwing.
  */
object TessellationOperations:

  /** Extension used by renderers, placement, and transform ops to convert a DCEL vertex to the
    * `(id, canvas point)` tuple that downstream geometry code expects.
    */
  extension (vertex: Vertex)

    def toCoords: VertexCoord =
      (vertex.id, vertex.coords.toPoint)

  /** After a successful tiling mutation, clear stale symmetry overlays and the perimeter-edge selection
    * (which may point at an edge that no longer exists). Called from `clearTiling` and by every `attempt…`
    * method in the sibling ops objects.
    */
  def clearStaleAfterMutation(): Unit =
    SymmetryOperations.clearOverlays()
    EditorState.tessellationState.update(_.copy(selectedPerimeterEdges = Set.empty))

  /** Select a regular polygon from the palette. If the tiling is empty, seed it with a fresh copy of the
    * polygon; otherwise, just record the selection.
    */
  def selectPolygon(sides: Int): Unit =
    ifNotProcessing:
      // Selecting a regular polygon deselects the irregular
      EditorState.irregularState.update(_.deselected)
      EditorState.toolState.update(_.copy(selectedPolygon = Some(sides)))

      if EditorState.tessellationState.now().currentTiling.isEmpty then
        UndoManager.saveState()
        try
          val tiling = TilingDCEL.createRegularPolygon(RegularPolygon(sides))
          EditorState.tessellationState.update(_.copy(currentTiling = tiling))
          ensureColorsForFaces(tiling.innerFaces.map(_.id), EditorState.colorState.now().fillColor)
          SelectionOperations.clearAllSelections()
        catch
          case e: Throwable =>
            UndoManager.undo()
            ErrorOperations.showError(s"Failed to create tiling from $sides-sided polygon")

  /** Empty the current tiling (clear polygons + selections). Undo-snapshotted if non-empty. */
  def clearTiling(): Unit =
    ifNotProcessing:
      if !EditorState.tessellationState.now().currentTiling.isEmpty then
        UndoManager.saveState()

      EditorState.tessellationState.update(_.copy(currentTiling = TilingDCEL.empty))
      clearStaleAfterMutation()
      EditorState.colorState.update(_.copy(polygonColors = Map.empty))
      EditorState.tessellationState.update(_.copy(selectedTilingPolygons = Set.empty))

  /** Select the irregular polygon in the palette (deselect regular if any). */
  def selectIrregularInPalette(): Unit =
    ifNotProcessing:
      if EditorState.irregularState.now().recentIrregularPolygons.nonEmpty then
        EditorState.toolState.update(_.copy(selectedPolygon = None))
        EditorState.irregularState.update(_.selectHead)

  /** If the tiling is empty and a recent irregular exists, initialize the tiling with it. */
  def initializeWithIrregularIfEmpty(): Unit =
    ifNotProcessing:
      if EditorState.tessellationState.now().currentTiling.isEmpty then
        EditorState.irregularState.now().headOption match
          case Some(angles) =>
            UndoManager.saveState()
            TilingDCEL.createSimplePolygon(angles).toOption match
              case Some(tiling) =>
                EditorState.tessellationState.update(_.copy(currentTiling = tiling))
                ensureColorsForFaces(tiling.innerFaces.map(_.id), EditorState.colorState.now().fillColor)
                SelectionOperations.clearAllSelections()
              case None         =>
                UndoManager.undo()
                ErrorOperations.showError("Failed to create tiling from irregular polygon")
          case None         => ()
