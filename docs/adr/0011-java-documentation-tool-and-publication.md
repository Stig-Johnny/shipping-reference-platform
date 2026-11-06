---
title: "ADR 0011: A Java documentation tool, a static site and a scanned publication"
description: "One Java tool generates the C4 view pages, the budgeted brain and a static documentation site, and fails on drift and on claims without evidence; the site is published on GitHub Pages from main after a fail-closed scan. Supersedes ADR 0010."
status: accepted
updated: 2026-11-06
date: 2026-11-06
decision-makers: repository owner
enforcedBy:
  - "java: :tools:docs:checkGenerated, part of check (BrainGeneratorTest, BrainCheckTest)"
  - "java: :tools:docs:site, part of check (SiteBuilderTest, SitePagesTest, SiteAccessibilityTest, SiteAssetsTest)"
  - "pages: the build job of .github/workflows/pages.yml scans the site before every upload (PublicationScannerTest, ScannedNamesTest, DocsToolTest, PagesWorkflowTest)"
plannedEnforcement:
  - "a check that a pull request changing a module's code or tests also changes a document that names the module"
---

# ADR 0011: A Java documentation tool, a static site and a scanned publication

Supersedes ADR 0010. Accepted on 2026-11-06, when the owner accepted the documentation tool's reviewed dependency set
in pull request #7.

## Context and Problem Statement

Agents start every task without memory. They need a short, current summary of the system, and they need to know
which capabilities are shipped and which are only planned. Hand-maintained summaries go stale and overclaim.

ADR 0010 answered this with documentation as code and a generated brain, built as a VitePress 1.6.4 site from a
`docs` npm workspace, with a separate brain generator, Gradle project `:tools:brain`, a required `docs-brain` check,
and a site kept private until an independent review. Before any of it was built, two things changed. A VitePress
build would have put an npm tree of several hundred packages on the review path of ADR 0007 for the documentation
alone. And the owner decided to publish the documentation. How is the documentation generated, checked and
published now?

## Decision Drivers

- Documentation changes in the same pull request as the behaviour it describes.
- A compact entry point for agents that never claims more than the evidence supports.
- Generated content that is reproducible byte for byte.
- Every third-party component needs a review before use (ADR 0007), so fewer components are better.
- A public site must not publish secrets, confidential names, local paths, private addresses or links that only
  the repository's members can open.
- Every page keeps apart what is accepted design, what is planned implementation and what the evidence shows.

## Considered Options

1. One Java tool, `:tools:docs`, that generates the C4 view pages, a budgeted brain and a static site and checks
   them in CI, and a publication on GitHub Pages from `main` after a fail-closed scan of the built site.
2. ADR 0010 as decided: a VitePress 1.6.4 site from a `docs` npm workspace, a separate `:tools:brain`, and a private
   site.
3. An external wiki.
4. Hand-written agent instructions only.

## Decision Outcome

Chosen option 1. Option 2 would have put an npm build tree of several hundred packages on the review path for the
documentation alone; the Java tool reuses libraries that were already reviewed for the contract kit and adds one,
SnakeYAML Engine 3.2.

