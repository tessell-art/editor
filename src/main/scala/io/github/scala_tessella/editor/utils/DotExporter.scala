package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.models.EditorState

object DotExporter:

  def exportTilingToDOT(): Unit =
    val tiling = EditorState.currentTiling.now()
    if !tiling.isEmpty then
      val dotContent = tiling.toDOT
      FileDownloader.trigger(dotContent, "tessellation.txt", "text/plain;charset=utf-8")
    

