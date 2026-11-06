package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The shell around every page: the policy, the skip link, the banner with the navigation disclosure and the theme
 * switch, the main landmark with the page template, and the footer. Links come from a {@link UnaryOperator} that turns
 * a site path into a URL: relative for every page, absolute under the Pages base path for the 404 page only, because
 * GitHub Pages serves that one at whatever path was requested.
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

    static String render(SitePage page, List<SitePage> pages, String revision, UnaryOperator<String> url) {
        StringBuilder out = new StringBuilder("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
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
                .append("</head>\n<body>\n<a class=\"skip-link\" href=\"#site-main\">Skip to main content</a>\n")
                .append("<div class=\"site\">\n<header class=\"site-header\">\n<p class=\"site-name\"><a href=\"")
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
        if (page.status() != null) {
            out.append(Navigation.breadcrumb(page, url));
        }
        out.append("<h1>").append(Html.escape(page.title())).append("</h1>\n")
                .append("<p class=\"page-summary\">").append(Html.escape(page.summary())).append("</p>\n");
        StatusPanel status = page.status();
        if (status != null) {
            out.append(status.html());
        }
        if (!page.contents().isEmpty()) {
            out.append("<nav class=\"page-contents\" aria-labelledby=\"site-contents-title\">\n")
                    .append("<h2 id=\"site-contents-title\">Contents</h2>\n<ol>\n");
            for (SitePage.Contents entry : page.contents()) {
                out.append("<li><a href=\"#").append(Html.escape(entry.id())).append("\">")
                        .append(Html.escape(entry.text())).append("</a></li>\n");
            }
            out.append("</ol>\n</nav>\n");
        }
        out.append("<div class=\"page-body\">\n").append(page.body()).append("</div>\n");
        if (!page.related().isEmpty()) {
            out.append("<section class=\"page-related\" aria-labelledby=\"site-related-title\">\n")
                    .append("<h2 id=\"site-related-title\">Related pages</h2>\n<ul>\n");
            for (SitePage.Related related : page.related()) {
                out.append("<li><a href=\"").append(Html.escape(url.apply(related.path()))).append("\">")
                        .append(Html.escape(related.title())).append("</a></li>\n");
            }
            out.append("</ul>\n</section>\n");
        }
        out.append("<p class=\"page-source\">Source: ").append(Html.escape(page.source())).append(". ")
                .append(revision(revision)).append("</p>\n</div>\n</main>\n")
                .append("<footer class=\"site-footer\">\n<p>Generated from the repository's sources by its documentation "
                        + "tool. All data is synthetic. <a href=\"").append(url.apply(LegalPages.LICENSE_PAGE))
                .append("\">Repository licence</a>, <a href=\"").append(url.apply(LegalPages.NOTICE_PAGE))
                .append("\">Repository NOTICE</a> and <a href=\"").append(url.apply(NoticesPage.PAGE))
                .append("\">Third-party notices</a>.</p>\n</footer>\n</div>\n</body>\n</html>\n");
        return out.toString();
    }

    /** A commit ID in code type; a local build says so in words; any other text is plain. */
    static String revision(String revision) {
        if (revision.isBlank()) {
            return "Built locally, not from a published commit.";
        }
        if (revision.matches("[0-9a-f]{7,64}")) {
            return "Revision <code>" + revision + "</code>.";
        }
        return "Revision " + Html.escape(revision) + ".";
    }
}
