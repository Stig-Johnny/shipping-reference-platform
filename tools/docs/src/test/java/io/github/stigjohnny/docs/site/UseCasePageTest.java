package io.github.stigjohnny.docs.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.mermaid.MermaidValidator;
import io.github.stigjohnny.docs.repo.Capabilities;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.repo.TestEvidence;
import io.github.stigjohnny.docs.testing.BuiltSite;
import io.github.stigjohnny.docs.testing.Fixture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The use case overview, sheet M-07 (docs/ui/documentation-site.md, "The use case overview"): every product capability
 * of docs/capabilities/capabilities.yaml as a use case, grouped by module, linked to the actors the registry names,
 * with its status in the registry and a stamp of what the build that generated the site observed of its acceptance
 * tests. The copy adds one capability for each way a stamp falls short of green: a test that failed, one without a
 * report, a skipped one and an empty list; and a TypeScript test file that no producer reads.
 */
class UseCasePageTest {

    private static final String PAGE = "model/use-cases.html";
    private static final String REVISION = "0123456789abcdef0123456789abcdef01234567";
    private static final String QUOTES = "io.github.stigjohnny.shipping.quotes.";
    private static final String FIXTURES = """
              - id: quotes.fixture-failing
                module: quotes
                title: A fixture whose first named test failed in this build.
                status: planned
                target: v0.1
                evidence: none yet
                acceptanceTests: [QuoteOffersServiceTest, RatingTest]
                docs: docs/modules/quotes.md
                actors: [clerk]
              - id: tenants.fixture-unreported
                module: tenants
                title: A fixture whose named test exists but has no report in this build.
                status: planned
                target: v0.1
                evidence: none yet
                acceptanceTests: [ThreadTenantContextTest]
                docs: docs/modules/tenants.md
                actors: []
              - id: quotes.fixture-skipped
                module: quotes
                title: A fixture with a skipped test beside a passing one.
                status: planned
                target: v0.1
                evidence: none yet
                acceptanceTests: [PlaceTest, QuoteExpiryTest]
                docs: docs/modules/quotes.md
                actors: [clerk]
              - id: quotes.fixture-unnamed
                module: quotes
                title: A fixture that names no acceptance test.
                status: planned
                target: v0.1
                evidence: none yet
                acceptanceTests: []
                docs: docs/modules/quotes.md
                actors: []
            """;
    private static final Pattern DEFINITION = Pattern.compile(
            "<pre class=\"diagram-source\" data-diagram=\"use-cases\">(.*?)</pre>", Pattern.DOTALL);

    @TempDir
    static Path dir;

    private static BuiltSite site;
    private static String page;

    @BeforeAll
    static void buildTheSite() {
        Fixture fixture = Fixture.copy(dir.resolve("copy")).passingEvidence().withoutDemos()
                .edit(Capabilities.PATH, text -> text.replace("\ncontrols:\n", "\n" + FIXTURES + "\ncontrols:\n"))
                .write("apps/shipping-web/src/features/quotes/ParcelForm.test.tsx", "// A fixture: never run here.\n")
                .report(QUOTES + "internal.application.QuoteOffersServiceTest", 4, 1, 0, 0)
                .report(QUOTES + "api.PlaceTest", 3, 0, 0, 1);
        SiteBuilder.build(Sources.read(fixture.root(), List.of(fixture.reports())), dir.resolve("site"), REVISION);
        site = BuiltSite.read(dir.resolve("site"));
        page = site.text(PAGE);
    }

    @Test
    void theOverviewIsSheetM07OfTheModelSectionAfterItsFlowAndDrawsUnderTheDiagramPolicy() {
        assertThat(Sheets.code(PAGE)).isEqualTo("M-07");
        assertThat(page).contains("<p class=\"sheet-code\"><span class=\"visually-hidden\">Sheet </span>M-07</p>\n"
                + "<h1>Use cases</h1>", "<li><span aria-current=\"page\">Use cases</span></li>",
                SiteBuilderTest.MERMAID_CSP, "<script src=\"../assets/mermaid/11.17.2/mermaid.min.js\" defer></script>",
                "<figure class=\"diagram\" id=\"figure-use-cases\">",
                "<p class=\"diagram-title\">M-07 · Use cases</p>");
        String nav = page.substring(page.indexOf("<nav class=\"site-nav\""), page.indexOf("</nav>"));
        assertThat(List.of(nav.indexOf(">M-00<"), nav.indexOf(">M-04<"), nav.indexOf(">M-05<"), nav.indexOf(">M-07<")))
                .doesNotContain(-1).isSorted();
        assertThat(site.text("model/index.html")).contains("href=\"use-cases.html\"");
    }

