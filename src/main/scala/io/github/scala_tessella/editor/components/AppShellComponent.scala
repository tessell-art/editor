package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.components.popup.*
import io.github.scala_tessella.editor.i18n.{I18n, Locale}
import io.github.scala_tessella.editor.models.{EditorState, Theme}
import io.github.scala_tessella.editor.utils.LocaleStorage

/** Top app shell — the persistent surface that surrounds the editor.
  *
  * Owns:
  *   - logo,
  *   - hamburger toggle (mobile drawer; polished further in Phase 3),
  *   - menu container (menu items themselves come from `MenuBarComponent`),
  *   - language selector slot (disabled placeholder; wired in Phase 4 / 5),
  *   - theme toggle,
  *   - mount points for the menu-driven popups (About, Guide, Shortcuts, Settings, IrregularPolygon).
  *
  * Splitting the shell out of `MenuBarComponent` matches the IA's `S-shell` surface and lets Phase 3 swap in
  * a phone-specific layout (hamburger drawer, etc.) without touching menu definitions.
  */
object AppShellComponent:

  def element(
      effectiveTheme: Signal[Theme],
      setUserThemePreference: Observer[Option[Theme]]
  ): Element =
    div(
      navTag(
        className := "app-shell",
        // Left side: logo + hamburger + menu items
        div(
          className := "app-shell-left",
          img(
            src       := "tessella-logo.svg",
            alt       := "Tessella Logo",
            className := "app-shell-logo"
          ),
          // Hamburger toggle — visible only at phone widths via CSS
          button(
            className := "app-shell-hamburger",
            onClick --> (_ => EditorState.uiState.update(s => s.copy(isMenuOpen = !s.isMenuOpen))),
            aria.label <-- I18n.t("ui.menu.toggle"),
            "☰"
          ),
          div(
            className <-- EditorState.uiState.signal.map(_.isMenuOpen).distinct.map(open =>
              if open then "app-shell-menu open" else "app-shell-menu"
            ),
            MenuBarComponent.menuItems()
          )
        ),
        // Right side: language slot + theme toggle
        div(
          className := "app-shell-right",
          languageSelector(),
          themeSwitcher(effectiveTheme, setUserThemePreference)
        )
      ),
      // Backdrop behind the open mobile menu drawer; tapping it closes the drawer.
      // CSS-hidden at desktop widths (the drawer is inline there).
      child.maybe <-- EditorState.uiState.signal.map(_.isMenuOpen).distinct.map: open =>
        if open then Some(menuBackdrop()) else None,
      // Popup mount points. Driven by popupState flags; rendered above all other surfaces.
      child.maybe <-- EditorState.popupState.signal.map(_.showIrregularPolygonPopup).distinct.map: show =>
        if show then Some(IrregularPolygonPopup.element) else None,
      child.maybe <-- EditorState.popupState.signal.map(_.showGuidePopup).distinct.map: show =>
        if show then Some(GuidePopup.element) else None,
      child.maybe <-- EditorState.popupState.signal.map(_.showShortcutsPopup).distinct.map: show =>
        if show then Some(ShortcutsPopup.element) else None,
      child.maybe <-- EditorState.popupState.signal.map(_.showAboutPopup).distinct.map: show =>
        if show then Some(AboutPopup.element) else None,
      child.maybe <-- EditorState.popupState.signal.map(_.showSettingsPopup).distinct.map: show =>
        if show then Some(SettingsPopup.element) else None,
      child.maybe <-- EditorState.popupState.signal.map(_.showTemplateGallery).distinct.map: show =>
        if show then Some(TemplateGalleryPopup.element) else None,
      child.maybe <-- EditorState.popupState.signal.map(_.showRecentFilesPanel).distinct.map: show =>
        if show then Some(RecentFilesPopup.element) else None,
      child.maybe <-- EditorState.popupState.signal.map(_.showPrintPopup).distinct.map: show =>
        if show then Some(PrintPopup.element) else None,
      child.maybe <-- EditorState.popupState.signal.map(_.showUnsavedConfirm).distinct.map: show =>
        if show then Some(UnsavedChangesPopup.element) else None,
      // First-run welcome overlay — visible until the user picks any of the three CTAs.
      child.maybe <-- EditorState.uiState.signal.map(_.showFirstRunOverlay).distinct.map: show =>
        if show then Some(FirstRunOverlay.element) else None
    )

  private def menuBackdrop(): Element =
    div(
      className := "app-shell-menu-backdrop",
      onClick --> { _ =>

        EditorState.uiState.update(_.copy(isMenuOpen = false))
      }
    )

  // Click cycles through `Locale.all`. With two languages (EN ↔ ES) it's a simple toggle; if a
  // third locale lands, the same button keeps working — replace with a popover at that point.
  private def languageSelector(): Element =
    button(
      className := "app-shell-language",
      tpe       := "button",
      title <-- I18n.t("ui.language.toggleTitle"),
      child.text <-- EditorState.localeState.signal.map(_.displayCode),
      onClick --> { _ =>

        val next = Locale.next(EditorState.localeState.now())
        EditorState.localeState.set(next)
        LocaleStorage.save(next)
      }
    )

  private def themeSwitcher(
      effectiveTheme: Signal[Theme],
      setUserThemePreference: Observer[Option[Theme]]
  ): Element =
    button(
      className := "app-shell-theme-toggle",
      title <-- effectiveTheme.combineWith(EditorState.localeState.signal).map { case (theme, _) =>
        theme match
          case Theme.Dark  => I18n.tNow("ui.theme.toLight")
          case Theme.Light => I18n.tNow("ui.theme.toDark")
      },
      onClick.compose(stream =>
        stream.withCurrentValueOf(effectiveTheme).map { case (_, currentTheme) =>
          Some(currentTheme.toggle)
        }
      ) --> setUserThemePreference,
      child <-- effectiveTheme.map {
        case Theme.Dark  => IconsSVG.sunIcon  // dark mode → click for light
        case Theme.Light => IconsSVG.moonIcon // light mode → click for dark
      }
    )
