package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.repo.Acceptance;
import io.github.stigjohnny.docs.repo.Allowlist;
import io.github.stigjohnny.docs.repo.AllowlistEntry;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.ReviewRecord;
import io.github.stigjohnny.docs.repo.Sources;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The allowlist page: security/README.md, then the owner's current acceptance with its full digest, recomputed by this
 * build, the previous acceptances, and every entry, approved and rejected, with its scope, verdict, expiry, conditions
 * and the public summary of its review. Mermaid is never loaded here.
 */
final class AllowlistPage {

    static final String PAGE = "trust/allowlist.html";

    private AllowlistPage() {
    }

    static SitePage of(SiteContext context) {
        Sources sources = context.sources();
        Allowlist allowlist = sources.allowlist();
        Acceptance current = allowlist.current();
        String recomputed = allowlist.reviewedSetDigest(sources.reviewBytes());
        if (!allowlist.accepted() || !recomputed.equals(current.reviewedSetSha256())) {
            throw new DocsException(Allowlist.PATH + ": the recorded acceptance digest does not match the reviewed set "
                    + "(recomputed " + recomputed + "); the site never shows an acceptance that no longer holds");
        }
        StringBuilder body = new StringBuilder("<h2 id=\"current-acceptance\">Current acceptance</h2>\n<dl class=\"facts\">\n")
                .append(fact("Reviewed-set digest (SHA-256)", "<code class=\"digest\">"
                        + Html.escape(current.reviewedSetSha256()) + "</code>"))
                .append(fact("Recomputed by this build", Badge.MATCHES.html("Matches") + " The build that generated "
                        + "this site recomputed the digest from the " + allowlist.entries().size() + " entries and their "
                        + "review records, the way the preflight checker does."))
                .append(fact("Accepted", "<time datetime=\"" + Html.escape(current.at()) + "\">"
                        + Html.escape(current.at()) + "</time>, by the repository owner"))
                .append(fact("Record", Html.escape(record(current))));
        for (String note : current.notes()) {
            body.append(fact("Also recorded", Html.escape(note)));
        }
        body.append("</dl>\n<p class=\"prose\">The two earliest acceptances also recorded <code>entriesSha256</code>, "
                + "the first checker's digest over the enforced fields only: the pull request that introduced "
                + "<code>reviewedSetSha256</code> was judged by main's older checker, so its acceptance recorded both, "
                + "and no check reads the older digest any more.</p>\n");
        body.append("<h2 id=\"previous-acceptances\">Previous acceptances</h2>\n");
        HtmlTable previous = HtmlTable.of("Earlier acceptances of the reviewed set, newest first",
                "Previous acceptances, scrolls sideways", "Accepted", "Record", "reviewedSetSha256", "entriesSha256");
        for (Acceptance acceptance : allowlist.previous()) {
            previous.row(Html.escape(acceptance.at()), Html.escape(record(acceptance)),
                    digest(acceptance.reviewedSetSha256()), digest(acceptance.entriesSha256()));
        }
        body.append(previous.html());
        body.append("<h2 id=\"entries\">Entries</h2>\n").append(counts(allowlist)).append(entries(context));
        for (String id : List.of("current-acceptance", "previous-acceptances", "entries")) {
            context.map().anchor(PAGE, id);
        }
        SitePage readme = DocPages.of(context, sources.securityReadme(), SiteSection.TRUST, "Dependency allowlist",
                body.toString(), control(sources));
        List<SitePage.Contents> contents = new ArrayList<>(readme.contents());
        contents.add(new SitePage.Contents("current-acceptance", "Current acceptance"));
        contents.add(new SitePage.Contents("previous-acceptances", "Previous acceptances"));
        contents.add(new SitePage.Contents("entries", "Entries"));
        return new SitePage(PAGE, readme.title(), readme.navLabel(), SiteSection.TRUST, readme.summary(),
                readme.status(), contents, readme.body(), readme.related(), Sources.SECURITY_README + " and "
                        + Allowlist.PATH, false);
    }

    private static List<Capability> control(Sources sources) {
        return sources.capabilities().controls().stream().filter(c -> c.id().equals("control.dependency-allowlist"))
                .toList();
    }

