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
                "flowchart: { htmlLabels: false", "fontFamily: 'system-ui, sans-serif'",
                "themeVariables: { fontFamily: 'system-ui, sans-serif', fontSize: '16px' }")
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
        String light = """
                  --surface: #FAF8F5;
                  --surface-raised: #FFFFFF;
                  --text: #1E293B;
                  --text-muted: #475569;
                  --border-input: #64748B;
                  --border-subtle: #E2E8F0;
                  --action: #0F766E;
                  --danger: #B91C1C;
                  --warning: #92400E;
                  --success: #166534;
                """;
        String dark = """
                  --surface: #0F172A;
                  --surface-raised: #1E293B;
                  --text: #F1F5F9;
                  --text-muted: #CBD5E1;
                  --border-input: #94A3B8;
                  --border-subtle: #334155;
                  --action: #2DD4BF;
                  --danger: #F87171;
                  --warning: #FBBF24;
                  --success: #4ADE80;
                """;

        assertThat(css).contains(":root {\n" + light, ":root[data-theme=\"dark\"] {\n" + dark)
                .contains("@media (prefers-color-scheme: dark) {\n  :root:not([data-theme=\"light\"]) {\n"
                        + dark.replace("\n  ", "\n    ").replaceFirst("^  ", "    "));
    }

    @Test
    void theStylesheetSetsTheShellTypeSpacingFocusAndMotion() {
        assertThat(css).contains("font-family: system-ui, sans-serif;", "font-family: ui-monospace, monospace;",
                "font-size: 16px;", "line-height: 1.5;", "font-size: 32px;", "font-size: 24px;", "font-size: 20px;",
                "font-size: 14px;")
                .contains("grid-template-columns: 240px minmax(0, 1fr);", "max-width: 1120px;", "max-width: 72ch;",
                        "padding: 32px;", "@media (max-width: 719.98px)", "padding: 16px;")
                .contains("outline: 2px solid var(--action);", "outline-offset: 2px;")
                .contains("@media (prefers-reduced-motion: reduce)")
                .contains("border-radius: 8px;").doesNotContain("box-shadow: 0", "@import", "url(http", "@font-face");
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
        assertThat(css).contains(".site-nav a {\n  display: block;\n  min-height: 24px;")
                .contains("min-height: 32px;");
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
        assertThat(css).contains(".site-main:focus {\n  outline: 2px solid var(--action);\n  outline-offset: -2px;\n}")
                .doesNotContain("outline: none");
        assertThat(js).contains("disclosure.contains(document.activeElement)", "summary.focus()",
                "a[aria-current=\"page\"]");
        // The focus transfer runs on every change, also when the disclosure was already open or closed: an open mobile
        // disclosure whose focused summary disappears at desktop width hands focus on too.
        assertThat(js).doesNotContain("if (disclosure.open === opening)");
    }

    @Test
    void legalTextsAreReadableAndReflow() {
        assertThat(css).contains("pre.license {\n  max-width: 72ch;\n  font-size: 16px;\n  line-height: 1.5;\n"
                + "  white-space: pre-wrap;\n  overflow-wrap: anywhere;");
    }

    @Test
    void theDiagramPanelIsWhiteInBothThemes() {
        assertThat(css).contains(".diagram-scroll {").contains("background: #FFFFFF;");
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }
}
