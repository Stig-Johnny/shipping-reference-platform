package io.github.stigjohnny.docs.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.markdown.LinkDecision.Keep;
import io.github.stigjohnny.docs.markdown.LinkDecision.Reject;
import io.github.stigjohnny.docs.markdown.LinkDecision.Rewrite;
import io.github.stigjohnny.docs.markdown.LinkDecision.Unlink;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The CommonMark conditions of security/reviews/maven--docs-tool-scope-extension.md as negative fixtures: raw HTML is
 * escaped, unsafe destinations fail the build, links are rewritten on the parsed nodes, and nothing pathological
 * crashes or hangs the build.
 */
class MarkdownRendererTest {

    private static final LinkResolver KEEP_ALL = destination -> new Keep();

    @Test
    void rawHtmlIsEscapedInTextAndInTableCells() {
        String markdown = """
                <script>alert(1)</script>

                Text <img src=x onerror=alert(1)> here.

                | a | b |
                |---|---|
                | <script>alert(2)</script> | <img src=y onerror=alert(3)> |
                """;

        String html = MarkdownRenderer.render(markdown, "docs/x.md", KEEP_ALL, false).html();

        assertThat(html).doesNotContain("<script", "<img", "onerror=alert(1)>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;", "&lt;img src=x onerror=alert(1)&gt;",
                        "<td>&lt;script&gt;alert(2)&lt;/script&gt;</td>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[x](javascript:alert(1))", "[x](JaVaScRiPt:alert(1))", "[x](data:text/html,hi)",
            "![x](data:image/png;base64,AAAA)", "<javascript:alert(1)>", "![x](https://example.com/a.png)",
            "![x](picture.png)", "[x](vbscript:x)", "[x](//evil.example/x)", "[x](file:///etc/passwd)",
            "| a |\n|---|\n| [x](javascript:alert(1)) |", "[x]\n\n[x]: javascript:alert(1)"})
    void anUnsafeLinkOrAnyImageFailsTheBuild(String markdown) {
        assertThatThrownBy(() -> MarkdownRenderer.render(markdown, "docs/x.md", KEEP_ALL, false))
                .isInstanceOf(DocsException.class).hasMessageContaining("docs/x.md");
    }

    @Test
    void safeLinksAreKeptOrRewrittenOnTheParsedNodes() {
        LinkResolver links = destination -> destination.startsWith("../") ? new Rewrite("../trust/gaps.html#a")
                : new Keep();

        String html = MarkdownRenderer.render("[spec](https://spec.openapis.org/oas/v3.1.2.html), "
                + "[mail](mailto:owner@example.com), [gaps](../gaps.md#a)", "docs/x.md", links, false).html();

        assertThat(html).contains("href=\"https://spec.openapis.org/oas/v3.1.2.html\"",
                "href=\"mailto:owner@example.com\"", "href=\"../trust/gaps.html#a\"");
    }

    @Test
    void anUnlinkedLinkBecomesPlainText() {
        LinkResolver links = destination -> destination.startsWith("https://github.com/")
                ? new Unlink("the private repository") : new Unlink("");

        String html = MarkdownRenderer.render("See [the listener](../../apps/x/Listener.java) and "
                + "<https://github.com/example/private/pull/7>.", "docs/x.md", links, false).html();

        assertThat(html).doesNotContain("<a ", "github.com").contains("See the listener and the private repository.");
    }

    @Test
    void aRejectedLinkFailsTheBuildWithItsReason() {
        assertThatThrownBy(() -> MarkdownRenderer.render("[x](missing.md)", "docs/x.md",
                destination -> new Reject("missing.md does not exist"), false))
                .isInstanceOf(DocsException.class).hasMessageContaining("docs/x.md")
                .hasMessageContaining("missing.md does not exist");
    }

    @Test
    void inPageAnchorsMustExist() {
        String ok = "## Section A\n\nSee [a](#section-a).\n";

        assertThat(MarkdownRenderer.render(ok, "docs/x.md", KEEP_ALL, false).html())
                .contains("href=\"#section-a\"");
        assertThatThrownBy(() -> MarkdownRenderer.render("## Section A\n\n[b](#section-b)\n", "docs/x.md", KEEP_ALL,
                false)).isInstanceOf(DocsException.class).hasMessageContaining("#section-b");
    }

