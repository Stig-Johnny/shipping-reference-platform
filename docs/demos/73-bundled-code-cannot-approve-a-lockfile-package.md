---
title: "A copy bundled inside a reviewed tool can no longer approve that package in a lockfile"
description: "The workbench's test tools are reviewed down to the code they bundle, and each bundled copy gets an allowlist entry that the lockfile check never matches."
status: accepted
updated: 2026-11-07
pr: 73
kind: gate
recorded: 2026-11-07
tests: [AllowlistTest]
---

## What changed

**Before.** The allowlist named the browser-test tools by package only. The code they carry inside was not listed:
Playwright inlines 171 other packages and axe-core 21. The one bundled copy that did have an entry, the npm inside the
Node.js archive, was listed as an ordinary npm package. So a lockfile entry for that same npm version passed the
dependency check, although nobody had reviewed it as an install.

**Now.** Every bundled copy has its own entry, of a new kind, `bundled`. The entry names the file that carries the copy
and the review that inventoried it. The check matches lockfile entries only against kind `npm`, so a bundled copy can
never approve the standalone package. The same pull request adds:

- the reviews of TypeScript 5.9.3 and of the maintenance environment that generates lockfiles;
- the Node.js verification and the browser's pinned hash;
- the attended-demo records.

The owner's acceptance of the reviewed set is pending.

## Try it

With the reviewed JDK as `JAVA_HOME`, create `package-lock.json` at the repository root with one package:
`node_modules/npm` at version 11.20.0, resolved from `https://registry.npmjs.org/npm/-/npm-11.20.0.tgz`, with an
`sha512-` integrity value. Run the check; it reports the package. Replace that entry with `node_modules/typescript` at
5.9.3, resolved from `https://registry.npmjs.org/typescript/-/typescript-5.9.3.tgz`, and run it again; it passes.
Delete the file afterwards. Until the owner's acceptance is recorded, the check also reports that it is pending.

```sh
java tools/policy/PolicyCheck.java .
```

## Evidence

Recorded output — not rerun by this site build.

Recorded 2026-11-07T22:41:46Z on macOS with Temurin 25.0.5+8. The fixture lockfiles held the registry's own `resolved`
and `integrity` values.

On a copy of main at 2e0d8f7, with npm 11.20.0 in the lockfile, the check exited 0:

```text
## Dependency allowlist: clean

Checked against `security/allowlist.json` (308 entries) on 2026-11-07.
```

On a copy of pull request #73's files, with npm 11.20.0 in the lockfile, it exited 1. In that copy the owner's
acceptance was simulated by setting its status to accepted, and nothing else was changed:

```text
## Dependency allowlist: 1 finding(s)

Checked against `security/allowlist.json` (519 entries) on 2026-11-07.

### npm

- npm 11.20.0 (in package-lock.json) has no review on the allowlist
```

On the same copy, with typescript 5.9.3 in the lockfile instead, it exited 0:

```text
## Dependency allowlist: clean

Checked against `security/allowlist.json` (519 entries) on 2026-11-07.
```

`AllowlistTest` recomputes the reviewed-set digest in every build. Reviewed in pull request #73.

## Limits

- **The owner's acceptance is pending.** The outputs above come from a copy where it was simulated. On the pull
  request's own files the check also fails on the pending acceptance until the owner's yes is recorded.
- **The check does not compare integrity values.** It matches a lockfile entry by kind, name and version, checks the
  registry prefix and that an `sha512-` value is present. Comparing `integrity` with the reviewed value is a step of
  the lockfile pull request, done by hand.
- **The inventories are evidence, not proven closures.** They list what the tools say they bundle, checked against
  their build manifests. Parts of the minified web apps are inferred.
- **No lockfile exists in the repository yet.** Nothing was installed or run for this demonstration.
