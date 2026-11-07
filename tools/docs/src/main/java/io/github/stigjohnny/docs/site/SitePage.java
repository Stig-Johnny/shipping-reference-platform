package io.github.stigjohnny.docs.site;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One page of the site before it is wrapped in the shell, following the page template: breadcrumb, title, summary
 * (with its facts, where a page has them), status, contents, body, related pages, source.
 *
 * @param path the site path, for example {@code modules/quotes.html}
 * @param navLabel the page's label in the navigation and the breadcrumb
 * @param summary one paragraph of plain text under the title
 * @param status the accepted design, planned implementation and verified evidence; null only for the 404 page
 * @param contents the page's own sections, for the table of contents
 * @param body HTML that this tool wrote or rendered safely
 * @param related links to other pages, as site paths with titles
 * @param source what the page is generated from, as repository paths in words
 * @param diagrams whether the page draws Mermaid diagrams (and so loads Mermaid under its own policy)
 * @param navBadge a status word shown as a neutral badge after the label in the navigation, such as "Superseded"
 * @param facts HTML this tool wrote, shown beside the summary, such as a demonstration's pull request, kind and
 *     recorded date; null for a page without
 * @param lang the page's language, which {@code <html>} states: the site's, {@code en}; a deck's pages take the
 *     language their document declares, which the deck's check holds to the site's
 * @param layout how the shell lays the page out
 */
public record SitePage(String path, String title, String navLabel, SiteSection section, String summary,
        @Nullable StatusPanel status, List<Contents> contents, String body, List<Related> related, String source,
        boolean diagrams, @Nullable String navBadge, @Nullable String facts, String lang, Layout layout) {

    /** The site's language. */
    public static final String ENGLISH = "en";

    public SitePage {
        contents = List.copyOf(contents);
        related = List.copyOf(related);
    }

    /** How the shell lays a page out. */
    public enum Layout {
        /** A sheet beside the register. */
        SHEET,
        /** A deck: the register, the overview and the slide index above the slides, which have the full width. */
        DECK
    }

    /** An English sheet beside the register. */
    public SitePage(String path, String title, String navLabel, SiteSection section, String summary,
            @Nullable StatusPanel status, List<Contents> contents, String body, List<Related> related, String source,
            boolean diagrams, @Nullable String navBadge, @Nullable String facts) {
        this(path, title, navLabel, section, summary, status, contents, body, related, source, diagrams, navBadge, facts,
                ENGLISH, Layout.SHEET);
    }

    /** A page without facts beside its summary. */
    public SitePage(String path, String title, String navLabel, SiteSection section, String summary,
            @Nullable StatusPanel status, List<Contents> contents, String body, List<Related> related, String source,
            boolean diagrams, @Nullable String navBadge) {
        this(path, title, navLabel, section, summary, status, contents, body, related, source, diagrams, navBadge,
                null);
    }

    /** A page without a badge in the navigation. */
    public SitePage(String path, String title, String navLabel, SiteSection section, String summary,
            @Nullable StatusPanel status, List<Contents> contents, String body, List<Related> related, String source,
            boolean diagrams) {
        this(path, title, navLabel, section, summary, status, contents, body, related, source, diagrams, null);
    }

    /** An entry of the table of contents. */
    public record Contents(String id, String text) {
    }

    /** A related page. */
    public record Related(String path, String title) {
    }

    public boolean isIndex() {
        return path.equals(section.index());
    }
}
