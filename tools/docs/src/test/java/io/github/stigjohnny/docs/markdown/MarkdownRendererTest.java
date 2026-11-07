package io.github.stigjohnny.docs.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.markdown.LinkDecision.Keep;
import io.github.stigjohnny.docs.markdown.LinkDecision.Reject;
import io.github.stigjohnny.docs.markdown.LinkDecision.Rewrite;
import io.github.stigjohnny.docs.markdown.LinkDecision.Unlink;
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

    private static final String DECK = """
            # Milepæl

            ## Første lysbilde

            Første avsnitt med `kode`.

            - Et punkt

            ### Notater

            - Et notat som aldri vises på lysbildet

            ## Andre lysbilde

            ```text
            ## Not a slide
            ```

            ## Vedlegg: tabell

            | a | b |
            |---|---|
            | 1 | 2 |
            """;

    @Test
    void aDeckIsGroupedBySlideOnTheParsedBlocksWithItsNotesApart() {
        DeckOutline deck = MarkdownRenderer.deck(DECK, "docs/demos/deck-x.md", "Notater");

        assertThat(deck.front()).extracting(MarkdownBlock::kind, MarkdownBlock::level, MarkdownBlock::text)
                .containsExactly(tuple(MarkdownBlock.Kind.HEADING, 1, "Milepæl"));
        // A level-2 line inside a fence is text: three slides, not four.
        assertThat(deck.slides()).extracting(SlideOutline::title).containsExactly("Første lysbilde", "Andre lysbilde",
                "Vedlegg: tabell");
        assertThat(deck.slides()).extracting(SlideOutline::id).containsExactly("første-lysbilde", "andre-lysbilde",
                "vedlegg-tabell");
        SlideOutline first = deck.slides().getFirst();
        assertThat(first.content()).extracting(MarkdownBlock::text).containsExactly("Første avsnitt med kode.",
                "Et punkt");
        assertThat(first.content().getFirst().spans()).containsExactly("kode");
        assertThat(first.hasNotes()).isTrue();
        assertThat(first.notes()).extracting(MarkdownBlock::text).containsExactly("Et notat som aldri vises på "
                + "lysbildet");
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

                Tekst.

                ## Notater

                Tekst.

                #### Notater

                ### Notes

                ### Notater

                - Notat

                ### Notater
                """, "docs/demos/deck-x.md", "Notater");

        // A level-2 "Notater" is a slide, a level-4 one and an English "Notes" stay in the slide's content.
        assertThat(deck.slides()).extracting(SlideOutline::title).containsExactly("A", "Notater");
        SlideOutline second = deck.slides().get(1);
        assertThat(second.content()).extracting(MarkdownBlock::kind, MarkdownBlock::level).containsExactly(
                tuple(MarkdownBlock.Kind.OTHER, 0),
                tuple(MarkdownBlock.Kind.HEADING, 4),
                tuple(MarkdownBlock.Kind.HEADING, 3));
        assertThat(second.hasNotes()).isTrue();
        assertThat(second.notes()).extracting(MarkdownBlock::kind, MarkdownBlock::text).containsExactly(
                tuple(MarkdownBlock.Kind.OTHER, "Notat"),
                tuple(MarkdownBlock.Kind.HEADING, "Notater"));
    }

    @Test
    void aDeckRendersEachSlideAndItsNotesApart() {
        RenderedDeck deck = MarkdownRenderer.renderDeck(DECK, "docs/demos/deck-x.md", KEEP_ALL, "Notater",
                (number, slide) -> "Tabell " + number + " i " + slide);

        assertThat(deck.slides()).extracting(RenderedSlide::id).containsExactly("første-lysbilde", "andre-lysbilde",
                "vedlegg-tabell");
        RenderedSlide first = deck.slides().getFirst();
        assertThat(first.titleHtml()).isEqualTo("Første lysbilde");
        assertThat(first.html()).isEqualTo("""
                <p>Første avsnitt med <code>kode</code>.</p>
                <ul>
                <li>Et punkt</li>
                </ul>
                """);
        assertThat(first.notesHtml()).isEqualTo("<ul>\n<li>Et notat som aldri vises på lysbildet</li>\n</ul>\n");
        assertThat(first.html()).doesNotContain("Notater", "Et notat");
        assertThat(deck.slides().get(1).html()).contains("<pre><code class=\"language-text\">## Not a slide\n");
        assertThat(deck.slides().get(1).notesHtml()).isEmpty();
        assertThat(deck.slides().get(2).html()).contains("<div class=\"table-scroll\" role=\"region\" tabindex=\"0\" "
                + "aria-label=\"Tabell 1 i Vedlegg: tabell\">\n<table>");
        // The page shows the title; the deck's own blocks never include it.
        assertThat(String.join("", deck.slides().stream().map(RenderedSlide::html).toList())).doesNotContain("<h1",
                "Milepæl");
    }

    @Test
    void aDecksInPageLinksMustNameASlideAndItsRawHtmlIsEscaped() {
        String linked = DECK.replace("- Et punkt", "- Et punkt, se [andre](#andre-lysbilde) <b>fet</b>");

        assertThat(MarkdownRenderer.renderDeck(linked, "docs/demos/deck-x.md", KEEP_ALL, "Notater",
                (number, slide) -> "t").slides().getFirst().html())
                .contains("<a rel=\"nofollow\" href=\"#andre-lysbilde\">andre</a>", "&lt;b&gt;fet&lt;/b&gt;")
                .doesNotContain("<b>");
        // A notes heading has an ID on GitHub, but the deck's pages have only the slides' anchors.
        assertThatThrownBy(() -> MarkdownRenderer.renderDeck(DECK.replace("- Et punkt", "- [notater](#notater)"),
                "docs/demos/deck-x.md", KEEP_ALL, "Notater", (number, slide) -> "t"))
                .isInstanceOf(DocsException.class).hasMessageContaining("#notater");
        assertThatThrownBy(() -> MarkdownRenderer.renderDeck(DECK.replace("- Et punkt", "- ![bilde](bilde.png)"),
                "docs/demos/deck-x.md", KEEP_ALL, "Notater", (number, slide) -> "t"))
                .isInstanceOf(DocsException.class).hasMessageContaining("bilde.png");
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
}
