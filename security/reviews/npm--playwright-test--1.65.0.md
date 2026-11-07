---
component: "@playwright/test (with playwright and playwright-core)"
kind: npm
version: "1.65.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "devDependency of apps/shipping-web: E2E and accessibility tests in the contract-e2e job on GitHub-hosted ubuntu-24.04, against the locally started application only; installed by npm ci --ignore-scripts from the reviewed lockfile"
conditions: ["Lockfile pins @playwright/test, playwright and playwright-core 1.65.0 with the integrity values in section 3", "No `playwright install`, `install-deps`, `--with-deps` or branded channels (chrome, msedge) in CI; PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1; the browser comes only via browser-binary--chrome-headless-shell--158.0.8127.18 and launchOptions.executablePath", "No PLAYWRIGHT_DOWNLOAD_HOST or PLAYWRIGHT_*_DOWNLOAD_HOST overrides", "Tests navigate only to the application started by webServer on 127.0.0.1; webServer.command is a CODEOWNERS-protected repository command", "No --ui, show-report or trace-viewer servers in CI (reports are uploaded as artifacts); no video recording (it needs the unreviewed ffmpeg download)", "Only the playwright test command runs: never init-agents, init-skills, install-skill, mcp, run-test-mcp-server, test-server, cli, run-server, launch-server, run-driver, show-trace, open, show-report, install, install-deps, uninstall or mark-docker-image; nothing those commands write (.claude/, .github/agents/, .github/workflows/copilot-setup-steps.yml, .vscode/mcp.json, SKILL.md files) is committed", "The HTML reporter, if configured, sets open: 'never'; if its report is uploaded, a notices file listing the report app's inlined code (React 19.2.7, React DOM 19.2.7, scheduler 0.27.0, zip.js 2.7.73: MIT and BSD-3-Clause) goes with it", "No proxy in the job: no HTTP_PROXY, HTTPS_PROXY, ALL_PROXY or launchOptions.proxy, so the inlined proxy agents and ip-address 10.7.2 (GHSA-rxp7-6hxx-x8q5) are not reached", "Glob patterns (testMatch, testIgnore, command-line filters) come only from the CODEOWNERS-protected config and workflow, so minimatch 10.2.5 (GHSA-g3hv-75wg-m249) gets no outside pattern", "The third-party code inlined in the three packages (section B of the 2026-11-07 amendment, one allowlist entry of kind bundled each) is approved only as inlined there; none of it is installed, resolved or required on its own", "Licences outside the list accepted without conditions, inlined and never distributed: ISC (12 components), BlueOak-1.0.0 (minimatch) and CC-BY-4.0 (caniuse-lite data); the owner's explicit decision (security/AGENTS.md)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: @playwright/test 1.65.0 (plus playwright 1.65.0 and playwright-core 1.65.0)

**Method.** Read on 2026-11-06:

- registry docs for all three packages, deps.dev, osv.dev (GHSA-7mvr-c777-76hp);
- `browsers.json`, `registry/index.ts`, `browserFetcher.ts`, `oopDownloadBrowserMain.ts`, `dependencies.ts`,
  `packages/utils/network.ts` and `packages/utils/third_party/extractZip.ts` at tag v1.65.0;
- playwright.dev (`/docs/browsers`, `/docs/test-webserver`, `/docs/api/class-browsertype`).

Nothing was downloaded or run.

## 1. Verdict

**APPROVE-WITH-CONDITIONS.** All three packages were built from the same commit through trusted publishing, with
verified provenance, and none has install scripts. The risks are in what the runner **does**:

- **Browser downloads.** They are not hash-checked, and the downloader follows redirects to any host, including
  https→http.
- **`install-deps`** runs `apt-get` as root through `sudo`.
- **Chromium** launches without its sandbox by default.

The conditions remove the downloads from Playwright entirely and confine the browser to our own app.

## 2. What it is and why it is needed

The E2E and accessibility runner ("E2E/accessibility: Quote→book→label, denial, flag states", blueprint §4, §7).

## 3. Provenance

| Package | Published | Integrity | Unpacked | Dependencies |
|---|---|---|---|---|
| `@playwright/test` | 2026-11-05T18:30:18Z | `sha512-iGURLGuKDLakSEl7Pgfv8h7f+XLT+V861Wfhz0zlEl5nyFuKUopdkFBPKqTllTUp06aOwPuH8iuUEb3yp3MpbQ==` | 28,544 B | `playwright` 1.65.0 |
| `playwright` | 2026-11-05T18:28:14Z | `sha512-F3AMWcR44qMBt4kq+6Kb6mX7bjGXshktKFOPULd7reED6ZJWevjbNRqysP7q+AATE9kdPEZBibex1BYsvpVoeA==` | 5,063,827 B | `playwright-core` 1.65.0 |
| `playwright-core` | 2026-11-05T20:17:40Z | `sha512-vNSKgH6YqtN7cdT/L334zo0a+YwkrpicyrHJlWWPIm/Q+Nn2SWInil01oT0mbu96U4GgoRtCYQD9CzJQgju+lA==` | 13,969,978 B | none (third-party code vendored) |

- **Publisher.** All three show `_npmUser` "GitHub Actions" (trustedPublisher `github`).
- **npm maintainers:** pavelfeldman, yurys, dgozman-ms, microsoft-oss-releases, microsoft1es.
- **SLSA provenance.** microsoft/playwright commit `a1f35e1955976e84411f9c10358228a80b763ebe`, `verified: true`
  (deps.dev, all three).
- **Install scripts:** none. There are no `scripts` at all in `playwright` or `playwright-core`, and `@playwright/test`
  has `{}`.
- **Native binaries:** none in the packages. Browsers are fetched separately (§7).

## 4. Maintenance and adoption

- **Releases.** 1.65.0 is `latest`. The previous stable was 1.64.1 (2026-10-14), and 1.66 alphas are already
  published (https://api.deps.dev/v3/systems/npm/packages/playwright).
- **Scorecard 5.6** (2026-08-24) for microsoft/playwright, 98,981 stars:
  - Code-Review 10, Maintained 10
  - Token-Permissions 0, Branch-Protection 0
  - Pinned-Dependencies 5, Binary-Artifacts 7

## 5. Known vulnerabilities at this version

**None found on 2026-11-06.** deps.dev has `advisoryKeys: []` for all three.

GHSA-7mvr-c777-76hp is the browser install scripts' `curl -k`. It affects `playwright` before 1.55.1, so 1.65.0 is
not affected (https://api.osv.dev/v1/vulns/GHSA-7mvr-c777-76hp). osv.dev list pagination failed, so deps.dev is the
source for anything older.

## 6. License

Apache-2.0.

## 7. Execution privileges

- **Test time.**
  - It executes `playwright.config.*` and test files in Node workers.
  - It spawns browser processes. `chromiumSandbox` defaults to `false` ("Enable Chromium sandboxing. Defaults to
    `false`", https://playwright.dev/docs/api/class-browsertype).
  - `webServer.command` is a "Shell command" whose process inherits `process.env` (https://playwright.dev/docs/test-webserver).
- **`playwright install`, from `registry/index.ts`, `browserFetcher.ts` and `network.ts`.**
  - It downloads zips into `~/.cache/ms-playwright` and validates only HTTP 200 and `content-length`. **There is no
    hash or signature check.**
  - It follows any 3xx `Location`, with no limit and no host or protocol check.
  - Download-host environment overrides replace every mirror, whatever the scheme.
  - `extractZip` rejects parent-directory escapes but creates symlinks without checking their targets.
  - Installing `chromium` or the headless shell always adds **ffmpeg** (`resolveBrowsers`).
- **`install-deps` / `--with-deps`** run `sudo -- sh -c "apt-get update && apt-get install -y --no-install-recommends …"`.
  On ubuntu24.04 that is 21 packages for chromium (`dependencies.ts`, `nativeDeps.ts`).
- **Servers.** UI mode, `show-report` and the trace viewer start HTTP servers.

## 8. Direct dependencies as published

- **The chain:** `@playwright/test` → `playwright` 1.65.0 (exact) → `playwright-core` 1.65.0 (exact).
- **No other npm dependencies.** `playwright-core` vendors its third-party code: `yauzl`, `proxy-from-env` and
  `https-proxy-agent` are referenced in the source read. **The tarball audit should inventory these bundled
  modules**, because the lockfile cannot see them.
- **Engines:** `node >=20`.

## 9. Alternatives considered

- **Cypress:** larger, and it has its own binary download.
- **WebdriverIO:** needs drivers.

Playwright is the blueprint's choice, and axe integrates with it.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter.

**Residual risk.**

- **Release age.** The release was one day old at review.
- **No Chromium sandbox.** Chromium runs unsandboxed, so it must only ever load our own app.
- **Vendored code.** It is invisible to lockfile-based checks.

## 11. Not verified

- **Tarball contents.** The tarballs and the vendored module list were not read. *Closed by the 2026-11-07 amendment.*
- **TLS for downloads.** No `rejectUnauthorized` is set in the download files read, so Node's default certificate
  verification applies. The remaining TLS code paths were not read. *Still open; unreachable while `install` is
  excluded.*
- **Runtime libraries.** Whether the runner image already has every library Chromium needs without `install-deps`.
  *Moved to condition 6 of the Chrome Headless Shell review.*

## Amendment, 2026-11-07: the tarball audit and the bundled code

**Method.** The security-review agent did this on 2026-11-07, in new empty directories on the owner's MacBook, with
`/usr/bin/curl --proto '=https'`, `/usr/bin/openssl`, `/usr/bin/tar` and `/usr/bin/python3`:

- **Downloaded** the three tarballs, after reading their version documents.
- **Checked** each against `dist.integrity` and `dist.shasum`, and verified the registry signatures.
- **Verified the attestations.** The DSSE signatures of both attestations of each package were checked. The provenance
  certificate chain was checked against Sigstore's public-good Fulcio root, from `trusted_root.json` of
  sigstore/root-signing `main` (SHA-256 `6494e21e…0b66`).
- **Listed** every entry (no absolute path, `..` segment or link) before extracting the packages as data.
- **Read** the notices, the `.LICENSE` and `.txt` sidecars, the entry points, the CLI and the agent files.
- **Fetched** the release commit's `package.json`, `package-lock.json` and copied third-party sources from
  `raw.githubusercontent.com`.
- **Queried advisories** in OSV (`POST /v1/query` per package, `POST /v1/querybatch` for the 171 inlined components),
  deps.dev and GitHub's advisory database.

Nothing was installed or run.

### A. The three packages

| Package | Tarball | Entries | SHA-256 | Integrity (equal to `dist.integrity`) |
|---|---|---|---|---|
| `@playwright/test` | 8,750 B | 11 | `2f8c414b270504d18743ff7aab16ad686c0e6eedfe55697c8e9c98b3dc093523` | `sha512-iGURLGuK…MpbQ==` (section 3) |
| `playwright` | 909,712 B | 60 | `5c535e6d0b00411c7b926f2f39aeaa80b9daa9ccfa2dd5c21db718cd8808aa42` | `sha512-F3AMWcR4…VoeA==` (section 3) |
| `playwright-core` | 3,232,467 B | 116 | `315247c55a793ca760b2f05bdcee0e6a53fd65b1f22d1d3de8323c8c9395add3` | `sha512-vNSKgH6Y…+lA==` (section 3) |

- **Registry signatures: verified.** Two ECDSA signatures per package, keyid
  `SHA256:DhQ8wR5APBvFHLF/+Tc+AYvPOdTpcIDqOhxsBHRwC7U`, each "Verified OK" against npm's published key.
- **npm's publish attestations: verified.** Their DSSE envelopes are signed with the same npm key, and each subject's
  SHA-512 equals the downloaded tarball.
- **SLSA provenance: verified as far as the certificate.**
  - The DSSE signature checks against the Fulcio leaf certificate.
  - The leaf certificate chains to the Fulcio root (`3B:A7:B6:CC…80:C1`) through `sigstore-intermediate`, checked at a
    time inside its ten-minute window. The same check at today's date fails ("certificate has expired"), so it can
    fail.
  - The certificate binds `https://github.com/microsoft/playwright/.github/workflows/publish_release.yml@refs/tags/v1.65.0`,
    issuer `https://token.actions.githubusercontent.com`, trigger `release`, commit
    `a1f35e1955976e84411f9c10358228a80b763ebe` and run `42084133661`, attempt 1.
  - The predicate names the same workflow, ref and commit, on a GitHub-hosted runner, and its subject's SHA-512 equals
    the tarball.
  - Not verified: the Rekor inclusion proofs. The bundles' `integratedTime` values fall inside each certificate's
    window.
- **A publication gap, explained.** `playwright` was published at 18:28:14Z on 2026-11-05 and `@playwright/test` at
  18:30, but `playwright-core` only at 20:17:40Z. Its provenance was signed at 18:27:01Z in the same workflow run, and
  the registry's publish attestation is from 20:17:52Z. The published bytes equal the provenance subject, so they are
  the bytes that release run built. Only the upload was late.
- **Install scripts: none** (section 3 confirmed from the tarballs' `package.json`).
- **Native code: none.** There is no `.node` file. `playwright-core/lib/webp_codec.wasm` (565,369 bytes, SHA-256
  `164cd9583a3e4d081f767d3b41ff0a730443dc7535fcc5caff83561463384b79`) is WebAssembly.
  - It is byte-identical to `packages/utils/webp/webp_codec.wasm` at the release commit.
  - It is built from libwebp commit `3757b8afeb54e305eaef18502812a9a88b7ed662` with Emscripten "latest" (tested emcc
    6.0.2), by `utils/libwebp-wasm/build.sh` at that commit. So it is reproducible only approximately.
  - It runs inside V8 and is used only for WebP screenshots and WebP screenshot comparisons.
- **Shell scripts.**
  - `playwright-core/bin/reinstall_*` (12) install branded Chrome or Edge with `sudo`, and `bin/*.ps1` are for
    Windows. All are reachable only through `playwright install chrome|msedge`, which is not approved.
  - `lib/xdg-open` (32,416 bytes, from xdg-utils, MIT) is byte-identical to `package/xdg-open` of `open` 11.0.0
    (SHA-256 `7d759d54…98cf`; that tarball checked against its registry integrity). The inlined `open` uses it to open a
    browser. In CI, the HTML reporter never opens one: it returns early when `CI` is set (`lib/runner/index.js:3788`),
    and it also requires a TTY.
- **What `playwright test` contacts.** Its own code names no telemetry host. The download hosts in `coreBundle.js`
  (`cdn.playwright.dev`, `playwright.download.prss.microsoft.com`, `edgeupdates.microsoft.com`) serve only `install`
  commands. With `executablePath` and no install, the test run talks only to the browser it starts and to the
  `webServer` URL.
- **1.65.0 adds CLI commands that the conditions now exclude.** Besides `test`, `show-report` and `merge-reports`,
  there are `mcp`, `cli`, `run-test-mcp-server`, `test-server`, `run-server`, `launch-server`, `run-driver`,
  `show-trace`, `open`, the trace subcommands, `init-agents`, `init-skills` and `install-skill`. The inlined servers
  are started by these commands:
  - the MCP SDK's HTTP transport, through `tools.start` (`tools/utils/mcp/http.ts`), from `mcp` and the hidden
    `run-test-mcp-server`;
  - Playwright's WebSocket server on `ws` (`remote/playwrightWebSocketServer.ts`), from `run-server` and
    `launch-server`.

  The test runner's path was not seen to start either, and condition 6 excludes the commands that do.
- **Text addressed to AI agents (untrusted data, not followed).** It is reported here as a finding:
  - `playwright-core/lib/tools/skills/` holds three `SKILL.md` files. Two declare `allowed-tools` that grant shell
    access (`Bash(npx playwright:*)`, `Bash(playwright-cli:*)`).
  - `playwright/lib/agents/` holds three agent definitions, prompts and a `copilot-setup-steps.yml` workflow. That
    workflow uses tag-pinned `actions/checkout@v6` and `actions/setup-node@v6` and runs
    `npx playwright install --with-deps`.
  - `init-agents` (`lib/agents/generateAgents.js`) writes such files into `.claude/agents`, `.github/agents`,
    `.github/workflows/copilot-setup-steps.yml`, `.vscode/mcp.json` and similar.
  - `@playwright/test`'s `README.md` tells coding agents to run `npm i -g @playwright/cli@latest` and
    `npx @playwright/mcp@latest`: unpinned, unreviewed packages.
  - New condition 6 keeps all of this out of the repository.

### B. The bundled third-party code

The three packages declare no third-party dependency. Each bundle that inlines npm packages ships a `.LICENSE` sidecar
naming every inlined `package@version`. The bundles in `playwright` also ship a `.txt` build manifest naming every
inlined source file. The inventory below was checked three ways:

- **Sidecars against contents.** For `babelBundle.js`, `expect.js` and `esmLoader.js` the manifests' `node_modules/…`
  paths name the same packages as the sidecars. For `utilsBundle.js` and `serverRegistry.js`, esbuild's `// …node_modules/…` path markers
  do the same. The only difference is the two `react-is` copies, which the manifest calls `react-is-18` and
  `react-is-19`.
- **Bundles without third-party code.** `coreBundle.js` (3,627,040 bytes) has no `node_modules` marker, and the
  manifests of `lib/runner`, `lib/common`, `lib/worker`, `lib/loader` and `isomorphic.js` name no third-party file.
- **Versions.** Every listed version is present at exactly that version in the release commit's `package-lock.json`.

**B1. `playwright-core/lib/utilsBundle.js`** (3,349,180 bytes; sidecar `utilsBundle.js.LICENSE`), 80 packages:
@hono/node-server 2.1.5, @modelcontextprotocol/sdk 1.32.0, @sec-ant/readable-stream 0.4.1, agent-base 9.0.0, ajv-formats 3.0.1, ajv 8.20.0, ansi-colors 4.1.3, ansi-regex 5.0.1, balanced-match 4.0.4, brace-expansion 5.0.12, buffer-crc32 1.0.0, buffer-from 1.1.2, bundle-name 4.1.0, bytes 3.1.2, chokidar 4.0.3, colors 1.4.0, commander 15.0.0, content-type 1.0.5, cross-spawn 7.0.6, debug 4.4.3, default-browser-id 5.0.1, default-browser 5.5.0, define-lazy-prop 3.0.0, diff 9.0.0, dotenv 17.4.2, enquirer 2.4.1, eventsource-parser 3.1.0, eventsource 3.0.7, fast-deep-equal 3.1.3, fast-uri 3.1.9, get-east-asian-width 1.6.0, get-stream 9.0.1, graceful-fs 4.2.11, has-flag 4.0.0, hono 4.13.12, https-proxy-agent 9.1.0, ini 7.0.0, ip-address 10.7.2, is-docker 3.0.0, is-in-ssh 1.0.0, is-inside-container 1.0.0, is-stream 4.0.1, is-wsl 3.1.1, isexe 2.0.0, jpeg-js 0.4.4, json-schema-traverse 1.0.0, json5 2.2.3, mime 4.1.0, minimatch 10.2.5, ms 2.1.3, open 11.0.0, path-key 3.1.1, pend 1.2.0, pkce-challenge 5.0.1, pngjs 7.0.0, powershell-utils 0.1.0, progress 2.0.3, proxy-agent-negotiate 1.1.0, proxy-from-env 2.1.0, readdirp 4.1.2, retry 0.13.1, run-applescript 7.1.0, shebang-command 2.0.0, shebang-regex 3.0.0, signal-exit 4.1.0, smart-buffer 4.2.0, socks-proxy-agent 10.1.0, socks 2.8.9, source-map-support 0.5.21, source-map 0.6.1, strip-ansi 6.0.1, supports-color 7.2.0, which 2.0.2, ws 8.21.0, wsl-utils 0.3.1, yaml 2.9.0, yauzl 3.4.0, yazl 3.3.1, zod-to-json-schema 3.25.2, zod 4.6.7.

**B2. `playwright-core/lib/serverRegistry.js`** (57,039 bytes): chokidar 4.0.3, readdirp 4.1.2 (also in B1).

**B3. `playwright/lib/matchers/expect.js`** (548,290 bytes), 27 packages: @babel/code-frame 7.29.7, @babel/helper-validator-identifier 7.29.7, @jest/diff-sequences 30.4.0, @jest/expect-utils 30.4.1, @jest/get-type 30.1.0, ansi-styles 4.3.0, ansi-styles 5.2.0, chalk 4.1.2, ci-info 4.4.0, color-convert 2.0.1, color-name 1.1.4, escape-string-regexp 2.0.0, graceful-fs 4.2.11, has-flag 4.0.0, jest-diff 30.4.1, jest-matcher-utils 30.4.1, jest-message-util 30.4.1, jest-util 30.4.1, js-tokens 4.0.0, picocolors 1.1.1, picomatch 4.0.7, pretty-format 30.4.1, react-is 18.3.1, react-is 19.2.7, slash 3.0.0, stack-utils 2.0.6, supports-color 7.2.0.

**B4. `playwright/lib/transform/babelBundle.js`** (2,826,376 bytes), 69 packages, Playwright's TypeScript and JSX
transform of the test files: @babel/code-frame 7.29.7, @babel/compat-data 7.29.7, @babel/core 7.29.7, @babel/generator 7.29.7, @babel/helper-annotate-as-pure 7.29.7, @babel/helper-compilation-targets 7.29.7, @babel/helper-create-class-features-plugin 7.29.7, @babel/helper-globals 7.29.7, @babel/helper-member-expression-to-functions 7.29.7, @babel/helper-module-imports 7.29.7, @babel/helper-module-transforms 7.29.7, @babel/helper-optimise-call-expression 7.29.7, @babel/helper-plugin-utils 7.29.7, @babel/helper-replace-supers 7.29.7, @babel/helper-skip-transparent-expression-wrappers 7.29.7, @babel/helper-string-parser 7.29.7, @babel/helper-validator-identifier 7.29.7, @babel/helper-validator-option 7.29.7, @babel/helpers 7.29.7, @babel/parser 7.29.7, @babel/plugin-proposal-decorators 7.29.7, @babel/plugin-syntax-async-generators 7.8.4, @babel/plugin-syntax-decorators 7.29.7, @babel/plugin-syntax-import-attributes 7.29.7, @babel/plugin-syntax-json-strings 7.8.3, @babel/plugin-syntax-jsx 7.29.7, @babel/plugin-syntax-object-rest-spread 7.8.3, @babel/plugin-syntax-optional-catch-binding 7.8.3, @babel/plugin-syntax-typescript 7.29.7, @babel/plugin-transform-class-properties 7.29.7, @babel/plugin-transform-class-static-block 7.29.7, @babel/plugin-transform-destructuring 7.29.7, @babel/plugin-transform-explicit-resource-management 7.29.7, @babel/plugin-transform-export-namespace-from 7.29.7, @babel/plugin-transform-logical-assignment-operators 7.29.7, @babel/plugin-transform-modules-commonjs 7.29.7, @babel/plugin-transform-nullish-coalescing-operator 7.29.7, @babel/plugin-transform-numeric-separator 7.29.7, @babel/plugin-transform-optional-chaining 7.29.7, @babel/plugin-transform-private-methods 7.29.7, @babel/plugin-transform-react-jsx 7.29.7, @babel/plugin-transform-typescript 7.29.7, @babel/preset-typescript 7.29.7, @babel/template 7.29.7, @babel/traverse 7.29.7, @babel/types 7.29.7, @jridgewell/gen-mapping 0.3.13, @jridgewell/remapping 2.3.5, @jridgewell/resolve-uri 3.1.2, @jridgewell/sourcemap-codec 1.5.5, @jridgewell/trace-mapping 0.3.31, baseline-browser-mapping 2.11.20, browserslist 4.28.8, caniuse-lite 1.0.30001810, convert-source-map 2.0.0, debug 4.4.3, electron-to-chromium 1.5.418, gensync 1.0.0-beta.2, has-flag 4.0.0, js-tokens 4.0.0, jsesc 3.1.0, json5 2.2.3, lru-cache 5.1.1, ms 2.1.3, node-releases 2.0.54, picocolors 1.1.1, semver 6.3.1, supports-color 7.2.0, yallist 3.1.1.

**B5. `playwright/lib/transform/esmLoader.js`** (296,727 bytes), 12 packages: balanced-match 4.0.4, brace-expansion 5.0.12, buffer-from 1.1.2, debug 4.4.3, has-flag 4.0.0, json5 2.2.3, mime 4.1.0, minimatch 10.2.5, ms 2.1.3, source-map-support 0.5.21, source-map 0.6.1, supports-color 7.2.0.

**B6. The web apps in `playwright-core/lib/vite/`** (`htmlReport`, `traceViewer`, `recorder` and `dashboard`). They are
minified builds without a sidecar or licence comments.

- **Inlined code** (the version from the release lockfile; its presence by fingerprint in the bundles):
  - React 19.2.7, React DOM 19.2.7 and scheduler 0.27.0: the React 19 element symbol, in `htmlReport/report.js`, the
    dashboard and recorder indexes, and the trace viewer's `defaultSettingsView` chunk.
  - CodeMirror 5.65.18: the version string, in the `codeMirrorModule` chunks.
  - `@xterm/xterm` 5.5.0 and `@xterm/addon-fit` 0.10.0: the `xterm-` classes, trace viewer only.
  - yaml 2.9.0 (also in B1): recorder and trace viewer.
  - `@zip.js/zip.js` 2.7.73: the HTML report, the trace viewer's service worker and the dashboard.
  - The `@browser-logos/*` images (dashboard only) are pictures, not code.
- **Where they run.** Only in a browser that opens the report or a viewer; none runs in CI under the conditions.
- **Licences.** The release lockfile gives React, scheduler, CodeMirror and xterm as MIT, and zip.js as BSD-3-Clause.
  None of these notices ships in the npm package (condition 7).

**B7. Third-party source copied into Playwright's own tree** (compiled into `coreBundle.js` or the web apps; no npm
version applies):

| Copied code | Licence (file header at the release commit) | Where |
|---|---|---|
| pixelmatch (Mapbox) | ISC | `packages/utils/third_party/pixelmatch.js` → `coreBundle.js` (screenshot comparison) |
| extract-zip (Max Ogden and contributors) | BSD-2-Clause | `packages/utils/third_party/extractZip.ts` → `coreBundle.js` (browser downloads only) |
| proper-lockfile (MOXY), modified | MIT | `packages/utils/third_party/lockfile.ts` → `coreBundle.js` |
| minimist (James Halliday), modified | MIT | `packages/playwright-core/src/tools/cli-client/minimist.ts` → `lib/tools/cli-client/minimist.js` (`cli` only) |
| Puppeteer BiDi helpers (Google) | Apache-2.0 | `packages/playwright-core/src/server/bidi/third_party/*` → `coreBundle.js` (BiDi browsers only) |
| Chromium DevTools snippet (The Chromium Authors) | BSD-3-Clause | `packages/trace-viewer/src/third_party/devtools.ts` → trace viewer |
| VS Code codicons (Microsoft) | MIT | `packages/web/src/third_party/vscode/*` → the web apps' fonts and CSS |
| libwebp `3757b8a` with the Emscripten runtime | BSD-3-Clause; MIT or NCSA | `lib/webp_codec.wasm` (A) |

**Advisories.**

- **The packages: none.** OSV, deps.dev and GitHub's advisory database return nothing for `@playwright/test`,
  `playwright` or `playwright-core` 1.65.0 on 2026-11-07. A control query for `playwright@1.55.0` returns
  GHSA-7mvr-c777-76hp from all three, so the queries can return a hit.
- **The 171 inlined components: two medium-severity advisories** (OSV `querybatch`):
  - **ip-address 10.7.2, GHSA-rxp7-6hxx-x8q5** (CVE-2026-111997, fixed in 10.7.4). Parsing an attacker-supplied IPv6
    string with many `%` characters takes quadratic time. The inlined `socks` client parses proxy addresses with it, so
    it is reachable only with a SOCKS proxy. Condition 8 keeps proxies out.
  - **minimatch 10.2.5, GHSA-g3hv-75wg-m249** (CVE-2026-118764, affects 7.0.0 to before 11.0.1). An attacker-supplied
    glob pattern can grow without bound in preprocessing. Playwright matches test files and filters with it, and those
    patterns come from our config (condition 9).
  - Both are denial of service on inputs that the conditions keep in the repository's hands. Neither can be upgraded
    apart from Playwright.
- **The copied sources in B7 and the axe-core-side packages** were not queried by version, because they carry none.

**Licences.** The inlined components are 150 MIT, 12 ISC (electron-to-chromium, graceful-fs, ini, isexe, lru-cache,
picocolors, semver, signal-exit, which, yallist, yaml, zod-to-json-schema), 5 BSD-3-Clause, 1 BSD-2-Clause,
1 Apache-2.0, 1 BlueOak-1.0.0 (minimatch) and 1 CC-BY-4.0 (caniuse-lite, browser-usage data), per the release lockfile.
ISC, BlueOak-1.0.0 and CC-BY-4.0 need the owner's decision (condition 11).

**Allowlist entries: one per inlined component.** This follows `security/AGENTS.md` ("bundled or shaded third-party
code (each needs its own entry)") and the shape of the only precedent, npm 11.20.0 inside the Node.js archive:

- `artifact` names the file that carries the component, and `sha256` is null.
- `reviewRecord` is this record.
- **What is new is the kind: `bundled`, not `npm`.** `PolicyCheck` approves a `package-lock.json` entry by kind `npm`,
  coordinate and version. A kind-`npm` entry for an inlined copy would therefore approve the standalone registry
  package of the same name and version, such as `ws` 8.21.0 or `debug` 4.4.3, which nobody reviewed as an install.
- With kind `bundled` the checker still validates the entries (verdict, review record, expiry and the acceptance
  digest), but no lockfile entry can match them.
- The npm 11.20.0 entry moves to the same kind.
- This is a proposal, made in this record, that the owner accepts or rejects with the set.

**The entries.**

- **171 for B1 to B6:** one per package and version, counted once where several bundles carry it. That is 164 from the
  sidecars and seven from the web apps only. `open`'s entry also covers `lib/xdg-open`.
- **8 for the copied sources of B7.**

**Closed and still open** (section 11):

- **Closed:** the tarball contents and the bundled-module list (above).
- **Not re-read:** TLS on the download path. It is unreachable while `install` is excluded.
- **Moved:** the runner libraries, to condition 6 of the Chrome Headless Shell review.
- **Still not verified:** the Rekor inclusion proofs; whether the minified web apps contain only the components named
  in B6, which needs their build manifests and Playwright does not ship them; and a rebuild of any bundle from source.
