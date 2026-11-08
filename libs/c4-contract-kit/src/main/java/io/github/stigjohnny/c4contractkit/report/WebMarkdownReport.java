package io.github.stigjohnny.c4contractkit.report;

import static io.github.stigjohnny.c4contractkit.report.MarkdownReport.code;
import static io.github.stigjohnny.c4contractkit.report.MarkdownReport.count;
import static io.github.stigjohnny.c4contractkit.report.MarkdownReport.table;
import static io.github.stigjohnny.c4contractkit.report.MarkdownReport.text;
import static io.github.stigjohnny.c4contractkit.report.MarkdownReport.words;

import io.github.stigjohnny.c4contractkit.rules.ExternalImportItem;
import io.github.stigjohnny.c4contractkit.rules.ImportEvidenceItem;
import io.github.stigjohnny.c4contractkit.rules.WebComparison;
import io.github.stigjohnny.c4contractkit.rules.WebComponentSummary;
import io.github.stigjohnny.c4contractkit.rules.WebEdgeOutcome;
import io.github.stigjohnny.c4contractkit.rules.WebFinding;
import io.github.stigjohnny.c4contractkit.rules.WebFindingType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * report.md of the web import check, for people: the verdict and the roots first, then every violation with its
 * evidence (file, line, specifier, kind and target), then a table of every edge with its status, then every import of
 * a package or a Node.js built-in with the rule and the allowlist entry behind it, then the components. A PERMITTED
 * relationship without imports reads "no static evidence".
 */
public final class WebMarkdownReport {

    /** Violations whose two identifiers are an importing and an imported component. */
    private static final Set<WebFindingType> DIRECTED = Set.of(WebFindingType.DIVERGENCE, WebFindingType.ABSENCE,
            WebFindingType.ENTRY_POINT_BYPASSED);

    private WebMarkdownReport() {
    }

    public static String render(String schema, String modelPath, WebComparison comparison) {
        List<String> blocks = new ArrayList<>();
        blocks.add("# C4 web import report");
        blocks.add("**Verdict: " + (comparison.passed() ? "PASSED" : "FAILED") + "** with "
                + count(comparison.findings().size()) + ". Model: " + code(modelPath) + ". Web root: "
                + code(comparison.sourceRoot()) + ". Test roots: " + roots(comparison.testRoots()) + ". Public roots: "
                + roots(comparison.publicRoots()) + ". Report format: " + code(schema) + ".");
        blocks.add("## Violations");
        if (comparison.findings().isEmpty()) {
            blocks.add("None.");
        }
        for (WebFinding finding : comparison.findings()) {
            blocks.add("### " + finding.type().name() + ": " + text(String.join(
                    DIRECTED.contains(finding.type()) ? " -> " : ", ", finding.identifiers())));
            blocks.add(text(finding.message()));
            if (!finding.evidence().isEmpty()) {
                blocks.add(evidence(finding.evidence()));
            }
        }
        blocks.add("## Edges");
        blocks.add(comparison.edges().isEmpty() ? "None." : edges(comparison.edges()));
        blocks.add("## Packages and built-ins");
        blocks.add(comparison.externalImports().isEmpty() ? "None." : externalImports(comparison.externalImports()));
        blocks.add("## Components");
        blocks.add(comparison.components().isEmpty() ? "None." : components(comparison.components()));
        return String.join("\n\n", blocks) + "\n";
    }

    private static String evidence(List<ImportEvidenceItem> items) {
        return table(List.of("File", "Specifier", "Kind", "Target"), items.stream().map(item -> List.of(
                code(item.file() + ":" + item.line()),
                item.specifier().isEmpty() ? "(none)" : code(item.specifier()),
                text(item.kind()),
                item.target().isEmpty() ? "(none)" : code(item.target()))).toList());
    }

    private static String edges(List<WebEdgeOutcome> edges) {
        return table(List.of("Source", "Target", "Mode", "Status", "Imports"), edges.stream().map(edge -> List.of(
                text(edge.source()),
                text(edge.target()),
                edge.mode().name(),
                words(edge.verdict().name()),
                String.valueOf(edge.evidence().size()))).toList());
    }

    private static String externalImports(List<ExternalImportItem> items) {
        return table(List.of("File", "Specifier", "Target", "Version", "Rule", "Allowlist entry", "Status"),
                items.stream().map(item -> List.of(
                        code(item.file() + ":" + item.line()),
                        code(item.specifier()),
                        code(item.target()),
                        item.version().isEmpty() ? "(none)" : text(item.version()),
                        text(item.rule()),
                        item.allowlistEntry().isEmpty() ? "(none)" : text(item.allowlistEntry()),
                        words(item.verdict().name()))).toList());
    }

    private static String roots(List<String> roots) {
        return roots.isEmpty() ? "none" : joined(roots);
    }

    private static String components(List<WebComponentSummary> components) {
        return table(List.of("Component", "Status", "Files", "Paths", "Entry", "Packages"),
                components.stream().map(component -> List.of(
                        text(component.id()),
                        words(component.status().name()),
                        String.valueOf(component.fileCount()),
                        joined(component.paths()),
                        component.entry().map(MarkdownReport::code).orElse("(none)"),
                        component.packages().isEmpty() ? "(none)" : joined(component.packages()))).toList());
    }

    private static String joined(List<String> values) {
        return values.stream().map(MarkdownReport::code).collect(Collectors.joining(", "));
    }
}
