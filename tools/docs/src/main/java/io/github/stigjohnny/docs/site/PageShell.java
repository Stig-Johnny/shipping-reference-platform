package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import java.util.List;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * The shell around every page: the policy, the skip link, the banner with the register (the navigation disclosure)
 * and the theme switch, the main landmark with the sheet (its header and title block, then the page template), and
 * the footer. Links come from a {@link UnaryOperator} that turns
 * a site path into a URL: relative for every page, absolute under the Pages base path for the 404 page only, because
 * GitHub Pages serves that one at whatever path was requested.
 *
 * <p>The shell is English. A page in another language, a deck's, names its language on {@code <html>} and marks each of
 * the shell's English regions as English: the skip link, the banner, the sheet header (whose title is in the page's
 * language), the status panel, the lists of sections and related pages, the source line and the footer. A deck's
 * layout is named on {@code <html>} too, so that the stylesheet puts its register above its slides.
 */
final class PageShell {

    /** Pages that draw diagrams: Mermaid's inline styles need 'unsafe-inline' for styles, and nothing else. */
    static final String MERMAID_POLICY = "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; font-src 'self'; connect-src 'none'; worker-src 'none'; base-uri 'none'; "
            + "form-action 'none'";
    /** Every other page: no inline style at all. */
    static final String STRICT_POLICY = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' "
            + "data:; font-src 'self'; connect-src 'none'; worker-src 'none'; base-uri 'none'; form-action 'none'";
    static final String SITE_NAME = "Shipping reference platform";

    private PageShell() {
    }

