package io.github.stigjohnny.docs.site;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.BuiltSite;
import io.github.stigjohnny.docs.testing.Decks;
import io.github.stigjohnny.docs.testing.Demos;
import io.github.stigjohnny.docs.testing.Fixture;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The structure WCAG 2.1 AA needs from the HTML, as the binding GUI design states it: the page's language, a skip link,
 * landmarks, one heading per page, labelled controls and regions, active navigation in words and aria-current, and the
 * page template in its order. Contrast, focus, reduced motion and reflow are in the stylesheet (SiteAssetsTest). The copy
 * has two demonstrations and a milestone deck, so that every rule covers their sheets, the register of the Demos
 * section, the deck's slides and its notes too. Every page is English, the deck's included.
 */
class SiteAccessibilityTest {

    private static final Pattern REGION = Pattern.compile("<[a-z]+ [^>]*role=\"region\"[^>]*>");

    @TempDir
    static Path dir;

    private static BuiltSite site;

    @BeforeAll
    static void buildTheSite() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().withoutDemos().write(Demos.path("gate"), Demos.GATE)
                .write(Demos.path("visual"), Demos.VISUAL).write(Decks.PATH, Decks.SAMPLE);
        SiteBuilder.build(Sources.read(fixture.root(), List.of(fixture.reports())), dir.resolve("site"), "0123456789abcdef0123456789abcdef01234567");
        site = BuiltSite.read(dir.resolve("site"));
    }

    /** The deck's page, whose root also names its layout, so that the stylesheet puts the register above the slides. */
    private static final String DECK = "demos/deck-milestone.html";

    @Test
    void everyPageIsEnglishWithASkipLinkFirstAndTheLandmarks() {
        assertThat(site.pages()).contains(DECK, "demos/deck-milestone-notes.html");
        for (String page : site.pages()) {
            String html = site.text(page);
            assertThat(html).as(page).startsWith("<!DOCTYPE html>\n<html lang=\"en\""
                    + (page.equals(DECK) ? " class=\"deck\">" : ">"));
            String body = html.substring(html.indexOf("<body"));
            assertThat(body.indexOf("<a ")).as(page).isEqualTo(body.indexOf("<a class=\"skip-link\" href=\"#site-main\""));
            assertThat(html).as(page).contains("<header class=\"site-header\">", "<nav class=\"site-nav\" aria-label=\"Site\">",
                    "<main id=\"site-main\" class=\"site-main\" tabindex=\"-1\">", "<footer class=\"site-footer\">");
            assertThat(count(html, "<h1")).as(page).isEqualTo(1);
            assertThat(html).as(page).contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        }
    }

    @Test
    void theDeckAndItsNotesAreEnglishThroughoutWithEnglishControls() {
        for (String page : List.of(DECK, "demos/deck-milestone-notes.html")) {
            String html = site.text(page);
            // One language for the whole page, stated once: no region needs a language of its own.
            assertThat(count(html, " lang=\"")).as(page).isEqualTo(1);
            assertThat(html).as(page).doesNotContain("hreflang", "lang=\"nb\"").containsPattern("<h1>[^<]+</h1>");
        }
        assertThat(site.text(DECK)).contains(">Previous</a>", ">Overview</a>", ">Next</a>", ">Start slides</a>",
                "<h2 id=\"site-deck-index-title\">Overview</h2>");
    }

    @Test
    void eachSlideIsASectionLabelledByItsTitleAndTheSlideIndexIsANamedNavigation() {
        String deck = site.text(DECK);
        Matcher slide = Pattern.compile("<section class=\"slide\" id=\"([^\"]+)\" aria-labelledby=\"(site-slide-[0-9]+)\">")
                .matcher(deck);
        int slides = 0;
        while (slide.find()) {
            slides++;
            assertThat(deck).as(slide.group(1)).contains("<h2 id=\"" + slide.group(2) + "\">")
                    .contains("<a href=\"#" + slide.group(1) + "\">");
        }
        assertThat(slides).isEqualTo(4);
        assertThat(deck).contains("<nav class=\"deck-index\" id=\"site-deck-index\" aria-labelledby=\""
                + "site-deck-index-title\">\n<h2 id=\"site-deck-index-title\">Overview</h2>");
        // The slide number reads as words, never as a date: 01/03 is hidden from assistive technology.
        assertThat(deck).contains("<span aria-hidden=\"true\"> · 01/03</span><span class=\"visually-hidden\">, "
                + "slide 1 of 3</span>");
        String notes = site.text("demos/deck-milestone-notes.html");
        assertThat(count(notes, "<section class=\"deck-note\"")).isEqualTo(4);
        assertThat(notes).contains("aria-labelledby=\"site-note-1\">\n<h2 id=\"site-note-1\">");
    }

    @Test
    void theNavigationIsALabelledDisclosureAndMarksTheCurrentPage() {
        for (String page : site.pages()) {
            String html = site.text(page);
            assertThat(html).as(page).contains("<details class=\"site-nav-disclosure\" open>",
                    "<summary>Site navigation</summary>");
            if (!page.equals("404.html")) {
                String nav = html.substring(html.indexOf("<nav class=\"site-nav\""), html.indexOf("</nav>",
                        html.indexOf("<nav class=\"site-nav\"")));
                String breadcrumb = html.substring(html.indexOf("<nav class=\"breadcrumb\" aria-label=\"Breadcrumb\">"),
                        html.indexOf("</nav>", html.indexOf("<nav class=\"breadcrumb\"")));
                assertThat(count(nav, "aria-current=\"page\"")).as(page + " navigation").isEqualTo(1);
                assertThat(count(breadcrumb, "aria-current=\"page\"")).as(page + " breadcrumb").isEqualTo(1);
                assertThat(count(html, "aria-current=\"page\"")).as(page).isEqualTo(2);
            }
        }
    }

    @Test
    void theThemeControlIsALabelledGroupOfThreeChoices() {
        String html = site.text("index.html");

        assertThat(html).contains("<fieldset class=\"theme-switch\" hidden>", "<legend>Theme</legend>")
                .contains("<input type=\"radio\" name=\"theme\" value=\"system\" checked> System</label>",
                        "<input type=\"radio\" name=\"theme\" value=\"light\"> Light</label>",
                        "<input type=\"radio\" name=\"theme\" value=\"dark\"> Dark</label>");
    }

    @Test
    void everyScrollingRegionIsLabelledAndReachableByKeyboard() {
        for (String page : site.pages()) {
            Matcher region = REGION.matcher(site.text(page));
            while (region.find()) {
                assertThat(region.group()).as(page).contains("tabindex=\"0\"").containsPattern("aria-label=\"[^\"]+\"");
            }
        }
    }

    @Test
    void everyGeneratedTableHasACaptionAndColumnHeaders() {
        for (String page : site.pages()) {
            String html = site.text(page);
            int tables = count(html, "<table");
            assertThat(count(html, "<th")).as(page).isGreaterThanOrEqualTo(tables);
            int regions = count(html, "<div class=\"table-scroll\"")
                    + count(html, "<div class=\"table-scroll table-wide-");
            assertThat(regions).as(page).isEqualTo(tables);
        }
    }

    @Test
    void statusIsAlwaysInWords() {
        String brain = site.text("brain/index.html");

        assertThat(brain).contains("<span class=\"badge badge-planned\">Planned</span>",
                "<span class=\"badge badge-shipped\">Shipped</span>");
        assertThat(site.text("trust/allowlist.html")).contains("<span class=\"badge badge-rejected\">Rejected</span>");
    }

    @Test
    void theDemonstrationsRegisterIsACaptionedTableAndASheetStatesItsFactsInWords() {
        String index = site.text("demos/index.html");
        String sheet = site.text("demos/42-gate.html");

        assertThat(index).contains("<div class=\"table-scroll table-wide-demos\" role=\"region\" tabindex=\"0\" "
                + "aria-label=\"Demonstrations, scrolls sideways\">\n<p class=\"table-hint\">Scroll horizontally for "
                + "all columns.</p>\n<table>\n<caption>Demonstrations (2), newest recording first</caption>",
                "<th scope=\"row\"><a href=\"43-visual.html\">");
        // The pull request, the kind and the date are words beside the description; commands and recorded output are
        // labelled in words, not by their styling.
        assertThat(sheet).contains("Pull request #42 · Kind: gate · Recorded <time datetime=\"2026-11-07\">",
                "<p class=\"fence-label\">Command</p>", "<p class=\"fence-label\">Recorded output</p>");
        assertThat(sheet.indexOf("class=\"page-facts\"")).isBetween(sheet.indexOf("class=\"page-summary\""),
                sheet.indexOf("class=\"sheet-meta\""));
    }

    @Test
    void thePageTemplateKeepsItsOrder() {
        for (String page : site.pages()) {
            if (page.equals("404.html")) {
                continue;
            }
            String html = site.text(page);
            int breadcrumb = html.indexOf("class=\"breadcrumb\"");
            int title = html.indexOf("<h1");
            int summary = html.indexOf("class=\"page-summary\"");
            int status = html.indexOf("class=\"status-panel\"");
            int body = html.indexOf("class=\"page-body");
            int source = html.indexOf("class=\"page-source\"");
            assertThat(List.of(breadcrumb, title, summary, status, body, source)).as(page).isSorted()
                    .doesNotContain(-1);
            int contents = html.indexOf("class=\"page-contents\"");
            if (contents >= 0) {
                assertThat(contents).as(page).isBetween(status, body);
            }
            int related = html.indexOf("class=\"page-related\"");
            if (related >= 0) {
                assertThat(related).as(page).isBetween(body, source);
            }
        }
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }
}
