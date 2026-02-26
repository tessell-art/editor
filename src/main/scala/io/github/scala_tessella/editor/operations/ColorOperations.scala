package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.structure.FaceId
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.operations.OperationGuard.ifNotProcessing
import io.github.scala_tessella.editor.utils.{ColorRGB, UndoManager}

object ColorOperations:

  def applyColorToSelectedPolygons(color: ColorRGB): Unit =
    ifNotProcessing:
      val selectedIds = EditorState.selectedTilingPolygons.now()
      if selectedIds.nonEmpty then
        UndoManager.saveState()
        EditorState.polygonColors.update { currentColors =>

          selectedIds.foldLeft(currentColors) { (colors, tag) =>

            colors + (tag -> color)
          }
        }

  def getPolygonColor(faceId: FaceId): Option[ColorRGB] =
    EditorState.polygonColors.now().get(faceId)

  def setPolygonColor(faceId: FaceId, color: ColorRGB): Unit =
    EditorState.polygonColors.update(_ + (faceId -> color))

  def ensureColorsForFaces(faceIds: Iterable[FaceId], defaultColor: ColorRGB): Unit =
    if faceIds.nonEmpty then
      EditorState.polygonColors.update: currentColors =>
        faceIds.foldLeft(currentColors): (colors, faceId) =>
          if colors.contains(faceId) then colors else colors + (faceId -> defaultColor)

  /** Keep polygonColors in sync with the current tiling faces:
    *   - Remove colors for faces that no longer exist
    *   - Add default color for any new faces
    */
  def syncColorsForFaces(faceIds: Iterable[FaceId], defaultColor: ColorRGB): Unit =
    val faceIdSet = faceIds.toSet
    EditorState.polygonColors.update: currentColors =>
      val trimmed = currentColors.filter: (faceId, _) =>
        faceIdSet.contains(faceId)
      faceIdSet.foldLeft(trimmed): (colors, faceId) =>
        if colors.contains(faceId) then colors else colors + (faceId -> defaultColor)