    @Test
    void headingsGetGitHubStyleIds() {
        String markdown = """
                # Title

                ## 7. Documentation

                ## Relationships (from `architecture/workspace.dsl`)

                ## Notes

                ### Notes
                """;

        assertThat(MarkdownRenderer.headings(markdown, "docs/x.md")).extracting(Section::id)
                .containsExactly("title", "7-documentation", "relationships-from-architectureworkspacedsl", "notes",
                        "notes-1");
        assertThat(MarkdownRenderer.render(markdown, "docs/x.md", KEEP_ALL, false).html())
                .contains("<h2 id=\"7-documentation\">7. Documentation</h2>",
                        "<h2 id=\"relationships-from-architectureworkspacedsl\">Relationships (from "
                                + "<code>architecture/workspace.dsl</code>)</h2>",
                        "<h3 id=\"notes-1\">Notes</h3>");
    }

    @Test
    void headingIdsNeverTakeTheSiteChromesIds() {
        assertThat(MarkdownRenderer.headings("## Site main\n", "docs/x.md")).extracting(Section::id)
                .containsExactly("doc-site-main");
    }

    @Test
    void theTitleIsLeftOutWhenThePageShowsIt() {
        RenderedMarkdown rendered = MarkdownRenderer.render("# Title\n\nBody.\n\n## Part\n", "docs/x.md", KEEP_ALL,
                true);

        assertThat(rendered.html()).doesNotContain("<h1").contains("<p>Body.</p>", "<h2 id=\"part\">Part</h2>");
        assertThat(rendered.headings()).extracting(Section::text).containsExactly("Part");
    }

    @Test
    void tablesScrollInsideLabelledRegions() {
        String html = MarkdownRenderer.render("## Steps\n\n| a | b |\n|---|---|\n| 1 | 2 |\n", "docs/x.md",
                KEEP_ALL, false).html();

        assertThat(html).contains("<div class=\"table-scroll\" role=\"region\" tabindex=\"0\" "
                + "aria-label=\"Table 1 in Steps, scrolls sideways\">\n<table>");
    }

    @Test
    void mermaidFencesAreShownAsTheirSourceAndNeverRendered() {
        String html = MarkdownRenderer.render("```mermaid\nflowchart LR\n    a-->b\n```\n", "docs/x.md", KEEP_ALL,
                false).html();

        assertThat(html).contains("<details class=\"diagram-source-block\">\n<summary>Workflow source \u2014 not "
                + "rendered</summary>", "a--&gt;b").doesNotContain("class=\"diagram-source\"", "<details class="
                + "\"diagram-source-block\" open");
        assertThat(MarkdownRenderer.render("```mermaid\nsequenceDiagram\n    a->>b: x\n```\n", "docs/x.md",
                KEEP_ALL, false).html()).contains("<summary>Sequence source \u2014 not rendered</summary>");
    }

    private static final String UNCHECKED = " <span class=\"badge badge-none diagram-unchecked\">Unchecked design"
            + "</span></summary>";
    private static final String LABEL = "**Unchecked design.** No test compares this diagram with the code.\n";

    @Test
    void aMermaidFenceAtAnyDepthIsShownAsItsClosedSource() {
        String html = MarkdownRenderer.render("""
                - Item

                  ```mermaid
                  sequenceDiagram
                  ```

                > ```mermaid
                > stateDiagram
                > ```
                """, "docs/x.md", KEEP_ALL, false).html();

        assertThat(html).contains("<li>\n<p>Item</p>\n<details class=\"diagram-source-block\">\n<summary>Sequence "
                + "source \u2014 not rendered</summary>\n<p class=\"diagram-note\">GitHub draws this diagram from its "
                + "source; the text on this page says the same.</p>\n<pre><code class=\"language-mermaid\">"
                + "sequenceDiagram\n</code></pre>\n</details>", "<blockquote>\n<details class=\"diagram-source-block\">"
                + "\n<summary>State diagram source \u2014 not rendered</summary>");
        assertThat(count(html, "<details class=\"diagram-source-block\">")).isEqualTo(2);
        assertThat(MarkdownRenderer.render("```java\nint x;\n```\n", "docs/x.md", KEEP_ALL, false).html())
                .isEqualTo("<pre><code class=\"language-java\">int x;\n</code></pre>\n");
    }

