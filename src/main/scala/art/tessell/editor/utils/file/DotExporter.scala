package art.tessell.editor.utils.file

import art.tessell.editor.models.EditorState
import art.tessell.editor.utils.AsyncUtils

object DotExporter:

  def exportTilingToDOT(): Unit =
    AsyncUtils.withLoadingState { () =>

      val tiling = EditorState.tessellationState.now().currentTiling
      if !tiling.isEmpty then
        val dotContent = tiling.toDOT
        FileDownloader.trigger(dotContent, "tessellation.gv", "text/plain;charset=utf-8")
    }: Unit
