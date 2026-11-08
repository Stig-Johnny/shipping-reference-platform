package io.github.stigjohnny.c4contractkit.api;

import java.util.List;

/**
 * The result of {@link WebConformance#check}: the roots it read, every component of the web container, every edge with
 * its status, every import of a package or a Node.js built-in with the rule and the approval behind it, and every
 * violation.
 *
 * <p>{@link WebConformance#check} returns the lists in a fixed order: roots by name, components by {@code web.id},
 * edges by source and then target, imports of packages and built-ins by file, line and specifier, violations by type,
 * then identifiers, then file and line. The lists are immutable.
 *
 * @param schema the report format, {@value #SCHEMA}
 * @param modelPath the model file, relative to the working directory, with {@code /} as separator
 * @param sourceRoot the web container's {@code web.root}, relative to the repository root
 * @param testRoots its {@code web.testRoots}, relative to the repository root
 * @param publicRoots its {@code web.publicRoots}, relative to the repository root
 * @param components the components of the web container
 * @param edges every declared relationship between two of its components, and every undeclared import between two
 *     of them (a {@link EdgeStatus#DIVERGENCE divergence})
 * @param externalImports every import of an npm package or a Node.js built-in, in every root, approved or not
 * @param violations everything that fails the check; empty when the sources conform
 */
public record WebReport(String schema, String modelPath, String sourceRoot, List<String> testRoots,
                        List<String> publicRoots, List<WebComponentResult> components, List<WebEdgeResult> edges,
                        List<WebExternalImport> externalImports, List<WebViolation> violations) {

    /** The report format that this version writes. */
    public static final String SCHEMA = "c4-contract-kit/web-report/v1";

    /** Copies the lists, so a report never changes after it is created. */
    public WebReport {
        testRoots = List.copyOf(testRoots);
        publicRoots = List.copyOf(publicRoots);
        components = List.copyOf(components);
        edges = List.copyOf(edges);
        externalImports = List.copyOf(externalImports);
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
