package io.github.stigjohnny.docs.site;

/**
 * What a page shows about its subject in three separate statements, each as HTML with a badge in words: the accepted
 * design, the planned implementation, and the verified evidence. Planned test names are acceptance targets and are
 * never shown as evidence.
 */
public record StatusPanel(String design, String implementation, String evidence) {

    /** The panel under its heading; {@code attributes}, such as a language, are written onto the section. */
    String html(String attributes) {
        return "<section class=\"status-panel\" aria-labelledby=\"site-status-title\"" + attributes + ">\n"
                + "<h2 id=\"site-status-title\" class=\"visually-hidden\">Status</h2>\n" + grid() + "</section>\n";
    }

    /** The three statements as a ruled list, without a heading: for a page's panel, or a slide that carries it. */
    String grid() {
        return "<dl class=\"status-grid\">\n" + item("Accepted design", design)
                + item("Planned implementation", implementation) + item("Verified evidence", evidence) + "</dl>\n";
    }

    private static String item(String term, String description) {
        return "<div class=\"status-item\"><dt>" + term + "</dt><dd>" + description + "</dd></div>\n";
    }
}