- Every Markdown file under `docs/` starts with YAML frontmatter: `title`, `description`, `status` (`draft`,
  `proposed`, `accepted`, `rejected`, `deprecated`, `superseded`) and `updated` (ISO date). Module documents also
  carry `module`, `moduleStatus` (equal to the component's `contract.status`) and `riskLevel`. Any pull request that
  edits a document updates its `updated` date.
- A pull request that changes a module's production code or tests also changes at least one document whose
  frontmatter `module` (or list `modules`) names that module, or the pull request body gives a reason under
  "Docs: none needed because".
- One Java program, Gradle project `:tools:docs` (Java 25, Error Prone and NullAway, `@NullMarked` packages),
  generates the documentation. It uses only what its reviews allow, under their conditions: Structurizr 6.2.4 to read
  `architecture/workspace.dsl` through its own parser factory with all ten features off, and only the Mermaid
  exporter; CommonMark 0.29.0 with GFM tables, escaping HTML and sanitising URLs; SnakeYAML Engine 3.2 through one
  factory with strict settings, for YAML and for JSON. It does not use Jackson directly.
- It writes committed Markdown: `docs/architecture/`, one page and one `.mmd` file per C4 view, from Structurizr's
  Mermaid exporter through a validating adapter (plain-text labels, the contract's REQUIRED and PERMITTED edges as
  line patterns and words, no HTML, `click`, directives or node metadata); `BRAIN.md`, the root router, at most
  12 KiB UTF-8; and `docs/brain/<module>.md`, at most 8 KiB each. They link to the full documents instead of copying
  them. Every output contains `Generated—do not hand-edit`, and generated Markdown under `docs/` starts with the
  required frontmatter. Their `updated` date is the newest `updated` date of the hand-written documents, so that two
  generations are byte-identical.
- `:tools:docs:checkGenerated`, part of `check`, regenerates in memory and fails on any byte difference, and on any
  file or link in `docs/architecture/` or `docs/brain/` that the tool did not generate: both directories are
  generated-only, whole. Before generating, the tool fails on missing or invalid frontmatter; an ADR
  that breaks the numbering, lacks a required field or section, or names in `enforcedBy` a test class that does not
  exist; a mention of an ADR that does not exist; a module document whose `moduleStatus` disagrees with the model;
  a link to a file or an anchor that does not exist; a capability marked `shipped` or `partial` whose evidence tests
  do not exist or did not pass in the same build (their JUnit reports), or a planned capability that names
  evidence; a shipped control whose check is not a workflow job; and an exceeded budget. A planned capability never
  gets a green badge because its package exists.
- `./gradlew :tools:docs:generate` rewrites the generated files. Both tasks run after the tool's tests, whose reports
  are the evidence of its own control, `control.brain-check`.
- `:tools:docs:site`, also part of `check`, builds the documentation site into `tools/docs/build/site/` from the
  same sources, after the same checks. It is static HTML with one stylesheet and one script of the tool's own, and
  relative links, so it works under any base path. Its sections are Start, Model, Modules, Decisions, API, Trust and
  Brain, with a reading path for a COO and one for a Java lead. Every page states three things apart: the accepted
  design, the planned implementation and the verified evidence; a test counts as evidence only when its report
  passed in the build that made the site. The API pages come from `contracts/shipping.openapi.yaml`, which is
  offered for download, and the Trust section shows the allowlist with every entry, rejected ones included, below
  the current acceptance and its reviewed-set digest, which the build recomputes and must match. The Trust section
  also publishes the repository's LICENSE and NOTICE, complete and unchanged, beside the third-party notices, as the
  Apache License 2.0 asks of a redistribution.
- A link in a document to a file the site does not publish becomes plain text, and a link to the repository on
  GitHub is not published. A link to a missing file or anchor fails the build. Every page names the full commit SHA
  it was built from, GITHUB_SHA in CI and the checked-out commit locally, and the build fails without one. Two builds
  of the same commit are byte-identical.
- The four view pages, and the flow page with the dynamic view of its flow, draw their diagram with Mermaid 11.17.2,
  the reviewed, unmodified `dist/mermaid.min.js`, vendored at `tools/docs/vendor/mermaid/11.17.2/`. The build copies
  it only after its SHA-256 matches the allowlist, and serves it from the site's own origin. The site's script
  initialises Mermaid once, with `securityLevel: 'strict'` and no HTML labels, and draws only the definitions the
  adapter generated. Those pages carry a Content-Security-Policy that allows scripts only from the site; every other
  page allows no third-party script at all and does not load Mermaid. Each diagram has its elements and relationships
  as tables, so nothing is only in the picture. Hand-written Mermaid in a document, such as a flow's sequence, is
  shown as closed, unrendered source.
- Before the site is published, `:tools:docs:scanSite` reads it as data, every file, and fails on: private keys and
  the token shapes of GitHub, AWS, Slack, Discord, Google, Stripe, npm and JWTs; a bearer credential, a URL with a
  password and a quoted secret assignment; a link to the repository on GitHub; an absolute path into a macOS, Linux
  or Windows home or temporary directory; a private, link-local or Tailscale address and a Tailscale or private-zone
  host name; an HTML comment; and any term of a dictionary. The dictionary is never committed: it comes from the
  environment variable `PUBLIC_SCAN_DICTIONARY`, one term per line, matched in any case, also inside words, after
  HTML entities are decoded and the text is normalised with NFKC (and without tags, zero-width characters or
  dash variants). Without it, or with a malformed one (an entry that holds a control character, or no valid
  entry), the scan fails closed. It prints the rule and the file only, never the value; file and
  directory names are scanned with the same rules, and a path whose name fails is replaced by a placeholder that
  holds a short SHA-256 of the path. Its exceptions, in `tools/docs/publication-scan-exceptions.json`, are exact
  (the file, the rule and the SHA-256 of the value) and exist only for the documented public demo tokens; one that
  no longer matches fails the scan. Its summary binds the result to the digest of the site's files and to the
  source revision.
- The owner decided on 2026-11-06 to publish the site on GitHub Pages, at
  <https://stig-johnny.github.io/shipping-reference-platform/>. The documentation site is public. The repository
  itself is planned to become public (ADR 0008); repository links will be enabled after that change.
- `.github/workflows/pages.yml` runs for `main` only, one run at a time. Its build job runs in the `github-pages`
  environment, whose secret is the dictionary, and may only read the repository. On a commit that is already on
  `main`, it stages the reviewed JDK and runs the allowlist check, its trusted bootstrap, then verifies the Gradle
  wrapper, runs `:tools:docs:check` and `:tools:docs:site`, and then `:tools:docs:scanSite` in a step of its own, the
  only one that receives the dictionary. It re-checks Mermaid's SHA-256 in the site, refuses symbolic links, hard
  links and hidden files there, and only then uploads the site directory. The deploy job has one step,
  `actions/deploy-pages`, and the workflow's only write permissions, `pages: write` and `id-token: write`. Every
  action runs at the full SHA its review approved; `actions/configure-pages` is not used, because the site's links
  are relative.

### What this replaces in other records

- ADR 0010 as a whole; it is superseded by this record.
- ADR 0007's decision names `docs` among the npm workspaces that share the root `package-lock.json`. The
  documentation is no npm workspace: only `apps/shipping-web` and `packages/shipping-client` are.
- ADR 0008's decision lists `docs-brain` among the required checks. There is no `docs-brain` job: the documentation
  and brain checks run inside the required `java` check.
- The Confirmation sections of ADR 0001 and ADR 0002 name `docs-brain` and `brain --check`.
  `:tools:docs:checkGenerated`, part of `check` in the required `java` check, does that work, as their `enforcedBy`
  records.

### Consequences

- Good, because the brain cannot silently claim a capability the tests do not show.
- Good, because agents get a bounded, current context instead of a growing instruction file.
- Good, because the documentation adds one reviewed library (SnakeYAML Engine) instead of an npm tree.
- Good, because readers without access to the repository get the same documents, diagrams drawn from the model and
  the evidence, with no build tool or account.
- Bad, because every documentation change runs the generator; a stale `updated` date or a missing link fails CI.
- Bad, because a summary within a byte budget leaves things out; the brain links to the full documents.
- Bad, because the generator is this repository's own code, with its own tests to keep up.
- Bad, because the site serves one third-party file, Mermaid's bundle of about 3.5 MB, which runs in readers'
  browsers. Its review conditions are met in code and tested, and its notices are published with the site.
- Bad, because a public site can disclose what the scan does not know: a paraphrase, a name missing from the
  dictionary, a detail that only a reader recognises. A person still reads every public page that changes.
- Neutral, because evidence is read from JUnit reports, so only Java tests count as passing so far; TypeScript test
  names stay acceptance targets until the web build writes reports.

### Confirmation

- `java` runs `./gradlew check`, which runs the tool's tests, `:tools:docs:checkGenerated` and `:tools:docs:site`.
- `BrainGeneratorTest` (determinism, ordering, budgets, the do-not-edit line and frontmatter, links that resolve) and
  `BrainCheckTest` (negative fixtures: a shipped capability without evidence, evidence that failed, was skipped or
  has no report, a planned capability with evidence, a broken link and a broken anchor, an edited and a stale
  generated file, an exceeded budget, a stale `moduleStatus`, an ADR without a required section, a gap in the ADR
  numbers, an unknown `enforcedBy` test, a mention of a missing ADR, invalid frontmatter) in `:tools:docs`.
- `SiteBuilderTest` (byte-identical builds, a clean output directory, relative links that all resolve, unpublished
  files as plain text, no link to the repository on GitHub, a broken link or anchor and a changed Mermaid file fail
  the build), `SitePagesTest` (the sections and their content, the status statements, the diagram template, the
  allowlist with its acceptance, the API, the brain and the notices), `SiteAccessibilityTest` (landmarks, the skip
  link, one H1 and the page order, labelled controls and scroll regions, `aria-current`, the copy status) and
  `SiteAssetsTest` (the design tokens in both themes, the layout, focus and motion rules, and the Mermaid
  conditions on the script).
- `PublicationScannerTest` (a negative fixture for every rule, the dictionary through entities, NFKC, tags and
  zero-width characters, failing closed without a dictionary or with a malformed one, exact and stale exceptions,
  links and unreadable files, the digest and the revision in the summary, and no value or term in any output),
  `ScannedNamesTest` (terms and token shapes in file and directory names, withheld from every output) and
  `DocsToolTest` (the scan command end to end, and exit codes for errors, `StackOverflowError` included, and for
  timeouts).
- `PagesWorkflowTest`: who may run the publishing workflow, each job's permissions, the dictionary in the scan step
  only, the build job's order (check before use, build, scan, re-check, guard, upload) and the four reviewed actions
  by full SHA. After the first deployment, a person checks that its log shows the OIDC token masked, as the
  deploy-pages review requires.
- The review conditions on the tool's libraries: `SafeYamlTest`, `RestrictedStructurizrTest`, `WorkspacesTest`,
  `MermaidValidatorTest`, `MermaidAdapterTest`, `MarkdownRendererTest`, `SiteUrlSanitizerTest` and
  `ReviewConditionsTest`.

## More Information

- [ADR 0010](0010-documentation-as-code-with-a-generated-brain.md), which this record supersedes.
- Capabilities: `docs/capabilities/capabilities.yaml`; gaps: `docs/gaps.md`.
- The tool's dependency reviews: `security/reviews/maven--docs-tool-scope-extension.md`,
  `maven--snakeyaml-engine--3.2.md` and `maven--docs-tool-test-scope-extension.md`; Mermaid's:
  `security/reviews/vendored-js--mermaid--11.17.2.md`; the Pages actions':
  `github-action--actions-upload-pages-artifact--5.0.0.md`, `github-action--actions-upload-artifact--7.0.0.md` and
  `github-action--actions-deploy-pages--5.0.1.md`.
- The site: <https://stig-johnny.github.io/shipping-reference-platform/>.
