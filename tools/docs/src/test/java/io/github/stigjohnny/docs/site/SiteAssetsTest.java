package io.github.stigjohnny.docs.site;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.BuiltSite;
import io.github.stigjohnny.docs.testing.Fixture;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The site's one stylesheet and one script: the binding design tokens and layout, and the Mermaid conditions of
 * security/reviews/vendored-js--mermaid--11.17.2.md (condition 3) on the only script that calls Mermaid.
 */
class SiteAssetsTest {

    @TempDir
    static Path dir;

    private static String css;
    private static String js;

    @BeforeAll
    static void buildTheSite() {
        Fixture fixture = Fixture.copy(dir).passingEvidence();
        SiteBuilder.build(Sources.read(fixture.root(), List.of(fixture.reports())), dir.resolve("site"), "0123456789abcdef0123456789abcdef01234567");
        BuiltSite site = BuiltSite.read(dir.resolve("site"));
        css = site.text("assets/site.css");
        js = site.text("assets/site.js");
    }

    @Test
    void theScriptInitialisesMermaidOnceStrictlyAndRendersOnlyTheGeneratorsDiagrams() {
        assertThat(count(js, "mermaid.initialize(")).isEqualTo(1);
        assertThat(js).contains("startOnLoad: false", "securityLevel: 'strict'", "htmlLabels: false",
                "flowchart: { htmlLabels: false", "fontFamily: 'system-ui, sans-serif'", "theme: 'base'",
                "fontFamily: 'system-ui, sans-serif', fontSize: '18px'",
                "background: '#FFFDF7', edgeLabelBackground: '#FFFDF7'",
                "primaryColor: '#F4F1E8', primaryTextColor: '#172B3A'",
                "primaryBorderColor: '#687987', textColor: '#172B3A'", "lineColor: '#172B3A', clusterBkg: '#FFFDF7'",
                "clusterBorder: '#687987'")
                .doesNotContain("theme: 'default'")
                .contains("document.querySelectorAll('pre.diagram-source[data-diagram]')")
                .doesNotContain("dompurifyConfig", "registerIconPacks", "loose", "sandbox", "mermaid.init(",
                        "startOnLoad: true");
    }

    @Test
    void theScriptStoresNothingFetchesNothingAndWritesNoMarkup() {
        assertThat(js).doesNotContain("localStorage", "sessionStorage", "indexedDB", "document.cookie", "fetch(",
                "XMLHttpRequest", "innerHTML", "outerHTML", "insertAdjacentHTML", "eval(", "new Function",
                "document.write", "import(", "setAttribute('style'");
    }

    @Test
    void theScriptProvidesTheThemeSwitchCopyAndEnlargement() {
        assertThat(js).contains("data-theme", "navigator.clipboard", "aria-pressed", "(min-width: 720px)")
                .doesNotContain("localStorage");
    }

    @Test
    void theStylesheetDefinesTheBindingTokensInBothThemes() {
        // docs/ui/documentation-site.md, "Tokens": drafting paper by default, a blueprint in the dark theme.
        String light = """
                  --page: #F4F1E8;
                  --sheet: #FFFDF7;
                  --ink: #172B3A;
                  --ink-muted: #4C5E6B;
                  --control: #687987;
                  --link: #164F86;
                  --failure: #A33225;
                  --rule: #CFD2CA;
                """;
        String dark = """
                  --page: #0B2538;
                  --sheet: #12344A;
                  --ink: #F3F0E6;
                  --ink-muted: #B8C8D1;
                  --control: #7F9CAB;
                  --link: #9ACBFF;
                  --failure: #FFAB9D;
                  --rule: #325369;
                """;

        assertThat(css).contains(":root {\n" + light, ":root[data-theme=\"dark\"] {\n" + dark)
                .contains("@media (prefers-color-scheme: dark) {\n  :root:not([data-theme=\"light\"]) {\n"
                        + dark.replace("\n  ", "\n    ").replaceFirst("^  ", "    "));
    }

    @Test
    void theStylesheetSetsTheShellTypeSpacingFocusAndMotion() {
        assertThat(css).contains("font-family: system-ui, sans-serif;",
                "font-family: ui-monospace, SFMono-Regular, Consolas, monospace;", "font-size: 16px;",
                "line-height: 1.5;", "font-size: 36px;", "font-size: 30px;", "font-size: 24px;", "font-size: 20px;",
                "font-size: 14px;", "letter-spacing: 0.06em;", "font-variant-numeric: tabular-nums;")
                .contains("grid-template-columns: 240px minmax(0, 1fr);", "max-width: 1120px;", "max-width: 72ch;",
                        "padding: 32px;", "@media (max-width: 719.98px)", "padding: 16px;")
                .contains("outline: 2px solid var(--link);", "outline-offset: 2px;")
                .contains("@media (prefers-reduced-motion: reduce)")
                .contains("border-radius: 2px;").doesNotContain("border-radius: 8px;", "box-shadow", "@import",
                        "url(", "@font-face", "gradient(", "rotate(");
    }

