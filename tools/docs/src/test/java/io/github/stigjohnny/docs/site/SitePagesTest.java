package io.github.stigjohnny.docs.site;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.generate.Brains;
import io.github.stigjohnny.docs.repo.Allowlist;
import io.github.stigjohnny.docs.repo.AllowlistEntry;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.BuiltSite;
import io.github.stigjohnny.docs.testing.Fixture;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The content each section must show: design section 3 of the site's plan, and the Trust and Brain pages. */
class SitePagesTest {

    @TempDir
    static Path dir;

    private static Sources sources;
    private static BuiltSite site;

    @BeforeAll
    static void buildTheSite() {
        Fixture fixture = Fixture.copy(dir).passingEvidence();
        sources = Sources.read(fixture.root(), List.of(fixture.reports()));
        SiteBuilder.build(sources, dir.resolve("site"), "test-revision");
        site = BuiltSite.read(dir.resolve("site"));
    }

    @Test
    void theStartPageStatesThePurposeTheStateTheDataAndTwoReadingPaths() {
        String start = site.text("index.html");

        assertThat(start).contains("<h1>Shipping reference platform</h1>", "A reference monorepo",
                "Specification only", "synthetic", "Reading path for a COO", "Reading path for a Java lead")
                .contains("href=\"model/flow-quote-book-label.html\"", "href=\"trust/risk-levels.html\"",
                        "href=\"trust/gaps.html\"", "href=\"model/api-components.html\"", "href=\"modules/index.html\"",
                        "href=\"api/index.html\"", "href=\"decisions/index.html\"", "href=\"trust/allowlist.html\"");
        assertThat(start.indexOf("Reading path for a COO")).isLessThan(start.indexOf("Reading path for a Java lead"));
    }

    @Test
    void everyPageSeparatesAcceptedDesignPlannedImplementationAndVerifiedEvidence() {
        for (String page : site.pages()) {
            if (page.equals("404.html")) {
                continue;
            }
            assertThat(site.text(page)).as(page).contains("Accepted design", "Planned implementation",
                    "Verified evidence");
        }
        assertThat(site.text("brain/index.html")).contains("BrainGeneratorTest", "BrainCheckTest")
                .contains("1 capability shipped (control.brain-check).");
        assertThat(site.text("modules/quotes.html")).contains("5 capabilities planned").contains("None yet");
    }

    @Test
    void eachViewPageHasItsPurposeThePanelTheLegendAndATextEquivalent() {
        String page = site.text("model/api-components.html");

        assertThat(page).contains("<pre class=\"diagram-source\" data-diagram=\"api-components\">flowchart LR")
                .contains("aria-pressed=\"false\"", "Enlarge diagram", "<figcaption", "REQUIRED")
                .contains("<caption>Elements of the view", "<caption>Relationships of the view")
                .contains("What this view cannot show");
        for (String module : List.of("Quotes", "Shipments", "Tenants", "Audit", "Orders", "Tracking", "Returns")) {
            assertThat(page).contains("<td>" + module + "</td>");
        }
        assertThat(page.indexOf("class=\"diagram-scroll\"")).isLessThan(page.indexOf("<figcaption"));
        assertThat(page.indexOf("<figcaption")).isLessThan(page.indexOf("<caption>Elements of the view"));
    }

    @Test
    void theRepositorysLicenceAndNoticeArePublishedUnchangedBesideTheThirdPartyNotices() {
        String licence = site.text("trust/license.html");
        String notice = site.text("trust/notice.html");

        assertThat(preformatted(licence)).isEqualTo(Sources.text(sources.repo(), "LICENSE"));
        assertThat(preformatted(notice)).isEqualTo(Sources.text(sources.repo(), "NOTICE"))
                .doesNotContain("Original project");
        assertThat(licence).contains("<span aria-current=\"page\">Repository licence</span>",
                "<h1>Repository licence</h1>");
        assertThat(notice).contains("<span aria-current=\"page\">Repository NOTICE</span>",
                "<h1>Repository NOTICE</h1>");
        assertThat(licence).contains("href=\"notice.html\"", "href=\"third-party-notices.html\"");
        assertThat(notice).contains("href=\"license.html\"", "href=\"third-party-notices.html\"");
        String thirdParty = site.text("trust/third-party-notices.html");
        assertThat(thirdParty).contains("href=\"license.html\"", "href=\"notice.html\"")
                .contains("<a rel=\"nofollow\" href=\"license.html\">LICENSE</a>");
        assertThat(site.text("start/repository.html")).contains("href=\"../trust/license.html\"",
                "href=\"../trust/notice.html\"");
        String index = site.text("trust/index.html");
        List<String> order = List.of("allowlist.html", "risk-levels.html", "gaps.html", "license.html",
                "notice.html", "third-party-notices.html");
        String navigation = index.substring(index.indexOf("<nav class=\"site-nav\""), index.indexOf("</nav>"));
        assertThat(order.stream().map(page -> navigation.indexOf("href=\"" + page + "\"")).toList())
                .doesNotContain(-1).isSorted();
        String cards = index.substring(index.indexOf("<div class=\"card-grid\">"));
        assertThat(order.stream().map(page -> cards.indexOf("href=\"" + page + "\"")).toList())
                .doesNotContain(-1).isSorted();
        assertThat(site.text("index.html")).contains("href=\"trust/license.html\"");
    }