    @Test
    void theDiagramGroupsTheUseCasesByModuleAndPointsFromAnActorThatStartsOneAndToASystemItReliesOn() {
        String definition = definition();

        assertThat(MermaidValidator.validate(definition, "the use case overview")).isEqualTo(definition);
        assertThat(definition).startsWith("flowchart LR\n    accTitle: Use cases\n").contains(
                "    actor_clerk[\"Shipping clerk [Person]\"]\n",
                "    subgraph module_quotes [\"Quotes\"]\n",
                "        uc_quotes_create_quote([\"create-quote (partial)\"])\n",
                "        uc_quotes_synthetic_tariff([\"synthetic-tariff (shipped)\"])\n",
                "    subgraph module_web [\"Web app\"]\n",
                "    actor_carrier[\"Carrier (simulated) [Software System] (external, simulated)\"]\n",
                "    actor_clerk --> uc_quotes_create_quote\n",
                "    actor_webshop --> uc_orders_import_webshop_orders\n",
                "    uc_shipments_simulated_label --> actor_carrier\n",
                "    uc_quotes_synthetic_tariff --> actor_carrier\n");
        // System behaviour has no actor, so its use case is drawn without an arrow; a person that no capability names
        // is not drawn, and its row says so.
        assertThat(count(definition, "uc_audit_idempotent_consumer")).isEqualTo(1);
        assertThat(count(definition, "uc_tenants_row_level_security")).isEqualTo(1);
        assertThat(definition).doesNotContain("actor_admin", "style ", "classDef", "==>", "-.->");
        assertThat(row("actors", "Platform administrator")).contains("No use case names it.");
    }

    @Test
    void aUseCaseIsGreenOnlyWhenEveryAcceptanceTestTheRegistryNamesPassed() {
        assertThat(row("use-cases", "quotes.synthetic-tariff")).contains(
                "<span class=\"badge badge-shipped\">All passed</span> All 4 named tests passed in this build.",
                "SyntheticTariffTest: passed", "<span class=\"badge badge-shipped\">Shipped</span>",
                "Relies on Carrier (simulated)").doesNotContain("Shipping clerk");
        // Two of its three tests passed; QuotesHttpContractTest is not written yet.
        assertThat(row("use-cases", "quotes.create-quote")).contains(
                "<span class=\"badge badge-none\">Not run or missing</span> 2 of 3 named tests passed in this build; 1 "
                        + "not in the repository yet.", "QuotesHttpContractTest: not in the repository yet",
                "<span class=\"badge badge-partial\">Partial</span>", "Started by Shipping clerk")
                .doesNotContain("badge-shipped");
        assertThat(row("use-cases", "quotes.get-quote")).contains("<span class=\"badge badge-none\">No tests "
                + "yet</span> None of its 2 named tests is in the repository yet.",
                "<span class=\"badge badge-planned\">Planned</span>");
    }

    @Test
    void aFailedTestMakesItsUseCaseFailingInWordsAndInTheFailureColour() {
        assertThat(row("use-cases", "quotes.fixture-failing")).contains(
                "<span class=\"badge badge-failing\">Some failing</span> 1 of 2 named tests failed in this build; 1 "
                        + "passed.", "QuoteOffersServiceTest: failed in this build", "RatingTest: passed")
                .doesNotContain("badge-shipped");
    }

    @Test
    void aTestMissingFromTheReportsIsNotRunNeverPassed() {
        assertThat(row("use-cases", "tenants.fixture-unreported")).contains(
                "<span class=\"badge badge-none\">Not run or missing</span> 0 of 1 named tests passed in this build; 1 "
                        + "not run in this build.", "ThreadTenantContextTest: no report in this build",
                "No actor: system behaviour").doesNotContain("badge-shipped");
    }

    @Test
    void aSkippedTestIsNotRunEvenBesideAPassingOne() {
        assertThat(row("use-cases", "quotes.fixture-skipped")).contains(
                "<span class=\"badge badge-none\">Not run or missing</span> 1 of 2 named tests passed in this build; 1 "
                        + "skipped.", "PlaceTest: skipped in this build", "QuoteExpiryTest: passed")
                .doesNotContain("badge-shipped");
    }

    @Test
    void anEmptyAcceptanceListIsNeverGreen() {
        assertThat(row("use-cases", "quotes.fixture-unnamed")).contains(
                "<span class=\"badge badge-none\">No tests yet</span> No acceptance test is named yet.")
                .doesNotContain("badge-shipped", "<details");
    }

