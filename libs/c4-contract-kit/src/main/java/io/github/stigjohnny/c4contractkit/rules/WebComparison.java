package io.github.stigjohnny.c4contractkit.rules;

import java.util.Comparator;
import java.util.List;

/**
 * Everything the web import check found, in the order reports use: components by id, edges by source and then
 * target, findings by type, then identifiers (element by element), then the file and line of their first evidence.
 * Whatever order the parts arrive in, a comparison holds them in this order, so identical inputs give identical
 * reports.
 *
 * @param sourceRoot the web root, relative to the repository root
 */
public record WebComparison(String sourceRoot, List<WebComponentSummary> components, List<WebEdgeOutcome> edges,
                            List<WebFinding> findings) {

    /** Identifiers never contain NUL, so joining them with it orders them element by element. */
    private static final Comparator<WebFinding> FINDING_ORDER = Comparator
            .comparing((WebFinding finding) -> finding.type().name())
            .thenComparing(finding -> String.join("\u0000", finding.identifiers()))
            .thenComparing(finding -> finding.evidence().isEmpty() ? "" : finding.evidence().getFirst().file())
            .thenComparingInt(finding -> finding.evidence().isEmpty() ? 0 : finding.evidence().getFirst().line())
            .thenComparing(WebFinding::message);

    public WebComparison {
        components = components.stream().sorted(Comparator.comparing(WebComponentSummary::id)).toList();
        edges = edges.stream()
                .sorted(Comparator.comparing(WebEdgeOutcome::source).thenComparing(WebEdgeOutcome::target))
                .toList();
        findings = findings.stream().sorted(FINDING_ORDER).toList();
    }

    public boolean passed() {
        return findings.isEmpty();
    }
}
