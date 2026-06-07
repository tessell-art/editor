package art.tessell.editor.operations

import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.geometry.{AngleDegree, RegularPolygon}
import io.github.scala_tessella.dcel.structure.Vertex
import ColorOperations.ensureColorsForFaces
import OperationGuard.ifNotProcessing
import art.tessell.editor.models.{EditorState, VertexCoord}
import art.tessell.editor.utils.geo.TessellationGeometry.toPoint

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

  /** Move the just-placed shape to the head of the palette queue so the most-recently-used shape is always at
    * the front. Selection mode is preserved: when an irregular palette entry was the source
    * (`selectedIndex.isDefined`), the selection follows the moved shape to its new index 0; when a regular
    * polygon was selected via `selectedPolygon`, the queue still updates but no spurious irregular selection
    * is introduced.
    */
  def recordPlacedShape(angles: Vector[AngleDegree]): Unit =
    val keepIrregularSelection = EditorState.irregularState.now().selectedIndex.isDefined
    EditorState.irregularState.update(_.withShape(angles, selectIt = keepIrregularSelection))

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
          val polygon = RegularPolygon(sides)
          val tiling  = TilingDCEL.createRegularPolygon(polygon)
          EditorState.tessellationState.update(_.copy(currentTiling = tiling))
          ensureColorsForFaces(tiling.innerFaces.map(_.id), EditorState.colorState.now().fillColor)
          SelectionOperations.clearAllSelections()
          recordPlacedShape(polygon.angles)
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

  /** Select the irregular polygon at the given MRU index (deselect regular if any). No-op if the index is out
    * of range.
    */
  def selectIrregularInPalette(index: Int): Unit =
    ifNotProcessing:
      val mru = EditorState.irregularState.now().recentIrregularPolygons
      if index >= 0 && index < mru.size then
        EditorState.toolState.update(_.copy(selectedPolygon = None))
        EditorState.irregularState.update(_.selectAt(index))

  /** If the tiling is empty and an irregular polygon is selected, initialize the tiling with it. */
  def initializeWithIrregularIfEmpty(): Unit =
    ifNotProcessing:
      if EditorState.tessellationState.now().currentTiling.isEmpty then
        EditorState.irregularState.now().selectedShape match
          case Some(angles) =>
            UndoManager.saveState()
            TilingDCEL.createSimplePolygon(angles).toOption match
              case Some(tiling) =>
                EditorState.tessellationState.update(_.copy(currentTiling = tiling))
                ensureColorsForFaces(tiling.innerFaces.map(_.id), EditorState.colorState.now().fillColor)
                SelectionOperations.clearAllSelections()
                recordPlacedShape(angles)
              case None         =>
                UndoManager.undo()
                ErrorOperations.showError("Failed to create tiling from irregular polygon")
          case None         => ()
