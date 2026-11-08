---
title: "ADR 0017: Strict same-origin content security for the workbench"
description: "The workbench permits only same-origin resources and API connections, with a tested meta policy and a required response header when serving is implemented."
status: accepted
updated: 2026-11-08
date: 2026-11-08
decision-makers: repository owner
enforcedBy:
  - "web: node --test, csp.test.ts asserts the entry document policy text and placement; no browser enforcement evidence"
plannedEnforcement:
  - "Browser tests of enforcement and the delivered CSP response header when API asset serving exists"
  - "Compiler-derived import graph comparison with the C4 model (ADR 0015)"
---

# ADR 0017: Strict same-origin content security for the workbench

## Context and Problem Statement

ADR 0012 places the workbench and API on one origin; ADR 0015 separates partial import lint from browser runtime
controls. The owner selected strict content security for issue #60. The app needs modules, a stylesheet and API
requests, all on that origin. Its sources exist, but the API currently serves no static files.

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
When repository-owned serving is implemented, it must repeat the policy as a response header and add
`frame-ancestors 'none'`, which is not supported in a meta policy. No server is added by this change.

### Consequences

The browser has a strict declaration to enforce. The current test checks its text and placement only; it does not
execute a browser. The policy restricts origins, not application module boundaries. Same-origin unsafe assets would
still be a risk, and the partial import reader's known misses remain open. No change to the API contract,
model relationships, dependency reviews or preview scope is needed.

### Confirmation

`csp.test.ts`, under exactly `node --test` in the web workspace, fails if the policy text changes, disappears,
is duplicated, or follows a resource element. The `web` job runs it after the reviewed compiler.
Browser enforcement, a delivered response header and the integrated workbench flow remain unverified. No Playwright
end-to-end or axe test is implemented, and no browser binary is installed or run for this recording.