    private static String counts(Allowlist allowlist) {
        Map<String, long[]> byKind = new TreeMap<>();
        for (AllowlistEntry entry : allowlist.entries()) {
            long[] counts = byKind.computeIfAbsent(entry.kind(), k -> new long[2]);
            counts[entry.rejected() ? 1 : 0]++;
        }
        HtmlTable table = HtmlTable.of("Entries by kind: " + allowlist.approved() + " approved and "
                + allowlist.rejected() + " rejected", "Entries by kind, scrolls sideways", "Kind", "Approved",
                "Rejected");
        byKind.forEach((kind, counts) -> table.row(Html.escape(kind), Long.toString(counts[0]),
                Long.toString(counts[1])));
        return table.html();
    }

    /** One table, grouped by review record: each group shows the review's public summary once. */
    private static String entries(SiteContext context) {
        Sources sources = context.sources();
        Allowlist allowlist = sources.allowlist();
        Map<String, List<AllowlistEntry>> byReview = new TreeMap<>();
        for (AllowlistEntry entry : allowlist.entries()) {
            byReview.computeIfAbsent(entry.reviewRecord(), r -> new ArrayList<>()).add(entry);
        }
        HtmlTable table = HtmlTable.of("All " + allowlist.entries().size() + " entries of " + Allowlist.PATH
                + ", approved and rejected, grouped by review record", "Allowlist entries, scrolls sideways",
                "Component and version", "Kind", "Scope", "Verdict", "Expires", "Conditions");
        List<Map.Entry<String, List<AllowlistEntry>>> groups = new ArrayList<>(byReview.entrySet());
        groups.sort(Comparator.comparing((Map.Entry<String, List<AllowlistEntry>> g) -> g.getValue().getFirst().kind())
                .thenComparing(Map.Entry::getKey));
        for (Map.Entry<String, List<AllowlistEntry>> group : groups) {
            ReviewRecord review = sources.reviews().get(group.getKey());
            if (review == null) {
                throw SiteContext.missing("the review record " + group.getKey());
            }
            table.group("<span class=\"group-title\">Review: " + Html.escape(review.component()) + "</span> "
                    + "<span class=\"group-summary\">" + context.fragment(review.summary(), group.getKey(), PAGE)
                    + "</span> <span class=\"group-record\">Record: <code>" + Html.escape(group.getKey())
                    + "</code> in the repository.</span>");
            List<AllowlistEntry> entries = new ArrayList<>(group.getValue());
            entries.sort(Comparator.comparing(AllowlistEntry::coordinate).thenComparing(AllowlistEntry::version));
            for (AllowlistEntry entry : entries) {
                table.row(entry.rejected() ? "entry rejected" : "entry", List.of(
                        "<code>" + Html.escape(entry.coordinate()) + "</code> <span class=\"version\">"
                                + Html.escape(entry.version()) + "</span>",
                        Html.escape(entry.kind()), Html.escape(entry.scope()), verdict(entry),
                        "<time datetime=\"" + Html.escape(entry.expiresAt()) + "\">" + Html.escape(entry.expiresAt())
                                + "</time>", conditions(context, entry)));
            }
        }
        return table.html();
    }

    private static String verdict(AllowlistEntry entry) {
        return switch (entry.verdict()) {
            case "REJECT" -> Badge.REJECTED.html("Rejected");
            case "APPROVE" -> Badge.APPROVED.html("Approved");
            default -> Badge.CONDITIONS.html(entry.verdictWords());
        };
    }

    private static String conditions(SiteContext context, AllowlistEntry entry) {
        if (entry.conditions().isEmpty()) {
            return "None";
        }
        StringBuilder out = new StringBuilder("<ul class=\"conditions\">");
        for (String condition : entry.conditions()) {
            out.append("<li>").append(context.fragment(condition, Allowlist.PATH, PAGE)).append("</li>");
        }
        return out.append("</ul>").toString();
    }

    private static String record(Acceptance acceptance) {
        return acceptance.record().isEmpty() ? "not recorded" : acceptance.record() + " in the repository";
    }

    private static String digest(String sha256) {
        return sha256.isEmpty() ? "not recorded" : "<code class=\"digest\">" + Html.escape(sha256) + "</code>";
    }

    private static String fact(String term, String description) {
        return "<div><dt>" + Html.escape(term) + "</dt><dd>" + description + "</dd></div>\n";
    }
}
