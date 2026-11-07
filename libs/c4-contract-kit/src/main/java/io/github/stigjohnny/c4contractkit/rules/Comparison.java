package io.github.stigjohnny.c4contractkit.rules;

import java.util.Comparator;
import java.util.List;

/**
 * Everything the comparison found, in the order reports use: components by id, edges by source and then target,
 * findings by type and then identifiers (element by element). Whatever order the parts arrive in, a comparison holds
 * them in this order, so identical inputs give identical reports.
 */
public record Comparison(List<ComponentSummary> components, List<EdgeOutcome> edges, List<Finding> findings) {

    /** Identifiers never contain NUL, so joining them with it orders them element by element. */
    private static final Comparator<Finding> FINDING_ORDER = Comparator
            .comparing((Finding finding) -> finding.type().name())
            .thenComparing(finding -> String.join("\u0000", finding.identifiers()));

    public Comparison {
        components = components.stream().sorted(Comparator.comparing(ComponentSummary::id)).toList();
        edges = edges.stream()
                .sorted(Comparator.comparing(EdgeOutcome::source).thenComparing(EdgeOutcome::target))
                .toList();
        findings = findings.stream().sorted(FINDING_ORDER).toList();
    }

    public boolean passed() {
        return findings.isEmpty();
    }
}
