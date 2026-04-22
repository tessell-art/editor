package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.{ColorRGB, SettingsStorage}

/** Operations over user-editable settings (default polygon fill, perimeter edge colour). Handles both the
  * runtime state and its persistence through [[SettingsStorage]].
  */
object SettingsOperations:

  /** Syncs the temporary settings-popup values from the current saved settings and hides the settings color
    * picker.
    */
  def refreshSettingsTempValues(): Unit =
    EditorState.colorState.update(_.copy(tempDefaultFillColor =
      EditorState.colorState.now().defaultStartFillColor
    ))
    EditorState.colorState.update(_.copy(tempPerimeterEdgeColor =
      EditorState.colorState.now().perimeterEdgeColor
    ))
    EditorState.colorState.update(_.copy(tempSettingsPickerColor =
      EditorState.colorState.now().defaultStartFillColor
    ))
    EditorState.popupState.update(_.copy(showSettingsColorPicker = false))

  /** Applies editor settings (default fill color, perimeter edge color) and persists them via
    * [[SettingsStorage]].
    */
  def applySettings(defaultFill: ColorRGB, perimeterEdge: ColorRGB): Unit =
    EditorState.colorState.update(_.copy(defaultStartFillColor = defaultFill))
    EditorState.colorState.update(_.copy(fillColor = defaultFill))
    SettingsStorage.saveDefaultStartFillColor(defaultFill)
    EditorState.colorState.update(_.copy(perimeterEdgeColor = perimeterEdge))
    SettingsStorage.savePerimeterEdgeColor(perimeterEdge)

  /** Resets the current fill color to the default start fill color. */
  def resetFillColorToDefault(): Unit =
    EditorState.colorState.update(_.copy(fillColor = EditorState.colorState.now().defaultStartFillColor))