    @Test
    void theNavigationSummaryHidesOnlyWhileTheNavigationIsOpenSoTheNavigationCanNeverBeLost() {
        assertThat(css).contains("@media (min-width: 720px) {\n  .site-nav-disclosure[open] > summary {\n"
                + "    display: none;")
                .doesNotContain(".site-nav-disclosure > summary {\n    display: none;");
    }

    @Test
    void linksThatStandAloneInAListOrACardTitleAreAtLeast24PxTall() {
        assertThat(css).contains(".page-contents a,\n.page-related a,\n.card-title a {\n  display: inline-block;\n"
                + "  min-height: 24px;\n  max-width: 100%;\n  overflow-wrap: anywhere;");
        assertThat(css).contains(".site-nav a {\n  display: grid;\n  min-height: 24px;")
                .contains("min-height: 32px;");
        // Below 720 px, register rows and the navigation's summary are 44 px touch targets.
        int narrow = css.indexOf("@media (max-width: 719.98px)");
        assertThat(css.substring(narrow, css.indexOf("@media (min-width: 720px)")))
                .contains(".site-nav a {\n    min-height: 44px;", "summary {\n    min-height: 44px;");
    }

    @Test
    void moduleSummariesUseTheHeadingScaleAndTheProseMeasure() {
        assertThat(css).contains(".module-body h3 {\n  margin-top: 16px;\n  font-size: 20px;\n}")
                .contains(".module-body > p,\n.module-body > ul,\n");
        int measure = css.indexOf(".module-body > p,\n.module-body > ul,\n");
        assertThat(css.substring(measure, css.indexOf("}", measure))).contains("max-width: 72ch;");
    }

    @Test
    void reviewSummariesStayWithinTheProseMeasureAndReflow() {
        assertThat(css).contains(".group-summary {\n  max-width: min(72ch, calc(100vw - 80px));\n"
                + "  overflow-wrap: anywhere;\n}");
    }

    @Test
    void theMainRegionShowsItsFocusAndTheNavigationKeepsFocusWhenItOpensOrCloses() {
        assertThat(css).contains(".site-main:focus {\n  outline: 2px solid var(--link);\n  outline-offset: -2px;\n}")
                .doesNotContain("outline: none");
        assertThat(js).contains("disclosure.contains(active)", "summary.focus()",
                "a[aria-current=\"page\"]");
        // The focus transfer runs on every change, also when the disclosure was already open or closed: an open mobile
        // disclosure whose focused summary disappears at desktop width hands focus on too.
        assertThat(js).doesNotContain("if (disclosure.open === opening)");
        // Chrome's focus fixup can move focus to <body> before the media query's change event runs, so the script
        // remembers where focus was last, and a pointer press outside the navigation forgets it.
        assertThat(js).contains("document.addEventListener('focusin'", "document.addEventListener('pointerdown'",
                "active === document.body", "lost && lastInside", "lost && lastSummary");
    }

    @Test
    void legalTextsAreReadableAndReflow() {
        assertThat(css).contains("pre.license {\n  max-width: 72ch;\n  font-size: 16px;\n  line-height: 1.5;\n"
                + "  white-space: pre-wrap;\n  overflow-wrap: anywhere;");
    }

    @Test
    void theDiagramPanelIsTheLightSheetInBothThemes() {
        int panel = css.indexOf(".diagram-scroll {");
        assertThat(css.substring(panel, css.indexOf("}", panel))).contains("background: #FFFDF7;",
                "color: #172B3A;", "border: 1px solid var(--ink);");
    }

    @Test
    void theModelPagesWideTablesKeepAMinimumWidthAndTheirHintShowsOnlyWhileTheyScroll() {
        assertThat(css).contains(".table-wide-model,\n.table-wide-flow {\n  container-type: inline-size;\n}")
                .contains(".table-wide-model table {\n  min-width: 60rem;\n}")
                .contains(".table-wide-flow table {\n  min-width: 48rem;\n}")
                .contains("@container (min-width: 60rem) {\n  .table-wide-model .table-hint {\n    display: none;\n")
                .contains("@container (min-width: 48rem) {\n  .table-wide-flow .table-hint {\n    display: none;\n");
        // The region stays the one that scrolls; the page never does.
        assertThat(css).contains(".table-scroll {\n  margin: 16px 0 24px;\n  overflow-x: auto;");
    }

