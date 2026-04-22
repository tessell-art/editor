# Tessella Editor — Playwright smoke suite

ADR-003 Tier 2: a small end-to-end smoke suite that validates what JSDOM cannot —
real canvas/SVG layout, real pointer/keyboard events, and (eventually) visual
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

See `tests/smoke.spec.ts` — four scenarios:

1. App boots and renders the polygon palette.
2. Clicking the hexagon palette button creates a tiling.
3. Ctrl+Z undoes a tiling creation.
4. Opening and closing the About popup via the menu.

## What's deliberately *not* yet covered

These remain on the ADR-003 roadmap and will be added when the editor surface
they exercise is stable enough that snapshot churn won't dominate review:

- SVG export → re-import round-trip (needs `page.waitForEvent('download')` plus a file-picker hand-off).
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
