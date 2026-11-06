---
component: "jsdom"
kind: npm
version: "30.1.3"
verdict: APPROVE-WITH-CONDITIONS
scope: "devDependency of apps/shipping-web: the Vitest test environment (environment: 'jsdom') in vitest run; installed by npm ci --ignore-scripts from the reviewed lockfile"
conditions: ["Lockfile pins jsdom 30.1.3 (sha512-hQMUPvRgN7KxsfIytDQji7bimuJeRQa6Qo5+iuiZQk5CafeRxLIA10/cG5YCBFNpD9RO3oqIiN4CO1Y44kxqXg==), not 31.0.0", "No resources: 'usable' or custom resource loader in environmentOptions; tests load only repository fixtures, never remote URLs or untrusted HTML (Vitest sets runScripts: 'dangerously'; jsdom's VM is not a sandbox)", "The optional canvas peer is not installed", "The look-alike packages jsdom-js and vitest-environment-jsdom-patched (OSV MAL entries) never appear in the lockfile"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: jsdom 30.1.3 (recommended Vitest DOM environment)

**Method.** Read on 2026-11-06: https://registry.npmjs.org/jsdom/30.1.3, deps.dev, the osv.dev searches for jsdom
and happy-dom, Vitest's `jsdom.ts` at v5.1.0, https://vitest.dev/guide/environment and the scorecards. Nothing was
downloaded or run.

## 1. Verdict

**APPROVE-WITH-CONDITIONS. jsdom is the recommended DOM environment, and happy-dom is rejected**
(`npm--happy-dom--21.0.4.md`). The comparison:

| | jsdom | happy-dom |
|---|---|---|
| Advisories on OSV | **none** for the exact package | eight since November 2024, three of them critical code-execution or isolation failures |
| npm maintainers | 6 | 1 |
| Scorecard | 7.3 | 6.4 |

**Version: 30.1.3, not 31.0.0.** 30.1.3 is the line Vitest 5.1.0 tests against (its devDependency is `jsdom
^30.1.3`). 31.0.0 is a new major published one day before this review.

## 2. What it is and why it is needed

A JavaScript implementation of the DOM and HTML standards for Node. Vitest needs a DOM environment, `jsdom` or
`happy-dom`, to run React component tests ("component behaviour", blueprint §7). The brief asked for a recommendation.

## 3. Provenance

- **Publisher.** `_npmUser` is "GitHub Actions" (trustedPublisher `github`). npm maintainers: timothygu, domenic,
  sebmaster, zirro, tmpvar, joris-van-der-wel.
- **SLSA provenance.** Source jsdom/jsdom, commit `058c0f55f369a70bcb113d660ae562b9066f2d44` (= `gitHead`),
  `verified: true` (https://api.deps.dev/v3/systems/npm/packages/jsdom/versions/30.1.3).
- **Install scripts:** none. `prepare` (`wireit`) and `pretest` are development scripts, and `--ignore-scripts`
  applies.
- **Native binaries:** none. The optional `canvas` peer is native and is excluded.

## 4. Maintenance and adoption

- **Releases.** 30.1.3 came out on 2026-10-15. The 30.x line has had six releases since 2026-07-27, and 31.0.0 came
  out on 2026-11-05 (https://api.deps.dev/v3/systems/npm/packages/jsdom).
- **Scorecard 7.3** (2026-08-24): Maintained 10, Code-Review 6, Security-Policy 10, Token-Permissions 10,
  Pinned-Dependencies 3. 21,746 stars (https://api.deps.dev/v3/projects/github.com%2Fjsdom%2Fjsdom).

## 5. Known vulnerabilities at this version

**None found on 2026-11-06.**

- deps.dev: `advisoryKeys: []`.
- osv.dev lists nothing for `jsdom` itself. The only hits are malicious look-alikes: MAL-2026-225 `jsdom-js`,
  MAL-2025-191486 `vitest-environment-jsdom-patched` and MAL-2022-2818 `eslint-plugin-jsdom-internal`
  (https://osv.dev/list?ecosystem=npm&q=jsdom).
- The second of those follows Vitest's `vitest-environment-<name>` naming pattern for custom environments. That is the
  reason for the last condition.

## 6. License

MIT.

## 7. Execution privileges

- **When it runs:** test time only, in Node.
- **How Vitest constructs it** (https://raw.githubusercontent.com/vitest-dev/vitest/v5.1.0/packages/vitest/src/integrations/env/jsdom.ts):

  ```ts
  new JSDOM(html, { pretendToBeVisual, runScripts, url, … })
  ```

  - `runScripts = 'dangerously'` executes inline `<script>` in a `vm` context. That is not a security boundary.
  - `url = 'http://localhost:3000'`.
  - `resources` is undefined, so no subresources are fetched, unless it is set in the environment options.
- **Vitest swaps in Node's globals.** It replaces `fetch`, `Response`, `Headers`, `AbortController` and
  `URLSearchParams` with Node's own, so any `fetch` in a test is real network.
- **Network code inside jsdom.** jsdom depends on `undici`, presumably for its own resource loading and XHR. The call
  sites were not read.

## 8. Direct dependencies as published

20 dependencies:

- **Parsing:** `parse5` ^8.0.1 and `saxes` ^6.0.0 (HTML/XML parsers), `css-tree` ^3.2.1 with
  `@csstools/css-syntax-patches-for-csstree` ^1.1.15, `@asamuzakjp/css-color` ^7.1.3 and `@bramus/specificity`
  ^2.4.2 (CSS), and `@asamuzakjp/dom-selector` ^10.0.3 (selectors).
- **WHATWG standards:** `whatwg-url` ^17.2.0, `whatwg-mimetype` ^5.0.0, `data-urls` ^8.0.0, `webidl-conversions`
  ^8.0.1, `html-encoding-sniffer` ^7.0.0 and `@exodus/bytes` ^1.16.0 (encodings).
- **Network:** `undici` ^8.12.1, which resolves to 8.12.4 (2026-11-05). It has trusted publishing with provenance and
  no advisories. **Flag it for the transitive audit.**
- **Other:** `tough-cookie` ^6.0.2 (cookies), `w3c-xmlserializer` ^6.0.0, `xml-name-validator` ^5.0.0,
  `is-potential-custom-element-name` ^1.0.1, `lru-cache` ^11.5.3 and `decimal.js` ^10.6.0.
- **Engines:** `^22.22.2 || ^24.15.0 || >=26.0.0`, which accepts Node 26.13.0.

## 9. Alternatives considered

- **happy-dom 21.0.4:** rejected, see its review.
- **Vitest Browser Mode with real Chromium:** more faithful, but it starts servers that have had advisories
  (GHSA-p458-7748-mcp3, GHSA-p63j-vcc4-9vmv) and needs `@vitest/browser-*`.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter.

**Residual risk.**

- Tests run fixture HTML with scripts enabled. That is safe only because the fixtures are our own.
- The tree is about 20 packages, plus transitives.

## 11. Not verified

- **Tarball contents.** Not read.
- **undici call sites** inside jsdom: where it actually reaches the network.
- **Compatibility of 30.1.3 with React 19.3**: not exercised.
- **Script isolation.** "Not a security boundary" is jsdom's long-standing guidance on `runScripts`
  (https://github.com/jsdom/jsdom#executing-scripts). The README was not re-read for this review.
