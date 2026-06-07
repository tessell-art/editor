package art.tessell.editor.utils

import art.tessell.editor.models.EditorConfig

object SettingsDefaults:

  def tempDefaults: (ColorRGB, ColorRGB, ColorRGB) =
    (
      EditorConfig.defaultPolygonColor,
      EditorConfig.defaultPerimeterEdgeColor,
      EditorConfig.defaultPolygonEdgeColor
    )
