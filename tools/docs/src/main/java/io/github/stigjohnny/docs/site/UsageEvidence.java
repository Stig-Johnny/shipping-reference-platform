package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.repo.Allowlist;
import io.github.stigjohnny.docs.repo.AllowlistEntry;
import io.github.stigjohnny.docs.repo.DependencyUsage;
import io.github.stigjohnny.docs.repo.DependencyUsage.Location;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

/** Same-build declaration excerpts; links never open arbitrary files or relax the publication boundary. */
final class UsageEvidence {

    private UsageEvidence() {
    }

    static String entry(DependencyUsage usage, AllowlistEntry entry) {
        List<Location> locations = usage.locations(entry);
        if (locations.isEmpty()) {
            String state = DependencyUsage.supportsKind(entry.kind())
                    ? "No declaration found in the scanned inputs." : "Evidence not collected for this kind.";
            return "<p class=\"usage-note\">" + state + "</p><details class=\"dependency-usage\">"
                    + "<summary>Coverage limits</summary><p>"
                    + Html.escape(DependencyUsage.limitation(entry.kind())) + "</p></details>";
        }
        StringBuilder out = new StringBuilder("<details class=\"dependency-usage\"><summary>Current declarations (")
                .append(locations.size()).append(")</summary><ul>");
        for (Location location : locations) {
            out.append("<li><a href=\"#").append(id(location)).append("\">")
                    .append(Html.escape(label(location))).append("</a><br>")
                    .append(Html.escape(location.context())).append("</li>");
        }
        return out.append("</ul></details>").toString();
    }

    static String appendix(SiteContext context) {
        DependencyUsage usage = context.sources().dependencyUsage();
        List<Location> locations = context.sources().allowlist().entries().stream()
                .flatMap(entry -> usage.locations(entry).stream()).distinct()
                .sorted(Comparator.comparing(Location::path).thenComparingInt(Location::line)
                        .thenComparing(Location::context).thenComparing(Location::excerpt)).toList();
        StringBuilder out = new StringBuilder("<h2 id=\"usage-evidence\">Current usage evidence</h2>\n")
                .append("<p class=\"prose\">Declaration evidence is not runtime execution evidence. These excerpts ")
                .append("are generated from this build's repository inputs. Structured files name a field instead ")
                .append("of guessing a line number; vendored files report their observed digest. ")
                .append("The review history stays in this register when a declaration is removed.</p>\n");
        context.map().anchor(AllowlistPage.PAGE, "usage-evidence");
        for (Location location : locations) {
            String anchor = id(location);
            context.map().anchor(AllowlistPage.PAGE, anchor);
            out.append("<section class=\"usage-source\" aria-labelledby=\"").append(anchor).append("\">\n")
                    .append("<h3 id=\"").append(anchor).append("\">").append(Html.escape(label(location)))
                    .append("</h3>\n<p>").append(Html.escape(location.context())).append("</p>\n<pre><code>")
                    .append(Html.escape(location.excerpt())).append("</code></pre>\n")
                    .append("<p><a href=\"#entries\">Back to entries</a></p>\n</section>\n");
        }
        return out.toString();
    }

    private static String label(Location location) {
        return location.path() + (location.line() > 0 ? ":" + location.line() : "");
    }

    private static String id(Location location) {
        return "usage-source-" + Allowlist.sha256((location.path() + "\n" + location.line() + "\n"
                + location.context() + "\n" + location.excerpt()).getBytes(StandardCharsets.UTF_8));
    }
}
