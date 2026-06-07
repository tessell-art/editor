# Tessella Editor — Playwright smoke suite

A small end-to-end smoke suite that validates what JSDOM cannot — real
canvas/SVG layout, real pointer/keyboard events, and (eventually) visual
regression.

This is a **sibling project** to the main editor, with its own `package.json` and
`node_modules`. Keeping it separate avoids polluting the editor's runtime
dependency graph and lets contributors who don't touch the e2e suite skip the
~200 MB Playwright browser download.

## First-time setup

```bash
cd e2e
npm install
npm run install-browsers    # downloads Chromium (~150 MB) once
```

## Running locally

```bash
cd e2e
npm test                     # headless
npm run test:headed          # visible browser
npm run test:ui              # interactive Playwright UI mode
npm run report               # open the HTML report from the last run
```

The Playwright config auto-starts `vite dev` in the parent directory and tears
it down at the end of the run. If you already have `npm run dev` open in
another terminal, Playwright will reuse it (controlled by
`reuseExistingServer` outside CI).

## What's covered (current smoke suite)

See `tests/smoke.spec.ts` — five scenarios:

1. App boots and renders the polygon palette.
2. Clicking the hexagon palette button creates a tiling.
3. Ctrl+Z undoes a tiling creation.
4. Opening and closing the About popup via the menu.
5. SVG export → Clear Tiling → re-import round-trip preserves the tiling
   shape (`tilingPolygonCount` and `firstFaceVertexCount` both match
   pre/post). Exercises `window.prompt` interception, download capture
   via `page.waitForEvent('download')`, and file-input feeding via
   `page.waitForEvent('filechooser')` + `setFiles`.

## Domain-state assertions: Scala-exposed test hooks

For assertions about editor *state* (rather than DOM presence), prefer the
Scala-exposed hook API in `tests/fixtures/hooks.ts` over CSS selectors:

```ts
import { hooks, expectHook } from './fixtures/hooks';

// Domain assertions — survives any CSS class rename.
expect(await hooks.isTilingEmpty(page)).toBe(true);
await expectHook.tilingPolygonCount(page, 1);

// vs. selector assertions (use these for genuinely UI-shaped concerns):
await expect(page.locator('.popup-overlay')).toBeVisible();
```

The hooks are registered from
[`TestHooks.scala`](../src/main/scala/art/tessell/editor/TestHooks.scala)
at `globalThis.__tessellaTestHooks__`. Keep observation-only — mutations
should still go through user-visible paths (clicking buttons, pressing keys)
so the e2e suite exercises the same interaction surface real users do.

Adding a hook: add a `@JSExport`-annotated method in `TestHooks.scala`,
add the matching entry under `Window.__tessellaTestHooks__` in `hooks.ts`,
and (if useful) a polling helper under `expectHook`.

## What's deliberately *not* yet covered

These will be added when the editor surface they exercise is stable enough
that snapshot churn won't dominate review:

- Touch / pointer gestures (`TouchEventHandler`).
- Visual regression (`expect(page).toHaveScreenshot()`) for the canvas.
- Theme toggle (the toggle wiring is already covered by `ThemeSpec` in the unit tier).

## CI integration

Not wired into `.github/workflows/build.yml` yet. When ready:

```yaml
- name: Install Playwright browsers
  run: cd e2e && npm ci && npx playwright install --with-deps chromium

- name: Run e2e smoke
  run: cd e2e && npm test
```

The browser download is heavy; consider a separate workflow that runs only on
`main` pushes if PR build time becomes a concern.
