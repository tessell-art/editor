package io.github.scala_tessella.editor.utils.file

import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.AsyncUtils

object DotExporter:

  def exportTilingToDOT(): Unit =
    AsyncUtils.withLoadingState { () =>
      val tiling = EditorState.currentTiling.now()
      if !tiling.isEmpty then
        val dotContent = tiling.toDOT
        FileDownloader.trigger(dotContent, "tessellation.gv", "text/plain;charset=utf-8")
    }: Unit
