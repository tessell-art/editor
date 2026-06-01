package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.i18n.I18n
import io.github.scala_tessella.editor.models.{EditorState, MenuShortcuts}
import io.github.scala_tessella.editor.models.MenuShortcuts.MenuAction
import io.github.scala_tessella.editor.operations.ViewOperations
import io.github.scala_tessella.editor.utils.file.{DotExporter, SvgExporter}
import io.github.scala_tessella.editor.operations.UndoManager

object MenuBarComponent:

  /** The four top-level menus (File / Edit / View / Help) as renderable elements.
    *
    * The outer chrome (logo, hamburger, theme toggle, language selector, popup mounters) lives in
    * [[AppShellComponent]]; this method just returns the menu drop-downs themselves.
    */
  def menuItems(): Seq[Element] =
    Seq(fileMenu(), editMenu(), viewMenu(), helpMenu())

  // A nested flyout submenu inside a dropdown (e.g. "Add Copy ▸"). Hovering the row reveals `children`
  // to the right via the `.submenu-item:hover > .submenu-content` rule in MenuBarStyles.css. The parent
  // row itself is inert (it only expands); the leaf rows carry the actions.
  private def submenuItem(titleKey: String, children: Mod[HtmlElement]*): Element =
    div(
      className := "submenu-item",
      a(
        href        := "#",
        onClick.preventDefault --> { _ =>

          ()
        },
        span(child.text <-- I18n.t(titleKey)),
        span(className := "submenu-arrow", "▸")
      ),
      div(className := "submenu-content", children)
    )

  // A helper to create a top-level menu item like "File", "Edit"
  private def menuItem(titleKey: String, children: Mod[HtmlElement]*): Element =
    div(
      className := "menu-item",
      button(
        className := "menu-button",
        child.text <-- I18n.t(titleKey)
      ),
      div(
        className := "dropdown-content",
        children
      )
    )

  // `action: => Unit` is by-name: call sites pass `foo()` directly, not `() => foo()`.
  private def dropdownLinkBase(
      title: Mod[HtmlElement],
      action: => Unit,
      enabled: Signal[Boolean],
      shortcut: Option[String]
  ): Element =
    a(
      href := "#",
      onClick.preventDefault.compose(
        _.withCurrentValueOf(enabled).collect:
          case (_, true) => ()
      ) --> { _ =>

        action
        EditorState.uiState.update(_.copy(isMenuOpen = false))
      }, // close menu on action
      className("disabled") <-- enabled.map(!_),
      span(title),
      shortcut.map(s => span(className := "shortcut", s))
    )

  /** Dropdown item bound to a translation key. Use [[dropdownLinkDynamic]] for items whose label depends on
    * toggle state in addition to locale.
    */
  private def dropdownLink(
      titleKey: String,
      action: => Unit,
      enabled: Signal[Boolean] = Val(true),
      shortcut: Option[String] = None
  ): Element =
    dropdownLinkBase(child.text <-- I18n.t(titleKey), action, enabled, shortcut)

  // For toggles whose label flips with state — accepts a precomputed Signal[String].
  private def dropdownLinkDynamic(
      title: Signal[String],
      action: => Unit,
      enabled: Signal[Boolean] = Val(true),
      shortcut: Option[String] = None
  ): Element =
    dropdownLinkBase(child.text <-- title, action, enabled, shortcut)

  /** Toggle label that depends on a boolean signal AND the current locale. The bool selects the key, `I18n.t`
    * maps the key to the translated string.
    */
  private def toggleLabel(state: Signal[Boolean], onKey: String, offKey: String): Signal[String] =
    state.combineWith(EditorState.localeState.signal).map { case (on, _) =>
      I18n.tNow(if on then onKey else offKey)
    }

  private def fileMenu(): Element =
    menuItem(
      "menu.file",
      dropdownLink("menu.file.new", AppState.newTiling()),
      dropdownLink("menu.file.newFromTemplate", AppState.openTemplateGallery()),
      dropdownLink("menu.file.recent", AppState.openRecentFilesPanel()),
      div(className := "menu-separator"),
      dropdownLink("menu.file.loadSvg", AppState.loadSvgFile()),
      dropdownLink(
        "menu.file.saveSvg",
        SvgExporter.saveTilingToSVG(),
        enabled = EditorState.canSaveCurrentFileWhenIdleSignal,
        shortcut = Some(MenuShortcuts.labelOf(MenuAction.FileSave))
      ),
      dropdownLink(
        "menu.file.saveSvgAs",
        SvgExporter.saveAsTilingToSVG(),
        enabled = EditorState.canMutateTilingSignal
      ),
      div(className := "menu-separator"),
      dropdownLink(
        "menu.file.exportDot",
        DotExporter.exportTilingToDOT(),
        enabled = EditorState.canMutateTilingSignal
      ),
//      dropdownLink(
//        "menu.file.printPdf",
//        AppState.openPrintPopup(),
//        enabled = EditorState.canMutateTilingSignal
//      ),
      div(className := "menu-separator"),
      dropdownLink("menu.file.settings", EditorState.popupState.update(_.copy(showSettingsPopup = true)))
    )

  private def editMenu(): Element =
    menuItem(
      "menu.edit",
      dropdownLink(
        "menu.edit.undo",
        UndoManager.undo(),
        AppState.canUndo,
        shortcut = Some(MenuShortcuts.labelOf(MenuAction.EditUndo))
      ),
      dropdownLink(
        "menu.edit.redo",
        UndoManager.redo(),
        AppState.canRedo,
        shortcut = Some(MenuShortcuts.labelOf(MenuAction.EditRedo))
      ),
      div(className := "menu-separator"),
      dropdownLink("menu.edit.clearTiling", AppState.clearTiling()),
      dropdownLink("menu.edit.mirror", AppState.mirrorTiling(), enabled = EditorState.canMutateTilingSignal),
      submenuItem(
        "menu.edit.addCopy",
        dropdownLink(
          "menu.edit.addCopy.translate",
          AppState.enterTranslateCopyMode(),
          enabled = EditorState.canMutateTilingSignal,
          shortcut = Some(MenuShortcuts.labelOf(MenuAction.EditAddCopyTranslate))
        ),
        dropdownLink(
          "menu.edit.addCopy.rotate",
          AppState.enterRotateCopyMode(),
          enabled = EditorState.canMutateTilingSignal,
          shortcut = Some(MenuShortcuts.labelOf(MenuAction.EditAddCopyRotate))
        ),
        dropdownLink(
          "menu.edit.addCopy.reflect",
          AppState.enterReflectCopyMode(),
          enabled = EditorState.canMutateTilingSignal,
          shortcut = Some(MenuShortcuts.labelOf(MenuAction.EditAddCopyReflect))
        ),
        dropdownLink(
          "menu.edit.addCopy.glide",
          AppState.enterGlideReflectCopyMode(),
          enabled = EditorState.canMutateTilingSignal
        )
      ),
      dropdownLink(
        "menu.edit.measurement",
        AppState.enterMeasureMode(),
        enabled = EditorState.canMutateTilingSignal
      ),
      div(className := "menu-separator"),
      dropdownLink("menu.edit.selectAll", AppState.selectAll(), EditorState.canMutateTilingSignal),
      dropdownLink(
        "menu.edit.deselectAll",
        AppState.deselectAll(),
        EditorState.canDeselectAllSignal,
        shortcut = Some(MenuShortcuts.labelOf(MenuAction.EditDeselectAll))
      ),
      div(className := "menu-separator"),
      a(
        href        := "#",
        span(child.text <-- I18n.t("menu.edit.fillColor")),
        onClick.preventDefault.compose(
          _.withCurrentValueOf(EditorState.colorState.signal.map(_.fillColor).distinct)
            .map((_, color) => color)
        ) --> { color =>

          EditorState.colorState.update(_.copy(tempColor = color))
          EditorState.colorState.update(_.copy(showColorPicker = true))
          EditorState.uiState.update(_.copy(isMenuOpen = false))
        }
      )
    )

  private def viewMenu(): Element =
    menuItem(
      "menu.view",
      dropdownLinkDynamic(
        toggleLabel(
          EditorState.viewState.signal.map(_.showNodeLabels).distinct,
          onKey = "menu.view.hideLabels",
          offKey = "menu.view.showLabels"
        ),
        AppState.toggleNodeLabels()
      ),
      dropdownLinkDynamic(
        toggleLabel(
          EditorState.viewState.signal.map(_.showUniformity).distinct,
          onKey = "menu.view.hideUniformity",
          offKey = "menu.view.showUniformity"
        ),
        AppState.toggleShowUniformity()
      ),
      dropdownLinkDynamic(
        toggleLabel(
          EditorState.viewState.signal.map(_.showRotation).distinct,
          onKey = "menu.view.hideRotation",
          offKey = "menu.view.showRotation"
        ),
        AppState.toggleShowRotation()
      ),
      dropdownLinkDynamic(
        toggleLabel(
          EditorState.viewState.signal.map(_.showReflection).distinct,
          onKey = "menu.view.hideReflection",
          offKey = "menu.view.showReflection"
        ),
        AppState.toggleShowReflection()
      ),
//      dropdownLinkDynamic(
//        toggleLabel(
//          EditorState.viewState.signal.map(_.showTilingInfo).distinct,
//          onKey = "menu.view.hideTilingInfo",
//          offKey = "menu.view.showTilingInfo"
//        ),
//        AppState.toggleShowTilingInfo()
//      ),
      div(className := "menu-separator"),
      dropdownLink(
        "menu.view.fitToCanvas",
        AppState.fitTilingToCanvas(),
        enabled = EditorState.canMutateTilingSignal,
        shortcut = Some(MenuShortcuts.labelOf(MenuAction.ViewFitToCanvas))
      ),
      dropdownLink("menu.view.resetView", ViewOperations.resetView()),
      div(className := "menu-separator"),
      dropdownLink(
        "menu.view.zoomIn",
        ViewOperations.zoomIn(),
        shortcut = Some(MenuShortcuts.labelOf(MenuAction.ViewZoomIn))
      ),
      dropdownLink(
        "menu.view.zoomOut",
        ViewOperations.zoomOut(),
        shortcut = Some(MenuShortcuts.labelOf(MenuAction.ViewZoomOut))
      ),
      dropdownLink(
        "menu.view.rotateLeft",
        ViewOperations.rotateView(-30),
        shortcut = Some(MenuShortcuts.labelOf(MenuAction.ViewRotateLeft))
      ),
      dropdownLink(
        "menu.view.rotateRight",
        ViewOperations.rotateView(30),
        shortcut = Some(MenuShortcuts.labelOf(MenuAction.ViewRotateRight))
      )
    )

  private def helpMenu(): Element =
    menuItem(
      "menu.help",
      dropdownLink("menu.help.guide", EditorState.popupState.update(_.copy(showGuidePopup = true))),
      dropdownLink(
        "menu.help.shortcuts",
        EditorState.popupState.update(_.copy(showShortcutsPopup = true))
      ),
      div(className := "menu-separator"),
      dropdownLink("menu.help.about", EditorState.popupState.update(_.copy(showAboutPopup = true)))
    )
