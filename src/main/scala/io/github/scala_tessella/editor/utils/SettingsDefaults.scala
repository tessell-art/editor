package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.models.EditorConfig

object SettingsDefaults:

  def tempDefaults: (ColorRGB, ColorRGB, ColorRGB) =
    (
      EditorConfig.defaultPolygonColor,
      EditorConfig.defaultPerimeterEdgeColor,
      EditorConfig.defaultPolygonEdgeColor
    )
