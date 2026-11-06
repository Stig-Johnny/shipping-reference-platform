package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.TestEvidence;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The three statements of a page's status panel, built from the sources. Each starts with a badge in words. */
final class Statuses {

    private Statuses() {
    }

    /** The accepted design of a document: its status and its update date. */
    static String design(DocFile doc) {
        return design(doc.status(), "Updated " + doc.updated() + ".");
    }

    static String design(String status, String detail) {
        Badge badge = switch (status) {
            case "accepted" -> Badge.ACCEPTED;
            case "superseded" -> Badge.NEUTRAL;
            case "rejected", "deprecated" -> Badge.REJECTED;
            default -> Badge.PLANNED;
        };
        return badge.html(words(status)) + " " + Html.escape(detail);
    }

    /** What is planned and what is built of the given capabilities, in words; up to three are named. */
    static String implementation(List<Capability> capabilities) {
        if (capabilities.isEmpty()) {
            return Badge.NEUTRAL.html("Not applicable") + " This page specifies no capability or control of its own.";
        }
        List<String> parts = new ArrayList<>();
        for (Capability.Status status : Capability.Status.values()) {
            long count = capabilities.stream().filter(c -> c.status() == status).count();
            if (count > 0) {
                parts.add(count + " " + (count == 1 ? "capability" : "capabilities") + " " + status.value());
            }
        }
        boolean allPlanned = capabilities.stream().allMatch(c -> c.status() == Capability.Status.PLANNED);
        Badge badge = allPlanned ? Badge.PLANNED
                : capabilities.stream().allMatch(c -> c.status() == Capability.Status.SHIPPED) ? Badge.SHIPPED
                        : Badge.PARTIAL;
        String named = capabilities.size() <= 3
                ? " (" + String.join(", ", capabilities.stream().map(Capability::id).toList()) + ")" : "";
        return badge.html(allPlanned ? "Planned" : badge == Badge.SHIPPED ? "Shipped" : "Partly shipped") + " "
                + Html.escape(String.join(", ", parts) + named + ".");
    }

    /** The passing evidence of the given capabilities; named tests that do not pass are never listed. */
    static String evidence(List<Capability> capabilities, TestEvidence evidence) {
        Set<String> passing = new LinkedHashSet<>();
        for (Capability capability : capabilities) {
            capability.evidence().stream().filter(evidence::passed).forEach(passing::add);
        }
        if (passing.isEmpty()) {
            return Badge.NONE.html("None yet") + " No passing test is evidence for this page yet; test names in its "
                    + "text are acceptance targets.";
        }
        return Badge.SHIPPED.html("Passing") + " " + Html.escape(String.join(", ", passing)
                + " passed in the build that generated this site.");
    }

    static String words(String status) {
        return status.isEmpty() ? status : status.substring(0, 1).toUpperCase(Locale.ROOT) + status.substring(1);
    }
}
