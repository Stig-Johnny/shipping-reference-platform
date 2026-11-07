package io.github.stigjohnny.docs.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.BuiltSite;
import io.github.stigjohnny.docs.testing.Decks;
import io.github.stigjohnny.docs.testing.Demos;
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
 * line, the register, and the start sheet's drawing. The copy has two demonstrations, so that their sheets are part of
 * the set: pull request 45 was recorded before pull request 42; and a milestone deck, whose slides and notes are sheets
 * X-D01 and X-N01.
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
        Fixture fixture = Fixture.copy(dir).passingEvidence().withoutDemos().write(Demos.path("gate"), Demos.GATE)
                .write(Demos.path("build"), Demos.BUILD).write(Decks.PATH, Decks.SAMPLE);
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
    void demonstrationsTakeTheirCodeFromTheirPullRequestNeverFromTheirPosition() {
        assertThat(Sheets.code("demos/index.html")).isEqualTo("X-00");
        assertThat(Sheets.code("demos/how-to.html")).isEqualTo("X-GUIDE");
        assertThat(Sheets.code("demos/42-gate.html")).isEqualTo("X-42");
        assertThat(Sheets.code("demos/7-a-change.html")).isEqualTo("X-07");
        assertThat(Sheets.code("demos/1234-a-change.html")).isEqualTo("X-1234");
        assertThatThrownBy(() -> Sheets.code("demos/0-a-change.html")).isInstanceOf(DocsException.class)
                .hasMessageContaining("has no sheet code");
        assertThatThrownBy(() -> Sheets.code("demos/notes.html")).isInstanceOf(DocsException.class)
                .hasMessageContaining("has no sheet code");
        // The register lists pull request 42 first, because it was recorded last; each sheet keeps its own number.
        String nav = navigation(site.text("demos/45-build.html"));
        assertThat(List.of(nav.indexOf(">X-00<"), nav.indexOf(">X-GUIDE<"), nav.indexOf(">X-42<"),
                nav.indexOf(">X-45<"))).doesNotContain(-1).isSorted();
        assertThat(SHEET_CODE.matcher(site.text("demos/45-build.html")).results().map(m -> m.group(1)).toList())
                .containsExactly("X-45");
    }

    @Test
    void aDeckAndItsNotesHaveTheirFixedCodesAndAnotherDeckHasNoneUntilItIsGivenOne() {
        assertThat(Sheets.code("demos/deck-milestone.html")).isEqualTo("X-D01");
        assertThat(Sheets.code("demos/deck-milestone-notes.html")).isEqualTo("X-N01");
        assertThatThrownBy(() -> Sheets.code("demos/deck-review.html")).isInstanceOf(DocsException.class)
                .hasMessageContaining("demos/deck-review.html has no sheet code");
        // The deck and its notes stand in the register after the how-to, before the demonstrations.
        String nav = navigation(site.text("demos/deck-milestone.html"));
        assertThat(List.of(nav.indexOf(">X-00<"), nav.indexOf(">X-GUIDE<"), nav.indexOf(">X-D01<"),
                nav.indexOf(">X-N01<"), nav.indexOf(">X-42<"))).doesNotContain(-1).isSorted();
        assertThat(count(nav, "aria-current=\"page\"")).isEqualTo(1);
    }

    @Test
    void twoPagesWithOneCodeFailTheBuild() {
        assertThatThrownBy(() -> Sheets.unique(List.of("demos/deck-a.html", "demos/deck-b.html"), page -> "X-D01"))
                .isInstanceOf(DocsException.class).hasMessageContaining("the sheet code X-D01 is given to "
                        + "demos/deck-a.html and to demos/deck-b.html; each sheet has a code of its own");
        Sheets.unique(site.pages().stream().toList(), Sheets::code);
    }

    @Test
    void aDecksSheetsShowTheRevisionTheUpdateDateAndTheMilestoneApart() {
        assertThat(titleBlock("demos/deck-milestone.html")).contains(
                "<div><dt>Source</dt><dd>docs/demos/deck-milestone.md</dd></div>",
                "<div><dt>Revision</dt><dd><code>01234567</code></dd></div>",
                "<div><dt>Updated</dt><dd>2026-11-07</dd></div>").doesNotContain("2026-11-10");
        assertThat(titleBlock("demos/deck-milestone-notes.html")).contains("<div><dt>Updated</dt><dd>2026-11-07</dd>");
        String deck = site.text("demos/deck-milestone.html");
        assertThat(deck).contains("Milestone <time datetime=\"2026-11-10\">2026-11-10</time>");
        // Each slide is drawn as a sheet of the set: the deck's code with the slide's number, and the revision.
        assertThat(count(deck, "<p class=\"slide-code\">X-D01<span aria-hidden=\"true\"> · ")).isEqualTo(4);
        assertThat(count(deck, "<p class=\"slide-source\">docs/demos/deck-milestone.md · Revision <code>01234567</code>"
                + "</p>")).isEqualTo(4);
        // A slide's number is never a sheet code: one code per sheet.
        assertThat(SHEET_CODE.matcher(deck).results().map(m -> m.group(1)).toList()).containsExactly("X-D01");
    }

    @Test
    void aDemonstrationsTitleBlockHasItsRecordsDateAndTheRegisterNone() {
        assertThat(titleBlock("demos/42-gate.html")).contains(
                "<div><dt>Source</dt><dd>docs/demos/42-gate.md</dd></div>",
                "<div><dt>Revision</dt><dd><code>01234567</code></dd></div>",
                "<div><dt>Updated</dt><dd>2026-11-07</dd></div>");
        assertThat(titleBlock("demos/how-to.html")).contains("<dt>Updated</dt>");
        assertThat(titleBlock("demos/index.html")).contains("<dt>Revision</dt>").doesNotContain("<dt>Updated</dt>");
    }

    @Test
    void theRegistersCodeColumnFitsSevenCharactersSuchAsXGuide() {
        // A code is 12 px monospace with 0.06em tracking, about 7.9 px a character: X-GUIDE needs 56 px.
        assertThat(site.text("assets/site.css")).contains(".site-nav a {\n  display: grid;\n  min-height: 24px;\n"
                + "  grid-template-columns: 56px minmax(0, 1fr);");
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
        // While only controls contribute passing evidence, the badge and the line say that it is the tooling's.
        boolean tooling = sources.capabilities().all().stream()
                .filter(c -> c.evidence().stream().anyMatch(sources.evidence()::passed)).allMatch(Capability::control);
        assertThat(line).contains(tooling ? "<span class=\"badge badge-shipped\">Tooling tests passing</span> "
                        : "<span class=\"badge badge-shipped\">Passing</span> ")
                .endsWith(" evidence test classes passed in the build that generated this site." + (tooling
                        ? " These tests verify repository tooling, not shipping behaviour." : ""))
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

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }
}
