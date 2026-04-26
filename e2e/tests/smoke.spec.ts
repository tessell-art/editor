import { test, expect } from '@playwright/test';
import { hooks, expectHook } from './fixtures/hooks';

/**
 * Tessella Editor smoke suite (ADR-003 Tier 2).
 *
 * Each test is intentionally small and focused on a single user-visible
 * outcome. The point is signal on what JSDOM cannot honestly cover — real
 * canvas/SVG rendering, real keyboard events, real CSS-driven hover behaviour
 * for the menu — not parallel coverage of what the unit tier already verifies.
 *
 * Domain-state assertions are made via the Scala-exposed hook API (ADR-004,
 * see `./fixtures/hooks.ts`); pure UI assertions (visibility, presence) keep
 * using Playwright locators directly.
 */
test.describe('Tessella Editor smoke', () => {

  test.beforeEach(async ({ page }) => {
    // Seed the "first-run seen" flag BEFORE navigation so the welcome overlay
    // (added in Phase 4.6) never mounts. `addInitScript` runs on every new
    // document, ahead of the app's bundle, so `FirstRunStorage.hasSeenFirstRun`
    // returns true on the very first read.
    await page.addInitScript(() => {
      localStorage.setItem('tessella.hasSeenFirstRun', 'true');
    });

    await page.goto('/');
    // Wait for the polygon palette to render — proxy for "Scala.js bundle loaded
    // and Laminar finished its first render".
    await expect(page.locator('.polygon-palette')).toBeVisible();
  });

  test('app boots and renders the polygon palette plus a canvas', async ({ page }) => {
    await expect(page.locator('.polygon-palette')).toBeVisible();
    // At least one polygon button is present.
    await expect(page.locator('.polygon-btn').first()).toBeVisible();
    // Canvas SVG is rendered.
    await expect(page.locator('.canvas-container svg').first()).toBeVisible();
  });

  test('clicking the hexagon palette button creates a tiling', async ({ page }) => {
    // Initially no tiling polygons are rendered. Asserted via the domain hook rather than by
    // counting `polygon.tiling-polygon` selectors — the test survives any CSS class rename.
    expect(await hooks.isTilingEmpty(page)).toBe(true);

    // The 6-sided polygon button has a `title` prefixed with "6-sided polygon".
    await page.locator('button[title^="6-sided polygon"]').click();

    // A single hexagon face appears. Polled because the click handler routes through
    // OperationRunner.runTilingOp, which has a 50ms loading-state delay before the mutation
    // applies — exactly the case `expect.poll` is for.
    await expectHook.tilingPolygonCount(page, 1);
  });

  test('Ctrl+Z undoes a tiling creation', async ({ page }) => {
    await page.locator('button[title^="6-sided polygon"]').click();
    await expect(page.locator('polygon.tiling-polygon')).toHaveCount(1);

    // Use Control+KeyZ for cross-platform: KeyboardEventHandler.handleKeyDown
    // accepts either Ctrl (non-mac) or Meta (mac) as the primary modifier.
    await page.keyboard.press('Control+KeyZ');

    await expect(page.locator('polygon.tiling-polygon')).toHaveCount(0);
  });

  test('Help → About... opens the popup; close button dismisses it', async ({ page }) => {
    // The Help dropdown is opened on hover (CSS :hover-driven on desktop).
    const helpItem = page.locator('.menu-item', { has: page.locator('button.menu-button', { hasText: 'Help' }) });
    await helpItem.hover();

    // Click "About…" inside Help's dropdown-content. Matches with a leading-anchor regex
    // so it survives whether the trailing punctuation is "…" (current i18n catalog) or "...".
    await helpItem.locator('.dropdown-content a', { hasText: /^About/ }).click();

    // Popup overlay + content + the title h1.
    await expect(page.locator('.popup-overlay')).toBeVisible();
    await expect(page.locator('.popup-content h1', { hasText: 'Tessella' })).toBeVisible();

    // Close via the X button.
    await page.locator('.popup-close-btn').click();
    await expect(page.locator('.popup-overlay')).toHaveCount(0);
  });

  test('SVG export → Clear Tiling → re-import round-trip preserves shape', async ({ page }) => {
    // 1. Create a hexagon and capture the shape signature via hooks.
    await page.locator('button[title^="6-sided polygon"]').click();
    await expectHook.tilingPolygonCount(page, 1);
    await expectHook.firstFaceVertexCount(page, 6); // discriminates hexagon from any other 1-face shape

    // 2. Save SVG as... — uses window.prompt for filename, then downloads via a hidden anchor.
    // Set up the dialog handler before clicking so the prompt's accept fires synchronously.
    page.once('dialog', dialog => dialog.accept('round-trip.svg'));

    const fileItem = page.locator(
      '.menu-item',
      { has: page.locator('button.menu-button', { hasText: 'File' }) }
    );
    await fileItem.hover();

    const downloadPromise = page.waitForEvent('download');
    await fileItem.locator('.dropdown-content a', { hasText: /^Save SVG as/ }).click();
    const download = await downloadPromise;
    const downloadPath = await download.path();
    expect(downloadPath).toBeTruthy();

    // 3. Clear the tiling via Edit → Clear Tiling. Use the menu (deterministic) over keyboard
    // shortcuts so we exercise the full UI path.
    const editItem = page.locator(
      '.menu-item',
      { has: page.locator('button.menu-button', { hasText: 'Edit' }) }
    );
    await editItem.hover();
    await editItem.locator('.dropdown-content a', { hasText: 'Clear Tiling' }).click();
    await expectHook.isTilingEmpty(page, true);
    await expectHook.firstFaceVertexCount(page, 0);

    // 4. Load SVG... — clicks a hidden file input that opens the native file chooser.
    // Playwright's `filechooser` event captures it; we feed back the file we just downloaded.
    await fileItem.hover();
    const fileChooserPromise = page.waitForEvent('filechooser');
    await fileItem.locator('.dropdown-content a', { hasText: /^Load SVG/ }).click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles(downloadPath);

    // 5. Same shape signature comes back: 1 face, 6 vertices.
    await expectHook.tilingPolygonCount(page, 1);
    await expectHook.firstFaceVertexCount(page, 6);
  });
});
