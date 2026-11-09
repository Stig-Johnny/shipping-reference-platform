package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A plain HTML index of the assembled public sheets, with no additional repository input. */
final class SearchPage {
    static final String PAGE = "search/index.html";
    // Input is this tool's rendered HTML, whose attribute values and text are escaped, never arbitrary HTML.
    private static final Pattern HEADING = Pattern.compile("<h([2-6])\\b[^>]*>(.*?)</h\\1>", Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]*>");

    private SearchPage() { }

    static SitePage of(List<SitePage> pages) {
        List<SitePage> indexed = new ArrayList<>(pages.stream().filter(sheet -> !sheet.path().equals(PAGE)).toList());
        indexed.add(page(""));
        StringBuilder out = new StringBuilder("""
                <div class="search-tools" hidden>
                <label for="site-search">Search titles, summaries and headings</label>
                <input type="search" id="site-search" placeholder="Try booking or tenant" autocomplete="off">
                <p id="search-status" role="status"></p>
                </div>
                <h2 id="all-pages">All published sheets</h2>
                <ol id="search-results">
                """);
        for (SitePage sheet : indexed) {
            out.append("<li data-search-page=\"").append(Html.escape(sheet.path())).append("\">\n")
                    .append("<h3 class=\"search-title\">")
                    .append(SiteContext.link(PAGE, sheet.path(), sheet.title())).append("</h3>\n")
                    .append("<p class=\"search-summary\">").append(Html.escape(sheet.summary())).append("</p>\n")
                    .append("<details><summary>Headings on this sheet</summary><ul class=\"search-headings\">\n");
            Matcher heading = HEADING.matcher(sheet.body());
            while (heading.find()) {
                // Keep escaped text escaped: decoding before removing tags would turn a literal <img> into markup.
                out.append("<li>").append(TAG.matcher(heading.group(2)).replaceAll("")).append("</li>\n");
            }
            out.append("</ul></details><p class=\"search-match\" hidden></p>\n</li>\n");
        }
        return page(out.append("</ol>\n").toString());
    }

    private static SitePage page(String body) {
        return new SitePage(PAGE, "Find a page", "Search", SiteSection.START,
                "Search the published titles, summaries and headings. Every sheet is listed below, even without JavaScript.",
                new StatusPanel(Statuses.design("accepted", "Issue 38: search the published pages only."),
                        "Available: local filtering of the complete HTML list.",
                        "The build checks the index against the assembled pages; search is not product evidence."),
                List.of(new SitePage.Contents("all-pages", "All published sheets")), body, List.of(),
                "the assembled public pages", false);
    }
}
