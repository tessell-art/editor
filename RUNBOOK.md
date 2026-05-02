# Tessella Editor — Runbook

Operational entry points for developers working on the editor. For features
and the project's purpose, see [README.md](README.md); for architecture
decisions, see [docs/adr/](docs/adr/); for ongoing improvement work, see
[BACKLOG.md](BACKLOG.md).

## Dev server

Starts Vite with the Scala.js plugin; hot-reloads on source edits.

```bash
npm run dev
```

Open <http://localhost:5173>.

## Unit tests (sbt + Scala.js under JSDOM)

```bash
sbt test
```

Tests run under `JSDOMNodeJSEnv` (see [`build.sbt:68`](build.sbt)), so specs
that touch the DOM, `localStorage`, or mount Laminar components work without
launching a real browser. Three test tiers are present:

| Tier                     | Location                                | Notes                                                                                         |
|--------------------------|-----------------------------------------|-----------------------------------------------------------------------------------------------|
| Unit + property          | `src/test/scala/.../**/*Spec.scala`     | MUnit + `munit-scalacheck`. `EditorStateFixture` handles global-state isolation.              |
| Laminar-in-JSDOM (ADR-003 Tier 1) | `src/test/scala/.../components/**` | Mount Laminar components via `LaminarTestSupport`. See `AboutPopupSpec` for the canonical shape. |
| Playwright smoke (ADR-003 Tier 2) | `e2e/tests/`                        | See below.                                                                                    |

A single `sbt test` runs the first two tiers; the Playwright suite is a
sibling project (own npm install, own entry point).

## Playwright smoke suite (e2e/)

The smoke suite is a sibling project under `e2e/` with its own
`package.json` / `node_modules`. It covers what JSDOM cannot — real CSS-driven
hover, real keyboard events reaching `windowEvents(_.onKeyDown)`, real SVG
layout. Authoritative docs: [`e2e/README.md`](e2e/README.md).

### First-time setup (once per machine)

```bash
cd e2e
npm install                  # ~50 MB of node_modules
npm run install-browsers     # ~150 MB Chromium, one-off
```

### Running

All commands run from `e2e/`:

```bash
npm test                     # headless, auto-starts vite dev, tears down
npm run test:headed          # visible browser
npm run test:ui              # interactive Playwright UI (best for debugging)
npm run report               # open the HTML report from the last run
```

`npm test` auto-starts `vite dev` in the parent directory via Playwright's
`webServer` config. If you already have `npm run dev` running in another
terminal, Playwright will reuse it — no port conflict.

### Running a single test

```bash
npm test -- --grep "hexagon"             # substring filter on test title
npm test -- tests/smoke.spec.ts:36       # by line number
```

### Debugging a failing test

```bash
npm run test:ui                          # step through, see DOM snapshots
npm run test -- --trace on               # trace the full run; view via `npm run report`
npx playwright test --debug              # Playwright Inspector
```

### Updating the Playwright browser binary

Playwright pins its own Chromium via `@playwright/test`. When bumping the
version in `e2e/package.json`:

```bash
cd e2e
npm install @playwright/test@latest
npm run install-browsers                 # re-pulls the matching Chromium
```

### CI integration

Not wired yet. The existing GitHub Actions workflow at
[`.github/workflows/build.yml`](.github/workflows/build.yml) runs `sbt test`
and `npm run build`; a Playwright step would add ~150 MB of browser download
per run. Recipe in [`e2e/README.md`](e2e/README.md). Consider gating on `main`
pushes only if PR build time becomes a concern.

### Troubleshooting

| Symptom                                       | Likely cause / fix                                                                      |
|-----------------------------------------------|-----------------------------------------------------------------------------------------|
| `Error: browserType.launch: Executable doesn't exist` | Browsers not installed. Run `npm run install-browsers` from `e2e/`.                     |
| `Error: port 5173 is already in use`          | A stray vite dev is running without `reuseExistingServer` picking it up. Kill it.       |
| Tests pass locally, fail in CI                | Usually a timing issue — bump `timeout` in `playwright.config.ts` or add an `await expect(...).toBeVisible()`. |
| First run after a Playwright bump hangs       | `npm run install-browsers` was skipped after the version change.                        |