    @Test
    void theDemoRegisterKeepsReadableColumnsOnPhonesAndItsHintShowsOnlyWhileItScrolls() {
        // A minimum width of 40rem, scrolling inside its region; up to 600 px wide a fixed layout with a 16rem first
        // column, so that an outcome and why it matters keep a readable measure; the hint hides once the table fits.
        assertThat(css).contains(".table-wide-demos {\n  container-type: inline-size;\n}",
                ".table-wide-demos table {\n  min-width: 40rem;\n}",
                "@container (min-width: 40rem) {\n  .table-wide-demos .table-hint {\n    display: none;\n",
                "@media (max-width: 600px) {\n  .table-wide-demos table {\n    table-layout: fixed;\n  }\n\n"
                        + "  .table-wide-demos th:first-child {\n    width: 16rem;\n  }\n}");
    }

    @Test
    void eachDrawnDiagramIsFittedToItsCompleteBoundsBeforeItShows() {
        // The bounds are measured in the drawing's own units: every shape, label and edge with half its stroke, and the
        // arrowhead at each marked end; then padding, and the same aspect ratio at every size.
        assertThat(js).contains("svg.getScreenCTM()", "shape.getBBox()", "'marker-start', 'marker-end'",
                "shape.getPointAtLength(", "svg.setAttribute('viewBox', ",
                "svg.setAttribute('preserveAspectRatio', 'xMidYMid meet')", "figure.setAttribute('data-fitted', 'true')");
        // Mermaid's own size limit gives way to the fitted size; enlargement keeps the same bounds and only widens it.
        assertThat(js).contains("svg.style.removeProperty('max-width')", "svg.style.setProperty('--diagram-width', ")
                .doesNotContain("svg.style.width =", "viewBox.baseVal");
        assertThat(css).contains(".diagram-scroll svg {\n  display: block;\n  max-width: 100%;\n  height: auto;\n")
                .contains("""
                        .diagram.is-enlarged .diagram-scroll svg {
                          width: max(var(--diagram-width, 100%), 150%);
                          max-width: none;
                        """);
        // Until it is fitted, only the drawing is invisible: never a cut frame, while the caption and the tables stay,
        // and without the script there is no drawing to hide, only its source.
        assertThat(css).contains(".diagram:not([data-fitted=\"true\"]) .diagram-scroll svg {\n  visibility: hidden;\n}");
        // A change of the region's width fits it again; a change of bounds hides the drawing until the next frame.
        assertThat(js).contains("new window.ResizeObserver(", "observer.observe(region)",
                "figure.removeAttribute('data-fitted')", "window.requestAnimationFrame(");
    }

    @Test
    void aDiagramThatCannotBeDrawnOrFittedShowsItsNoteAndSourceAndOffersNoEnlargement() {
        // The button is revealed only by revealEnlarge, after a successful fit.
        assertThat(count(js, "button.hidden = false")).isEqualTo(1);
        assertThat(js.indexOf("button.hidden = false")).isGreaterThan(js.indexOf("var revealEnlarge = function"));
        assertThat(js).contains("applyFit(figure, fit);\n          revealEnlarge(figure);",
                "failFigure(figure, source, originals[index]);", "}, failAll);", "} catch (error) {\n      failAll();",
                "source.textContent = original;", "button.hidden = true;", "note.hidden = false;");
        // A missing or incomplete Mermaid fails every figure on the page.
        assertThat(js).contains("if (diagrams.length > 0 && !(mermaid && typeof mermaid.initialize === 'function'\n"
                + "      && typeof mermaid.run === 'function')) {\n    failAll();");
    }

    @Test
    void statusWordsReflowAndNarrowScreensGetFortyFourPixelTargets() {
        int badge = css.indexOf("\n.badge {");
        assertThat(css.substring(badge, css.indexOf("}", badge))).contains("white-space: normal;",
                "max-width: 100%;", "overflow-wrap: anywhere;").doesNotContain("nowrap");
        int narrow = css.indexOf("@media (max-width: 719.98px)");
        String phone = css.substring(narrow, css.indexOf("@media (min-width: 720px)"));
        assertThat(phone).contains("""
                  .button,
                  .theme-switch label,
                  details > summary,
                  .evidence-tests summary,
                  .page-contents a,
                  .page-related a,
                  .card-title a,
                  .reading-paths a,
                  .actions > a,
                  .breadcrumb a,
                  .site-name a {
                    min-height: 44px;
                    min-width: 44px;
                  }
                """);
        int disclosure = css.indexOf(".evidence-tests summary {");
        assertThat(css.substring(disclosure, css.indexOf("}", disclosure))).contains("display: list-item;");
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }
}
