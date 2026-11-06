---
component: "happy-dom"
kind: npm
version: "21.0.4"
verdict: REJECT
scope: "Not approved anywhere. Vitest uses jsdom 30.1.3 (npm--jsdom--30.1.3)."
conditions: ["Re-review only if a documented jsdom gap blocks a required test; the re-review must cover the five older advisories not opened here"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: happy-dom 21.0.4 (alternative DOM environment, rejected)

**Method.** I read https://registry.npmjs.org/happy-dom/21.0.4, deps.dev, https://osv.dev/list?ecosystem=npm&q=happy-dom
and three advisory pages on 2026-11-06. Nothing was downloaded or run.

## 1. Verdict

**REJECT, in favour of jsdom.**

- 21.0.4 itself has no known advisory: it is the fix release for GHSA-834v-8h66-3h79.
- The project has had **eight advisories in two years**. Three are rated critical, for code execution or failed
  isolation. Two more came out in October 2026, and their fixes are only weeks old.
- Its defaults load iframes and stylesheets from the network.
- It has a **single npm maintainer**.

jsdom covers the same need with none of this history.

## 2. What it is and why it is needed

A fast DOM implementation for Node. It is one of the two environments Vitest supports, and the brief asks for one to
be recommended. Vitest's docs call it "faster than jsdom, but lacks some API" (https://vitest.dev/guide/environment).

## 3. Provenance

- **Publisher.** `_npmUser` is "GitHub Actions" (trustedPublisher `github`). The only maintainer is `davidortner`.
- **SLSA provenance.** capricorn86/happy-dom, commit `fd163520b74d9fa146be91f7b4e4f46d6cccbe13`, `verified: true`.
  This is the commit GHSA-834v lists as its fix.
- **Integrity.** `sha512-tFm+GaLBgo6o0awHnvHaNoraVo27qJ+2tZEtZ+cIa08hXbeitrxAJajLI05hsUy/2/YTKcUjQYzHe5mCcraLBw==`
- **Install scripts:** none.
- **Native binaries:** none.

## 4. Maintenance and adoption

- **Releases.** 21.0.4 was published 2026-10-20. It is the fifth 21.0.x release in nine days after 21.0.0
  (2026-10-11).
- **Scorecard 6.4** (2026-08-24): Maintained 10, Code-Review 3 (10 of 30 changesets approved), Token-Permissions 0.
  4,734 stars.

## 5. Known vulnerabilities at this version

**None at 21.0.4.** deps.dev shows `advisoryKeys: []`. The project's advisory history from
https://osv.dev/list?ecosystem=npm&q=happy-dom:

| ID | Summary | Severity | Fixed |
|---|---|---|---|
| GHSA-c8p4-j97h-3vg8 | SSRF via iframe/link stylesheet when parsing untrusted HTML. Applies with the defaults `disableIframePageLoading: false` and `disableCSSFileLoading: false` | 8.6 | 21.0.0 |
| GHSA-5fpq-26gf-qwcr | Cookie scope forgery | 6.5 | 20.14.6 |
| GHSA-834v-8h66-3h79 | Incomplete fix for CVE-2026-33943, leading to host code execution | 8.8 | 21.0.4 |
| GHSA-w4gp-fjgq-3q4g | fetch credentials use the wrong origin's cookies | 7.5 | not opened |
| GHSA-6q6h-j7hj-3r64 | ECMAScriptModuleCompiler code injection | 8.8 | not opened |
| GHSA-qpm2-6cq5-7pq5 | `--disallow-code-generation-from-strings` is not sufficient for isolation | critical | not opened |
| GHSA-37j7-fg3j-429f | VM context escape leading to RCE | 9.3 | not opened |
| GHSA-96g7-g7g9-jxw8 | Server-side code executed by a `<script>` tag | 9.3 | not opened |

## 6. License

MIT.

## 7. Execution privileges

- **When it would run:** test time, in Node.
- **What it does:** it evaluates page JavaScript in Node.
- **Network:** by default it loads iframes and CSS over the network, and it has a WebSocket client (`ws`).

## 8. Direct dependencies as published

- `ws` ^8.21.0
- `entities` ^7.0.1
- `whatwg-mimetype` ^3.0.0
- `buffer-image-size` ^0.6.4
- `@types/node` >=20.0.0, `@types/ws` ^8.18.1, `@types/whatwg-mimetype` ^3.0.2

## 9. Alternatives considered

**jsdom 30.1.3** is approved with conditions.

## 10. Conditions and residual risk

Rejected; see the frontmatter for when a re-review would make sense.

## 11. Not verified

- **The five older advisories:** their pages were not opened, so their fixed versions are not confirmed.
- **The tarball:** its contents were not read.
