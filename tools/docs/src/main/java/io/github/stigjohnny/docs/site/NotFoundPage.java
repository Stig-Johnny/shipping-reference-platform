package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The 404 page. GitHub Pages serves it at whatever path was requested, so relative links would break below the site's
 * root; it alone links through the Pages base path.
 */
final class NotFoundPage {

    static final String PAGE = "404.html";
    /** The project site's path on GitHub Pages: the repository's name. */
    static final String BASE_PATH = "/shipping-reference-platform/";

    private NotFoundPage() {
    }

    static UnaryOperator<String> urls() {
        return target -> BASE_PATH + target;
    }

    static SitePage of() {
        UnaryOperator<String> url = urls();
        StringBuilder body = new StringBuilder("<p class=\"prose\">There is no page at this address. It may have "
                + "moved, or the link that brought you here may be out of date.</p>\n<ul class=\"prose\">\n");
        for (SiteSection section : SiteSection.values()) {
            body.append("<li><a href=\"").append(Html.escape(url.apply(section.index()))).append("\">")
                    .append(Html.escape(section.label())).append("</a>: ").append(Html.escape(section.purpose()))
                    .append("</li>\n");
        }
        body.append("</ul>\n");
        return new SitePage(PAGE, "Page not found", "Page not found", SiteSection.START, "This address is not part of "
                + "the site.", null, List.of(), body.toString(), List.of(), "tools/docs", false);
    }
}
