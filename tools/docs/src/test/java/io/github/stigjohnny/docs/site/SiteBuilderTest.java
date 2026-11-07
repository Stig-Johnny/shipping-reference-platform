package io.github.stigjohnny.docs.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.generate.Brains;
import io.github.stigjohnny.docs.repo.Allowlist;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.BuiltSite;
import io.github.stigjohnny.docs.testing.Decks;
import io.github.stigjohnny.docs.testing.Demos;
import io.github.stigjohnny.docs.testing.Fixture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The site as a whole: deterministic, self-contained, relatively linked, and under the Content Security Policies of
 * security/reviews/vendored-js--mermaid--11.17.2.md.
 */
class SiteBuilderTest {

    static final String MERMAID_CSP = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; "
            + "script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; "
            + "connect-src 'none'; worker-src 'none'; base-uri 'none'; form-action 'none'\">";
    static final String STRICT_CSP = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; "
            + "script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'none'; "
            + "worker-src 'none'; base-uri 'none'; form-action 'none'\">";
    static final Set<String> DIAGRAM_PAGES = Set.of("index.html", "model/system-context.html",
            "model/containers.html", "model/api-components.html", "model/quote-book-label.html",
            "model/flow-quote-book-label.html", "model/use-cases.html");
    private static final String MERMAID_SHA256 = "581ed7d74bd9048d0e3a91363927d72ef22942d7722546b27f7cc29e35390eb8";

    @TempDir
    Path dir;

    private Fixture fixture;

    @BeforeEach
    void copyTheRepository() {
        fixture = Fixture.copy(dir).passingEvidence().withoutDemos();
    }

    @Test
    void twoCleanBuildsAreByteIdentical() {
        BuiltSite first = build("first");
        BuiltSite second = build("second");

        assertThat(second.files().keySet()).isEqualTo(first.files().keySet());
        first.files().forEach((path, bytes) -> assertThat(second.files().get(path)).as(path).isEqualTo(bytes));
    }

    @Test
    void theSiteHasThePagesOfEverySection() {
        BuiltSite site = build("site");

        assertThat(site.pages()).contains("index.html", "404.html", "model/index.html", "modules/index.html",
                "modules/quotes.html", "modules/web-workbench.html", "decisions/index.html",
                "decisions/0010-documentation-as-code-with-a-generated-brain.html",
                "decisions/0011-java-documentation-tool-and-publication.html", "api/index.html",
                "api/createQuote.html", "api/schemas.html", "trust/index.html", "trust/allowlist.html",
                "trust/risk-levels.html", "trust/gaps.html", "trust/license.html", "trust/notice.html",
                "trust/third-party-notices.html", "brain/index.html",
                "model/flow-quote-book-label.html", "start/repository.html", "demos/index.html",
                "demos/how-to.html").containsAll(DIAGRAM_PAGES);
        assertThat(site.files()).containsKeys("assets/site.css", "assets/site.js", SiteBuilder.MERMAID,
                "downloads/BRAIN.md", "downloads/shipping.openapi.yaml");
    }

    @Test
    void everyPageCarriesExactlyOneOfTheTwoPolicies() {
        BuiltSite site = build("site");

        for (String page : site.pages()) {
            String html = site.text(page);
            String expected = DIAGRAM_PAGES.contains(page) ? MERMAID_CSP : STRICT_CSP;
            assertThat(html).as(page).contains(expected);
            assertThat(count(html, "http-equiv=\"Content-Security-Policy\"")).as(page).isEqualTo(1);
        }
    }

    @Test
    void mermaidIsLoadedOnDiagramPagesOnlyAndNeverOnTrustPages() {
        BuiltSite site = build("site");

        Pattern loadsMermaid = Pattern.compile("<script src=\"[^\"]*mermaid\\.min\\.js\"");
        for (String page : site.pages()) {
            boolean loads = loadsMermaid.matcher(site.text(page)).find();
            assertThat(loads).as(page).isEqualTo(DIAGRAM_PAGES.contains(page));
        }
        assertThat(site.pages()).filteredOn(p -> p.startsWith("trust/"))
                .allSatisfy(p -> assertThat(site.text(p)).doesNotContain("class=\"diagram-source\""));
    }

