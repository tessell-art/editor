package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.structure.FaceId
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.operations.OperationGuard.ifNotProcessing
import io.github.scala_tessella.editor.utils.{ColorRGB, UndoManager}

object ColorOperations:

  def applyColorToSelectedPolygons(color: ColorRGB): Unit =
    ifNotProcessing:
      val selectedIds = EditorState.tessellationState.now().selectedTilingPolygons
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
    updateFaceColors(faceIds, defaultColor, trimMissing = false)

  /** Keep polygonColors in sync with the current tiling faces:
    *   - Remove colors for faces that no longer exist
    *   - Add default color for any new faces
    */
  def syncColorsForFaces(faceIds: Iterable[FaceId], defaultColor: ColorRGB): Unit =
    updateFaceColors(faceIds, defaultColor, trimMissing = true)

  private def updateFaceColors(
      faceIds: Iterable[FaceId],
      defaultColor: ColorRGB,
      trimMissing: Boolean
  ): Unit =
    if faceIds.nonEmpty then
      val faceIdSet = faceIds.toSet
      EditorState.polygonColors.update: currentColors =>

        val base =
          if trimMissing then
            currentColors.filter: (faceId, _) =>
              faceIdSet.contains(faceId)
          else currentColors
        faceIdSet.foldLeft(base): (colors, faceId) =>
          if colors.contains(faceId) then colors else colors + (faceId -> defaultColor)
