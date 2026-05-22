import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright smoke-suite configuration for Tessella Editor.
 *
 * Auto-starts `vite dev` in the parent project so the suite is self-contained:
 * a single `npm test` from this directory boots the editor, runs all tests,
 * and tears down. `reuseExistingServer` is on outside CI so an already-running
 * dev server (the dev's own `npm run dev`) is reused instead of spawning a
 * second one.
 *
 * Chromium-only by default. The point here is "things JSDOM can't cover"
 * (canvas/SVG layout, real pointer events, visual snapshots), not
 * cross-browser parity — adding Firefox/WebKit projects is one config edit if
 * the need ever arises.
 */
export default defineConfig({
  testDir: './tests',

  // Generous on first paint: the Scala.js bundle plus Vite cold start can
  // take a few seconds on first run; subsequent runs are fast.
  timeout: 30_000,
  expect: { timeout: 5_000 },

  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? 'github' : 'list',

  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
  },

  webServer: {
    command: 'npm run dev',
    cwd: '..',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
