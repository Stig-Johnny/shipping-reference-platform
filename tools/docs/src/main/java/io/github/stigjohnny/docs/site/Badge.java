package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;

/** A status in words with a colour that repeats it; colour is never the only signal. */
public enum Badge {
    ACCEPTED("accepted"),
    PLANNED("planned"),
    PARTIAL("partial"),
    SHIPPED("shipped"),
    APPROVED("approved"),
    CONDITIONS("conditions"),
    REJECTED("rejected"),
    /** A test that failed in the build that generated the site: the failure colour, like a rejection. */
    FAILING("failing"),
    MATCHES("matches"),
    NONE("none"),
    NEUTRAL("neutral");

    private final String cssName;

    Badge(String cssName) {
        this.cssName = cssName;
    }

    /** {@code <span class="badge badge-...">Words</span>}. */
    public String html(String words) {
        return "<span class=\"badge badge-" + cssName + "\">" + Html.escape(words) + "</span>";
    }
}
