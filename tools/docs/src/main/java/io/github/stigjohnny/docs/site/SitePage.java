package io.github.stigjohnny.docs.site;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One page of the site before it is wrapped in the shell, following the page template: breadcrumb, title, summary,
 * status, contents, body, related pages, source.
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
 */
public record SitePage(String path, String title, String navLabel, SiteSection section, String summary,
        @Nullable StatusPanel status, List<Contents> contents, String body, List<Related> related, String source,
        boolean diagrams) {

    public SitePage {
        contents = List.copyOf(contents);
        related = List.copyOf(related);
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