    @Test
    void aLabelledSequenceOrStateDiagramShowsTheUncheckedLabelInCurrentProseOnly() {
        String markdown = LABEL + "```mermaid\nsequenceDiagram\n    a->>b: x\n```\n\n- " + LABEL + "\n  ```mermaid\n"
                + "  stateDiagram-v2\n  ```\n";

        String html = labelled(markdown);

        assertThat(html).contains("<p><strong>Unchecked design.</strong> No test compares this diagram with the "
                + "code.</p>", "<summary>Sequence source \u2014 not rendered" + UNCHECKED,
                "<summary>State diagram source \u2014 not rendered" + UNCHECKED);
        assertThat(count(html, "diagram-unchecked")).isEqualTo(2);
        assertThat(MarkdownRenderer.render(markdown, "docs/x.md", KEEP_ALL, false).html())
                .doesNotContain("diagram-unchecked").contains("<summary>Sequence source \u2014 not rendered</summary>");
    }

    @Test
    void aDiagramWithoutTheLabelRightBeforeItOrAFlowchartGetsNoLabel() {
        assertThat(labelled("```mermaid\nsequenceDiagram\n```\n")).doesNotContain("diagram-unchecked");
        assertThat(labelled(LABEL + "\nText.\n\n```mermaid\nsequenceDiagram\n```\n")).doesNotContain("diagram-"
                + "unchecked");
        assertThat(labelled(LABEL + "```mermaid\nflowchart LR\n    a --> b\n```\n")).doesNotContain("diagram-"
                + "unchecked").contains("<summary>Workflow source \u2014 not rendered</summary>");
        assertThat(labelled("**Unchecked design**. Text.\n```mermaid\nstateDiagram\n```\n")).doesNotContain("diagram-"
                + "unchecked");
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }

    private static final String DECK = """
            # Milestone

            ## First slide

            First paragraph with `code`.

            - A point

            ### Notes

            - A note that the slide never shows

            ## Second slide

            ```text
            ## Not a slide
            ```

            ## Appendix: table

            | a | b |
            |---|---|
            | 1 | 2 |
            """;

    @Test
    void aDeckIsGroupedBySlideOnTheParsedBlocksWithItsNotesApart() {
        DeckOutline deck = MarkdownRenderer.deck(DECK, "docs/demos/deck-x.md", "Notes");

        assertThat(deck.front()).extracting(MarkdownBlock::kind, MarkdownBlock::level, MarkdownBlock::text)
                .containsExactly(tuple(MarkdownBlock.Kind.HEADING, 1, "Milestone"));
        // A level-2 line inside a fence is text: three slides, not four.
        assertThat(deck.slides()).extracting(SlideOutline::title).containsExactly("First slide", "Second slide",
                "Appendix: table");
        assertThat(deck.slides()).extracting(SlideOutline::id).containsExactly("first-slide", "second-slide",
                "appendix-table");
        SlideOutline first = deck.slides().getFirst();
        assertThat(first.content()).extracting(MarkdownBlock::text).containsExactly("First paragraph with code.",
                "A point");
        assertThat(first.content().getFirst().spans()).containsExactly("code");
        assertThat(first.hasNotes()).isTrue();
        assertThat(first.notes()).extracting(MarkdownBlock::text).containsExactly("A note that the slide never shows");
        SlideOutline second = deck.slides().get(1);
        assertThat(second.hasNotes()).isFalse();
        assertThat(second.notes()).isEmpty();
        assertThat(second.content()).singleElement().satisfies(block -> {
            assertThat(block.kind()).isEqualTo(MarkdownBlock.Kind.FENCED_CODE);
            assertThat(block.info()).isEqualTo("text");
            assertThat(block.text()).isEqualTo("## Not a slide\n");
        });
    }

