package io.github.scala_tessella.editor.components.popup

import com.raquo.laminar.api.L._
import io.github.scala_tessella.editor.components.IconsSVG
import io.github.scala_tessella.editor.i18n.I18n
import io.github.scala_tessella.editor.models.EditorState

object GuidePopup:

  import PopupCommons._

  private val titleId = "guide-popup-title"

  private val closeGuide: Observer[Any] =
    closePopup(EditorState.popupState.update(_.copy(showGuidePopup = false)))

  // Inline-element factories referenced by `{tokenName}` placeholders in the
  // `popup.guide.*` templates. Each factory is invoked on every locale change,
  // so embedded `tNow` lookups always reflect the current locale.
  private def tokens: Map[String, () => Element] =
    def emKey(key: String): () => Element                   = () => em(I18n.tNow(key))
    def menuItem(menu: String, item: String): () => Element =
      () => strong(s"${I18n.tNow(menu)} → ${I18n.tNow(item)}")
    def menuOnly(key: String): () => Element                = () => strong(I18n.tNow(key))

    Map(
      "addOutside"         -> emKey("popup.guide.mode.outside"),
      "addInside"          -> emKey("popup.guide.mode.inside"),
      "selectAll"          -> emKey("popup.guide.tool.selectAll"),
      "deselectAll"        -> emKey("popup.guide.tool.deselectAll"),
      "selectButton"       -> emKey("popup.guide.tool.selectButton"),
      "selectByColor"      -> emKey("popup.guide.tool.selectByColor"),
      "shapeAndColor"      -> emKey("popup.guide.tool.shapeAndColor"),
      "colorPicker"        -> emKey("popup.guide.tool.colorPicker"),
      "eraser"             -> emKey("popup.guide.tool.eraser"),
      "measurement"        -> emKey("popup.guide.tool.measurement"),
      "fit"                -> emKey("popup.guide.tool.fit"),
      "iconPlus"           -> (() => IconsSVG.plusIcon),
      "iconSelectAll"      -> (() => IconsSVG.selectionGridFilledIcon),
      "iconDeselectAll"    -> (() => IconsSVG.selectionGridEmptyIcon),
      "iconSelectByColor"  -> (() => IconsSVG.selectByColorIcon),
      "iconShapeAndColor"  -> (() => IconsSVG.eyeDropperPentagonIcon),
      "iconColorPicker"    -> (() => IconsSVG.eyeDropperIcon),
      "iconEraser"         -> (() => IconsSVG.eraserIcon),
      "iconRuler"          -> (() => IconsSVG.rulerIcon),
      "iconMaximize"       -> (() => IconsSVG.maximizeIcon),
      "kbdEsc"             -> (() => kbd("Esc")),
      "kbdQ"               -> (() => kbd('Q')),
      "kbdE"               -> (() => kbd('E')),
      "kbdF"               -> (() => kbd('F')),
      "kbdPlus"            -> (() => kbd('+')),
      "kbdMinus"           -> (() => kbd('-')),
      "menuMirror"         -> menuItem("menu.edit", "menu.edit.mirror"),
      "menuFillColor"      -> menuItem("menu.edit", "menu.edit.fillColor"),
      "menuShowLabels"     -> menuItem("menu.view", "menu.view.showLabels"),
      "menuShowUniformity" -> menuItem("menu.view", "menu.view.showUniformity"),
      "menuShowRotation"   -> menuItem("menu.view", "menu.view.showRotation"),
      "menuShowReflection" -> menuItem("menu.view", "menu.view.showReflection"),
      "menuResetView"      -> menuItem("menu.view", "menu.view.resetView"),
      "menuFile"           -> menuOnly("menu.file"),
      "menuSaveSvg"        -> menuOnly("menu.file.saveSvg"),
      "menuSaveSvgAs"      -> menuOnly("menu.file.saveSvgAs")
    )

  private def heading(key: String): Element =
    h3(child.text <-- I18n.t(key))

  private def item(key: String): Element =
    li(I18n.tFragments(key, tokens))

  def element: Element =
    popupOverlay(closeGuide)(
      popupContent(closeGuide)(
        aria.labelledBy := titleId,
        h2(idAttr   := titleId, child.text <-- I18n.t("popup.guide.title")),
        div(
          className := "popup-text-scrollable",
          tabIndex  := 0,
          aria.label <-- I18n.t("popup.guide.bodyAriaLabel"),
          heading("popup.guide.section.creating"),
          ul(
            item("popup.guide.creating.modes"),
            item("popup.guide.creating.start")
          ),
          heading("popup.guide.section.addOutside"),
          ul(item("popup.guide.addOutside.howTo")),
          heading("popup.guide.section.addInside"),
          ul(
            item("popup.guide.addInside.toggle"),
            item("popup.guide.addInside.howTo")
          ),
          heading("popup.guide.section.addIrregular"),
          ul(
            item("popup.guide.addIrregular.pick"),
            item("popup.guide.addIrregular.shift"),
            item("popup.guide.addIrregular.copy")
          ),
          heading("popup.guide.section.selecting"),
          ul(
            item("popup.guide.selecting.click"),
            item("popup.guide.selecting.all"),
            item("popup.guide.selecting.deselectAll"),
            item("popup.guide.selecting.sameShape"),
            item("popup.guide.selecting.byColor")
          ),
          heading("popup.guide.section.deleting"),
          ul(
            item("popup.guide.deleting.tool"),
            item("popup.guide.deleting.howTo")
          ),
          heading("popup.guide.section.mirror"),
          ul(
            item("popup.guide.mirror.howTo")
          ),
          heading("popup.guide.section.styling"),
          ul(
            item("popup.guide.styling.fill"),
            item("popup.guide.styling.applyTo"),
            item("popup.guide.styling.copy"),
            item("popup.guide.styling.copyShape")
          ),
          heading("popup.guide.section.visual"),
          ul(
            item("popup.guide.visual.labels"),
            item("popup.guide.visual.uniformity"),
            item("popup.guide.visual.rotation"),
            item("popup.guide.visual.reflection")
          ),
          heading("popup.guide.section.measurement"),
          ul(
            item("popup.guide.measurement.unitLength"),
            item("popup.guide.measurement.tool"),
            item("popup.guide.measurement.startEnd"),
            item("popup.guide.measurement.angle")
          ),
          heading("popup.guide.section.navigating"),
          ul(
            item("popup.guide.navigating.pan"),
            item("popup.guide.navigating.zoom"),
            item("popup.guide.navigating.rotate"),
            item("popup.guide.navigating.fit"),
            item("popup.guide.navigating.reset")
          ),
          heading("popup.guide.section.savingLoading"),
          ul(
            item("popup.guide.savingLoading.svg"),
            item("popup.guide.savingLoading.dot")
          ),
          heading("popup.guide.section.validation"),
          ul(
            item("popup.guide.validation.add"),
            item("popup.guide.validation.remove")
          )
        )
      )
    )
