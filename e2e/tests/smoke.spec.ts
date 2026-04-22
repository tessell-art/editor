import { test, expect } from '@playwright/test';

/**
 * Tessella Editor smoke suite (ADR-003 Tier 2).
 *
 * Each test is intentionally small and focused on a single user-visible
 * outcome. The point is signal on what JSDOM cannot honestly cover — real
 * canvas/SVG rendering, real keyboard events, real CSS-driven hover behaviour
 * for the menu — not parallel coverage of what the unit tier already verifies.
 */
test.describe('Tessella Editor smoke', () => {

  test.beforeEach(async ({ page }) => {
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
    // Initially no tiling polygons are rendered.
    await expect(page.locator('polygon.tiling-polygon')).toHaveCount(0);

    // The 6-sided polygon button has a `title` prefixed with "6-sided polygon".
    await page.locator('button[title^="6-sided polygon"]').click();

    // A single hexagon face appears.
    await expect(page.locator('polygon.tiling-polygon')).toHaveCount(1);
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

    // Click "About..." inside Help's dropdown-content.
    await helpItem.locator('.dropdown-content a', { hasText: 'About...' }).click();

    // Popup overlay + content + the title h1.
    await expect(page.locator('.popup-overlay')).toBeVisible();
    await expect(page.locator('.popup-content h1', { hasText: 'Tessella' })).toBeVisible();

    // Close via the X button.
    await page.locator('.popup-close-btn').click();
    await expect(page.locator('.popup-overlay')).toHaveCount(0);
  });
});
