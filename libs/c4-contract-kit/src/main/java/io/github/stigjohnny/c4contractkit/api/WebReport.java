package io.github.stigjohnny.c4contractkit.api;

import java.util.List;

/**
 * The result of {@link WebConformance#check}: every component of the web container, every edge with its status, and
 * every violation.
 *
 * <p>{@link WebConformance#check} returns the lists in a fixed order: components by {@code web.id}, edges by source and
 * then target, violations by type, then identifiers, then file and line. The lists are immutable.
 *
 * @param schema the report format, {@value #SCHEMA}
 * @param modelPath the model file, relative to the working directory, with {@code /} as separator
 * @param sourceRoot the web container's {@code web.root}, relative to the repository root
 * @param components the components of the web container
 * @param edges every declared relationship between two of its components, and every undeclared import between two
 *     of them (a {@link EdgeStatus#DIVERGENCE divergence})
 * @param violations everything that fails the check; empty when the sources conform
 */
public record WebReport(String schema, String modelPath, String sourceRoot, List<WebComponentResult> components,
                        List<WebEdgeResult> edges, List<WebViolation> violations) {

    /** The report format that this version writes. */
    public static final String SCHEMA = "c4-contract-kit/web-report/v1";

    /** Copies the lists, so a report never changes after it is created. */
    public WebReport {
        components = List.copyOf(components);
        edges = List.copyOf(edges);
        violations = List.copyOf(violations);
    }

    /**
     * Whether the web sources conform to the model.
     *
     * @return true when there is no violation
     */
    public boolean passed() {
        return violations.isEmpty();
    }
}
