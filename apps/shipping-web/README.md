# shipping-web

The shipping workbench of `docs/ui/workbench.md`: price a parcel, compare the offers, book one twice safely, find the
shipment again and download its simulated label. Plain TypeScript with no UI framework and no runtime dependencies,
compiled by `tsc` to native ES modules that the browser loads without a bundler.

## Structure

The six parts of the web container in `architecture/workspace.dsl`, one directory each under `src/`:

| Part | Directory | What it holds |
|---|---|---|
| `app` | `src/app` | Entry module, router on the History API, app shell |
| `auth` | `src/auth` | Synthetic user picker, the session (token and membership in memory only) |
| `api` | `src/api` | The only HTTP code: contract types, the fetch adapter, problems, client-side contract checks |
| `quote-feature` | `src/features/quotes` | ParcelForm, offer cards, the New quote and Quote screens; entry `index.ts` |
| `shipment-feature` | `src/features/shipments` | BookingForm, ShipmentTable, ShipmentDrawer, the Shipments screen; entry `index.ts` |
| `ui` | `src/ui` | DOM helper, formatting, presentational components, ThemeToggle |

Imports follow the model's relationships: `ui` and `api` import no other part, `auth` imports `api` and `ui`, the
features import `api` and `ui`, the quote feature imports the shipment feature through its `index.ts` only, and `app`
imports the rest (the features through their `index.ts`). Relative imports carry their `.ts` extension, which
`rewriteRelativeImportExtensions` turns into `.js` in the output.

Each screen keeps its logic in a model module without DOM access (`*Model.ts`, `session.ts`, `routes.ts`, the `api`
modules), which `node --test` covers; the view modules build the DOM with `document.createElement` and text nodes
only. The page needs no inline script or style, so it runs under `Content-Security-Policy: default-src 'self'`.

## Commands

Use only reviewed Node 26.13.0 and its bundled npm 11.20.0. From the repository root, install the unchanged
reviewed lock and compile with TypeScript 5.9.3 from `node_modules/.bin` on PATH:

```sh
npm ci --ignore-scripts
tsc -p apps/shipping-web
```

With working directory `apps/shipping-web`, run exactly:

```sh
node --test
```

The only manifest scripts are root `build:web` = `tsc -p apps/shipping-web` and workspace `test:unit` =
`node --test`, matching the reviewed forms. No bundler, test loader or runtime dependency is used.
`tsc` emits modules under `dist/`, without copying `index.html` or `public/workbench.css`. It does not compile
or type-check logic tests: Node runs their erasable TypeScript directly, and Node type declarations are not reviewed.
The 125 logic tests run without a DOM. No Playwright e2e, axe or manual accessibility evidence exists yet.

## Content security and serving

The entry document declares this meta policy before its stylesheet and module script:

```text
default-src 'self'; script-src 'self'; connect-src 'self'; style-src 'self'; img-src 'self'; font-src 'self'; base-uri 'none'; form-action 'none'; object-src 'none'; frame-src 'none'; worker-src 'none'
```

`src/app/csp.test.ts` asserts its exact text and placement. The app uses same-origin `/api/v1`, external module files
and an external stylesheet; no external origin, inline script/style or evaluated string is allowed. ADR 0017 records
the owner's decision. The test proves the declaration, not browser enforcement or general runtime safety.

The API currently disables static-resource serving. Wiring the emitted modules, `index.html`, the stylesheet and
History API route fallbacks into the same-origin Java application remains pending. There is no app server in this
slice and no live end-to-end flow. A future repository-owned server must repeat the policy as a response header,
add `frame-ancestors 'none'` in that header, serve only on loopback for the demo, and test the delivered response.
Do not make API paths fall back to HTML.

## Evidence limits

All six parts are built and the partial JDK import gate observes production imports for all ten REQUIRED
relationships. Known reader misses remain; `tsc` type-checking does not compare its graph with the C4 model.
The four `web.*` capabilities remain planned because the named component/e2e acceptance tests are absent and the
documentation build cannot consume Node reports. Cancellation and the label preview remain deferred.
