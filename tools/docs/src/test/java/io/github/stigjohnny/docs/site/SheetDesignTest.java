package io.github.stigjohnny.docs.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.BuiltSite;
import io.github.stigjohnny.docs.testing.Fixture;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The drawing set of docs/ui/documentation-site.md: each sheet's code, its title block, the status ledger's evidence
 * line, the register, and the start sheet's drawing.
 */
class SheetDesignTest {

    private static final String REVISION = "0123456789abcdef0123456789abcdef01234567";
    private static final Pattern SHEET_CODE = Pattern.compile(
            "<p class=\"sheet-code\"><span class=\"visually-hidden\">Sheet </span>([^<]+)</p>");

    @TempDir
    static Path dir;

    private static Sources sources;
    private static BuiltSite site;

    @BeforeAll
    static void buildTheSite() {
        Fixture fixture = Fixture.copy(dir).passingEvidence();
        sources = Sources.read(fixture.root(), List.of(fixture.reports()));
        SiteBuilder.build(sources, dir.resolve("site"), REVISION);
        site = BuiltSite.read(dir.resolve("site"));
    }

    @Test
    void everySheetShowsOneCodeAndNoTwoSheetsShareOne() {
        Map<String, String> pageByCode = new HashMap<>();
        for (String page : site.pages()) {
            Matcher code = SHEET_CODE.matcher(site.text(page));
            assertThat(code.find()).as(page).isTrue();
            String previous = pageByCode.put(code.group(1), page);
            assertThat(previous).as(code.group(1) + " on " + page).isNull();
            assertThat(code.find()).as(page).isFalse();
        }
    }

    @Test
    void codesAreWrittenOutOrComeFromTheDecisionsNumberAndAnUnknownPageFailsTheBuild() {
        assertThat(Sheets.code("index.html")).isEqualTo("S-00");
        assertThat(Sheets.code("model/api-components.html")).isEqualTo("M-03");
        assertThat(Sheets.code("decisions/0002-c4-model-is-the-structural-authority.html")).isEqualTo("D-02");
        assertThat(Sheets.code("decisions/0011-java-documentation-tool-and-publication.html")).isEqualTo("D-11");
        assertThatThrownBy(() -> Sheets.code("modules/billing.html")).isInstanceOf(DocsException.class)
                .hasMessageContaining("modules/billing.html has no sheet code");
    }

    @Test
    void theTitleBlockNamesTheSourceAndTheRevisionAndADateOnlyWhereOneDocumentGivesIt() {
        DocFile adr = sources.doc("docs/adr/0002-c4-model-is-the-structural-authority.md").orElseThrow();
        String decision = titleBlock("decisions/0002-c4-model-is-the-structural-authority.html");
        assertThat(decision).contains("<div><dt>Source</dt><dd>" + adr.path() + "</dd></div>",
                "<div><dt>Revision</dt><dd><code>01234567</code></dd></div>",
                "<div><dt>Updated</dt><dd>" + adr.updated() + "</dd></div>");
        // A sheet made from several sources, or from sources without a date, shows no date rather than an invented one.
        for (String page : List.of("index.html", "model/api-components.html", "api/createQuote.html",
                "brain/index.html")) {
            assertThat(titleBlock(page)).as(page).contains("<dt>Revision</dt>").doesNotContain("<dt>Updated</dt>");
        }
        // The full SHA stays at the foot of every sheet.
        assertThat(site.text("model/api-components.html")).contains("Source revision: <code>" + REVISION + "</code>");
    }

    @Test
    void passingEvidenceIsACountWithWhatItDoesNotProveAndTheNamesInADisclosure() {
        String start = ledgerRow(site.text("index.html"), "Verified evidence");
        String line = start.substring(0, start.indexOf("\n<details"));
        // Only controls contribute passing evidence today, so the badge and the line say it is the tooling's.
        assertThat(line).contains("<span class=\"badge badge-shipped\">Tooling tests passing</span> ")
                .endsWith(" evidence test classes passed in the build that generated this site. These tests verify "
                        + "repository tooling, not shipping behaviour.")
                .doesNotContain("ConformanceDivergenceTest");
        assertThat(start.substring(start.indexOf("\n<details"))).startsWith(
                "\n<details class=\"evidence-tests\"><summary>Evidence test classes (").contains("ConformanceDivergenceTest");
        String model = ledgerRow(site.text("model/api-components.html"), "Verified evidence");
        assertThat(model.substring(0, model.indexOf("\n<details")))
                .endsWith("These tests verify the checker; the shipping modules remain planned.");
    }

