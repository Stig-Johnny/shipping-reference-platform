---
title: "ADR 0019: Strict same-origin content security for the workbench"
description: "The workbench permits only same-origin resources and API connections, with a tested meta policy, delivered response header and explicit workbench page routes."
status: accepted
updated: 2026-11-09
date: 2026-11-08
decision-makers: repository owner
enforcedBy:
  - "web: node --test, csp.test.ts asserts the entry policy text, placement and emitted module graph serving paths"
  - "java: DemoSmokeTest and DemoSecurityHeadersTest assert the delivered response policy and direct page routes"
plannedEnforcement:
  - "Adversarial browser tests of enforcement"
  - "Compiler-derived import graph comparison with the C4 model (ADR 0015)"
---

# ADR 0019: Strict same-origin content security for the workbench

## Context and Problem Statement

ADR 0012 places the workbench and API on one origin; ADR 0015 separates partial import lint from browser runtime
controls. The owner selected strict content security for issue #60. The app needs modules, a stylesheet and API
requests, all on that origin. The demo profile serves its staged static build on the API origin (ADR 0018).

## Considered Options

1. Restrict all resources and connections to the app's origin, with explicit script and connection directives.
2. Allow inline execution, evaluated strings or additional origins for convenience.

## Decision Outcome

Chosen option 1, as directed by the owner. `apps/shipping-web/index.html` declares this policy in a meta element
before any resource loads:

```text
default-src 'self'; script-src 'self'; connect-src 'self'; style-src 'self'; img-src 'self'; font-src 'self'; base-uri 'none'; form-action 'none'; object-src 'none'; frame-src 'none'; worker-src 'none'
```

The API is same-origin `/api/v1`, so no separate origin is allowed. There is no `unsafe-inline`, `unsafe-eval`,
external script, font or connection origin. The policy needs no nonce or hash because scripts and styles are files.
The demo server repeats the policy as a response header and adds `frame-ancestors 'none'`, which is not supported
in a meta policy. API responses through the demo security chain carry the same policy, except the label's stricter
policy; early/outer error-response exceptions remain as documented in the contract.

### Workbench integration, 2026-11-09

Compile modules into `dist/assets`, and stage `index.html` and `workbench.css` into `dist`. The `/assets` prefix
keeps the adapter module out of the authenticated `/api` namespace. The entry test walks the emitted module graph.
The static controller maps only `/sign-in`, `/quotes/new`, `/quotes/<uuid>`, `/shipments` and
`/shipments/<uuid>` (with an optional trailing slash), plus `/`, to the entry document. Other paths resolve only
actual contained files; missing files stay 404. API paths never become HTML. This preserves History API reloads
without hiding missing assets or changing API authentication. No model edge, dependency or security review changes.

### Consequences

The browser has a strict declaration to enforce. The automated tests check its text, placement and delivered header; they do not execute a browser. The policy restricts origins, not application module boundaries. Same-origin unsafe assets would
still be a risk, and the partial import reader's known misses remain open. The contract records the stricter demo header. Model relationships, dependency reviews and preview scope stay unchanged.

### Confirmation

`csp.test.ts`, under exactly `node --test` in the web workspace, fails if the policy text changes, disappears,
is duplicated, or follows a resource element. The `web` job runs it after the reviewed compiler.
`DemoSmokeTest` first failed on missing direct routes and the old header, then passed after integration. The
attended memory-mode browser flow is recorded in demo #87. This is compatibility evidence, not adversarial CSP
proof. No Playwright end-to-end or axe test is implemented, and no new browser binary was installed.
