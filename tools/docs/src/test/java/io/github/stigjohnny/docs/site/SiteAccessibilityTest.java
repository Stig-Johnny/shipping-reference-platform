package io.github.stigjohnny.docs.site;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.BuiltSite;
import io.github.stigjohnny.docs.testing.Decks;
import io.github.stigjohnny.docs.testing.Demos;
import io.github.stigjohnny.docs.testing.Fixture;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
 * section, the deck's slides and its notes too. The site is English; the deck and its notes are Norwegian Bokmål, with
 * the site's English regions marked as English.
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

    /** The pages that are not English: the deck and its notes, in Norwegian Bokmål. Every other page is English. */
    private static final Map<String, String> LANGUAGES = Map.of("demos/deck-milestone.html", "nb",
            "demos/deck-milestone-notes.html", "nb");

    @Test
    void everyPageStatesItsLanguageWithASkipLinkFirstAndTheLandmarks() {
        assertThat(site.pages()).containsAll(LANGUAGES.keySet());
        for (String page : site.pages()) {
            String html = site.text(page);
            String language = LANGUAGES.getOrDefault(page, "en");
            // On a page in another language, the site's English regions say that they are English.
            String english = language.equals("en") ? "" : " lang=\"en\"";
            assertThat(html).as(page).startsWith("<!DOCTYPE html>\n<html lang=\"" + language + "\"");
            String body = html.substring(html.indexOf("<body"));
            assertThat(body.indexOf("<a ")).as(page).isEqualTo(body.indexOf("<a class=\"skip-link\" href=\"#site-main\""));
            assertThat(html).as(page).contains("<header class=\"site-header\"" + english + ">",
                    "<nav class=\"site-nav\" aria-label=\"Site\">",
                    "<main id=\"site-main\" class=\"site-main\" tabindex=\"-1\">",
                    "<footer class=\"site-footer\"" + english + ">");
            assertThat(count(html, "<h1")).as(page).isEqualTo(1);
            assertThat(html).as(page).contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        }
    }

    @Test
    void theDeckAndItsNotesAreNorwegianAndMarkEachEnglishRegion() {
        for (String page : LANGUAGES.keySet()) {
            String html = site.text(page);
            assertThat(html).as(page).contains("<a class=\"skip-link\" href=\"#site-main\" lang=\"en\">Skip to main "
                    + "content</a>", "<header class=\"site-header\" lang=\"en\">",
                    "<header class=\"sheet-header\" lang=\"en\">",
                    "<section class=\"status-panel\" aria-labelledby=\"site-status-title\" lang=\"en\">",
                    "<p class=\"page-source\" lang=\"en\">", "<footer class=\"site-footer\" lang=\"en\">")
                    // The title is the deck's own, in its language; the body inherits the page's.
                    .containsPattern("<h1 lang=\"nb\">[^<]+</h1>")
                    .contains("<div class=\"page-body\">\n");
        }
        String deck = site.text("demos/deck-milestone.html");
        // The slide controls are Norwegian words; each slide's provenance line and the ledger on slide 02 are English.
        assertThat(deck).contains(">Forrige</a>", ">Oversikt</a>", ">Neste</a>", ">Start lysbildene</a>",
                "<p class=\"slide-source\" lang=\"en\">", "<div class=\"slide-ledger\" lang=\"en\">",
                "<p lang=\"en\">Recorded output \u2014 not rerun by this site build.")
                .doesNotContain(">Previous<", ">Next<", ">Index<");
    }

    @Test
    void eachSlideIsASectionLabelledByItsTitleAndTheSlideIndexIsANamedNavigation() {
        String deck = site.text("demos/deck-milestone.html");
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
                + "site-deck-index-title\">\n<h2 id=\"site-deck-index-title\">Oversikt</h2>");
        // The slide number reads as words, never as a date: 01/03 is hidden from assistive technology.
        assertThat(deck).contains("<span aria-hidden=\"true\"> · 01/03</span><span class=\"visually-hidden\">, "
                + "lysbilde 1 av 3</span>");
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
