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

    // Locate the hexagon by its queue-slot label (the digit "6") rather than by tooltip wording —
    // the title is i18n-driven and the slot's .polygon-label is locale-stable.
    await page.locator('.palette-queue-slot', { has: page.locator('.polygon-label', { hasText: /^6$/ }) }).click();

    // A single hexagon face appears. Polled because the click handler routes through
    // OperationRunner.runTilingOp, which has a 50ms loading-state delay before the mutation
    // applies — exactly the case `expect.poll` is for.
    await expectHook.tilingPolygonCount(page, 1);
  });

  test('Ctrl+Z undoes a tiling creation', async ({ page }) => {
    await page.locator('.palette-queue-slot', { has: page.locator('.polygon-label', { hasText: /^6$/ }) }).click();
    await expect(page.locator('polygon.tiling-polygon')).toHaveCount(1);

    // Use Control+KeyZ for cross-platform: KeyboardEventHandler.handleKeyDown
    // accepts either Ctrl (non-mac) or Meta (mac) as the primary modifier.
    await page.keyboard.press('Control+KeyZ');

    await expect(page.locator('polygon.tiling-polygon')).toHaveCount(0);
  });

  /**
   * Drive a palette → canvas drag by dispatching `PointerEvent`s directly to the source button.
   *
   * Why not `page.mouse.*`? Playwright's synthesized mouse events depend on `setPointerCapture`
   * to keep pointermove/pointerup flowing to the source button after the cursor has crossed onto
   * the canvas. Pointer-capture engagement under CDP-driven mouse synthesis turns out to be
   * unreliable in this scenario (intermediate moves never reach the gesture's onPointerMove,
   * so the snap never primes a placement). Dispatching the events straight at the button
   * sidesteps capture entirely and tests the gesture's own logic on its own terms — which is
   * what we actually want.
   *
   * The selector resolves to the first `.palette-queue-slot` whose `.polygon-label` text matches
   * `labelText` exactly (e.g. "3" for the triangle, "6" for the hexagon).
   */
  async function dragQueueSlot(
    page: import('@playwright/test').Page,
    labelText: string,
    toClient: { x: number; y: number },
  ): Promise<void> {
    await page.evaluate(
      ({ label, to }) => {
        const slot = Array.from(document.querySelectorAll('.palette-queue-slot'))
          .find(el => el.querySelector('.polygon-label')?.textContent?.trim() === label) as HTMLElement | undefined;
        if (!slot) throw new Error(`No queue slot with label "${label}"`);
        const rect = slot.getBoundingClientRect();
        const fromX = rect.left + rect.width / 2;
        const fromY = rect.top + rect.height / 2;
        const dispatch = (type: string, x: number, y: number, buttons: number) => {
          slot.dispatchEvent(new PointerEvent(type, {
            bubbles: true, cancelable: true,
            pointerId: 1, pointerType: 'mouse', isPrimary: true,
            clientX: x, clientY: y, button: 0, buttons,
          }));
        };
        dispatch('pointerdown', fromX, fromY, 1);
        // 10 intermediate pointermoves so the gesture observably crosses its drag threshold
        // (a single jump can be classed as a tap by gesture handlers that compare to origin).
        for (let i = 1; i <= 10; i++) {
          const t = i / 10;
          dispatch('pointermove', fromX + (to.x - fromX) * t, fromY + (to.y - fromY) * t, 1);
        }
        dispatch('pointerup', to.x, to.y, 0);
      },
      { label: labelText, to: toClient },
    );
  }

  test('drag from a palette slot places a polygon on the canvas', async ({ page }) => {
    // Why this test exists: the drag-from-palette gesture (PaletteDragGesture, Phase 5.6) relies on
    // real pointer-event semantics — threshold-based drag detection, `getBoundingClientRect`-
    // driven snap geometry, and the canvas's SVG viewBox transform. JSDOM lies about all of those
    // (zero-sized rects, no real layout), so the gesture has no honest unit-test coverage.
    // This exercises the full path end-to-end and would catch the pointer-capture-during-rebuild
    // regression class that bit us when selecting an unselected queue slot used to mutate state
    // mid-drag.
    //
    // The setup pre-seeds a hexagon by clicking — the gesture only snaps when there's a perimeter
    // to snap to. Starting from an empty canvas would fall back to the click path (which auto-
    // creates the first polygon), defeating the test. With one polygon already present, a
    // successful drag adds a second one; a broken gesture (no snap, no commit) leaves the count
    // at 1 because the click path on a non-empty tiling only sets selection.

    await page.locator(
      '.palette-queue-slot',
      { has: page.locator('.polygon-label', { hasText: /^6$/ }) }
    ).click();
    // Wait for the SVG polygon to render — this is what proves the canvas is in the DOM and
    // `EditorState.uiState.canvasElementRef` is set. Without it, the gesture's
    // `clientPointToSvg` returns None, so no snap fires, no `previewPlacement` is latched, and
    // the drag commit becomes a no-op. The state-only `tilingPolygonCount` hook can flip to 1
    // before the canvas mounts, so it is not sufficient as a wait condition for drag tests.
    await expect(page.locator('polygon.tiling-polygon')).toHaveCount(1);

    // Use `svg.editor-canvas` rather than `.canvas-container svg`: the latter also matches small
    // toolbar icons rendered by `CanvasControlComponent` at the top of the canvas column, and
    // `.first()` would pick one of those (16×16) instead of the actual canvas.
    const canvasBox = await page.locator('svg.editor-canvas').boundingBox();
    expect(canvasBox).not.toBeNull();

    // Aim into the right half of the canvas so the snap deterministically picks a right-side
    // perimeter edge of the seeded hexagon. Centre would be ambiguous (all 6 edges equidistant).
    await dragQueueSlot(page, '3', {
      x: canvasBox!.x + canvasBox!.width * 0.7,
      y: canvasBox!.y + canvasBox!.height / 2,
    });

    await expectHook.tilingPolygonCount(page, 2);
  });

  test('releasing a palette drag off-canvas does not place a polygon', async ({ page }) => {
    // Why this test exists: a drag the user changes their mind about — released back over the
    // palette area, away from any perimeter — must not commit a placement. This encodes the
    // gesture's "no snap, no commit" rule so a future refactor can't silently start auto-placing
    // on every drag-end.

    await page.locator(
      '.palette-queue-slot',
      { has: page.locator('.polygon-label', { hasText: /^6$/ }) }
    ).click();
    // Wait for the rendered polygon — same canvas-mount precondition as the drag-and-place test
    // above. Without it this test could pass coincidentally (count stuck at 1 because the
    // canvas isn't mounted, not because the gesture cancelled cleanly).
    await expect(page.locator('polygon.tiling-polygon')).toHaveCount(1);

    // Aim at the top-left corner of the viewport — far from the canvas viewBox, so
    // `isInsideCanvas` rejects every move and no `previewPlacement` ever latches.
    await dragQueueSlot(page, '3', { x: 5, y: 5 });

    // Count is unchanged from the seeded value — the gesture did not place a polygon.
    // (The synthetic click that follows pointerup will set `selectedPolygon` for follow-up
    // click-to-place — that's expected click-handler behaviour and out of scope for this test.)
    await expectHook.tilingPolygonCount(page, 1);
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
    await page.locator('.palette-queue-slot', { has: page.locator('.polygon-label', { hasText: /^6$/ }) }).click();
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