    @Test
    void aTypeScriptTestInTheRepositoryIsNotReadAndShowsAsNotRun() {
        assertThat(row("use-cases", "web.quote-workbench")).contains("Not run or missing",
                "ParcelForm.test.tsx: in the repository, but this build reads no TypeScript or JavaScript reports",
                "QuoteCardList.test.tsx: not in the repository yet").doesNotContain("badge-shipped");
    }

    @Test
    void theLegendSaysWhatAStampMeansAndThatTheArrowsWereNotTraced() {
        assertThat(page).contains("Arrows are what the registry and the model declare; no test traced them. A stamp "
                        + "in the table says that named tests passed in this build, not that the use case ran as drawn.",
                "A stamp says which named tests passed in the build that generated this site. It is not a runtime "
                        + "trace of the use case",
                "This build read the JUnit reports of ",
                "A named test that none of them ran shows as not run, never as passed.");
        assertThat(page).contains("<h2 id=\"use-cases\">", "<h2 id=\"actors\">", "<h2 id=\"stamps\">",
                "<li><a href=\"#use-cases\">", "<li><a href=\"#actors\">", "<li><a href=\"#stamps\">");
    }

    @Test
    void theRegisterScrollsInsideItsRegionKeepsEveryStatusWordWholeAndFailingIsInTheFailureColour() {
        assertThat(page).contains("<div class=\"table-scroll table-wide-use-cases\" role=\"region\" tabindex=\"0\" "
                + "aria-label=\"Use cases, scrolls sideways\">\n<p class=\"table-hint\">Scroll horizontally for all "
                + "columns.</p>\n<table>\n<caption>Use cases (31), by module</caption>");
        String css = site.text("assets/site.css");
        assertThat(css).contains(".table-wide-use-cases table {\n  min-width: 60rem;\n  table-layout: fixed;\n}",
                ".table-wide-use-cases thead th:nth-child(2) {\n  width: 11%;\n}",
                "@media (max-width: 600px) {\n  .table-wide-use-cases table {\n    min-width: 44rem;\n  }",
                "@container (min-width: 60rem) {\n  .table-wide-use-cases .table-hint {\n    display: none;\n",
                ".badge-rejected,\n.badge-failing {\n  color: var(--failure);\n}");
    }

    @Test
    void theReportsReadAreNamedByTheirGradleTaskAndNeverByAPathOutsideTheRepository() throws IOException {
        Path repo = dir.resolve("producers");
        Path tests = repo.resolve("apps/api/build/test-results/test");
        Files.createDirectories(tests);
        Files.writeString(tests.resolve("TEST-x.ATest.xml"), "<testsuite name=\"x.ATest\" tests=\"1\"/>\n");

        TestEvidence evidence = TestEvidence.read(repo, List.of(tests,
                repo.resolve("apps/api/build/test-results/databaseTest"), dir.resolve("elsewhere")));

        assertThat(evidence.producers()).containsExactly(new TestEvidence.Producer(":apps:api:test", 1),
                new TestEvidence.Producer(":apps:api:databaseTest", 0),
                new TestEvidence.Producer("a report directory outside the repository", 0));
    }

    @Test
    void anUnknownActorFailsTheBuild() {
        Fixture fixture = Fixture.copy(dir.resolve("unknown")).passingEvidence()
                .edit(Capabilities.PATH, text -> text.replaceFirst("(id: quotes.create-quote\n(?:    .*\n){7})"
                        + "    actors: \\[clerk]", "$1    actors: [courier]"));

        assertThatThrownBy(() -> SiteBuilder.build(Sources.read(fixture.root(), List.of(fixture.reports())),
                dir.resolve("unknown-site"), REVISION)).isInstanceOf(DocsException.class).hasMessageContaining(
                        "quotes.create-quote: actor 'courier' is not a person or external system of "
                                + "architecture/workspace.dsl");
    }

    private static String definition() {
        Matcher source = DEFINITION.matcher(page);
        assertThat(source.find()).isTrue();
        return source.group(1).replace("&quot;", "\"").replace("&gt;", ">").replace("&lt;", "<").replace("&#39;", "'")
                .replace("&amp;", "&");
    }

    /** The table row, in the section with this id, whose header cell contains the text. */
    private static String row(String section, String header) {
        int start = page.indexOf("<h2 id=\"" + section + "\">");
        assertThat(start).as(section).isNotNegative();
        int end = page.indexOf("</table>", start);
        Matcher row = Pattern.compile("<tr>(?:(?!</tr>).)*?<th scope=\"row\">(?:(?!</th>).)*?" + Pattern.quote(header)
                + "(?:(?!</tr>).)*</tr>", Pattern.DOTALL).matcher(page.substring(start, end));
        assertThat(row.find()).as(header).isTrue();
        return row.group();
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }
}
