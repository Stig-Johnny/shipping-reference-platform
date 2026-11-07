package io.github.stigjohnny.c4contractkit.api;

import java.util.List;

/**
 * The result of {@link Conformance#check}: every component, every edge with its status, and every violation.
 *
 * <p>{@link Conformance#check} returns the lists in a fixed order: components by {@code contract.id}, edges by source
 * and then target, violations by type and then identifiers. The lists are immutable.
 *
 * @param schema the report format, {@value #SCHEMA}
 * @param modelPath the model file, relative to the working directory, with {@code /} as separator
 * @param components the components of the checked container
 * @param edges every declared relationship between two components, and every undeclared dependency between two
 *     components (a {@link EdgeStatus#DIVERGENCE divergence})
 * @param violations everything that fails the check; empty when the code conforms
 */
public record Report(String schema, String modelPath, List<ComponentResult> components, List<EdgeResult> edges,
                     List<Violation> violations) {

    /** The report format that this version writes. */
    public static final String SCHEMA = "c4-contract-kit/report/v1";

    /** Copies the lists, so a report never changes after it is created. */
    public Report {
        components = List.copyOf(components);
        edges = List.copyOf(edges);
        violations = List.copyOf(violations);
    }

    /**
     * Whether the code conforms to the model.
     *
     * @return true when there is no violation
     */
    public boolean passed() {
        return violations.isEmpty();
    }
}