    @Test
    void onlyALevelThreeHeadingWithTheNotesWordStartsTheNotesAndASecondOneIsANotesBlock() {
        DeckOutline deck = MarkdownRenderer.deck("""
                # T

                ## A

                Text.

                ## Notes

                Text.

                #### Notes

                ### Speaker notes

                ### Notes

                - A note

                ### Notes
                """, "docs/demos/deck-x.md", "Notes");

        // A level-2 "Notes" is a slide; a level-4 one and other words stay in the slide's content.
        assertThat(deck.slides()).extracting(SlideOutline::title).containsExactly("A", "Notes");
        SlideOutline second = deck.slides().get(1);
        assertThat(second.content()).extracting(MarkdownBlock::kind, MarkdownBlock::level).containsExactly(
                tuple(MarkdownBlock.Kind.OTHER, 0),
                tuple(MarkdownBlock.Kind.HEADING, 4),
                tuple(MarkdownBlock.Kind.HEADING, 3));
        assertThat(second.hasNotes()).isTrue();
        assertThat(second.notes()).extracting(MarkdownBlock::kind, MarkdownBlock::text).containsExactly(
                tuple(MarkdownBlock.Kind.OTHER, "A note"),
                tuple(MarkdownBlock.Kind.HEADING, "Notes"));
    }

    @Test
    void aDeckRendersEachSlideAndItsNotesApart() {
        RenderedDeck deck = MarkdownRenderer.renderDeck(DECK, "docs/demos/deck-x.md", KEEP_ALL, "Notes");

        assertThat(deck.slides()).extracting(RenderedSlide::id).containsExactly("first-slide", "second-slide",
                "appendix-table");
        RenderedSlide first = deck.slides().getFirst();
        assertThat(first.titleHtml()).isEqualTo("First slide");
        assertThat(first.html()).isEqualTo("""
                <p>First paragraph with <code>code</code>.</p>
                <ul>
                <li>A point</li>
                </ul>
                """);
        assertThat(first.notesHtml()).isEqualTo("<ul>\n<li>A note that the slide never shows</li>\n</ul>\n");
        assertThat(first.html()).doesNotContain("Notes", "A note");
        assertThat(deck.slides().get(1).html()).contains("<pre><code class=\"language-text\">## Not a slide\n");
        assertThat(deck.slides().get(1).notesHtml()).isEmpty();
        // A slide's tables are named as a document's are, by their number and the slide's title.
        assertThat(deck.slides().get(2).html()).contains("<div class=\"table-scroll\" role=\"region\" tabindex=\"0\" "
                + "aria-label=\"Table 1 in Appendix: table, scrolls sideways\">\n<table>");
        // The page shows the title; the deck's own blocks never include it.
        assertThat(String.join("", deck.slides().stream().map(RenderedSlide::html).toList())).doesNotContain("<h1",
                "Milestone");
    }

    @Test
    void aSlidesAnchorIsTakenFromItsSourceBeforeItsLinksAreRewritten() {
        // The site shows the repository's address as "the repository on GitHub". Were a slide's anchor taken after its
        // links are rewritten, these two slides would share one, which the deck's check, reading the source, never
        // sees.
        String deck = """
                # T

                ## <https://github.com/Stig-Johnny/shipping-reference-platform>

                Text.

                ## The repository on GitHub

                Text.
                """;
        LinkResolver links = destination -> new Unlink("the repository on GitHub");

        RenderedDeck rendered = MarkdownRenderer.renderDeck(deck, "docs/demos/deck-x.md", links, "Notes");

        List<String> outline = MarkdownRenderer.deck(deck, "docs/demos/deck-x.md", "Notes").slides().stream()
                .map(SlideOutline::id).toList();
        assertThat(rendered.slides()).extracting(RenderedSlide::id).doesNotHaveDuplicates()
                .containsExactly("httpsgithubcomstig-johnnyshipping-reference-platform", "the-repository-on-github")
                .containsExactlyElementsOf(outline);
        // What a slide shows is its heading as rewritten.
        assertThat(rendered.slides()).extracting(RenderedSlide::title, RenderedSlide::titleHtml).containsExactly(
                tuple("the repository on GitHub", "the repository on GitHub"),
                tuple("The repository on GitHub", "The repository on GitHub"));
    }

