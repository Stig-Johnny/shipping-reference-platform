package io.github.stigjohnny.docs.site;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.generate.Brains;
import io.github.stigjohnny.docs.generate.GeneratedFiles;
import io.github.stigjohnny.docs.repo.Allowlist;
import io.github.stigjohnny.docs.repo.AllowlistEntry;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.repo.TestEvidence;
import io.github.stigjohnny.docs.testing.BuiltSite;
import io.github.stigjohnny.docs.testing.Fixture;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        SiteBuilder.build(sources, dir.resolve("site"), "0123456789abcdef0123456789abcdef01234567");
        site = BuiltSite.read(dir.resolve("site"));
    }

    @Test
    void theStartPageStatesThePurposeTheStateTheDataAndTwoReadingPaths() {
        String start = site.text("index.html");

        assertThat(start).contains("<h1>Shipping reference platform</h1>", "A reference monorepo",
                "<span class=\"badge badge-partial\">Partly built</span>", "synthetic", "Reading path for a COO",
                "Reading path for a Java lead").doesNotContain("Specification only", "No application code exists yet")
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
        assertThat(site.text("brain/index.html")).contains("BrainGeneratorTest", "BrainCheckTest");
        // quotes has passing tests for all five of its capabilities, shipments for two of its six and tenants for two
        // of its six.
        assertThat(site.text("modules/quotes.html")).contains("<span class=\"badge badge-partial\">Partly shipped"
                + "</span> 2 capabilities shipped, 3 capabilities partial.",
                "<span class=\"badge badge-shipped\">Passing</span> 8 evidence test classes passed");
        assertThat(site.text("modules/shipments.html")).contains("<span class=\"badge badge-partial\">Partly shipped"
                + "</span> 2 capabilities partial, 4 capabilities planned.",
                "<span class=\"badge badge-shipped\">Passing</span> 3 evidence test classes passed");
        assertThat(site.text("modules/tenants.html")).contains("<span class=\"badge badge-partial\">Partly shipped"
                + "</span> 2 capabilities partial, 4 capabilities planned.",
                "<span class=\"badge badge-shipped\">Passing</span> 3 evidence test classes passed");
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
        assertThat(components).contains("Where both modules are built, the checker in libs/c4-contract-kit fails when "
                + "the compiled classes have no static reference for it.", "The checker does not evaluate a line "
                + "that touches a planned module until both modules are built.", "A REQUIRED arrow specifies an "
                + "obligation; where both modules are built, a passing conformance check establishes a static "
                + "reference, not runtime behaviour.").doesNotContain("a future passing conformance check");
        for (String page : List.of(containers, components, site.text("model/system-context.html"),
                site.text("model/quote-book-label.html"), flow)) {
            assertThat(page).doesNotContain("the build fails when it does not", "proves that a reference exists",
                    "no status: not a checked module", "Asserted by", "the tests that assert them");
        }
        assertThat(flow).contains(">Steps and acceptance tests</", "<th>Acceptance tests and status</th>");
    }

    @Test
    void plannedElementsAreLabelledNotFaded() {
        String page = site.text("model/api-components.html");

        assertThat(page).contains("Audit [Component] (planned)", "Shipments [Component] (built)",
                "Quotes [Component] (built)", "Web app [Container] (planned)", "Database [Container] (planned)",
                "Planned");
        assertThat(site.text("model/containers.html")).contains("Web app [Container] (planned)",
                "Database [Container] (planned)").doesNotContain("API [Container] (planned)");
        for (String path : site.pages()) {
            assertThat(site.text(path)).as(path).doesNotContainIgnoringCase("faded").doesNotContain("opacity");
        }
    }

    @Test
    void theAllowlistPageShowsTheAcceptanceAndEveryEntryWithTheRejectedOnes() {
        String page = site.text("trust/allowlist.html");
        Allowlist allowlist = sources.allowlist();

        assertThat(page).contains(allowlist.current().reviewedSetSha256(), "pull request #73",
                "2026-11-07T23:45:39.135Z", "pull request #10", "2026-11-06T20:03:16.131Z",
                "pull request #7", "pull request #3", "entriesSha256",
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
        int steps = flow.indexOf("<h2 id=\"steps-and-acceptance-tests\">");
        assertThat(List.of(heading, figure, source, steps)).doesNotContain(-1).isSorted();
        assertThat(flow).contains("The step numbers in the tables below follow the sequence source",
                "<th>Step (sequence numbers)</th>", "its number in the view&#39;s relationships table");
        assertThat(flow).doesNotContain("<h2 id=\"sequence\">").contains("Enlarge diagram",
                "<script src=\"../assets/mermaid/11.17.2/mermaid.min.js\" defer></script>",
                "href=\"quote-book-label.html\"");
    }

    @Test
    void theStartPageSaysTheSiteAndTheRepositoryArePublicAndNamesTheRepositoryAsPlainText() {
        String start = site.text("index.html");
        String statement = "<p class=\"prose\">The documentation site and the repository are both public: the "
                + "repository is Stig-Johnny/" + "shipping-reference-platform on GitHub.</p>";

        assertThat(start).contains(statement).doesNotContain("planned to become public",
                "repository links will be enabled", "github.com/" + "Stig-Johnny");
        assertThat(site.text("start/repository.html")).contains("The README of the repository as committed")
                .doesNotContain("private repository");
    }

    @Test
    void theBrainOverviewMatchesItsTables() {
        String brain = site.text("brain/index.html");
        assertThat(sources.capabilities().specificationOnly()).as("some product capabilities have passing tests")
                .isFalse();

        assertThat(brain).contains("<span class=\"badge badge-partial\">Partly shipped</span> "
                + counted(sources.capabilities().capabilities(), "capability", "capabilities") + "; "
                + counted(sources.capabilities().controls(), "control", "controls") + ".");
        List<Capability> evidenced = sources.capabilities().all().stream()
                .filter(c -> c.evidence().stream().anyMatch(sources.evidence()::passed)).toList();
        if (evidenced.stream().allMatch(c -> c.control() && c.where().startsWith("tools/docs"))) {
            assertThat(brain).contains("Documentation controls only: BrainGeneratorTest, BrainCheckTest, "
                    + "PublicationScannerTest, PagesWorkflowTest passed in the build that generated this site.");
        } else {
            // Once other controls ship, such as the contract kit's, every passing evidence test is listed instead.
            assertThat(brain).doesNotContain("Documentation controls only");
            evidenced.forEach(c -> c.evidence().forEach(test -> assertThat(brain).contains(test)));
        }
        assertThat(brain).doesNotContain("names this module").contains("No module-specific gaps are recorded here. "
                + "See the <a href=\"../trust/gaps.html\">project-wide known gaps</a>.");
    }

    @Test
    void trustKeepsTheEnforcedSubsetApartFromThePlannedControl() {
        String planned = "Schema validation, policy tests and approval binding remain planned.";
        String enforced = "Current enforcement covers preflight, dependency verification and acceptance-digest checks.";
        String allowlist = site.text("trust/allowlist.html");
        String trust = site.text("trust/index.html");
        String adr = site.text("decisions/0007-dependency-allowlist-and-pre-use-enforcement.html");

        for (String page : List.of(allowlist, trust)) {
            String status = page.substring(page.indexOf("status-grid"), page.indexOf("</dl>", page.indexOf("status-grid")));
            assertThat(status).contains(planned, enforced, "Partly enforced").doesNotContain(">Shipped<");
        }
        String adrStatus = adr.substring(adr.indexOf("status-grid"), adr.indexOf("</dl>", adr.indexOf("status-grid")));
        assertThat(adrStatus).contains("<a href=\"#implementation-status-2026-11-06\">" + planned + "</a>",
                "<a href=\"#implementation-status-2026-11-06\">" + enforced + "</a>")
                .doesNotContain("pull_request_target workflow on the pull request");
    }

    @Test
    void riskLevelsSayFirstThatClassificationIsManual() {
        String risk = site.text("trust/risk-levels.html").replaceAll("\\s+", " ");
        String statement = "Risk classification is currently manual: the author states the level and the owner records "
                + "approval for level 3. Automated classification and approval enforcement are planned.";

        assertThat(risk).contains(statement).doesNotContain("Today nothing computes the level automatically");
        assertThat(risk.indexOf(statement)).isLessThan(risk.indexOf("<h2 id=\"the-four-levels\">"))
                .isGreaterThan(risk.indexOf("class=\"page-body\""));
        assertThat(risk).contains("Under the accepted policy", "No check applies these rules yet.");
    }

    @Test
    void adr0011sEvidenceIsOneSentenceAndItsTestsAreListedOnceUnderConfirmation() {
        String adr = site.text("decisions/0011-java-documentation-tool-and-publication.html");
        String status = adr.substring(adr.indexOf("status-grid"), adr.indexOf("</dl>", adr.indexOf("status-grid")));

        // A count of test classes and what they verify, then the names in a disclosure; the checks themselves are
        // described once, under Confirmation.
        assertThat(status).containsPattern("<dt>Verified evidence</dt><dd><span class=\"badge badge-shipped\">Passing"
                + "</span> \\d+ evidence test classes passed in the build that generated this site\\. This verifies "
                + "documentation tooling and publication controls, not shipping behaviour\\. <a href=\"#confirmation\">"
                + "See confirmation</a>\\.\n<details class=\"evidence-tests\"><summary>Evidence test classes \\(\\d+\\)"
                + "</summary>").doesNotContain("Passed in the build", "pages: the build job");
        assertThat(count(adr, "Passed in the build that generated this site:")).isEqualTo(1);
        assertThat(adr.indexOf("Passed in the build that generated this site:"))
                .isGreaterThan(adr.indexOf("<h3 id=\"confirmation\">Confirmation</h3>"));
        assertThat(adr).contains("Passed in the build that generated this site: BrainGeneratorTest, BrainCheckTest, "
                + "PublicationScannerTest, PagesWorkflowTest.");
    }

    @Test
    void reviewSummariesAreBlocksAndTheNotFoundPageHasItsBreadcrumbAndStatements() {
        assertThat(site.text("trust/allowlist.html")).contains("<div class=\"group-summary\">")
                .doesNotContain("<span class=\"group-summary\">");
        String notFound = site.text("404.html");
        assertThat(notFound).contains("<li><a href=\"/shipping-reference-platform/index.html\">Start</a></li>\n"
                + "<li><span aria-current=\"page\">Page not found</span></li>",
                "<dt>Accepted design</dt><dd>Not applicable</dd>", "<dt>Planned implementation</dt><dd>Not applicable</dd>",
                "<dt>Verified evidence</dt><dd>This error page makes no application or control claim.</dd>");
        assertThat(notFound.indexOf("class=\"page-summary\"")).isLessThan(notFound.indexOf("status-grid"));
        assertThat(notFound).doesNotContain("is-active");
    }

    @Test
    void theRepositoryPageSaysPlannedWhereNothingIsBuilt() {
        String repository = site.text("start/repository.html");

        assertThat(repository).contains("<th>Implementation state</th>", "The planned v0.1 behaviour is: a shipping "
                + "clerk", "<td>Minimal implementation planned for v0.1; no code yet</td>",
                "<td>Built in part: booking from an offer, idempotent per tenant and key, without HTTP or a "
                        + "database</td>",
                "<td>Built in part: tariff, rating, quote creation and reading, and offer lookup, over HTTP in tests; "
                        + "no database yet</td>",
                "<td>Built in part: demo authentication over HTTP, <code>GET /api/v1/me</code> and the tenant bound "
                        + "for each request; the database adapter, row-level security and prototype flags are planned"
                        + "</td>")
                .doesNotContain("<td>built</td>", "<td>built (minimal)</td>", "v0.1 target, not implemented");
    }

    @Test
    void theModelPagesQualifyTheCheckersEvidenceAndSayWhatItCovers() {
        // The count and what the tests do not prove stay in the ledger's line; the names are in the disclosure.
        String evidence = "<dt>Verified evidence</dt><dd><span class=\"badge badge-shipped\">Conformance tests "
                + "passing</span> 3 evidence test classes passed in the build that generated this site. These tests "
                + "verify the checker and the built modules&#39; static dependencies against the model, not their "
                + "behaviour.\n<details class=\"evidence-tests\"><summary>Evidence test classes (3)</summary><p>"
                + "ConformanceDivergenceTest, ArchitectureConformanceTest, ConformanceAbsenceTest</p></details></dd>";
        for (String page : List.of("model/index.html", "model/system-context.html", "model/containers.html",
                "model/api-components.html", "model/quote-book-label.html")) {
            assertThat(site.text(page)).as(page).contains(evidence).doesNotContain(">Passing</span>",
                    "It is not implemented yet", "The planned conformance check", "the planned check");
        }
        // The flow page keeps its own evidence: the quotes, shipments and tenants modules' tests that pass.
        assertThat(site.text("model/flow-quote-book-label.html"))
                .contains("<dt>Verified evidence</dt><dd><span class=\"badge badge-shipped\">Passing</span> 14 "
                        + "evidence test classes passed")
                .doesNotContain("Checker tests passing", "Conformance tests passing");
        String index = site.text("model/index.html");
        assertThat(index).contains("The checker in <code>libs/c4-contract-kit</code> is implemented and its own "
                + "tests pass. It compares the direct static references in compiled classes with the model in both "
                + "directions: a dependency between two modules that the model does not declare fails the check, and "
                + "so does a REQUIRED relationship without any static reference; a PERMITTED relationship without one "
                + "is reported as \"no static evidence\". It does not evaluate a relationship that touches a planned "
                + "module. Where a relationship touches a planned module, the views show the accepted design, not "
                + "verified structure.").doesNotContain("That check is planned", "is to compare",
                        "Every module of the API is planned");
    }

    @Test
    void theGeneratedArchitecturePagesLinkFiguresThatTheSiteHas() {
        Pattern link = Pattern.compile("\\(https://stig-johnny\\.github\\.io/shipping-reference-platform/"
                + "(model/[a-z-]+\\.html)#(figure-[a-z-]+)\\)\n\n```mermaid\n");
        List<String> targets = new ArrayList<>();
        GeneratedFiles.generate(sources).forEach((path, text) -> {
            Matcher found = link.matcher(text);
            while (found.find()) {
                assertThat(site.pages()).as(path).contains(found.group(1));
                assertThat(site.text(found.group(1))).as(path).contains("<figure class=\"diagram\" id=\""
                        + found.group(2) + "\">");
                targets.add(found.group(1));
            }
        });
        assertThat(targets).containsExactlyInAnyOrder("model/api-components.html", "model/containers.html",
                "model/quote-book-label.html", "model/system-context.html");
    }

    @Test
    void theCheckersEvidenceNamesNoPlannedModulesOnceSomeAreBuilt() {
        List<Capability> conformance = sources.capabilities().controls().stream()
                .filter(c -> c.id().startsWith("control.c4-")).toList();

        assertThat(ModelPages.checkerEvidence(conformance, sources.evidence(), false))
                .contains("passed in the build that generated this site. These tests verify the checker and the built "
                        + "modules&#39; static dependencies against the model, not their behaviour.\n<details "
                        + "class=\"evidence-tests\">");
        // Without the API's own check among them, the same tests verify the checker only.
        TestEvidence checkerOnly = new TestEvidence(Set.of("ConformanceDivergenceTest", "ConformanceAbsenceTest"),
                Map.of("ConformanceDivergenceTest", passed("ConformanceDivergenceTest"), "ConformanceAbsenceTest",
                        passed("ConformanceAbsenceTest")));
        assertThat(ModelPages.checkerEvidence(conformance, checkerOnly, false)).startsWith("<span class=\"badge "
                + "badge-shipped\">Checker tests passing</span> ").contains("These tests verify the checker, not the "
                + "shipping modules.");
        assertThat(ModelPages.checkerEvidence(conformance, new TestEvidence(Set.of(), Map.of()), true))
                .contains(">None yet</span>");
    }

    @Test
    void theModelAndFlowTablesKeepReadableColumnsOnNarrowScreensAndSaySo() {
        String hint = "<p class=\"table-hint\">Scroll horizontally for all columns.</p>\n<table>";
        for (String page : List.of("model/system-context.html", "model/containers.html", "model/api-components.html",
                "model/quote-book-label.html")) {
            String html = site.text(page);
            assertThat(count(html, "<div class=\"table-scroll table-wide-model\" role=\"region\" tabindex=\"0\" "
                    + "aria-label=\"")).as(page).isEqualTo(2);
            assertThat(count(html, hint)).as(page).isEqualTo(2);
        }
        String flow = site.text("model/flow-quote-book-label.html");
        String flowRegion = "<div class=\"table-scroll table-wide-flow\" role=\"region\" tabindex=\"0\" aria-label=\"";
        String steps = "Table 1 in Steps and acceptance tests, scrolls sideways";
        assertThat(count(flow, flowRegion + steps + "\">\n" + hint)).isEqualTo(1);
        assertThat(count(flow, flowRegion + "Table 2 in Alternatives and failures, scrolls sideways\">\n" + hint))
                .isEqualTo(1);
        // Scoped to the model pages and the register of demonstrations: every other table keeps its plain region.
        for (String page : site.pages()) {
            if (!page.startsWith("model/") && !page.equals("demos/index.html")) {
                assertThat(site.text(page)).as(page).doesNotContain("table-wide", "table-hint");
            }
        }
    }

    @Test
    void theFlowDiagramDescribesItselfForScreenReaders() {
        String flow = site.text("model/flow-quote-book-label.html");

        assertThat(flow).contains("accDescr: Dynamic view with 8 elements and 15 relationships. The linked Quote, book "
                + "and download a simulated label page lists its elements and relationships. The step tables below "
                + "follow the separate 29-step sequence.");
        assertThat(site.text("model/quote-book-label.html")).contains("accDescr: Dynamic view with 8 elements and 15 "
                + "relationships. The tables on the page list every one of them.");
    }

    @Test
    void theRepositoryPageDescribesTheWorkflowInOrder() {
        String repository = site.text("start/repository.html");

        assertThat(repository).contains("<li>An issue states the change.</li>",
                "<li>The risk level decides: levels 0 to 2 merge; level 3 merges only after the owner approves the "
                        + "current head.</li>");
        assertThat(repository.indexOf("<li>An issue states the change.</li>"))
                .isGreaterThan(repository.indexOf("Workflow source"));
        assertThat(site.text("trust/third-party-notices.html")).contains("seven diagram pages");
    }

    @Test
    void theModulesIndexSaysTheWebAppCallsTheApiThroughItsHandWrittenAdapter() {
        // ADR 0012: the first runnable slice generates no client.
        String row = "<tr><th scope=\"row\">Web app</th><td>a container, not a checked module</td><td></td>"
                + "<td>the API, through its hand-written typed HTTP adapter</td>";

        assertThat(site.text("modules/index.html")).contains(row).doesNotContain("generated client");
    }

    @Test
    void theFirstSliceRecordSupersedesFourRecordsAndThePagesCiteIt() {
        String successor = "0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.html";
        List<String> superseded = List.of("0003-modular-monolith-with-spring-modulith-and-hexagonal-modules",
                "0004-contract-first-openapi-with-rfc-9457-problems",
                "0005-postgresql-spring-data-jdbc-flyway-and-forced-rls",
                "0009-react-workbench-with-a-generated-client");
        String record = site.text("decisions/" + successor);

        for (String old : superseded) {
            String number = old.substring(0, 4);
            assertThat(record).contains("Supersedes <a href=\"" + old + ".html\">ADR " + number + "</a>.");
            assertThat(site.text("decisions/" + old + ".html")).as(old).contains("<span class=\"badge "
                    + "badge-neutral\">Superseded</span> Superseded by <a href=\"" + successor + "\">ADR 0012</a>.");
        }
        // Scope these assertions to the panel: the record's body already contains all these checks.
        String panel = record.substring(record.indexOf("<section class=\"status-panel\""));
        panel = panel.substring(0, panel.indexOf("</section>"));
        assertThat(panel).contains("6 evidence test classes passed", "ModularityTest", "HexagonalLayeringTest",
                "ArchitectureConformanceTest", "QuotesHttpContractTest", "TenantsHttpContractTest",
                "ProblemDetailsHttpContractTest", "This verifies the structure of the built modules",
                "<span class=\"badge badge-partial\">Partly enforced</span> Every response that the HTTP tests exercise",
                "The shipments operations (ShipmentsHttpContractTest)", "PostgreSQL-backed application remain.",
                "RowLevelSecurityCatalogTest", "RowLevelSecurityIntegrationTest", "CrossTenantAccessTest",
                "MigrationsIntegrationTest", "TableOwnershipTest", "WebArchitectureConformanceTest",
                "web: type check and emit", "node --test", "contract-e2e: Playwright and axe-core")
                .doesNotContain(Statuses.TOOLING);
        assertThat(site.text("api/index.html")).contains("The contract is the authority for the HTTP interface "
                + "(ADR 0012).", "Contract-first (ADR 0012): OpenAPI 3.1.2").doesNotContain("ADR 0004");
        assertThat(site.text("modules/index.html")).contains("<a href=\"../decisions/" + successor + "\">ADR 0012: "
                + "First runnable slice with a plain TypeScript workbench and Spring JDBC</a>")
                .doesNotContain("0003-modular");
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }

    /** The brain's count of entries by status, as its overview words it: "2 capabilities shipped and 3 planned". */
    private static String counted(List<Capability> entries, String one, String many) {
        List<String> parts = new ArrayList<>();
        for (Capability.Status status : Capability.Status.values()) {
            long count = entries.stream().filter(c -> c.status() == status).count();
            if (count > 0) {
                parts.add(count + " " + (count == 1 ? one : many) + " " + status.value());
            }
        }
        return parts.size() == 1 ? parts.getFirst()
                : String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
    }

    private static TestEvidence.Result passed(String test) {
        return new TestEvidence.Result("fx." + test, 1, 0, 0, 0);
    }
}
