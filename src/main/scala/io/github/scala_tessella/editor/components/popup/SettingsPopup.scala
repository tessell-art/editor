package io.github.scala_tessella.editor.components.popup

import com.raquo.laminar.api.L._
import com.raquo.laminar.api.features.unitArrows
import io.github.nguyenyou.ui5.webcomponents.laminar.ColorPicker
import io.github.scala_tessella.editor.models.{EditorConfig, EditorState}
import io.github.scala_tessella.editor.utils.{ColorRGB, SettingsStorage}
import io.github.scala_tessella.editor.utils.ColorRGB.*

object SettingsPopup:

  import PopupCommons._

  private val tempDefaultFillColor: Var[ColorRGB]   = Var(EditorState.defaultStartFillColor.now())
  private val tempPerimeterColor: Var[ColorRGB]     = Var(EditorState.perimeterEdgeColor.now())
  private val showSettingsColorPicker: Var[Boolean] = Var(false)
  private val tempPickerColor: Var[ColorRGB]        = Var(EditorState.defaultStartFillColor.now())

  private enum SettingsColorTarget:
    case DefaultFill, PerimeterEdge

  private val settingsColorTarget: Var[SettingsColorTarget] = Var(SettingsColorTarget.DefaultFill)

  private val closeSettings: Observer[org.scalajs.dom.MouseEvent] =
    closePopup(EditorState.showSettingsPopup)

  private def refreshTempValues(): Unit =
    tempDefaultFillColor.set(EditorState.defaultStartFillColor.now())
    tempPerimeterColor.set(EditorState.perimeterEdgeColor.now())
    showSettingsColorPicker.set(false)

  private def openColorPicker(target: SettingsColorTarget, currentColor: ColorRGB): Unit =
    settingsColorTarget.set(target)
    tempPickerColor.set(currentColor)
    showSettingsColorPicker.set(true)

  private def applyPickerColor(color: ColorRGB): Unit =
    settingsColorTarget.now() match
      case SettingsColorTarget.DefaultFill   => tempDefaultFillColor.set(color)
      case SettingsColorTarget.PerimeterEdge => tempPerimeterColor.set(color)

  private def resetToDefaults(): Unit =
    tempDefaultFillColor.set(EditorConfig.defaultPolygonColor)
    tempPerimeterColor.set(EditorConfig.defaultPerimeterEdgeColor)

  private def settingsColorPickerPopup: Element =
    div(
      className := "popup-overlay settings-color-picker-overlay",
      display <-- showSettingsColorPicker.signal.map(if _ then "flex" else "none"),
      onClick.stopPropagation --> { _ =>

        showSettingsColorPicker.set(false)
      },
      div(
        className := "popup-content settings-color-picker-content",
        onClick.stopPropagation --> {},
        h3("Select color"),
        ColorPicker(
          _.simplified := true,
          _.value <-- tempPickerColor.signal.map(_.toRgba),
          _.onChange.map { event =>
            val color = event.target._colorValue._rgb
            ColorRGB(color.r.toInt, color.g.toInt, color.b.toInt)
          } --> tempPickerColor.writer
        )(),
        div(
          className := "popup-actions",
          button(
            "Cancel",
            onClick --> { _ =>

              showSettingsColorPicker.set(false)
            }
          ),
          button(
            "Apply",
            onClick --> { _ =>
              applyPickerColor(tempPickerColor.now())
              showSettingsColorPicker.set(false)
            }
          )
        )
      )
    )

  def element: Element =
    div(
      className := "popup-overlay settings-popup",
      display <-- EditorState.showSettingsPopup.signal.map(if _ then "flex" else "none"),
      onClick --> closeSettings,
      onMountCallback: ctx =>
        EditorState.showSettingsPopup.signal
          .changes
          .filter(identity)
          .foreach(_ => refreshTempValues())(using ctx.owner): Unit,
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
              div(
                className := "settings-swatch",
                backgroundColor <-- tempDefaultFillColor.signal.map(_.toHex)
              ),
              button(
                "Pick...",
                onClick --> { _ =>

                  openColorPicker(SettingsColorTarget.DefaultFill, tempDefaultFillColor.now())
                }
              ),
              span(
                className := "settings-value",
                child.text <-- tempDefaultFillColor.signal.map(_.toHex)
              )
            )
          ),
          div(
            className := "settings-row",
            div(className := "settings-label", "Perimeter edge color"),
            div(
              className   := "settings-control",
              div(
                className := "settings-swatch",
                backgroundColor <-- tempPerimeterColor.signal.map(_.toHex)
              ),
              button(
                "Pick...",
                onClick --> { _ =>

                  openColorPicker(SettingsColorTarget.PerimeterEdge, tempPerimeterColor.now())
                }
              ),
              span(
                className := "settings-value",
                child.text <-- tempPerimeterColor.signal.map(_.toHex)
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
            onClick --> { _ =>
              val fill = tempDefaultFillColor.now()
              EditorState.defaultStartFillColor.set(fill)
              EditorState.fillColor.set(fill)
              SettingsStorage.saveDefaultStartFillColor(fill)

              val perimeter = tempPerimeterColor.now()
              EditorState.perimeterEdgeColor.set(perimeter)
              SettingsStorage.savePerimeterEdgeColor(perimeter)
              EditorState.showSettingsPopup.set(false)
            }
          )
        )
      ),
      settingsColorPickerPopup
    )
