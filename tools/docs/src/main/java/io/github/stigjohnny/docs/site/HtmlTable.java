package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A captioned table with column headers, inside a labelled region that scrolls sideways on narrow screens, so that the
 * page itself never scrolls horizontally. Cells are HTML that the caller produced safely; captions, labels and headers
 * are escaped here. A wide table keeps a minimum width, so its columns stay readable, and shows a hint while its region
 * is narrower than that.
 */
final class HtmlTable {

    /** The minimum width of a wide table: the stylesheet's {@code table-wide-model} and {@code table-wide-flow}. */
    enum Width {
        /** A model view's element or relationship table: 60rem. */
        MODEL("model"),
        /** A flow's three-column table: 48rem. */
        FLOW("flow");

        private final String cssName;

        Width(String cssName) {
            this.cssName = cssName;
        }

        String regionClass() {
            return "table-scroll table-wide-" + cssName;
        }
    }

    /** The visible hint at the start of a wide table's region; the stylesheet hides it once the table fits. */
    static final String HINT = "<p class=\"table-hint\">Scroll horizontally for all columns.</p>\n";

    private final String caption;
    private final String label;
    private final List<String> headers;
    private final StringBuilder body = new StringBuilder();
    private boolean grouped;
    private @Nullable Width width;

    private HtmlTable(String caption, String label, List<String> headers) {
        this.caption = caption;
        this.label = label;
        this.headers = List.copyOf(headers);
    }

    /** A table with a caption, the region's accessible name and plain-text column headers. */
    static HtmlTable of(String caption, String label, String... headers) {
        return new HtmlTable(caption, label, List.of(headers));
    }

    /** Keeps the table at the given minimum width, scrolling inside its region, with the hint while it scrolls. */
    HtmlTable wide(Width minimum) {
        width = minimum;
        return this;
    }

    /** A row whose first cell is its row header. */
    HtmlTable row(String... cells) {
        return row("", List.of(cells));
    }

    HtmlTable row(String rowClass, List<String> cells) {
        if (cells.size() != headers.size()) {
            throw new IllegalArgumentException("a row has " + cells.size() + " cells for " + headers.size()
                    + " columns");
        }
        body.append(rowClass.isEmpty() ? "<tr>" : "<tr class=\"" + Html.escape(rowClass) + "\">");
        for (int i = 0; i < cells.size(); i++) {
            body.append(i == 0 ? "<th scope=\"row\">" : "<td>").append(cells.get(i)).append(i == 0 ? "</th>" : "</td>");
        }
        body.append("</tr>\n");
        return this;
    }

    /** Starts a group of rows under a header that spans the table. */
    HtmlTable group(String headerHtml) {
        body.append(grouped ? "</tbody>\n<tbody>\n" : "<tbody>\n");
        grouped = true;
        body.append("<tr class=\"group\"><th scope=\"rowgroup\" colspan=\"").append(headers.size()).append("\">")
                .append(headerHtml).append("</th></tr>\n");
        return this;
    }

    String html() {
        StringBuilder out = new StringBuilder("<div class=\"")
                .append(width == null ? "table-scroll" : width.regionClass())
                .append("\" role=\"region\" tabindex=\"0\" aria-label=\"").append(Html.escape(label)).append("\">\n")
                .append(width == null ? "" : HINT).append("<table>\n<caption>").append(Html.escape(caption))
                .append("</caption>\n<thead><tr>");
        for (String header : headers) {
            out.append("<th scope=\"col\">").append(Html.escape(header)).append("</th>");
        }
        out.append("</tr></thead>\n").append(grouped ? "" : "<tbody>\n").append(body).append("</tbody>\n</table>\n</div>\n");
        return out.toString();
    }
}
