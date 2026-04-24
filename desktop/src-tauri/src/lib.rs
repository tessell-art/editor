//! Tauri desktop shell for Tessella (ADR-008). Thin wrapper: builds a
//! native menu that mirrors the DOM menu in MenuBarComponent.scala,
//! emits "menu" events with the clicked item's id as payload, and
//! hands off to DesktopMenuBridge.scala on the JS side.

mod menu;
mod menu_shortcuts;

pub fn run() {
    tauri::Builder::default()
        .menu(menu::build)
        .on_menu_event(menu::on_event)
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
