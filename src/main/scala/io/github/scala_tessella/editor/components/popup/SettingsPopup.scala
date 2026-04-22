package io.github.scala_tessella.editor.components.popup

import com.raquo.laminar.api.L._
import com.raquo.laminar.api.features.unitArrows
import io.github.nguyenyou.ui5.webcomponents.laminar.ColorPicker
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.{ColorRGB, SettingsDefaults}
import io.github.scala_tessella.editor.utils.ColorRGB.*

object SettingsPopup:

  import PopupCommons._

  private enum SettingsColorTarget:
    case DefaultFill, PerimeterEdge

  private val settingsColorTarget: Var[SettingsColorTarget] = Var(SettingsColorTarget.DefaultFill)

  private val closeSettings: Observer[org.scalajs.dom.MouseEvent] =
    closePopup(EditorState.popupState.update(_.copy(showSettingsPopup = false)))

  private def openColorPicker(target: SettingsColorTarget, currentColor: ColorRGB): Unit =
    settingsColorTarget.set(target)
    EditorState.tempSettingsPickerColor.set(currentColor)
    EditorState.popupState.update(_.copy(showSettingsColorPicker = true))

  private def applyPickerColor(target: SettingsColorTarget, color: ColorRGB): Unit =
    target match
      case SettingsColorTarget.DefaultFill   => EditorState.tempDefaultFillColor.set(color)
      case SettingsColorTarget.PerimeterEdge => EditorState.tempPerimeterEdgeColor.set(color)

  private def resetToDefaults(): Unit =
    val (fill, perimeter) = SettingsDefaults.tempDefaults
    EditorState.tempDefaultFillColor.set(fill)
    EditorState.tempPerimeterEdgeColor.set(perimeter)

  private def settingsColorPickerPopup: Element =
    div(
      className := "popup-overlay settings-color-picker-overlay",
      onClick.stopPropagation --> { _ =>

        EditorState.popupState.update(_.copy(showSettingsColorPicker = false))
      },
      div(
        className := "popup-content settings-color-picker-content",
        onClick.stopPropagation --> {},
        h3("Select color"),
        ColorPicker(
          _.simplified := true,
          _.value <-- EditorState.tempSettingsPickerColor.signal.map:
            _.toRgba
          ,
          _.onChange.map { event =>

            val color = event.target._colorValue._rgb
            ColorRGB(color.r.toInt, color.g.toInt, color.b.toInt)
          } --> EditorState.tempSettingsPickerColor.writer
        )(),
        div(
          className := "popup-actions",
          button(
            "Cancel",
            onClick.stopPropagation --> { _ =>

              EditorState.popupState.update(_.copy(showSettingsColorPicker = false))
            }
          ),
          button(
            "Apply",
            onClick.stopPropagation.compose(
              _.withCurrentValueOf(
                settingsColorTarget.signal.combineWith(EditorState.tempSettingsPickerColor.signal)
              )
            ) --> { case (_, target: SettingsColorTarget, color: ColorRGB) =>
              applyPickerColor(target, color)
              EditorState.popupState.update(_.copy(showSettingsColorPicker = false))
            }
          )
        )
      )
    )

  def element: Element =
    popupOverlay(
      closeSettings,
      overlayClassName = "popup-overlay settings-popup",
      onMountCallback: ctx =>
        EditorState.popupState.signal.map(_.showSettingsPopup).distinct
          .changes
          .filter:
            identity
          .foreach(_ => AppState.refreshSettingsTempValues())(using ctx.owner): Unit
    )(
      popupContent(closeSettings, contentClassName = "popup-content settings-content")(
        h1("Settings"),
        div(
          className := "settings-grid",
          div(
            className := "settings-row",
            div(className := "settings-label", "Default start fill color"),
            div(
              className   := "settings-control",
              button(
                className := "settings-swatch-button",
                onClick.compose(
                  _.withCurrentValueOf(EditorState.tempDefaultFillColor.signal)
                    .map((_, color) => color)
                ) --> { color =>

                  openColorPicker(SettingsColorTarget.DefaultFill, color)
                },
                div(
                  className := "settings-swatch",
                  backgroundColor <-- EditorState.tempDefaultFillColor.signal.map:
                    _.toHex
                )
              ),
              span(
                className := "settings-value",
                child.text <-- EditorState.tempDefaultFillColor.signal.map(_.toHex)
              )
            )
          ),
          div(
            className := "settings-row",
            div(className := "settings-label", "Perimeter edge color"),
            div(
              className   := "settings-control",
              button(
                className := "settings-swatch-button",
                onClick.compose(
                  _.withCurrentValueOf(EditorState.tempPerimeterEdgeColor.signal)
                    .map((_, color) => color)
                ) --> { color =>

                  openColorPicker(SettingsColorTarget.PerimeterEdge, color)
                },
                div(
                  className := "settings-swatch",
                  backgroundColor <-- EditorState.tempPerimeterEdgeColor.signal.map:
                    _.toHex
                )
              ),
              span(
                className := "settings-value",
                child.text <-- EditorState.tempPerimeterEdgeColor.signal.map:
                  _.toHex
              )
            )
          )
        ),
        div(
          className := "popup-actions",
          button(
            "Cancel",
            onClick --> { _ =>

              EditorState.popupState.update(_.copy(showSettingsPopup = false))
            }
          ),
          button(
            "Reset to defaults",
            onClick --> { _ =>

              resetToDefaults()
            }
          ),
          button(
            "Apply",
            onClick.compose(
              _.withCurrentValueOf(
                EditorState.tempDefaultFillColor.signal.combineWith(
                  EditorState.tempPerimeterEdgeColor.signal
                )
              )
            ) --> { case (_, fill: ColorRGB, perimeter: ColorRGB) =>
              AppState.applySettings(fill, perimeter)
              EditorState.popupState.update(_.copy(showSettingsPopup = false))
            }
          )
        )
      ),
      child.maybe <-- EditorState.popupState.signal.map(_.showSettingsColorPicker).distinct.map: show =>
        if show then Some(settingsColorPickerPopup) else None
    )
