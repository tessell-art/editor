//! Native menu for the Tauri shell. Mirrors the DOM menu built in
//! MenuBarComponent.scala:fileMenu/editMenu/viewMenu/helpMenu so users
//! get the same entries via the OS menu bar (mandatory on macOS,
//! additive on Windows/Linux — see ADR-008 §"Menu integration").
//!
//! Click handling is intentionally thin: `on_event` emits a "menu"
//! event whose payload is the item id. DesktopMenuBridge.scala
//! subscribes there and dispatches to AppState — no exposed JS
//! commands, preserves the "no JS bridge" posture from ADR-005.

use tauri::{
    menu::{Menu, MenuBuilder, MenuEvent, MenuItemBuilder, PredefinedMenuItem, SubmenuBuilder},
    AppHandle, Emitter, Manager, Runtime,
};

use crate::menu_shortcuts::{
    EDIT_DESELECT_ALL, EDIT_DOUBLE_TO_INFINITE, EDIT_REDO, EDIT_UNDO, FILE_SAVE,
    VIEW_FIT_TO_CANVAS, VIEW_ROTATE_LEFT, VIEW_ROTATE_RIGHT, VIEW_ZOOM_IN, VIEW_ZOOM_OUT,
};

pub fn build<R: Runtime>(app: &AppHandle<R>) -> tauri::Result<Menu<R>> {
    let menu = MenuBuilder::new(app);

    // macOS: populate the app menu with standard items (About/Hide/Quit).
    // Not populating it leaves the default skeleton that looks broken.
    // Shadowing rather than `let mut` so non-macOS builds don't warn
    // about an unused `mut` binding.
    #[cfg(target_os = "macos")]
    let menu = {
        let app_menu = SubmenuBuilder::new(app, "Tessella")
            .item(&PredefinedMenuItem::about(app, None, None)?)
            .separator()
            .item(&PredefinedMenuItem::services(app, None)?)
            .separator()
            .item(&PredefinedMenuItem::hide(app, None)?)
            .item(&PredefinedMenuItem::hide_others(app, None)?)
            .item(&PredefinedMenuItem::show_all(app, None)?)
            .separator()
            .item(&PredefinedMenuItem::quit(app, None)?)
            .build()?;
        menu.item(&app_menu)
    };

    let file_menu = SubmenuBuilder::new(app, "File")
        .text("new", "New")
        .separator()
        .text("load-svg", "Load SVG...")
        .item(
            &MenuItemBuilder::with_id("save-svg", "Save SVG")
                .accelerator(FILE_SAVE.accelerator())
                .build(app)?,
        )
        .text("save-svg-as", "Save SVG as...")
        .separator()
        .text("export-dot", "Export to DOT...")
        .separator()
        .text("settings", "Settings...")
        .build()?;

    let edit_menu = SubmenuBuilder::new(app, "Edit")
        // Predefined items: OS handles Cut/Copy/Paste for text fields
        // (the editor has inputs in Settings and elsewhere). Shortcut
        // accelerators are the OS-standard ones, not ours.
        .item(&PredefinedMenuItem::cut(app, None)?)
        .item(&PredefinedMenuItem::copy(app, None)?)
        .item(&PredefinedMenuItem::paste(app, None)?)
        .separator()
        .item(
            &MenuItemBuilder::with_id("undo", "Undo")
                .accelerator(EDIT_UNDO.accelerator())
                .build(app)?,
        )
        .item(
            &MenuItemBuilder::with_id("redo", "Redo")
                .accelerator(EDIT_REDO.accelerator())
                .build(app)?,
        )
        .separator()
        .text("clear-tiling", "Clear Tiling")
        .item(
            &MenuItemBuilder::with_id("double-to-infinite", "Double (to infinite)")
                .accelerator(EDIT_DOUBLE_TO_INFINITE.accelerator())
                .build(app)?,
        )
        .text("mirror", "Mirror")
        .separator()
        // Our "Select All" selects polygons, not text. Rename so it doesn't
        // shadow the predefined text-field Select All — slice deliberately
        // leaves the predefined one out, and we let Ctrl/Cmd+A reach the
        // focused input directly.
        .text("select-all-polygons", "Select All Polygons")
        .item(
            &MenuItemBuilder::with_id("deselect-all", "Deselect All")
                .accelerator(EDIT_DESELECT_ALL.accelerator())
                .build(app)?,
        )
        .separator()
        .text("fill-color", "Fill Color...")
        .build()?;

    let view_menu = SubmenuBuilder::new(app, "View")
        .text("toggle-node-labels", "Toggle Node Labels")
        .text("toggle-uniformity", "Toggle Uniformity")
        .text("toggle-rotational-symmetry", "Toggle Rotational Symmetry")
        .text("toggle-reflectional-symmetry", "Toggle Reflectional Symmetry")
        .separator()
        .item(
            &MenuItemBuilder::with_id("fit-to-canvas", "Fit to Canvas")
                .accelerator(VIEW_FIT_TO_CANVAS.accelerator())
                .build(app)?,
        )
        .text("reset-view", "Reset View")
        .separator()
        .item(
            &MenuItemBuilder::with_id("zoom-in", "Zoom In")
                .accelerator(VIEW_ZOOM_IN.accelerator())
                .build(app)?,
        )
        .item(
            &MenuItemBuilder::with_id("zoom-out", "Zoom Out")
                .accelerator(VIEW_ZOOM_OUT.accelerator())
                .build(app)?,
        )
        .item(
            &MenuItemBuilder::with_id("rotate-left", "Rotate Left")
                .accelerator(VIEW_ROTATE_LEFT.accelerator())
                .build(app)?,
        )
        .item(
            &MenuItemBuilder::with_id("rotate-right", "Rotate Right")
                .accelerator(VIEW_ROTATE_RIGHT.accelerator())
                .build(app)?,
        )
        .build()?;

    let help_menu = SubmenuBuilder::new(app, "Help")
        .text("guide", "Guide...")
        .text("keyboard-shortcuts", "Keyboard Shortcuts...")
        .separator()
        .text("about", "About...")
        .build()?;

    menu.item(&file_menu)
        .item(&edit_menu)
        .item(&view_menu)
        .item(&help_menu)
        .build()
}

pub fn on_event<R: Runtime>(app: &AppHandle<R>, event: MenuEvent) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.emit("menu", event.id().0.clone());
    }
}