    @Test
    void aDecksInPageLinksMustNameASlideAndItsRawHtmlIsEscaped() {
        String linked = DECK.replace("- A point", "- A point, see [the second](#second-slide) <b>bold</b>");

        assertThat(MarkdownRenderer.renderDeck(linked, "docs/demos/deck-x.md", KEEP_ALL, "Notes").slides().getFirst()
                .html()).contains("<a rel=\"nofollow\" href=\"#second-slide\">the second</a>", "&lt;b&gt;bold&lt;/b&gt;")
                .doesNotContain("<b>");
        // A notes heading has an ID on GitHub, but the deck's pages have only the slides' anchors.
        assertThatThrownBy(() -> MarkdownRenderer.renderDeck(DECK.replace("- A point", "- [the notes](#notes)"),
                "docs/demos/deck-x.md", KEEP_ALL, "Notes"))
                .isInstanceOf(DocsException.class).hasMessageContaining("#notes");
        assertThatThrownBy(() -> MarkdownRenderer.renderDeck(DECK.replace("- A point", "- ![a picture](picture.png)"),
                "docs/demos/deck-x.md", KEEP_ALL, "Notes"))
                .isInstanceOf(DocsException.class).hasMessageContaining("picture.png");
    }

    @Test
    void deeplyNestedInputFailsInsteadOfCrashing() {
        String emphasis = "*".repeat(20_000) + "x" + "*".repeat(20_000);
        String quotes = "> ".repeat(10_000) + "x";

        assertThatThrownBy(() -> MarkdownRenderer.render(emphasis, "docs/deep.md", KEEP_ALL, false))
                .isInstanceOf(DocsException.class).hasMessageContaining("docs/deep.md");
        assertThatThrownBy(() -> MarkdownRenderer.render(quotes, "docs/deep.md", KEEP_ALL, false))
                .isInstanceOf(DocsException.class).hasMessageContaining("docs/deep.md");
    }

    @Test
    void inlineCodeIsReadWithTheLineItStartsOnAndNeverFromCodeBlocks() {
        String markdown = """
                # Title

                A `FirstTest` and ``SecondTest`` on line three.

                | Name | Test |
                |---|---|
                | one | `TableTest` |

                ```text
                `FencedTest`
                ```

                    `IndentedTest`

                A span over `two
                lines` ends here.
                """;

        assertThat(MarkdownRenderer.inlineCode(markdown, "docs/x.md")).containsExactly(
                new InlineCode(3, "FirstTest"), new InlineCode(3, "SecondTest"),
                new InlineCode(7, "TableTest"), new InlineCode(15, "two lines"));
    }

    // Reference markers (ADR 0023): the site shows a label, GitHub the marker as written.

    private static final String PLANNED_LABEL =
            "&nbsp;<span class=\"badge badge-planned reference-label\">planned</span>";
    private static final String EXAMPLE_LABEL =
            "&nbsp;<span class=\"badge badge-neutral reference-label\">example</span>";
    private static final String ABSENT_LABEL = "&nbsp;<span class=\"badge badge-none reference-label\">absent</span>";

    private static String labelled(String markdown) {
        return MarkdownRenderer.render(markdown, "docs/x.md", KEEP_ALL, false, ReferenceMarkers.LABELLED).html();
    }

    @Test
    void aPlannedTestIsShownAsItsNameAndAPlannedLabelWithThePunctuationAfterIt() {
        String html = labelled("Proven by `planned:test:CrossTenantAccessTest`, planned, and "
                + "(`planned:test:TenantScopeTest`).");

        assertThat(html).isEqualTo("<p>Proven by <code>CrossTenantAccessTest</code>" + PLANNED_LABEL
                + ", planned, and (<code>TenantScopeTest</code>" + PLANNED_LABEL + ").</p>\n");
    }

    @Test
    void anExampleIsShownAsItsEscapedPlaceholderAndAnExampleLabel() {
        String html = labelled("See `illustrative:test:<Module>ArchitectureTest` and "
                + "`illustrative:test:<script>alert(1)</script>Test`.");

        assertThat(html).isEqualTo("<p>See <code>&lt;Module&gt;ArchitectureTest</code>" + EXAMPLE_LABEL
                + " and <code>&lt;script&gt;alert(1)&lt;/script&gt;Test</code>" + EXAMPLE_LABEL + ".</p>\n");
    }