## Production build

```bash
npm run build
```

Output in `dist/`. Vite drives the Scala.js fullLink task under the hood;
no separate sbt invocation needed.

## Deploy to Cloudflare Pages

Production deploys are automated on version-tag pushes — see
[`.github/workflows/cloudflare-pages.yml`](.github/workflows/cloudflare-pages.yml).
A pushed tag matching `v*.*.*` builds and deploys `dist/` to the Pages
production branch.

### One-time setup

1. In Cloudflare Pages, create a Pages project (for example `tessella-editor`).
   Set the project's **Production branch** to `main`.
2. Generate a Cloudflare API token with the **Account → Cloudflare Pages → Edit**
   permission (User Profile → API Tokens → Create Token).
3. In GitHub repository settings (Settings → Secrets and variables → Actions), add:
   - secret `CLOUDFLARE_API_TOKEN` — the token from step 2
   - secret `CLOUDFLARE_ACCOUNT_ID` — from the Cloudflare dashboard sidebar
   - variable `CLOUDFLARE_PAGES_PROJECT_NAME` — the Pages project name from step 1

### Cutting a release

The version string is duplicated across four manifests (`build.sbt` for sbt,
`package.json` for npm, `desktop/src-tauri/tauri.conf.json` for the Tauri
shell, `desktop/src-tauri/Cargo.toml` for the Rust crate) plus two
lockfiles. `scripts/sync-version.mjs` reads `package.json#version` and
propagates it to the other three manifests, then runs
`cargo update -p tessella-editor-desktop --offline` to refresh
`Cargo.lock`. It's wired into the `npm version` lifecycle.

Recommended flow — review before committing:

```bash
# 1. Working tree must be clean.
npm version 0.4.0 --no-git-tag-version    # bumps all 6 files in place

# 2. Sanity-check the diff.
git diff --stat
#   expect 6 files: package.json, package-lock.json,
#   build.sbt, tauri.conf.json, Cargo.toml, Cargo.lock

# 3. Commit, tag, push.
git commit -am "Bump to v0.4.0"
git tag v0.4.0
git push && git push origin v0.4.0
```

The Cloudflare Pages workflow triggers off the `v*.*.*` tag — it runs
`npm ci && npm run build` and deploys `dist/` to the production branch. To
redeploy without a new tag, use the **Run workflow** button on the Actions
tab (`workflow_dispatch`).

**Re-syncing manually.** If you edited `package.json#version` by hand (or
need to verify nothing drifted), run the propagator without the npm
ceremony:

```bash
npm run version:sync
```

Idempotent — re-running is a no-op when everything is already in sync.

**What is NOT bumped automatically.** The script's regex is scoped to the
manifests' own version lines. Test fixtures (`UpdateCheckerSpec` uses
`0.3.7` as a comparison example, not as the project's current version) and
ADR text containing example payloads stay as-written. If you grep for the
old version string after bumping, you'll see those — they are intentional.

### Manual deploy (local fallback)

```bash
export CLOUDFLARE_API_TOKEN=...
export CLOUDFLARE_ACCOUNT_ID=...
npm run build
npx wrangler pages deploy dist --project-name=<project-name> --branch=main
```

Omit `--branch=main` to create a preview deploy under the current git branch
name. First-time `wrangler` use prompts for browser auth if the env vars are
not set.

## Other common tasks

| Task                          | Command                                                              |
|-------------------------------|----------------------------------------------------------------------|
| Re-run only changed tests     | `sbt ~testQuick`                                                     |
| Single test class             | `sbt 'testOnly *ViewOperationsPropertySpec'`                         |
| Force scalex reindex          | `scalex index --verbose` (after adding files)                        |
| Check ADR-001 layering        | `sbt checkLayering` (runs automatically before every `compile`)      |
| Open the scaladoc             | `sbt doc` — output under `target/scala-*/api/`                       |
