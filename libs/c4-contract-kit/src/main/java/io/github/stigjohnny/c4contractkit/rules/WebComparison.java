package io.github.stigjohnny.c4contractkit.rules;

import java.util.Comparator;
import java.util.List;

/**
 * Everything the web import check found, in the order reports use: roots by name, components by id, edges by source
 * and then target, imports of packages and built-ins by file, line and specifier, findings by type, then identifiers
 * (element by element), then the file and line of their first evidence.
 * Whatever order the parts arrive in, a comparison holds them in this order, so identical inputs give identical
 * reports.
 *
 * @param sourceRoot the web root, relative to the repository root
 * @param testRoots the test roots, relative to the repository root
 * @param publicRoots the public roots, relative to the repository root
 * @param externalImports every import of a package or a Node.js built-in, approved or not
 */
public record WebComparison(String sourceRoot, List<String> testRoots, List<String> publicRoots,
                            List<WebComponentSummary> components, List<WebEdgeOutcome> edges,
                            List<ExternalImportItem> externalImports, List<WebFinding> findings) {

    /** Identifiers never contain NUL, so joining them with it orders them element by element. */
    private static final Comparator<WebFinding> FINDING_ORDER = Comparator
            .comparing((WebFinding finding) -> finding.type().name())
            .thenComparing(finding -> String.join("\u0000", finding.identifiers()))
            .thenComparing(finding -> finding.evidence().isEmpty() ? "" : finding.evidence().getFirst().file())
            .thenComparingInt(finding -> finding.evidence().isEmpty() ? 0 : finding.evidence().getFirst().line())
            .thenComparing(WebFinding::message);

    public WebComparison {
        testRoots = testRoots.stream().sorted().toList();
        publicRoots = publicRoots.stream().sorted().toList();
        components = components.stream().sorted(Comparator.comparing(WebComponentSummary::id)).toList();
        externalImports = externalImports.stream().sorted(ExternalImportItem.ORDER).toList();
        edges = edges.stream()
                .sorted(Comparator.comparing(WebEdgeOutcome::source).thenComparing(WebEdgeOutcome::target))
                .toList();
        findings = findings.stream().sorted(FINDING_ORDER).toList();
    }

    public boolean passed() {
        return findings.isEmpty();
    }
}
