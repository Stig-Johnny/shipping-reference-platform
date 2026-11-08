---
title: "ADR 0003: Modular monolith with Spring Modulith and hexagonal modules"
description: "One deployable Spring Boot application; each business module is a top-level package with a public api package and hexagonal internals."
status: superseded
supersededBy: "0012"
updated: 2026-11-07
date: 2026-11-06
decision-makers: repository owner
enforcedBy:
  - "java: ModularityTest (Spring Modulith verify)"
  - "java: HexagonalLayeringTest (ArchUnit)"
  - "java: ArchitectureConformanceTest (C4 edges)"
  - "java: compilation with Error Prone and NullAway"
---

# ADR 0003: Modular monolith with Spring Modulith and hexagonal modules

## Context and Problem Statement

v0.1 completes two business modules (quotes, shipments) with two supporting slices (tenants, audit) and keeps three
modules planned (orders, tracking, returns). One developer and several agents build it over a weekend. The
boundaries must be real enough to check, while deployment stays simple. How is the API structured?

## Decision Drivers

- Boundaries that a build can verify, not conventions in a wiki.
- One deployable and one database for a demo that must not fail on infrastructure.
- Java that a sceptical lead developer recognises as idiomatic and current.
- No shared "common" module that collects unrelated code.

## Considered Options

1. Modular monolith on Spring Boot 4.1.2 and Spring Modulith 2.1.2, hexagonal packages inside each module.
2. Microservices, one per module.
3. Layered monolith with global `controller`, `service` and `repository` packages.

## Decision Outcome

Chosen option 1.

- Base package `io.github.stigjohnny.shipping`. Each module is a direct sub-package: `quotes`, `shipments`,
  `tenants`, `audit` (planned for v0.1) and `orders`, `tracking`, `returns` (planned, no code).
- Inside a built module exactly these packages may exist (a module omits the ones it does not need):
  - `api`: the only package other modules may use. Annotated `@NamedInterface("api")` in `package-info.java`.
  - `internal.domain`: records, validated values, aggregates, sealed outcomes. No Spring, Jakarta, JDBC or adapter
    imports.
  - `internal.application`: use cases (one public method each), outbound port interfaces, event listeners.
  - `internal.adapter.in.web`: controllers, request and response records, problem mapping.
  - `internal.adapter.out.jdbc`: Spring Data JDBC repositories and port implementations.
- Allowed dependencies inside a module: `adapter.*` may use `application`, `domain` and `api`; `application` may use
  `domain` and `api`; `domain` may use the JDK, JSpecify and `api` packages, and nothing from Spring, Jakarta, JDBC,
  `application` or adapters; `adapter.in` must not use `adapter.out`. Across modules only `api` packages may be used,
  and only along edges declared in `architecture/workspace.dsl`.
- Module `package-info.java` carries `@ApplicationModule` without `allowedDependencies`: the C4 model is the single
  list of allowed edges, so it is not duplicated in annotations.
- The root package holds only the isolated classes named in the model (`ShippingApplication`, `CorrelationIdFilter`,
  `FallbackProblemHandler`). They must not depend on modules and modules must not depend on them.
- Java style: records for values and events; validation in canonical constructors; sealed interfaces for expected
  business outcomes (for example `QuoteResult permits Offered, Unavailable`); exceptions only for exceptional
  failures; constructor injection only; an injected `java.time.Clock`; `@NullMarked` on every package
  (JSpecify 1.0.1), enforced by NullAway 0.14.3 on Error Prone 2.51.0; no Lombok; no field injection.

### Consequences

- Good, because module boundaries fail the build in three complementary ways: Modulith (internals, cycles), ArchUnit
  (layering) and c4-contract-kit (declared edges in both directions).
- Good, because each module's boundary is explicit: its public `api` package, its own schema (ADR 0005) and events
  where a module only reacts to another (ADR 0006). A later extraction would start from a boundary already drawn.
- Bad, because extracting a module would still be a redesign of that boundary, not a packaging change. A booking
  looks up its quote's offer and records its event publication in one database transaction
  (`docs/modules/shipments.md`). Across services that lookup becomes a network call that can fail or time out on its
  own, and the planned in-process publication would need reliable cross-service delivery, for example through a
  message broker.
- Bad, because hexagonal ports add interfaces that a small CRUD module would not need.
- Bad, because Error Prone and NullAway couple the build to compiler internals; a JDK update needs a compatibility
  check and an allowlist review.

### Confirmation

- `java` runs `ModularityTest`: `ApplicationModules.of(ShippingApplication.class).verify()`.
- `java` runs `HexagonalLayeringTest` (ArchUnit 1.5.1): only the five package names above inside a built module;
  domain free of `org.springframework..`, `jakarta..`, `java.sql..`, `javax.sql..`, `application` and adapter
  packages; `application` free of adapter packages; `adapter.in` independent of `adapter.out`; every package has a
  `package-info.java` annotated `@NullMarked`. Each rule also fails on a fixture that breaks it.
- `java` runs `ArchitectureConformanceTest` (ADR 0002).
- Compilation fails on Error Prone errors and NullAway findings; `@SuppressWarnings("NullAway")` is a level 3
  behaviour signal in `policy/risk.json`.

## More Information

- Spring Modulith reference: <https://docs.spring.io/spring-modulith/reference/>
- Module documentation: `docs/modules/<module>.md`.
- Correction, 2026-11-07: a consequence above said that extracting a module later is "a packaging change, not a
  redesign". That left out the network calls and the transaction boundary an extraction changes; the two
  consequences about the module boundary replace it. The decision is unchanged.