    private static String preformatted(String page) {
        Matcher pre = Pattern.compile("<pre class=\"license\">\n(.*?)</pre>", Pattern.DOTALL).matcher(page);
        assertThat(pre.find()).isTrue();
        return pre.group(1).replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&amp;", "&");
    }

    @Test
    void aSupersededDecisionSaysSoInWordsAndLinksBothWays() {
        String old = site.text("decisions/0010-documentation-as-code-with-a-generated-brain.html");
        String successor = site.text("decisions/0011-java-documentation-tool-and-publication.html");
        String index = site.text("decisions/index.html");

        assertThat(old).contains("<span class=\"badge badge-neutral\">Superseded</span> Superseded by "
                + "<a href=\"0011-java-documentation-tool-and-publication.html\">ADR 0011</a>.");
        assertThat(successor).contains("<span class=\"badge badge-accepted\">Accepted</span> Decided 2026-11-06",
                "Supersedes <a href=\"0010-documentation-as-code-with-a-generated-brain.html\">ADR 0010</a>.");
        assertThat(index).containsPattern("0010: Documentation as code[^<]*</a></th>\\s*<td><span class=\"badge "
                + "badge-neutral\">Superseded</span>");
        int at0010 = successor.indexOf(">0010: Documentation as code with a generated brain");
        int at0011 = successor.indexOf(">0011: ");
        assertThat(at0010).isPositive().isLessThan(at0011);
        assertThat(successor.substring(at0010, at0011)).contains("<span class=\"badge badge-neutral\">Superseded"
                + "</span>");
        assertThat(site.text("assets/site.css")).doesNotContain("line-through", "opacity");
        assertThat(site.text("brain/index.html")).contains("ADR 0011: ");
    }

    @Test
    void theViewsSpecifyAndDoNotClaimVerifiedImplementation() {
        String containers = site.text("model/containers.html");
        String components = site.text("model/api-components.html");
        String flow = site.text("model/flow-quote-book-label.html");

        assertThat(containers).contains("<td>Planned; not covered by module conformance</td>",
                "<td>Role; implementation status not applicable</td>");
        assertThat(components).contains("The planned conformance check will require a static reference for REQUIRED "
                + "relationships. It is not implemented yet.", "A REQUIRED arrow specifies an obligation; a future "
                + "passing conformance check would establish a static reference, not runtime behaviour.");
        for (String page : List.of(containers, components, site.text("model/system-context.html"),
                site.text("model/quote-book-label.html"), flow)) {
            assertThat(page).doesNotContain("the build fails when it does not", "proves that a reference exists",
                    "no status: not a checked module", "Asserted by", "the tests that assert them");
        }
        assertThat(flow).contains(">Steps and planned acceptance tests</", "<th>Planned acceptance test</th>");
    }

    @Test
    void plannedElementsAreLabelledNotFaded() {
        String page = site.text("model/api-components.html");

        assertThat(page).contains("Quotes [Component] (planned)", "Planned");
        for (String path : site.pages()) {
            assertThat(site.text(path)).as(path).doesNotContainIgnoringCase("faded").doesNotContain("opacity");
        }
    }

    @Test
    void theAllowlistPageShowsTheAcceptanceAndEveryEntryWithTheRejectedOnes() {
        String page = site.text("trust/allowlist.html");
        Allowlist allowlist = sources.allowlist();

        assertThat(page).contains(allowlist.current().reviewedSetSha256(), "pull request #10",
                "2026-11-06T20:03:16.131Z", "pull request #7", "pull request #3", "entriesSha256",
                "Recomputed by this build", "Matches");
        assertThat(page.indexOf(allowlist.current().reviewedSetSha256()))
                .isLessThan(page.indexOf("<caption>All " + allowlist.entries().size() + " entries"));
        assertThat(page).contains("<caption>All " + allowlist.entries().size() + " entries");
        assertThat(count(page, "<tr class=\"entry")).isEqualTo(allowlist.entries().size());
        for (AllowlistEntry rejected : allowlist.entries().stream().filter(AllowlistEntry::rejected).toList()) {
            assertThat(page).contains(Html.escape(rejected.coordinate()));
        }
        assertThat(page).contains("Rejected", "Approved with conditions", "Expires");
    }

