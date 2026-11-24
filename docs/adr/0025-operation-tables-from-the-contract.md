---
title: "ADR 0025: Operation tables come from the contract and the capabilities"
description: "The module documents' HTTP operation sections are generated from the OpenAPI contract, with planned marks from the capabilities that name each operation and problem type; the contract's problem registry and each response's problem types are read strictly, so that nothing drops out of a table silently."
status: accepted
updated: 2026-11-24
date: 2026-11-24
decision-makers: orchestrating agent, under the repository owner's standing delegation
enforcedBy:
  - "java: the documentation check fails on a malformed problem registry, a line of its section outside its table that holds a pipe, a type registered twice or raised by an unknown owner; on a problem response whose description does not start with registered types of its status, whose key is no status code, that has a description beside its $ref or references another reference; and on a path item's $ref, webhooks, callbacks and an operationId twice (ContractProblemsTest)"
plannedEnforcement:
  - "java: every operation and every problem type that the registry gives a module is named by a capability, and an operation or a problem type is planned when every capability that names it is planned; the brain and the API pages show it (issue 157)"
  - "java: each module document's section that starts with the heading HTTP operations is generated from the contract and those capabilities, and checkGenerated fails on any difference (issue 155)"
---

# ADR 0025: Operation tables come from the contract and the capabilities

## Context and Problem Statement

Step 4 of issue 136 asks that the tables of HTTP operations in the prose come from the OpenAPI contract. On
2026-11-23 three module documents wrote them by hand, and nothing compared them with `contracts/shipping.openapi.yaml`:
shipments five rows, quotes two and tenants one bullet. The brain pages and the site's API pages were already
generated from the contract.

Two facts in those tables are not in the contract's structure. The contract names a response's problem types only in
its prose, and says which module raises each type only in the problem registry, a table in its description. And the
shipments table marks `cancelShipment` and the label's `409 shipment-cancelled` as planned: both are in the contract,
but no code serves the operation and none raises the type. That is the state of the code, which
`docs/capabilities/capabilities.yaml` already records with evidence: `cap:shipments.cancel-shipment` is planned.

## Decision Drivers

- One source for each fact: the contract for the interface, the capabilities for what is built.
- A misspelled type, or a type under the wrong status, must fail, not drop out of a generated table.
- A reader, person or agent, sees that a section is generated and where to change it.
- The module document keeps the table, because AGENTS.md sends readers to it before the code.

## Considered Options

1. Generate each module document's operation section from the contract, take the planned marks from the
   capabilities that name each operation and problem type, and read the registry and the responses' types strictly.
2. Keep the tables hand-written and check them against the contract.
3. Generate the tables into files of their own and link them from the module documents.
4. Mark planned operations in the contract with an `x-` extension.
5. Read which operations are built from the controllers' annotations.

## Decision Outcome

Chosen: option 1. Option 2 keeps two sources of the same facts. Option 3 takes the table out of the document that
readers are sent to. Option 4 puts a second record of what is built beside the capabilities, bound to no evidence.
Option 5 reads source code, which breaks with any refactoring of a controller.

- **The problem registry is read strictly.** It is the one table in the section Errors (RFC 9457) of the contract's
  description, which runs to the next heading of level 1 or 2. A line of that section outside the table that holds a
  pipe fails, so that no row can fall out of it. The header and the rule have the four columns type (suffix), status,
  title and raised by. Each row's cells are read stripped: a type as a code span, a status code, a title that is not
  blank, and its owners, `any module`, `HTTP edge` or modules that are tags of the contract, with an optional note in
  parentheses, such as "has `reason`", which names an extension member. A malformed row, a type twice, an unknown
  owner and a missing table fail when the contract is read.
- **A problem response names its types first.** Every response of an operation with status 400 or above starts its
  description with its problem types: code spans joined by commas and "or", each with an optional note in
  parentheses, ended by a colon, a full stop or the end. Each type is registered with the response's status; code
  spans after them are prose. A response is keyed by its status code, its description stands where the response is
  defined, never beside a `$ref`, so that every use gives the same types, and a reference names the component that
  defines the response, not another reference.
- **Every operation is read.** The operations are those under `paths`, `trace` included. A path item's `$ref`,
  `webhooks` and `callbacks` fail when the contract is read, because no check would read the operations they hold,
  and an operationId names one operation.
- **Capabilities name their operations and problem types** (issue 157). A capability, never a control, may list
  `operations` and `problems`. Each operation is tagged with the capability's module, and the registry gives each
  problem type to it. Every operation, and every problem type that the registry gives a module, is named by at least
  one capability, so that none has an unknown state. An operation or a problem type is planned when every capability
  that names it is planned.
- **The section is generated** (issue 155). The generator owns the section of `docs/modules/<tag>.md` that starts
  with the level-2 heading HTTP operations, up to the next level-1 or level-2 heading. It writes a notice that the
  section is generated and from what, then one row per operation: its operationId, its method and path, its success
  responses and the problem types that the registry gives the module.

### Consequences

- Good, because a misspelled problem type or one under another status fails the documentation check, and a module's
  problem types come from one table.
- Good, because the planned marks follow the capabilities' evidence: when cancellation is built and its capability is
  partial, its operation is no longer marked.
- Neutral, because the contract's prose now has a form to keep: a problem response's description starts with its
  types.
- Bad, because a generated section inside a hand-written document is a new kind of ownership. Its notice says so, and
  `checkGenerated` refuses an edit of it.
- Bad, because an unmarked operation may still be partly built: its capabilities may be partial.

### Confirmation

`ContractProblemsTest` reads the registry and fails on each malformed form above, a line outside its table and a
padded cell read as part of its value. It fails on a response that names an unregistered type, a type of another
status or no types first, on a response key that is no status code, on a description beside a `$ref` and on a
reference to a reference, and on each form of operation above that no check would read. The documentation check runs `ContractProblems` over the contract in every `generate`,
`checkGenerated` and site build. The bindings (`planned:test:OperationBindingsTest`) and the generated sections
(`planned:test:OperationSectionsTest`) come with issues 157 and 155.