    @Test
    void aCapabilityOrControlIdLosesOnlyItsPrefixAndGetsNoLabel() {
        String html = labelled("`cap:tenants.cross-tenant-denial` and `cap:control.inline-references` are named.");

        assertThat(html).isEqualTo("<p><code>tenants.cross-tenant-denial</code> and "
                + "<code>control.inline-references</code> are named.</p>\n");
    }

    @Test
    void aPathMarkerIsShownAsItsPathAsWrittenAndItsLabel() {
        String html = labelled("Planned: `planned:path:policy/rulesets.json`, `planned:path:./e2e/` and "
                + "`planned:path:apps/x/src/main/java/io/example/audit/**`; absent: `absent:path:tools/web-imports/`; "
                + "an example: `illustrative:path:docs/adr/<NNNN>-<script>.md`.");

        assertThat(html).isEqualTo("<p>Planned: <code>policy/rulesets.json</code>" + PLANNED_LABEL + ", <code>./e2e/</code>"
                + PLANNED_LABEL + " and <code>apps/x/src/main/java/io/example/audit/**</code>" + PLANNED_LABEL
                + "; absent: <code>tools/web-imports/</code>" + ABSENT_LABEL + "; an example: "
                + "<code>docs/adr/&lt;NNNN&gt;-&lt;script&gt;.md</code>" + EXAMPLE_LABEL + ".</p>\n");
    }

    @Test
    void aPathWithoutAMarkerStaysAsWrittenWithItsAnchor() {
        assertThat(labelled("`/architecture/`, `./architecture/self.dsl` and `docs/modules/<module>.md`")).isEqualTo(
                "<p><code>/architecture/</code>, <code>./architecture/self.dsl</code> and "
                        + "<code>docs/modules/&lt;module&gt;.md</code></p>\n");
    }

    @Test
    void anAbsentPathInAHeadingGivesItsLabelInWordsAndKeepsTheAnchorOfTheSource() {
        RenderedMarkdown rendered = MarkdownRenderer.render("## No `absent:path:tools/web-imports/` script\n",
                "docs/x.md", KEEP_ALL, false, ReferenceMarkers.LABELLED);

        assertThat(rendered.headings()).containsExactly(new Section(2, "No tools/web-imports/ (absent) script",
                "no-absentpathtoolsweb-imports-script"));
        assertThat(rendered.html()).isEqualTo("<h2 id=\"no-absentpathtoolsweb-imports-script\">No "
                + "<code>tools/web-imports/</code>" + ABSENT_LABEL + " script</h2>\n");
    }

    @Test
    void markersStayAsWrittenUnlessTheCallerAsksForLabels() {
        String markdown = "`planned:test:CrossTenantAccessTest`, `illustrative:test:<Name>Test`, "
                + "`absent:path:tools/web-imports/` and `cap:control.inline-references`.";
        String expected = "<p><code>planned:test:CrossTenantAccessTest</code>, "
                + "<code>illustrative:test:&lt;Name&gt;Test</code>, <code>absent:path:tools/web-imports/</code> and "
                + "<code>cap:control.inline-references</code>.</p>\n";

        assertThat(MarkdownRenderer.render(markdown, "docs/x.md", KEEP_ALL, false).html()).isEqualTo(expected);
        assertThat(MarkdownRenderer.render(markdown, "docs/x.md", KEEP_ALL, false, ReferenceMarkers.AS_WRITTEN)
                .html()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"planned:path:two words", "illustrative:path:docs/x.md", "absent:path:../x",
            "planned:path:**/x.md", "planned:path:", "absent:path:docs/<a<b>>.md", "planned:paths:docs/x.md",
            "planned:test:", "planned:test:notATestName",
            "planned:test:Two WordsTest", "illustrative:test:NoPlaceholderTest", "absent:test:GoneTest", "cap:",
            "cap:two words", "cap:Tenants.Upper-Case", "planned:Test:CrossTenantAccessTest", "ReferencesTest",
            "tenants.cross-tenant-denial"})
    void aSpanTheGrammarDoesNotDefineAsAMarkerStaysAsWritten(String span) {
        String html = labelled("A `" + span + "` here.");

        assertThat(html).isEqualTo("<p>A <code>" + span.replace("<", "&lt;").replace(">", "&gt;")
                + "</code> here.</p>\n");
    }