    @Test
    void theApiSectionHasEveryOperationTheSchemasTheErrorsAndTheDownload() {
        String index = site.text("api/index.html");

        for (String operation : List.of("getCurrentMembership", "createQuote", "getQuote", "bookShipment",
                "listShipments", "getShipment", "cancelShipment", "getShipmentLabel")) {
            assertThat(site.pages()).contains("api/" + operation + ".html");
            assertThat(index).contains("href=\"" + operation + ".html\"");
        }
        assertThat(index).contains("href=\"../downloads/shipping.openapi.yaml\"", "Errors (RFC 9457)",
                "no-offer-available");
        assertThat(site.text("api/createQuote.html")).contains("POST", "/api/v1/quotes", "QuoteRequest",
                "href=\"schemas.html#schema-quoterequest\"", "application/problem+json", "422");
        assertThat(site.text("api/schemas.html")).contains("id=\"schema-quote\"", "id=\"schema-problem\"");
    }

    @Test
    void theBrainPageHasTheTablesTheModulesAndTheAgentContext() {
        String page = site.text("brain/index.html");

        assertThat(page).contains("<caption>Capabilities", "<caption>Controls", "control.brain-check",
                "quotes.synthetic-tariff", "<details", "Copy agent context", "role=\"status\"",
                "href=\"../downloads/BRAIN.md\"");
        assertThat(page).contains("<pre id=\"agent-context\">" + Html.escape(Brains.root(sources)) + "</pre>");
        for (String module : List.of("quotes", "shipments", "tenants", "audit", "orders", "tracking", "returns")) {
            assertThat(page).contains("id=\"module-" + module + "\"");
        }
    }

    @Test
    void theNoticesCoverMermaidAndItsEmbeddedComponents() {
        String page = site.text("trust/third-party-notices.html");

        assertThat(page).contains("Mermaid 11.17.2", "MIT", "Copyright (c) 2014 - 2022 Knut Sveidqvist",
                "Permission is hereby granted", "DOMPurify 3.4.12", "Apache-2.0 OR MPL-2.0", "KaTeX 0.16.47",
                "khroma 2.1.0", "581ed7d74bd9048d0e3a91363927d72ef22942d7722546b27f7cc29e35390eb8");
    }

    @Test
    void theRiskPageListsThePolicysRules() {
        String page = site.text("trust/risk-levels.html");

        assertThat(page).contains("controls-tools", "tools/**", "pricing-quotes", "sql-change", "unmatched-path");
    }

    @Test
    void handWrittenMermaidIsShownAsClosedSourceAndNeverRendered() {
        String flow = site.text("model/flow-quote-book-label.html");
        String repository = site.text("start/repository.html");

        assertThat(flow).contains("<details class=\"diagram-source-block\">\n<summary>Sequence source \u2014 not "
                + "rendered</summary>", "sequenceDiagram");
        assertThat(count(flow, "class=\"diagram-source\"")).isEqualTo(1);
        assertThat(repository).contains("<details class=\"diagram-source-block\">\n<summary>Workflow source \u2014 "
                + "not rendered</summary>").doesNotContain("class=\"diagram-source\"", "mermaid.min.js\"");
        assertThat(site.text("modules/shipments.html")).contains("<summary>State diagram source \u2014 not rendered"
                + "</summary>").doesNotContain("class=\"diagram-source\"", "mermaid.min.js\"");
        assertThat(flow + repository).doesNotContain("<details class=\"diagram-source-block\" open");
    }

    @Test
    void theFlowPageDrawsTheGeneratedDynamicViewBeforeItsStepTables() {
        String flow = site.text("model/flow-quote-book-label.html");

        int heading = flow.indexOf("<h2 id=\"dynamic-view\">Dynamic view</h2>");
        int figure = flow.indexOf("<pre class=\"diagram-source\" data-diagram=\"quote-book-label\">flowchart LR");
        int source = flow.indexOf("<summary>Sequence source");
        int steps = flow.indexOf("<h2 id=\"steps-and-planned-acceptance-tests\">");
        assertThat(List.of(heading, figure, source, steps)).doesNotContain(-1).isSorted();
        assertThat(flow).contains("The step numbers in the tables below follow the sequence source",
                "<th>Step (sequence numbers)</th>", "its number in the view&#39;s relationships table");
        assertThat(flow).doesNotContain("<h2 id=\"sequence\">").contains("Enlarge diagram",
                "<script src=\"../assets/mermaid/11.17.2/mermaid.min.js\" defer></script>",
                "href=\"quote-book-label.html\"");
    }

    @Test
    void theRepositoryPageDescribesTheWorkflowInOrder() {
        String repository = site.text("start/repository.html");

        assertThat(repository).contains("<li>An issue states the change.</li>",
                "<li>The risk level decides: levels 0 to 2 merge; level 3 merges only after the owner approves the "
                        + "current head.</li>");
        assertThat(repository.indexOf("<li>An issue states the change.</li>"))
                .isGreaterThan(repository.indexOf("Workflow source"));
        assertThat(site.text("trust/third-party-notices.html")).contains("five diagram pages");
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }
}
