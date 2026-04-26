package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.SettingsStorage

/** Operations over user-editable settings: default polygon fill colour, perimeter edge colour, boundary edge
  * width, reduce-motion preference. Handles both the runtime state and its persistence through
  * [[SettingsStorage]].
  */
object SettingsOperations:

  /** Syncs the temporary settings-popup values from the current saved settings and hides the settings color
    * picker.
    */
  def refreshSettingsTempValues(): Unit =
    val cs = EditorState.colorState.now()
    EditorState.colorState.update(_.copy(
      tempDefaultFillColor = cs.defaultStartFillColor,
      tempPerimeterEdgeColor = cs.perimeterEdgeColor,
      tempSettingsPickerColor = cs.defaultStartFillColor
    ))
    EditorState.popupState.update(_.copy(showSettingsColorPicker = false))
    val ss = EditorState.settingsState.now()
    EditorState.settingsState.update(_.copy(
      tempBoundaryEdgeWidth = ss.boundaryEdgeWidth,
      tempReduceMotion = ss.reduceMotion
    ))

  /** Commits the popup's temp values into the saved-settings fields and persists them via
    * [[SettingsStorage]]. Reads temp values from state — popup just calls this then closes.
    */
  def applySettings(): Unit =
    val cs = EditorState.colorState.now()
    val ss = EditorState.settingsState.now()
    EditorState.colorState.update(_.copy(
      defaultStartFillColor = cs.tempDefaultFillColor,
      fillColor = cs.tempDefaultFillColor,
      perimeterEdgeColor = cs.tempPerimeterEdgeColor
    ))
    EditorState.settingsState.update(_.copy(
      boundaryEdgeWidth = ss.tempBoundaryEdgeWidth,
      reduceMotion = ss.tempReduceMotion
    ))
    SettingsStorage.saveDefaultStartFillColor(cs.tempDefaultFillColor)
    SettingsStorage.savePerimeterEdgeColor(cs.tempPerimeterEdgeColor)
    SettingsStorage.saveBoundaryEdgeWidth(ss.tempBoundaryEdgeWidth)
    SettingsStorage.saveReduceMotion(ss.tempReduceMotion)

  /** Resets the current fill color to the default start fill color. */
  def resetFillColorToDefault(): Unit =
    EditorState.colorState.update(_.copy(fillColor = EditorState.colorState.now().defaultStartFillColor))
