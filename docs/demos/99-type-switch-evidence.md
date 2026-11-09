---
title: "A test now records the checker's switch-pattern blind spot"
description: "Some Java type references are invisible to the architecture check; a focused fixture makes that limit explicit."
status: accepted
updated: 2026-11-09
pr: 99
kind: docs
recorded: 2026-11-09
tests: [BytecodeEvidenceTest]
---

## What changed

The architecture checker can miss a dependency used only in a Java switch type pattern. That limit was documented
but had no dedicated test. A new fixture now checks both named and unnamed patterns and compares them with an
ordinary type check that the importer does detect.

| Fixture form | Reference reported by the current importer |
|---|---|
| Named switch pattern, `case Target target` | None |
| Unnamed switch pattern, `case Target _` | None |
| Positive control, `value instanceof Target` | Target class, member, reference kind and source line |

This test records existing behavior. A passing architecture check can still miss a pattern-only dependency, and
that reference cannot satisfy a REQUIRED relationship.

## See it

Read [what the architecture check cannot prove](../gaps.md#1-what-the-architecture-check-cannot-prove), under
"Only what the class file records, as ArchUnit 1.5.1 reads it". The text now distinguishes the tested type-pattern
limit from unmeasured enum switches. [Issue 69's contribution record](../contributions/history.md#issue-69)
separates DeepSeek's implementation from coordinator review and integration.

## Evidence

Recorded output — not rerun by this site build.

Captured on 2026-11-09 on macOS ARM64 with reviewed Temurin 25.0.5+8. After applying the reviewed worker patch to
the merged contribution-records revision, the coordinator ran the focused `BytecodeEvidenceTest` suite. It exited
0; the JUnit report contained 28 tests, zero failures, errors or skips. Gradle excerpt, with task setup omitted:

```text
BUILD SUCCESSFUL in 8s
7 actionable tasks: 7 executed
```

The integrated ordinary checks and documentation generation then exited 0. The reports contained 4,457 tests,
zero failures, errors or skips; all 22 generated files were current. This recording preceded the numbered demo.
Excerpt, with task setup omitted:

```text
docs: wrote the site, 97 files
BUILD SUCCESSFUL in 1m 41s
```

The coordinator read the exact worker patch and checked its saved log hashes. An earlier separate reviewer also
compiled and disassembled the synthetic fixture, confirming both switch methods' type-switch bootstrap and the
target class argument without executing the fixture classes. Final integrated review is recorded on pull request
#99. Only the named test class passing in the publishing build supplies this sheet's current-build evidence.

## Limits

Detection is unchanged. The committed test checks reported references; it does not itself inspect bootstrap
arguments. Custom bootstrap extraction remains deferred, and `enumSwitch` is unmeasured. This recording ran no
database or mutation tests. Passing tests establish the recorded limitation, not complete dependency detection.
