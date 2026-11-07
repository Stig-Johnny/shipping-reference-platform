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

Node 26.13.0 runs the tests directly, with its built-in type stripping; nothing needs installing:

```bash
node --test apps/shipping-web/src
```

Compiling needs TypeScript 5.9.3 from the reviewed lock (`devDependencies` in `package.json`). Once the lock exists,
from the repository root:

```bash
npm ci --ignore-scripts
npm run typecheck --workspace @shipping-reference-platform/web
npm run test --workspace @shipping-reference-platform/web
npm run build --workspace @shipping-reference-platform/web
```

`build` writes `apps/shipping-web/dist/`: the compiled modules in the layout of `src/` (for example
`dist/app/main.js`), `index.html` and `workbench.css`. Test files are not compiled: `node --test` runs them, and they
are not type-checked, because the Node.js type declarations are not a reviewed dependency.

## Serving `dist/` from the Java application

The API pull request does the wiring; the shape is:

- **Location.** The `demo` profile serves the built directory from the file system, so that `./gradlew check` stays
  JDK-only and never depends on a web build: `spring.web.resources.static-locations=file:apps/shipping-web/dist/`
  in `application-demo.properties`. Alternatively the API's Gradle build copies a CI-built `dist/` into `static/`:

  ```kotlin
  tasks.processResources {
      from(rootProject.layout.projectDirectory.dir("apps/shipping-web/dist")) { into("static") }
  }
  ```

- **History API routes.** `GET /`, `/sign-in`, `/quotes/new`, `/quotes/{quoteId}`, `/shipments` and
  `/shipments/{shipmentId}` with `Accept: text/html` return `index.html`; everything under `/api/` stays the API.
- **Security.** The static files and those routes need no token; Spring Security permits them, and only `/api/**`
  requires the demo bearer token.
- **Headers on the HTML document.** `Content-Security-Policy: default-src 'self'; base-uri 'none'; form-action 'none';
  frame-ancestors 'none'; object-src 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin` and
  `Cache-Control: no-store`. The page repeats the CSP in a meta element, except `frame-ancestors`, which only a
  header can set.
- **Same origin.** The app calls `/api/v1` on its own origin; loopback only for the demo.
