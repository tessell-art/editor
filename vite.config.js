import { defineConfig } from "vite";
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs";
import { execSync } from "node:child_process";
import { readFileSync } from "node:fs";

// Rewrites UI5 Web Components' hardcoded jsdelivr.net URLs (SAP "72" fonts
// + OpenUI5 CLDR) to local relative paths served from public/ui5-assets/.
// Relative to the document URL at runtime so it works under http(s)://
// and file:// (Android WebView, Tauri desktop). Version segments are
// matched with `[^/]+` so UI5 upgrades don't silently skip the rewrite.
function ui5LocalAssets() {
    return {
        name: 'ui5-local-assets',
        transform(code) {
            if (!code.includes('cdn.jsdelivr.net/npm/@sap-theming') &&
                !code.includes('cdn.jsdelivr.net/npm/@openui5')) return null;
            return code
                .replace(
                    /https:\/\/cdn\.jsdelivr\.net\/npm\/@sap-theming\/theming-base-content@[^/]+\/content\/Base\/baseLib\/baseTheme\//g,
                    './ui5-assets/'
                )
                .replace(
                    /https:\/\/cdn\.jsdelivr\.net\/npm\/@openui5\/sap\.ui\.core@[^/]+\/src\/sap\/ui\/core\/cldr\//g,
                    './ui5-assets/cldr/'
                );
        }
    };
}

// Emit dist/version.json so a long-lived tab can poll for newer deploys
// and surface a "Reload" banner (see UpdateChecker.scala). Cache-Control
// for the file is pinned to no-store via public/_headers (Cloudflare
// Pages convention).
function versionJson() {
    return {
        name: 'version-json',
        apply: 'build',
        generateBundle() {
            const pkg = JSON.parse(readFileSync('package.json', 'utf-8'));
            const commit =
                process.env.GITHUB_SHA?.slice(0, 7) ??
                (() => {
                    try { return execSync('git rev-parse --short HEAD').toString().trim(); }
                    catch { return 'dev'; }
                })();
            const payload = {
                version: pkg.version,
                commit,
                builtAt: new Date().toISOString()
            };
            this.emitFile({
                type: 'asset',
                fileName: 'version.json',
                source: JSON.stringify(payload) + '\n'
            });
        }
    };
}

export default defineConfig({
    base: './',
    // Pin the syntax floor explicitly instead of inheriting Vite's moving
    // default. es2020 == optional chaining / nullish coalescing == Chromium
    // 80+ / Safari 13.1+. Matches the minimum-engine guard in index.html, and
    // keeps the floor stable across Vite upgrades. Lower targets aren't worth
    // it: the UI5 Web Components runtime needs a roughly current engine anyway,
    // and the guard handles older ones gracefully. See ADR-005 "minimum WebView".
    build: {
        target: 'es2020'
    },
    plugins: [scalaJSPlugin(), ui5LocalAssets(), versionJson()],
    optimizeDeps: {
        include: [
            '@ui5/webcomponents/dist/ColorPicker.js',
        ]
    }
});
