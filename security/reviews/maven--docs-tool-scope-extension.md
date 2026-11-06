---
component: "Scope extension to tools/docs: org.commonmark:commonmark, org.commonmark:commonmark-ext-gfm-tables, com.structurizr:structurizr-dsl/-core/-export (with the reviewed com.structurizr family and its locked transitives), com.fasterxml.jackson.core:jackson-databind/-core/-annotations"
kind: maven
version: "Exact, as already reviewed: commonmark and commonmark-ext-gfm-tables 0.29.0; com.structurizr 6.2.4; jackson-databind and jackson-core 2.22.3, jackson-annotations 2.22"
verdict: APPROVE-WITH-CONDITIONS
scope: "Build time only: compileClasspath, runtimeClasspath, testCompileClasspath and testRuntimeClasspath of the docs generator project (tools/docs), in CI and on local build hosts. The generated site ships only HTML, CSS, Markdown, .mmd text and the separately reviewed Mermaid bundle; none of these JARs is published. Existing approvals (javac plugin path; :core library classpaths) are unchanged"
conditions: ["Same versions and jar SHA-256 values as the existing reviews and their allowlist entries; :tools:docs gets its own lock and verification entries and resolves nothing outside them; guava resolves to 33.7.2-jre there too", "CommonMark: HtmlRenderer is built once with escapeHtml(true) and sanitizeUrls(true) and a UrlSanitizer that allows only http, https, mailto and relative or fragment URLs (the default also allows data:); a destination the sanitizer would empty fails the build instead of rendering href=\"\"", "CommonMark: only TablesExtension is used; no other commonmark-ext-* artifact, no custom node renderer that calls raw(), no AttributeProvider fed from document text", "Link rewriting (.md to site pages, rejection of private-repository links and unresolved anchors) is done on the parsed Link/Image nodes before rendering, not on HTML strings", "Markdown, DSL and JSON inputs come only from code-owned repository paths; the docs task has a timeout and any exception or Error (including StackOverflowError) fails the build, because CommonMark 0.29.0 lacks the nesting, block-parser and table-cell limits added in 0.30.0", "Structurizr: the DSL is parsed through the same factory as :core, with all ten Features disabled and HttpClient.allow(...) never called; every condition of the structurizr-dsl 6.2.4 review applies to the docs model (workspace.dsl) as written", "Structurizr export: only MermaidDiagramExporter is used, and its raw output is never published; the adapter's output must pass the validator in the Mermaid 11.17.2 review (no HTML, click, directives, node metadata or math); the mermaid.icons property is never set", "Jackson: reads only repository-owned JSON and writes JSON; no default typing (activateDefaultTyping/enableDefaultTyping), no class-name @JsonTypeInfo, no additional Jackson module or dataformat (jackson-dataformat-yaml would bring org.yaml:snakeyaml) without its own review", "Negative fixtures prove: raw <script> and <img onerror> in Markdown and table cells are escaped; javascript:, JaVaScRiPt: and data: links and images fail the build; a DSL with !include, !script or ${NAME} is rejected", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Scope extension: CommonMark, Structurizr and Jackson in the docs tool

**Not a new review.** This record extends the scope of these existing reviews:
- `maven--javac-plugin-path-transitives--ep-2.51.0-nullaway-0.14.3.md`, for commonmark and gfm-tables 0.29.0;
- `maven--structurizr-dsl--6.2.4.md` and `maven--structurizr-dsl-transitives--6.2.4.md`, for the com.structurizr
  family, Jackson and the other locked transitives.

Their provenance, maintenance, licence and vulnerability findings stand, and no new allowlist entries are created.

**Method.** Read on 2026-11-06 through WebFetch, at tag `commonmark-parent-0.29.0` and at Structurizr tag `v6.2.4`.
Nothing was run. `CM/` = `https://raw.githubusercontent.com/commonmark/commonmark-java/commonmark-parent-0.29.0/commonmark/src/main/java/org/commonmark/`.

## 1. Verdict
**APPROVE-WITH-CONDITIONS, build time only.**

The new exposure is not the JVM: the docs tool is our code, running on repository inputs. The new exposure is **the
public HTML these libraries produce.** Two defaults would put markup or script on the site:
- CommonMark passes raw HTML through and does not sanitise URLs by default.
- Structurizr's Mermaid exporter emits HTML labels and raw `click` URLs.

Both are fixable with the conditions above.

## 2. What changes and why
`docs-site-design.md` §1–2 asks `tools/docs` to do three things with these libraries:
- render Markdown (ADRs, module specifications, the brain) with CommonMark and GFM tables;
- parse `workspace.dsl` once and export four views through Structurizr's Mermaid exporter;
- use Jackson.

Today CommonMark is approved only on the javac plugin path, and Structurizr only on `:core`'s library classpaths, where
the exporter was "unused".

## 3. Findings that drive the conditions
**CommonMark 0.29.0**
- **Renderer defaults.** `escapeHtml = false` and `sanitizeUrls = false` (`CM/renderer/html/HtmlRenderer.java`,
  builder defaults). With `escapeHtml(true)`, `HtmlBlock` and `HtmlInline` are written through `html.text(...)`,
  escaped, not dropped (`CM/renderer/html/CoreHtmlNodeRenderer.java`).
- **URL sanitising** (`CM/renderer/html/DefaultUrlSanitizer.java`).
  - Allowed schemes: `http`, `https`, `mailto` **and `data`**. The scheme is lower-cased, so `JaVaScRiPt:` is rejected.
  - A rejected URL becomes `""`.
  - Image `src` is sanitised too, and links get `rel="nofollow"`.
- **Attribute escaping.** Every attribute value (`href`, `src`, `title`, `alt`, the code `class`) is escaped by
  `HtmlWriter.tag` (`CM/renderer/html/HtmlWriter.java`).
- **No limits in 0.29.0.** `maxOpenBlockParsers` defaults to unlimited; there is no `maxInlineNesting` and no table-cell
  cap ([Parser.java](https://raw.githubusercontent.com/commonmark/commonmark-java/commonmark-parent-0.29.0/commonmark/src/main/java/org/commonmark/parser/Parser.java)).
- **Fixed later, not in our version.** 0.30.0 (2026-08-06) fixed stack overflows and quadratic runtime on pathological
  input, and added those limits; 0.31.0 fixed two more quadratic cases
  ([CHANGELOG](https://github.com/commonmark/commonmark-java/blob/main/CHANGELOG.md)). These are not advisories (deps.dev
  `advisoryKeys: []`; [OSV](https://osv.dev/list?ecosystem=Maven&q=commonmark) has none). With repository-owned input,
  the effect is a failed or slow build, not a site compromise.
- **Dependencies.** commonmark has no runtime dependencies; gfm-tables depends only on commonmark.

**Structurizr 6.2.4 `MermaidDiagramExporter`**
([source](https://raw.githubusercontent.com/structurizr/structurizr/v6.2.4/structurizr-export/src/main/java/com/structurizr/export/mermaid/MermaidDiagramExporter.java)):
- **HTML in labels.** Every node label is wrapped in HTML, for example `<div style='font-weight: bold'>`, and so is
  every relationship label (`<div>%s</div>`). Sequence diagrams add `<br />`.
- **Icons.** With `mermaid.icons=true`, the exporter emits `<img src='…'>` from the style icon URL.
- **Links.** `click <id> <url> "<url>"` is written with the raw element URL.
- **Styles.** `style` lines carry colours that are not escaped.
- **Escaping.** Apart from `;` → `#59;` in sequence-diagram relationships, nothing is escaped (`"`, `<`, `>`, `&`, `'`).
  `IndentingWriter` only turns newlines into `\n`.
- **Rendering mode.** Structurizr's docs say the output needs `"securityLevel": "loose"`
  ([docs](https://docs.structurizr.com/export/mermaid)), and the design forbids that.
- **No I/O.** The exporter does no network, file or reflection work. `structurizr-export` depends only on
  `structurizr-core`.
- **PlantUML exporters.** They live in the same JAR. The earlier Structurizr application review (2026.09.19) found that
  they fetch icon URLs with `ImageIO.read`, bypassing the allowlist. This was not re-checked at 6.2.4, so condition 7
  allows only the Mermaid exporter.

**Jackson 2.22.3**
- Default typing is off by default (transitives review §7).
- The transitives review's condition — no untrusted JSON, no default typing — fits the docs tool, which reads only
  `security/allowlist.json` and other repository files.

## 4. Execution privileges
- Everything runs inside the docs-tool JVM at build time, with that JVM's rights.
- The libraries add no network or process access when the conditions hold: Structurizr Features are off, and nothing
  is ever `allow`ed.
- **The public site is the trust boundary.** Whatever these libraries emit is served to anonymous visitors, so the
  escaping conditions (2, 3, 7, 9) carry the weight.

## 5. Residual risk
1. **No parser limits in CommonMark 0.29.0.** A hostile Markdown file already needs repository write access. An upgrade
   to ≥ 0.31.0 would add the limits, but it is a new review on both paths.
2. **The adapter is hand-written.** Its correctness is enforced by the validator and the negative fixtures, not by
   Structurizr.
3. **GitHub renders the same `.mmd` fences in its own sandbox.** That rendering is outside this record.

## 6. Not verified
- The docs tool does not exist yet, so none of the conditions has been tested.
- CommonMark line positions were counted by hand.
- The PlantUML icon fetch was not re-checked at 6.2.4.
- That `:tools:docs` resolves exactly the reviewed versions: its lockfile does not exist yet.
- OSV POST was not run; deps.dev and the existing reviews stand in.