    static String render(SitePage page, List<SitePage> pages, String revision, @Nullable String updated,
            UnaryOperator<String> url) {
        boolean english = page.lang().equals(SitePage.ENGLISH);
        String en = english ? "" : " lang=\"" + SitePage.ENGLISH + "\"";
        StringBuilder out = new StringBuilder("<!DOCTYPE html>\n<html lang=\"").append(Html.escape(page.lang()))
                .append(page.layout() == SitePage.Layout.DECK ? "\" class=\"deck\">" : "\">")
                .append("\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<meta http-equiv=\"Content-Security-Policy\" content=\"")
                .append(page.diagrams() ? MERMAID_POLICY : STRICT_POLICY).append("\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>").append(Html.escape(page.path().equals(StartPages.INDEX) ? SITE_NAME
                        : page.title() + " · " + SITE_NAME)).append("</title>\n")
                .append("<meta name=\"description\" content=\"").append(Html.escape(page.summary())).append("\">\n")
                .append("<link rel=\"icon\" href=\"data:,\">\n")
                .append("<link rel=\"stylesheet\" href=\"").append(url.apply(SiteBuilder.CSS)).append("\">\n");
        if (page.diagrams()) {
            out.append("<script src=\"").append(url.apply(SiteBuilder.MERMAID)).append("\" defer></script>\n");
        }
        out.append("<script src=\"").append(url.apply(SiteBuilder.JS)).append("\" defer></script>\n")
                .append("</head>\n<body>\n<a class=\"skip-link\" href=\"#site-main\"").append(en)
                .append(">Skip to main content</a>\n")
                .append("<div class=\"site\">\n<header class=\"site-header\"").append(en)
                .append(">\n<p class=\"site-name\"><a href=\"")
                .append(url.apply(StartPages.INDEX)).append("\">").append(SITE_NAME).append("</a></p>\n")
                .append("<p class=\"site-tagline\">Generated from the repository. Synthetic data only.</p>\n")
                .append("<details class=\"site-nav-disclosure\" open>\n<summary>Site navigation</summary>\n")
                .append(Navigation.html(page, pages, url))
                .append("<fieldset class=\"theme-switch\" hidden>\n<legend>Theme</legend>\n")
                .append("<label><input type=\"radio\" name=\"theme\" value=\"system\" checked> System</label>\n")
                .append("<label><input type=\"radio\" name=\"theme\" value=\"light\"> Light</label>\n")
                .append("<label><input type=\"radio\" name=\"theme\" value=\"dark\"> Dark</label>\n")
                .append("</fieldset>\n</details>\n</header>\n")
                .append("<main id=\"site-main\" class=\"site-main\" tabindex=\"-1\">\n<div class=\"page\">\n");
        out.append("<header class=\"sheet-header\"").append(en).append(">\n");
        StatusPanel status = page.status();
        if (status != null) {
            out.append(Navigation.breadcrumb(page, url))
                    .append("<p class=\"sheet-code\"><span class=\"visually-hidden\">Sheet </span>")
                    .append(Sheets.code(page.path())).append("</p>\n");
        }
        out.append(english ? "<h1>" : "<h1 lang=\"" + Html.escape(page.lang()) + "\">").append(Html.escape(page.title()))
                .append("</h1>\n")
                .append("<p class=\"page-summary\">").append(Html.escape(page.summary())).append("</p>\n");
        String facts = page.facts();
        if (facts != null) {
            out.append(facts);
        }
        if (status != null) {
            out.append(sheetMeta(page, revision, updated));
        }
        out.append("</header>\n");
        if (status != null) {
            out.append(status.html(en));
        }
        if (!page.contents().isEmpty()) {
            out.append("<nav class=\"page-contents\" aria-labelledby=\"site-contents-title\"").append(en)
                    .append(">\n<details>\n")
                    .append("<summary id=\"site-contents-title\">On this sheet (").append(page.contents().size())
                    .append(")</summary>\n<ol>\n");
            for (SitePage.Contents entry : page.contents()) {
                out.append("<li><a href=\"#").append(Html.escape(entry.id())).append("\">")
                        .append(Html.escape(entry.text())).append("</a></li>\n");
            }
            out.append("</ol>\n</details>\n</nav>\n");
        }
        out.append("<div class=\"page-body\">\n").append(page.body()).append("</div>\n");
        if (!page.related().isEmpty()) {
            out.append("<section class=\"page-related\" aria-labelledby=\"site-related-title\"").append(en)
                    .append(">\n")
                    .append("<h2 id=\"site-related-title\">Related pages</h2>\n<ul>\n");
            for (SitePage.Related related : page.related()) {
                out.append("<li><a href=\"").append(Html.escape(url.apply(related.path()))).append("\">")
                        .append(Html.escape(related.title())).append("</a></li>\n");
            }
            out.append("</ul>\n</section>\n");
        }
        out.append("<p class=\"page-source\"").append(en).append(">Source: ").append(Html.escape(page.source()))
                .append(". ").append(revision(revision)).append("</p>\n</div>\n</main>\n")
                .append("<footer class=\"site-footer\"").append(en).append(">\n<p>Generated from the repository's "
                        + "sources by its documentation tool. All data is synthetic. <a href=\"")
                .append(url.apply(LegalPages.LICENSE_PAGE))
                .append("\">Repository licence</a>, <a href=\"").append(url.apply(LegalPages.NOTICE_PAGE))
                .append("\">Repository NOTICE</a> and <a href=\"").append(url.apply(NoticesPage.PAGE))
                .append("\">Third-party notices</a>.</p>\n</footer>\n</div>\n</body>\n</html>\n");
        return out.toString();
    }

    /**
     * The sheet's title block: what the page is generated from, the revision it was built from (its first eight
     * characters; the full SHA is at the foot of the page) and, when the page renders one hand-written document, that
     * document's own update date. A page made from several sources shows no date rather than an invented one.
     */
    static String sheetMeta(SitePage page, String revision, @Nullable String updated) {
        StringBuilder out = new StringBuilder("<dl class=\"sheet-meta\">\n")
                .append("<div><dt>Source</dt><dd>").append(Html.escape(page.source())).append("</dd></div>\n")
                .append("<div><dt>Revision</dt><dd><code>").append(Html.escape(revision.substring(0, 8)))
                .append("</code></dd></div>\n");
        if (updated != null) {
            out.append("<div><dt>Updated</dt><dd>").append(Html.escape(updated)).append("</dd></div>\n");
        }
        return out.append("</dl>\n").toString();
    }

    /** The full commit SHA the site was built from, as plain text; the builder accepts nothing else. */
    static String revision(String revision) {
        return "Source revision: <code>" + Html.escape(revision) + "</code>.";
    }
}
