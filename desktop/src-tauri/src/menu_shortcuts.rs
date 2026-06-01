//! Rust mirror of src/main/scala/.../models/MenuShortcuts.scala.
//!
//! The Scala side owns the DOM menu's display labels (Ctrl+S etc.).
//! This side owns the native menu's OS accelerators, which Tauri
//! remaps per-OS — "CmdOrCtrl" becomes ⌘ on macOS, Ctrl elsewhere.
//! Constant names here are the SCREAMING_SNAKE_CASE of the Scala
//! MenuAction enum values; MenuShortcutsParitySpec enforces that
//! every Scala case has a matching const here.

#![allow(dead_code)]

pub struct Shortcut {
    pub key: &'static str,
    pub primary: bool,
    pub shift: bool,
    pub alt: bool,
}

impl Shortcut {
    const fn key(key: &'static str) -> Self {
        Self { key, primary: false, shift: false, alt: false }
    }

    const fn primary(key: &'static str) -> Self {
        Self { key, primary: true, shift: false, alt: false }
    }

    const fn primary_shift(key: &'static str) -> Self {
        Self { key, primary: true, shift: true, alt: false }
    }

    /// Tauri accelerator string — "CmdOrCtrl+Shift+Z", "F", etc.
    pub fn accelerator(&self) -> String {
        let mut parts: Vec<&str> = Vec::new();
        if self.primary { parts.push("CmdOrCtrl"); }
        if self.shift   { parts.push("Shift"); }
        if self.alt     { parts.push("Alt"); }
        parts.push(self.key);
        parts.join("+")
    }
}

pub const FILE_SAVE:               Shortcut = Shortcut::primary("S");
pub const EDIT_UNDO:               Shortcut = Shortcut::primary("Z");
pub const EDIT_REDO:               Shortcut = Shortcut::primary_shift("Z");
pub const EDIT_DESELECT_ALL:       Shortcut = Shortcut::key("Escape");
pub const EDIT_ADD_COPY_TRANSLATE: Shortcut = Shortcut::key("T");
pub const EDIT_ADD_COPY_ROTATE:    Shortcut = Shortcut::key("R");
pub const EDIT_ADD_COPY_REFLECT:   Shortcut = Shortcut::key("Y");
pub const VIEW_FIT_TO_CANVAS:      Shortcut = Shortcut::key("F");
pub const VIEW_ZOOM_IN:            Shortcut = Shortcut::key("+");
pub const VIEW_ZOOM_OUT:           Shortcut = Shortcut::key("-");
pub const VIEW_ROTATE_LEFT:        Shortcut = Shortcut::key("Q");
pub const VIEW_ROTATE_RIGHT:       Shortcut = Shortcut::key("E");
