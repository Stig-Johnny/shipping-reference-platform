package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import java.util.ArrayList;
import java.util.List;

/**
 * A captioned table with column headers, inside a labelled region that scrolls sideways on narrow screens, so that the
 * page itself never scrolls horizontally. Cells are HTML that the caller produced safely; captions, labels and headers
 * are escaped here.
 */
final class HtmlTable {

    private final String caption;
    private final String label;
    private final List<String> headers;
    private final StringBuilder body = new StringBuilder();
    private boolean grouped;

    private HtmlTable(String caption, String label, List<String> headers) {
        this.caption = caption;
        this.label = label;
        this.headers = List.copyOf(headers);
    }

    /** A table with a caption, the region's accessible name and plain-text column headers. */
    static HtmlTable of(String caption, String label, String... headers) {
        return new HtmlTable(caption, label, List.of(headers));
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
        StringBuilder out = new StringBuilder("<div class=\"table-scroll\" role=\"region\" tabindex=\"0\" aria-label=\"")
                .append(Html.escape(label)).append("\">\n<table>\n<caption>").append(Html.escape(caption))
                .append("</caption>\n<thead><tr>");
        for (String header : headers) {
            out.append("<th scope=\"col\">").append(Html.escape(header)).append("</th>");
        }
        out.append("</tr></thead>\n").append(grouped ? "" : "<tbody>\n").append(body).append("</tbody>\n</table>\n</div>\n");
        return out.toString();
    }
}
