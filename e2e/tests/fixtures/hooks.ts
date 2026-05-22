import { Page, expect } from '@playwright/test';

/**
 * Typed bindings for the Scala.js-exported test-hook object.
 *
 * Must stay in sync with `src/main/scala/io/github/scala_tessella/editor/TestHooks.scala`.
 * The hook surface is versioned by convention: adding a hook is free; renaming or removing one
 * is an e2e-test-breaking change and should be called out in the commit that makes it.
 *
 * Usage:
 *
 *   import { hooks, expectHook } from './fixtures/hooks';
 *
 *   expect(await hooks.isTilingEmpty(page)).toBe(true);   // one-shot read
 *   await expectHook.tilingPolygonCount(page, 1);         // poll until it settles
 */

declare global {
  interface Window {
    __tessellaTestHooks__: {
      tilingPolygonCount(): number;
      isTilingEmpty(): boolean;
      currentFillColor(): string;
      firstFaceVertexCount(): number;
      selectedPolygonSides(): number;
      lastErrorMessage(): string;
    };
  }
}

/** One-shot reads of each hook. Each call runs a single `page.evaluate` in the page's world. */
export const hooks = {
  tilingPolygonCount: (page: Page): Promise<number> =>
    page.evaluate(() => window.__tessellaTestHooks__.tilingPolygonCount()),
  isTilingEmpty: (page: Page): Promise<boolean> =>
    page.evaluate(() => window.__tessellaTestHooks__.isTilingEmpty()),
  currentFillColor: (page: Page): Promise<string> =>
    page.evaluate(() => window.__tessellaTestHooks__.currentFillColor()),
  firstFaceVertexCount: (page: Page): Promise<number> =>
    page.evaluate(() => window.__tessellaTestHooks__.firstFaceVertexCount()),
  selectedPolygonSides: (page: Page): Promise<number> =>
    page.evaluate(() => window.__tessellaTestHooks__.selectedPolygonSides()),
  lastErrorMessage: (page: Page): Promise<string> =>
    page.evaluate(() => window.__tessellaTestHooks__.lastErrorMessage()),
};

/**
 * Polling variants — retry until the hook matches the expected value or Playwright's `expect`
 * timeout fires. Use these instead of `expect(await hooks.X(page)).toBe(expected)` whenever the
 * value you're asserting settles asynchronously after a user action (e.g. after a click whose
 * handler schedules an async DCEL operation). For state that resolves synchronously with the
 * action, plain `hooks.X(page)` + `expect(...).toBe(...)` is fine and a shade cheaper.
 */
export const expectHook = {
  tilingPolygonCount: (page: Page, expected: number) =>
    expect.poll(() => hooks.tilingPolygonCount(page)).toBe(expected),
  isTilingEmpty: (page: Page, expected: boolean) =>
    expect.poll(() => hooks.isTilingEmpty(page)).toBe(expected),
  currentFillColor: (page: Page, expected: string) =>
    expect.poll(() => hooks.currentFillColor(page)).toBe(expected),
  firstFaceVertexCount: (page: Page, expected: number) =>
    expect.poll(() => hooks.firstFaceVertexCount(page)).toBe(expected),
};