    @Test
    void noPageHasAnInlineScriptAnEventHandlerOrAStyleAttribute() {
        BuiltSite site = build("site");
        Pattern inlineScript = Pattern.compile("<script(?![^>]*\\ssrc=)[^>]*>");
        Pattern handler = Pattern.compile("\\son[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);

        for (String page : site.pages()) {
            String html = site.text(page);
            assertThat(inlineScript.matcher(html).find()).as(page).isFalse();
            assertThat(handler.matcher(html).find()).as(page).isFalse();
            assertThat(html).as(page).doesNotContain(" style=", "<style", "javascript:");
        }
    }

    @Test
    void everyLinkIsRelativeAndResolvesToAFileAndAnAnchor() {
        assertEveryLinkResolves(build("site"));
    }

    @Test
    void aSiteWithDemonstrationsIsByteIdenticalAndEveryLinkResolves() {
        for (String kind : List.of("gate", "visual", "docs", "build", "feature")) {
            fixture.write(Demos.path(kind), Demos.text(kind));
        }

        BuiltSite first = build("first");
        BuiltSite second = build("second");

        assertThat(first.pages()).contains("demos/index.html", "demos/how-to.html", "demos/42-gate.html",
                "demos/43-visual.html", "demos/44-docs.html", "demos/45-build.html", "demos/46-feature.html");
        first.files().forEach((path, bytes) -> assertThat(second.files().get(path)).as(path).isEqualTo(bytes));
        assertEveryLinkResolves(first);
    }

    @Test
    void aSiteWithADeckIsByteIdenticalEveryLinkResolvesAndItsPagesKeepThePolicy() {
        fixture.write(Decks.PATH, Decks.SAMPLE).write(Demos.path("gate"), Demos.GATE);

        BuiltSite first = build("first");
        BuiltSite second = build("second");

        assertThat(first.pages()).contains("demos/deck-milestone.html", "demos/deck-milestone-notes.html");
        assertThat(second.files().keySet()).isEqualTo(first.files().keySet());
        first.files().forEach((path, bytes) -> assertThat(second.files().get(path)).as(path).isEqualTo(bytes));
        assertEveryLinkResolves(first);
        // Every slide's anchor is linked from the slide index, and every note links back to its slide.
        assertThat(first.urls("demos/deck-milestone.html")).contains("#what-is-built", "#state-today",
                "#one-change-the-gate-rejects", "#appendix-tools", "#site-deck-index", "deck-milestone-notes.html");
        assertThat(first.urls("demos/deck-milestone-notes.html")).contains("deck-milestone.html#what-is-built",
                "deck-milestone.html#appendix-tools");
        Pattern inlineScript = Pattern.compile("<script(?![^>]*\\ssrc=)[^>]*>");
        for (String page : List.of("demos/deck-milestone.html", "demos/deck-milestone-notes.html")) {
            String html = first.text(page);
            assertThat(html).as(page).contains(STRICT_CSP).doesNotContain(" style=", "<style", "javascript:", "<img",
                    "<iframe", "<video", "<object", "mermaid.min.js");
            assertThat(inlineScript.matcher(html).find()).as(page).isFalse();
            assertThat(count(html, "http-equiv=\"Content-Security-Policy\"")).as(page).isEqualTo(1);
            // No identifier twice: each slide's anchor, each heading's and the page's own are distinct.
            assertThat(count(html, " id=\"")).as(page).isEqualTo(first.ids(page).size());
        }
    }

    @Test
    void aBrokenAnchorIntoTheDeckFailsTheBuild() {
        fixture.write(Decks.PATH, Decks.SAMPLE)
                .edit("docs/risk-levels.md", text -> text + "\nSee [the notes](demos/deck-milestone.md#notes).\n");

        // The notes heading has an ID on GitHub, but the deck's pages have only the slides' anchors.
        assertThatThrownBy(() -> build("site")).isInstanceOf(DocsException.class).hasMessageContaining("#notes");
    }

    private static void assertEveryLinkResolves(BuiltSite site) {
        for (String page : site.pages()) {
            if (page.equals("404.html")) {
                continue;
            }
            for (String url : site.urls(page)) {
                if (url.startsWith("https://") || url.startsWith("http://") || url.startsWith("mailto:")) {
                    assertThat(url).as(page).doesNotContainIgnoringCase(
                            "github.com/Stig-Johnny/shipping-reference-platform");
                    continue;
                }
                if (url.equals("data:,")) {
                    continue;
                }
                assertThat(url).as(page + " links " + url).doesNotStartWith("/").doesNotContain("://");
                String target = BuiltSite.resolve(page, url);
                assertThat(site.files()).as(page + " links " + url).containsKey(target);
                if (url.contains("#")) {
                    assertThat(site.ids(target)).as(page + " links " + url)
                            .contains(url.substring(url.indexOf('#') + 1));
                }
            }
        }
    }

    @Test
    void theNotFoundPageReachesTheSiteFromAnyPathUnderTheBasePath() {
        BuiltSite site = build("site");

        assertThat(site.urls("404.html")).isNotEmpty().allSatisfy(url ->
                assertThat(url).satisfiesAnyOf(
                        u -> assertThat(u).startsWith("/shipping-reference-platform/"),
                        u -> assertThat(u).startsWith("#"),
                        u -> assertThat(u).isEqualTo("data:,")));
        assertThat(site.text("404.html")).contains("<h1>Page not found</h1>");
    }

    @Test
    void theMermaidBundleAndTheDownloadsAreByteIdentical() {
        BuiltSite site = build("site");

        assertThat(Allowlist.sha256(site.bytes(SiteBuilder.MERMAID))).isEqualTo(MERMAID_SHA256);
        assertThat(site.bytes("downloads/shipping.openapi.yaml"))
                .isEqualTo(fixture.read("contracts/shipping.openapi.yaml").getBytes(StandardCharsets.UTF_8));
        assertThat(site.text("downloads/BRAIN.md")).isEqualTo(Brains.root(sources()));
    }

    @Test
    void anAlteredMermaidBundleFailsTheBuild() {
        fixture.edit("tools/docs/vendor/mermaid/11.17.2/mermaid.min.js", text -> text + "\n");

        assertThatThrownBy(() -> build("site")).isInstanceOf(DocsException.class)
                .hasMessageContaining("mermaid.min.js").hasMessageContaining("SHA-256");
    }

    @Test
    void noFileContainsTheBuildMachinesPaths() {
        BuiltSite site = build("site");
        String root = fixture.root().toString();

        site.files().forEach((path, bytes) -> {
            if (!path.endsWith(".js")) {
                assertThat(new String(bytes, StandardCharsets.UTF_8)).as(path).doesNotContain(root, dir.toString());
            }
        });
    }

    @Test
    void linksToSourcesBecomeSiteLinksAndUnpublishedTargetsBecomePlainText() {
        fixture.edit("docs/risk-levels.md", text -> text + """

                See [the gaps](gaps.md#7-documentation), [the tenants module](modules/tenants.md),
                [the contract](../contracts/shipping.openapi.yaml), [the checker](../tools/policy/PolicyCheck.java),
                [the workflow](../.github/workflows/java.yml) and
                [pull request 7](https://github.com/Stig-Johnny/shipping-reference-platform/pull/7).
                """);
        fixture.write("tools/policy/PolicyCheck.java", "class PolicyCheck {}\n");

        String html = build("site").text("trust/risk-levels.html");

        assertThat(html).contains("<a rel=\"nofollow\" href=\"gaps.html#7-documentation\">the gaps</a>",
                "<a rel=\"nofollow\" href=\"../modules/tenants.html\">the tenants module</a>",
                "<a rel=\"nofollow\" href=\"../downloads/shipping.openapi.yaml\">the contract</a>",
                ", the checker,", "the workflow and", "pull request 7.")
                .doesNotContain("PolicyCheck.java\"", "java.yml\"", "github.com/Stig-Johnny");
    }

    @Test
    void aStaleFileFromAnEarlierBuildIsRemoved() throws IOException {
        build("site");
        Files.writeString(dir.resolve("site/stale.html"), "<p>old</p>");

        assertThat(build("site").files()).doesNotContainKey("stale.html");
    }

    @Test
    void aDirectoryThatHoldsNoEarlierBuildIsNeverEmptied() throws IOException {
        Path notes = dir.resolve("notes/notes.txt");
        Files.createDirectories(notes.getParent());
        Files.writeString(notes, "mine");

        assertThatThrownBy(() -> build("notes")).isInstanceOf(DocsException.class)
                .hasMessageContaining("refusing to empty it");
        assertThat(notes).hasContent("mine");
    }

    @Test
    void aBrokenLinkFailsTheBuild() {
        fixture.edit("docs/risk-levels.md", text -> text + "\nSee [nowhere](no-such-page.md).\n");

        assertThatThrownBy(() -> build("site")).isInstanceOf(DocsException.class)
                .hasMessageContaining("no-such-page.md");
    }

    @Test
    void theFlowDiagramsDescriptionCountsItsSequenceFromTheSource() {
        fixture.edit("docs/flows/quote-book-label.md", text -> text.replaceFirst("(?m)^.*Web->>Shipments: GET "
                + "/api/v1/shipments/\\{id}/label\\n", ""));

        assertThat(build("site").text("model/flow-quote-book-label.html"))
                .contains("The step tables below follow the separate 28-step sequence.");
    }

    @Test
    void aBrokenAnchorFailsTheBuild() {
        fixture.edit("docs/risk-levels.md", text -> text + "\nSee [nowhere](gaps.md#no-such-section).\n");

        assertThatThrownBy(() -> build("site")).isInstanceOf(DocsException.class)
                .hasMessageContaining("no-such-section");
    }

    @Test
    void everyPageNamesItsSourceRevisionAsPlainText() {
        BuiltSite site = build("site", "fedcba9876543210fedcba9876543210fedcba98");

        assertThat(site.pages()).allSatisfy(page -> assertThat(site.text(page)).as(page)
                .contains("Source revision: <code>fedcba9876543210fedcba9876543210fedcba98</code>.")
                .doesNotContain("Built locally"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a local build", "test-revision", "0123456", "0123456789ABCDEF0123456789ABCDEF01234567",
        "0123456789abcdef0123456789abcdef0123456"})
    void aRevisionThatIsNotAFullCommitShaFailsTheBuild(String revision) {
        assertThatThrownBy(() -> build("site", revision)).isInstanceOf(DocsException.class)
                .hasMessageContaining("the source revision must be a full commit SHA");
    }

    private BuiltSite build(String name) {
        return build(name, "0123456789abcdef0123456789abcdef01234567");
    }

    private BuiltSite build(String name, String revision) {
        Path out = dir.resolve(name);
        SiteBuilder.build(sources(), out, revision);
        return BuiltSite.read(out);
    }

    private Sources sources() {
        return Sources.read(fixture.root(), List.of(fixture.reports()));
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }
}