    @Test
    void labelsAreShownInHeadingsListsEmphasisLinksAndTablesWithTheAnchorsAndDestinationsOfTheSource() {
        String markdown = """
                ## Uses `planned:test:CrossTenantAccessTest`

                - An item `planned:test:TenantScopeTest`.
                - *Emphasis `planned:test:PrototypeFlagsTest`*
                - [A link `planned:test:AuditReplayIntegrationTest`](https://example.com/x)

                | ID | Test |
                |---|---|
                | T6 | `planned:test:CrossTenantAccessTest` |
                """;

        RenderedMarkdown rendered = MarkdownRenderer.render(markdown, "docs/x.md", KEEP_ALL, false,
                ReferenceMarkers.LABELLED);

        assertThat(rendered.headings()).containsExactly(
                new Section(2, "Uses CrossTenantAccessTest (planned)", "uses-plannedtestcrosstenantaccesstest"));
        assertThat(rendered.html()).contains(
                "<h2 id=\"uses-plannedtestcrosstenantaccesstest\">Uses <code>CrossTenantAccessTest</code>"
                        + PLANNED_LABEL + "</h2>",
                "<li>An item <code>TenantScopeTest</code>" + PLANNED_LABEL + ".</li>",
                "<li><em>Emphasis <code>PrototypeFlagsTest</code>" + PLANNED_LABEL + "</em></li>",
                "<li><a rel=\"nofollow\" href=\"https://example.com/x\">A link <code>AuditReplayIntegrationTest</code>"
                        + PLANNED_LABEL + "</a></li>",
                "<td><code>CrossTenantAccessTest</code>" + PLANNED_LABEL + "</td>")
                .doesNotContain("planned:test:", "\u0000");
        // The region's name is words, never markup; a table that holds a label keeps a readable width and scrolls.
        assertThat(rendered.html()).contains("<div class=\"table-scroll table-wide-references\" role=\"region\" "
                + "tabindex=\"0\" aria-label=\"Table 1 in Uses CrossTenantAccessTest (planned), scrolls sideways\">\n"
                + "<p class=\"table-hint\">Scroll horizontally for all columns.</p>\n<table>");
    }

    @Test
    void aTableWithoutALabelKeepsItsPlainRegion() {
        assertThat(labelled("| ID | Test |\n|---|---|\n| T1 | `ReferencesTest` |\n")).startsWith(
                "<div class=\"table-scroll\" role=\"region\" tabindex=\"0\" aria-label=\"Table 1, scrolls sideways\">\n"
                        + "<table>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"x \u0000planned\u0000 y", "x &#0;planned&#0; y", "x &#x0;example&#X0; y",
            "`\u0000planned\u0000`", "[a](https://example.com/ \"\u0000planned\u0000\")",
            "[a](https://example.com/&#0;planned&#0;)", "```\n\u0000planned\u0000\n```",
            "| a |\n|---|\n| \u0000example\u0000 |", "# \u0000planned\u0000", "<span>\u0000planned\u0000</span>"})
    void aNulOrAPlaceholderInTheSourceNeverBecomesALabel(String markdown) {
        String html = labelled(markdown);

        assertThat(html).doesNotContain("\u0000", "reference-label", "<span").contains("�");
    }

    @Test
    void aLeftoverPlaceholderFailsTheBuild() {
        assertThatThrownBy(() -> MarkdownRenderer.placeLabels("<p>x\u0000stray\u0000</p>", "docs/x.md"))
                .isInstanceOf(DocsException.class).hasMessageContaining("docs/x.md");
        assertThatThrownBy(() -> MarkdownRenderer.placeLabels("<p>x\u0000</p>", "docs/x.md"))
                .isInstanceOf(DocsException.class).hasMessageContaining("docs/x.md");
    }

    @Test
    void aDeckIsARecordAndKeepsItsMarkersAsWritten() {
        RenderedDeck deck = MarkdownRenderer.renderDeck(DECK.replace("- A point",
                "- A point for `planned:test:CrossTenantAccessTest` and `cap:control.inline-references`"),
                "docs/demos/deck-x.md", KEEP_ALL, "Notes");

        assertThat(deck.slides().getFirst().html()).contains("<code>planned:test:CrossTenantAccessTest</code>",
                "<code>cap:control.inline-references</code>").doesNotContain("reference-label");
    }

}
