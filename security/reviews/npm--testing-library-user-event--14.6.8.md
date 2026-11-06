---
component: "@testing-library/user-event"
kind: npm
version: "14.6.8"
verdict: APPROVE-WITH-CONDITIONS
scope: "devDependency of apps/shipping-web: simulated user interaction in vitest run (jsdom); installed by npm ci --ignore-scripts from the reviewed lockfile"
conditions: ["Lockfile pins @testing-library/user-event 14.6.8 (sha512-vgSSqnANMnkcYR/vUNDbwS7aM3OErRLNUG8vCVVlj4oc6cKffUrccX7UAS5AmJQQNZTSibi9z5GvvRZ0aYgUCQ==)", "The orchestrator's tarball audit shows no network, child_process, eval or credential-path capability; any hit voids this approval (this library should need none)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: @testing-library/user-event 14.6.8

**Method.** Read on 2026-11-06:

- https://registry.npmjs.org/@testing-library/user-event/14.6.8 and `/14.6.1`
- deps.dev
- https://github.com/testing-library/user-event/releases
- the compare page `v14.6.1...v14.6.8` (it did not render)
- the scorecard

Nothing was downloaded or run.

## 1. Verdict

**APPROVE-WITH-CONDITIONS.** It has no dependencies, no install scripts and verified provenance. The release pattern
deserves a look, though:

- after an **18-month gap** (14.6.1, January 2025), six patch releases came out in ten weeks (14.6.3–14.6.8,
  August–October 2026);
- two npm maintainers were added in the same period;
- publishing moved from a bot token to trusted publishing.

The changes read as ordinary maintenance revival, and moving to trusted publishing improves provenance. Because the
diff could not be read, approval depends on the mechanical audit showing no capability a pure event-simulation
library should not need.

## 2. What it is and why it is needed

It simulates realistic user input (typing, clicking, keyboard) in component tests. The brief lists it with Testing
Library. The newest 14.x was chosen.

## 3. Provenance

- **14.6.8.**
  - `_npmUser` is "GitHub Actions" (trustedPublisher `github`).
  - SLSA provenance: testing-library/user-event commit `1951eb77cc190a68eb6dac6bd40b53648b18805f`, which equals
    `gitHead`, `verified: true` (https://api.deps.dev/v3/systems/npm/packages/%40testing-library%2Fuser-event/versions/14.6.8).
  - The release commits are GitHub-signed (`B5690EEEBB952194`).
- **14.6.1.** `_npmUser` is `testing-library-bot`, with **no attestations** (https://registry.npmjs.org/@testing-library/user-event/14.6.1).
- **Maintainers.** There were 16 at 14.6.1 and 18 at 14.6.8: `snowystinger` and `matanbobi` were added.
- **Install scripts:** none.
- **Native binaries:** none.

## 4. Maintenance and adoption

- **Releases** (https://github.com/testing-library/user-event/releases):
  - 14.6.3 (2026-08-03, "manually release a patch version")
  - 14.6.4 (keyboard `repeat`)
  - 14.6.5 (tab retargeting)
  - 14.6.6 (pointerType default)
  - 14.6.7 (2026-09-02, DataTransfer aliases plus "feature: Add iframe support for user.keyboard typing")
  - 14.6.8 (2026-10-13, null input values)
- **Oddity in 14.6.7.** One bullet's link text says `1e0020b`, but the link targets commit `d4b05935…`. It looks like
  a changelog-generator artefact; not investigated further.
- **Size of the change.** The compare page reports 27 commits, 73 files and 19 contributors since 14.6.1. The diff
  did not render.
- **Scorecard 4.6** (2026-08-24): Maintained 9, Code-Review 4 (13/29), Security-Policy 0, Token-Permissions 0.

## 5. Known vulnerabilities at this version

**None found on 2026-11-06.** deps.dev has `advisoryKeys: []`.

## 6. License

MIT.

## 7. Execution privileges

- **When it runs:** test time only, inside Vitest.
- **What it does:** dispatches DOM events on jsdom.
- **What it should not need:** network, process, eval or file-system access. That is the basis of the condition.

## 8. Direct dependencies as published

- **None** (`dependencies: {}`).
- **Peer:** `@testing-library/dom` `>=7.21.4`, satisfied by 10.4.2.
- **Engines:** `npm >=6`, `node >=12`.

## 9. Alternatives considered

- **14.6.1:** older, no provenance, and missing the 2026 fixes.
- **`fireEvent` from `@testing-library/dom`:** no extra package, but less realistic event sequences.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter.

**Residual risk.**

- The 27-commit diff since 14.6.1 was not read.
- The newly added maintainers' history was not checked.

## 11. Not verified

- **The diff** v14.6.1...v14.6.8 and the tarball contents.
- **The two new maintainers.** Their identity and history.
- **npm package-access settings.**
