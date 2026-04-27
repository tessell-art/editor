package io.github.scala_tessella.editor.components.popup

import com.raquo.laminar.api.L._
import com.raquo.laminar.api.features.unitArrows
import io.github.nguyenyou.ui5.webcomponents.laminar.ColorPicker
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.i18n.{I18n, Locale}
import io.github.scala_tessella.editor.models.{EditorConfig, EditorState, ReduceMotionPref}
import io.github.scala_tessella.editor.utils.{ColorRGB, LocaleStorage, SettingsDefaults}
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
    EditorState.colorState.update(_.copy(tempSettingsPickerColor = currentColor))
    EditorState.popupState.update(_.copy(showSettingsColorPicker = true))

  private def applyPickerColor(target: SettingsColorTarget, color: ColorRGB): Unit =
    target match
      case SettingsColorTarget.DefaultFill   =>
        EditorState.colorState.update(_.copy(tempDefaultFillColor = color))
      case SettingsColorTarget.PerimeterEdge =>
        EditorState.colorState.update(_.copy(tempPerimeterEdgeColor = color))

  private def resetToDefaults(): Unit =
    val (fill, perimeter) = SettingsDefaults.tempDefaults
    EditorState.colorState.update(_.copy(tempDefaultFillColor = fill))
    EditorState.colorState.update(_.copy(tempPerimeterEdgeColor = perimeter))
    EditorState.settingsState.update(_.copy(
      tempBoundaryEdgeWidth = EditorConfig.defaultBoundaryEdgeWidth,
      tempReduceMotion = ReduceMotionPref.Auto
    ))

  private def settingsColorPickerPopup: Element =
    div(
      className := "popup-overlay settings-color-picker-overlay",
      onClick.stopPropagation --> { _ =>

        EditorState.popupState.update(_.copy(showSettingsColorPicker = false))
      },
      div(
        className := "popup-content settings-color-picker-content",
        onClick.stopPropagation --> {},
        h3(child.text <-- I18n.t("popup.colorPicker.title")),
        ColorPicker(
          _.simplified := true,
          _.value <-- EditorState.colorState.signal.map(_.tempSettingsPickerColor).distinct.map:
            _.toRgba
          ,
          _.onChange.map { event =>

            val color = event.target._colorValue._rgb
            ColorRGB(color.r.toInt, color.g.toInt, color.b.toInt)
          } --> Observer[ColorRGB](c => EditorState.colorState.update(_.copy(tempSettingsPickerColor = c)))
        )(),
        div(
          className := "popup-actions",
          button(
            child.text <-- I18n.t("common.cancel"),
            onClick.stopPropagation --> { _ =>

              EditorState.popupState.update(_.copy(showSettingsColorPicker = false))
            }
          ),
          button(
            child.text <-- I18n.t("popup.settings.apply"),
            onClick.stopPropagation.compose(
              _.withCurrentValueOf(
                settingsColorTarget.signal.combineWith(
                  EditorState.colorState.signal.map(_.tempSettingsPickerColor).distinct
                )
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
        h1(child.text <-- I18n.t("popup.settings.title")),
        div(
          className := "settings-grid",
          div(
            className := "settings-row",
            div(className := "settings-label", child.text <-- I18n.t("popup.settings.fillColor")),
            div(
              className   := "settings-control",
              button(
                className := "settings-swatch-button",
                onClick.compose(
                  _.withCurrentValueOf(EditorState.colorState.signal.map(_.tempDefaultFillColor).distinct)
                    .map((_, color) => color)
                ) --> { color =>

                  openColorPicker(SettingsColorTarget.DefaultFill, color)
                },
                div(
                  className := "settings-swatch",
                  backgroundColor <-- EditorState.colorState.signal.map(_.tempDefaultFillColor).distinct.map:
                    _.toHex
                )
              ),
              span(
                className := "settings-value",
                child.text <-- EditorState.colorState.signal.map(_.tempDefaultFillColor).distinct.map(_.toHex)
              )
            )
          ),
          div(
            className := "settings-row",
            div(className := "settings-label", child.text <-- I18n.t("popup.settings.boundaryColor")),
            div(
              className   := "settings-control",
              button(
                className := "settings-swatch-button",
                onClick.compose(
                  _.withCurrentValueOf(EditorState.colorState.signal.map(_.tempPerimeterEdgeColor).distinct)
                    .map((_, color) => color)
                ) --> { color =>

                  openColorPicker(SettingsColorTarget.PerimeterEdge, color)
                },
                div(
                  className := "settings-swatch",
                  backgroundColor <--
                    EditorState.colorState.signal.map(_.tempPerimeterEdgeColor).distinct.map:
                      _.toHex
                )
              ),
              span(
                className := "settings-value",
                child.text <-- EditorState.colorState.signal.map(_.tempPerimeterEdgeColor).distinct.map:
                  _.toHex
              )
            )
          ),
          boundaryEdgeWidthRow(),
          reduceMotionRow()
//          languageRow()
        ),
        div(
          className := "popup-actions",
          button(
            child.text <-- I18n.t("common.cancel"),
            onClick --> { _ =>

              EditorState.popupState.update(_.copy(showSettingsPopup = false))
            }
          ),
          button(
            child.text <-- I18n.t("popup.settings.resetDefaults"),
            onClick --> { _ =>

              resetToDefaults()
            }
          ),
          button(
            child.text <-- I18n.t("popup.settings.apply"),
            onClick --> { _ =>

              AppState.applySettings()
              EditorState.popupState.update(_.copy(showSettingsPopup = false))
            }
          )
        )
      ),
      child.maybe <-- EditorState.popupState.signal.map(_.showSettingsColorPicker).distinct.map: show =>
        if show then Some(settingsColorPickerPopup) else None
    )

  private def boundaryEdgeWidthRow(): Element =
    val widthSignal = EditorState.settingsState.signal.map(_.tempBoundaryEdgeWidth).distinct
    div(
      className := "settings-row",
      div(className := "settings-label", child.text <-- I18n.t("popup.settings.boundaryWidth")),
      div(
        className   := "settings-control",
        input(
          tpe          := "range",
          className    := "settings-range",
          minAttr      := EditorConfig.minBoundaryEdgeWidth.toString,
          maxAttr      := EditorConfig.maxBoundaryEdgeWidth.toString,
          stepAttr     := "0.5",
          controlled(
            value <-- widthSignal.map(_.toString),
            onInput.mapToValue --> Observer[String]: v =>
              v.toDoubleOption.foreach: d =>
                EditorState.settingsState.update(_.copy(tempBoundaryEdgeWidth = d))
          )
        ),
        span(className := "settings-value", child.text <-- widthSignal.map(d => f"$d%.1f px"))
      )
    )

  private def reduceMotionRow(): Element =
    val prefSignal = EditorState.settingsState.signal.map(_.tempReduceMotion).distinct
    div(
      className := "settings-row",
      div(className := "settings-label", child.text <-- I18n.t("popup.settings.reduceMotion")),
      div(
        className   := "settings-control settings-radio-group",
        radio(
          "popup.settings.reduceMotion.auto",
          "settings-reduce-motion",
          ReduceMotionPref.Auto,
          prefSignal
        ),
        radio("popup.settings.reduceMotion.on", "settings-reduce-motion", ReduceMotionPref.On, prefSignal),
        radio("popup.settings.reduceMotion.off", "settings-reduce-motion", ReduceMotionPref.Off, prefSignal)
      )
    )

  private def radio(
      labelKey: String,
      groupName: String,
      pref: ReduceMotionPref,
      currentSignal: Signal[ReduceMotionPref]
  ): Element =
    label(
      className := "settings-radio-label",
      input(
        tpe      := "radio",
        nameAttr := groupName,
        checked <-- currentSignal.map(_ == pref),
        onChange --> { _ =>

          EditorState.settingsState.update(_.copy(tempReduceMotion = pref))
        }
      ),
      span(child.text <-- I18n.t(labelKey))
    )

  // The language picker here mirrors the top-bar selector but persists immediately and renders
  // every available locale. Selecting one updates `EditorState.localeState` (which retranslates
  // every UI label) and writes through `LocaleStorage`.
  private def languageRow(): Element =
    div(
      className := "settings-row",
      div(className := "settings-label", child.text <-- I18n.t("popup.settings.language")),
      div(
        className   := "settings-control",
        select(
          controlled(
            value <-- EditorState.localeState.signal.map(_.code),
            onChange.mapToValue --> Observer[String]: code =>
              Locale.fromCode(code).foreach: chosen =>

                EditorState.localeState.set(chosen)
                LocaleStorage.save(chosen)
          ),
          Locale.all.map(loc => option(value := loc.code, loc.displayName))
        )
      )
    )
