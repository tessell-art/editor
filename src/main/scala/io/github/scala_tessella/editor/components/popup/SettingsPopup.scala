package io.github.scala_tessella.editor.components.popup

import com.raquo.laminar.api.L._
import com.raquo.laminar.api.features.unitArrows
import io.github.nguyenyou.ui5.webcomponents.laminar.ColorPicker
import io.github.scala_tessella.editor.models.{AppState, EditorState}
import io.github.scala_tessella.editor.utils.{ColorRGB, SettingsDefaults}
import io.github.scala_tessella.editor.utils.ColorRGB.*

object SettingsPopup:

  import PopupCommons._

  private enum SettingsColorTarget:
    case DefaultFill, PerimeterEdge

  private val settingsColorTarget: Var[SettingsColorTarget] = Var(SettingsColorTarget.DefaultFill)

  private val closeSettings: Observer[org.scalajs.dom.MouseEvent] =
    closePopup(EditorState.showSettingsPopup)

  private def openColorPicker(target: SettingsColorTarget, currentColor: ColorRGB): Unit =
    settingsColorTarget.set(target)
    EditorState.tempSettingsPickerColor.set(currentColor)
    EditorState.showSettingsColorPicker.set(true)

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

        EditorState.showSettingsColorPicker.set(false)
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

              EditorState.showSettingsColorPicker.set(false)
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
              EditorState.showSettingsColorPicker.set(false)
            }
          )
        )
      )
    )

  def element: Element =
    div(
      className := "popup-overlay settings-popup",
      onClick --> closeSettings,
      onMountCallback: ctx =>
        EditorState.showSettingsPopup.signal
          .changes
          .filter:
            identity
          .foreach(_ => AppState.refreshSettingsTempValues())(using ctx.owner): Unit,
      div(
        className := "popup-content settings-content",
        onClick.stopPropagation --> {},
        button(
          className := "popup-close-btn",
          onClick --> closeSettings,
          closeIcon
        ),
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

              EditorState.showSettingsPopup.set(false)
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
              EditorState.showSettingsPopup.set(false)
            }
          )
        )
      ),
      child.maybe <-- EditorState.showSettingsColorPicker.signal.map: show =>
        if show then Some(settingsColorPickerPopup) else None
    )
