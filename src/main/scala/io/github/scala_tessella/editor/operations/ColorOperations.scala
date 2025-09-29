package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.FaceId
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

  def getOrAssignPolygonColor(faceId: FaceId): ColorRGB =
    EditorState.polygonColors.now().get(faceId) match
      case Some(rgb) => rgb
      case None      =>
        val rgb = EditorState.fillColor.now()
        EditorState.polygonColors.update(_ + (faceId -> rgb))
        rgb
