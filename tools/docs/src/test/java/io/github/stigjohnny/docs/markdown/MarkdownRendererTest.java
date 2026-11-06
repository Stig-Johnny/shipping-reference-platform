package io.github.stigjohnny.docs.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(html).contains("<details class=\"diagram-source-block\">", "a--&gt;b")
                .doesNotContain("class=\"diagram-source\"");
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
