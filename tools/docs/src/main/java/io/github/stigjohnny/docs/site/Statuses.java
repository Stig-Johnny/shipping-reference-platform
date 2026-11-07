package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.TestEvidence;
import java.util.ArrayList;
import java.util.Collection;
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
        String remainder = String.join(" ", capabilities.stream().map(Capability::plannedRemainder)
                .filter(text -> !text.isEmpty()).toList());
        return badge.html(allPlanned ? "Planned" : badge == Badge.SHIPPED ? "Shipped" : "Partly shipped") + " "
                + Html.escape(String.join(", ", parts) + named + "." + (remainder.isEmpty() ? "" : " " + remainder));
    }

    /** The passing evidence of the given capabilities; named tests that do not pass are never listed. */
    static String evidence(List<Capability> capabilities, TestEvidence evidence) {
        Set<String> passing = new LinkedHashSet<>();
        for (Capability capability : capabilities) {
            capability.evidence().stream().filter(evidence::passed).forEach(passing::add);
        }
        if (passing.isEmpty()) {
            String subset = String.join(" ", capabilities.stream().map(Capability::enforcedSubset)
                    .filter(text -> !text.isEmpty()).toList());
            if (!subset.isEmpty()) {
                return Badge.PARTIAL.html("Partly enforced") + " " + Html.escape(subset + " No acceptance test of "
                        + (capabilities.size() == 1 ? "the control" : "these controls") + " passes yet.");
            }
            return Badge.NONE.html("None yet") + " No passing test is evidence for this page yet; test names in its "
                    + "text are acceptance targets.";
        }
        boolean tooling = capabilities.stream().filter(c -> c.evidence().stream().anyMatch(evidence::passed))
                .allMatch(Capability::control);
        return tooling ? Badge.SHIPPED.html("Tooling tests passing") + " " + passed(passing, TOOLING)
                : Badge.SHIPPED.html("Passing") + " " + passed(passing, "");
    }

    /** What passing tests prove when only controls (the repository's tooling) contribute them. */
    static final String TOOLING = "These tests verify repository tooling, not shipping behaviour.";

    /**
     * Passing evidence as a count of test classes, then {@code limits} (which says what they do not prove, and is never
     * hidden), then the full list of class names in a disclosure. Built from the test names, never from HTML.
     */
    static String passed(Collection<String> tests, String limits) {
        return passed(tests, limits, "");
    }

    /** As {@link #passed(Collection, String)}, with {@code after}, HTML this tool wrote, ending the first line. */
    static String passed(Collection<String> tests, String limits, String after) {
        String count = tests.size() + (tests.size() == 1 ? " evidence test class" : " evidence test classes");
        return Html.escape(count + " passed in the build that generated this site." + (limits.isEmpty() ? ""
                : " " + limits)) + after + "\n<details class=\"evidence-tests\"><summary>Evidence test classes ("
                + tests.size() + ")</summary><p>" + Html.escape(String.join(", ", tests)) + "</p></details>";
    }

    static String words(String status) {
        return status.isEmpty() ? status : status.substring(0, 1).toUpperCase(Locale.ROOT) + status.substring(1);
    }
}