    @Test
    void theStartSheetDrawsTheSystemContextFirstAndStatesTheCountsFromTheSources() {
        String start = site.text("index.html");
        List<Capability> product = sources.capabilities().capabilities();
        long planned = product.stream().filter(c -> c.status() == Capability.Status.PLANNED).count();

        assertThat(start).contains("<section class=\"start-drawing\" aria-labelledby=\"start-drawing-title\">",
                "data-diagram=\"system-context\"", "<p class=\"diagram-title\">M-01 · System context</p>",
                planned + " of " + product.size() + " product capabilities are planned. Controls: ")
                .doesNotContain("class=\"page-contents\"");
        assertThat(start.indexOf("class=\"start-drawing\"")).isLessThan(start.indexOf("id=\"reading-paths\""));
        assertThat(start.indexOf("id=\"reading-paths\"")).isLessThan(start.indexOf("id=\"the-data\""));
    }

    @Test
    void theRegisterListsEverySectionWithItsCodeAndTheSiteDesignSheet() {
        String nav = navigation(site.text("start/site-design.html"));

        assertThat(nav).contains("<span class=\"register-code\">S-00</span> <span class=\"register-title\">Start</span>",
                "<span class=\"register-code\">M-00</span> <span class=\"register-title\">Model</span>",
                "<span class=\"register-code\">B-00</span> <span class=\"register-title\">Brain</span>",
                "<span class=\"register-code\">S-02</span> <span class=\"register-title\">How this site is drawn"
                        + "</span></a>");
        assertThat(site.text("start/site-design.html")).contains("Documentation site: visual design");
    }

    @Test
    void otherSheetsListTheirSectionsInADisclosure() {
        String api = site.text("model/api-components.html");

        assertThat(api).contains("<nav class=\"page-contents\" aria-labelledby=\"site-contents-title\">\n<details>\n"
                + "<summary id=\"site-contents-title\">On this sheet (3)</summary>");
    }

    @Test
    void theDiagramNotesComeBeforeTheDrawingAndSayWhereItsTextEquivalentIs() {
        String start = site.text("index.html");
        int failed = start.indexOf("<p class=\"diagram-note diagram-failed\" hidden>");
        int noScript = start.indexOf("<noscript><p class=\"diagram-note\">");
        int region = start.indexOf("<div class=\"diagram-scroll\"");

        assertThat(List.of(noScript, failed, region)).doesNotContain(-1).isSorted();
        assertThat(start.substring(failed, start.indexOf("</p>", failed))).contains("could not be drawn here; its source "
                + "is shown instead. Sheet M-01 lists every element and relationship: <a href=\"model/system-context.html\">");
        assertThat(start).contains("<button type=\"button\" class=\"button\" data-enlarge=\"figure-system-context\" "
                + "aria-pressed=\"false\" hidden>Enlarge diagram</button>");
    }

    private static String titleBlock(String page) {
        String html = site.text(page);
        int start = html.indexOf("<dl class=\"sheet-meta\">");
        assertThat(start).as(page).isNotNegative();
        return html.substring(start, html.indexOf("</dl>", start));
    }

    private static String ledgerRow(String html, String term) {
        int start = html.indexOf("<dt>" + term + "</dt><dd>");
        assertThat(start).isNotNegative();
        return html.substring(start + ("<dt>" + term + "</dt><dd>").length(), html.indexOf("</dd>", start));
    }

    private static String navigation(String html) {
        int start = html.indexOf("<nav class=\"site-nav\"");
        return html.substring(start, html.indexOf("</nav>", start));
    }
}
