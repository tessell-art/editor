package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.structure.FaceId
import io.github.scala_tessella.editor.models.{ColorPickerContext, EditorState}
import io.github.scala_tessella.editor.operations.OperationGuard.ifNotProcessing
import io.github.scala_tessella.editor.operations.UndoManager
import io.github.scala_tessella.editor.utils.ColorRGB

object ColorOperations:

  /** Open the fill-color picker, seeding its working value with the current fill color. The picker's OK
    * action applies the color to the current selection (or sets the default fill if there is none), matching
    * the existing Edit → Fill Color… menu behaviour. The context flag drives the popup's title to make the
    * "fill the selection on OK" intent explicit when entered from the on-canvas Fill swatch.
    */
  def openFillColorPicker(): Unit =
    val current = EditorState.colorState.now().fillColor
    EditorState.colorState.update(
      _.copy(
        tempColor = current,
        showColorPicker = true,
        colorPickerContext = ColorPickerContext.FillSelected
      )
    )

  def applyColorToSelectedPolygons(color: ColorRGB): Unit =
    ifNotProcessing:
      val selectedIds = EditorState.tessellationState.now().selectedTilingPolygons
      if selectedIds.nonEmpty then
        UndoManager.saveState()
        EditorState.colorState.update: s =>
          s.copy(polygonColors =
            selectedIds.foldLeft(s.polygonColors)((colors, tag) => colors + (tag -> color))
          )

  def getPolygonColor(faceId: FaceId): Option[ColorRGB] =
    EditorState.colorState.now().polygonColors.get(faceId)

  def setPolygonColor(faceId: FaceId, color: ColorRGB): Unit =
    EditorState.colorState.update(s => s.copy(polygonColors = s.polygonColors + (faceId -> color)))

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
      EditorState.colorState.update: s =>

        val base =
          if trimMissing then
            s.polygonColors.filter: (faceId, _) =>
              faceIdSet.contains(faceId)
          else s.polygonColors
        val next = faceIdSet.foldLeft(base): (colors, faceId) =>
          if colors.contains(faceId) then colors else colors + (faceId -> defaultColor)
        s.copy(polygonColors = next)
