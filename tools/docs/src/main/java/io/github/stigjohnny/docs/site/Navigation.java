package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The site navigation and the breadcrumb. Every section is listed; the current section also lists its pages. The
 * current page is marked with {@code aria-current="page"} and the current section with text emphasis.
 */
final class Navigation {

    private Navigation() {
    }

    static String html(SitePage current, List<SitePage> pages, UnaryOperator<String> url) {
        StringBuilder out = new StringBuilder("<nav class=\"site-nav\" aria-label=\"Site\">\n<ul>\n");
        for (SiteSection section : SiteSection.values()) {
            boolean inSection = current.section() == section && current.status() != null;
            boolean isIndex = inSection && current.path().equals(section.index());
            out.append("<li><a class=\"nav-section").append(inSection ? " is-active" : "").append("\" href=\"")
                    .append(Html.escape(url.apply(section.index()))).append('"')
                    .append(isIndex ? " aria-current=\"page\"" : "").append('>').append(Html.escape(section.label()))
                    .append("</a>");
            if (inSection) {
                List<SitePage> children = pages.stream()
                        .filter(p -> p.section() == section && !p.path().equals(section.index())).toList();
                if (!children.isEmpty()) {
                    out.append("\n<ul>\n");
                    for (SitePage child : children) {
                        boolean isCurrent = child.path().equals(current.path());
                        String badge = child.navBadge();
                        out.append("<li><a href=\"").append(Html.escape(url.apply(child.path()))).append('"')
                                .append(isCurrent ? " aria-current=\"page\"" : "").append('>')
                                .append(Html.escape(child.navLabel()))
                                .append(badge == null ? "" : " " + Badge.NEUTRAL.html(badge)).append("</a></li>\n");
                    }
                    out.append("</ul>\n");
                }
            }
            out.append("</li>\n");
        }
        return out.append("</ul>\n</nav>\n").toString();
    }

    static String breadcrumb(SitePage page, UnaryOperator<String> url) {
        StringBuilder out = new StringBuilder("<nav class=\"breadcrumb\" aria-label=\"Breadcrumb\">\n<ol>\n");
        if (!page.path().equals(StartPages.INDEX)) {
            out.append("<li><a href=\"").append(Html.escape(url.apply(StartPages.INDEX))).append("\">Start</a></li>\n");
            if (page.section() != SiteSection.START && !page.isIndex()) {
                out.append("<li><a href=\"").append(Html.escape(url.apply(page.section().index()))).append("\">")
                        .append(Html.escape(page.section().label())).append("</a></li>\n");
            }
        }
        String label = page.isIndex() ? page.section().label() : page.navLabel();
        out.append("<li><span aria-current=\"page\">").append(Html.escape(label)).append("</span></li>\n");
        return out.append("</ol>\n</nav>\n").toString();
    }
}
