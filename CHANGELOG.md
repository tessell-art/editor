# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Releases prior to `v0.4.1` are documented in the
[GitHub Releases page](https://github.com/tessell-art/editor/releases)
and in the git history.

## [Unreleased]

## [0.4.1] - 2026-05-23

### Added
- Apache License 2.0 — full text in `LICENSE` at the repo root.
- License notice, copyright line, and source-code link in the About popup
  (visible in both the web app and the Tauri desktop bundles).
- Desktop bundles are now attached to each GitHub Release as downloadable
  assets — `.deb` and `.AppImage` for Linux, `.dmg` for macOS, `.msi` and
  `.exe` for Windows. Previously the bundles were only retrievable from
  the per-run artifacts page of the GitHub Actions workflow, which
  expires after 90 days.

### Changed
- Build depends on the published `dcel` 0.1.0 (previously consumed via a
  vendored SNAPSHOT artifact under `lib-repo/`).
- CI: bumped `cloudflare/wrangler-action` from `v3` to `v4`; the obsolete
  `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24` workaround was removed from the
  Pages deploy workflow, clearing the Node 20 deprecation warning on
  every deploy run.

[Unreleased]: https://github.com/tessell-art/editor/compare/v0.4.1...HEAD
[0.4.1]: https://github.com/tessell-art/editor/compare/v0.4.0...v0.4.1
