package io.github.scala_tessella.editor.operations

import OperationGuard.ifNotProcessing
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.UndoManager

object ColorOperations:

  def applyColorToSelectedPolygons(color: (Int, Int, Int)): Unit =
    ifNotProcessing:
      val selectedIds = EditorState.selectedTilingPolygons.now()
      if selectedIds.nonEmpty then
        UndoManager.saveState()

        val selectedTags = selectedIds.map { id =>
          id
        }

        EditorState.polygonColors.update { currentColors =>
          selectedTags.foldLeft(currentColors) { (colors, tag) =>
            colors + (tag -> color)
          }
        }

  def getOrAssignPolygonColor(polyTag: String): (Int, Int, Int) =
    EditorState.polygonColors.now().get(polyTag) match
      case Some(rgb) => rgb
      case None =>
        val rgb = EditorState.fillColor.now()
        EditorState.polygonColors.update(_ + (polyTag -> rgb))
        rgb