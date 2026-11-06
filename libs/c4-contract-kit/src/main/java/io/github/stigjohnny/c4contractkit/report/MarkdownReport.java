package io.github.stigjohnny.c4contractkit.report;

import io.github.stigjohnny.c4contractkit.rules.Comparison;
import io.github.stigjohnny.c4contractkit.rules.ComponentSummary;
import io.github.stigjohnny.c4contractkit.rules.EdgeOutcome;
import io.github.stigjohnny.c4contractkit.rules.EvidenceItem;
import io.github.stigjohnny.c4contractkit.rules.Finding;
import io.github.stigjohnny.c4contractkit.rules.FindingType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * report.md, for people: the verdict first, then every violation with its evidence, then a table of every edge with
 * its status, then the components. A PERMITTED relationship without references reads "no static evidence".
 */
public final class MarkdownReport {

    /** Violations whose two identifiers are a depending and a depended-on side. */
    private static final Set<FindingType> DIRECTED = Set.of(FindingType.DIVERGENCE, FindingType.ABSENCE,
            FindingType.ISOLATION_BREACH);

    private MarkdownReport() {
    }

    public static String render(String schema, String modelPath, Comparison comparison) {
        List<String> blocks = new ArrayList<>();
        blocks.add("# C4 contract report");
        blocks.add("**Verdict: " + (comparison.passed() ? "PASSED" : "FAILED") + "** with "
                + count(comparison.findings().size()) + ". Model: " + code(modelPath) + ". Report format: "
                + code(schema) + ".");
        blocks.add("## Violations");
        if (comparison.findings().isEmpty()) {
            blocks.add("None.");
        }
        for (Finding finding : comparison.findings()) {
            blocks.add("### " + finding.type().name() + ": " + text(String.join(
                    DIRECTED.contains(finding.type()) ? " -> " : ", ", finding.identifiers())));
            blocks.add(text(finding.message()));
            if (!finding.evidence().isEmpty()) {
                blocks.add(evidence(finding.evidence()));
            }
        }
        blocks.add("## Edges");
        blocks.add(comparison.edges().isEmpty() ? "None." : edges(comparison.edges()));
        blocks.add("## Components");
        blocks.add(comparison.components().isEmpty() ? "None." : components(comparison.components()));
        return String.join("\n\n", blocks) + "\n";
    }

    private static String count(int violations) {
        return switch (violations) {
            case 0 -> "no violations";
            case 1 -> "1 violation";
            default -> violations + " violations";
        };
    }

    private static String evidence(List<EvidenceItem> items) {
        return table(List.of("Origin class", "Origin member", "Target class", "Target member", "Kind", "Source"),
                items.stream().map(item -> List.of(
                        code(item.originClass()),
                        item.originMember().isEmpty() ? "(class)" : code(item.originMember()),
                        code(item.targetClass()),
                        item.targetMember().isEmpty() ? "(type)" : code(item.targetMember()),
                        text(item.kind()),
                        source(item))).toList());
    }

    private static String source(EvidenceItem item) {
        if (item.sourceFile().isEmpty()) {
            return "unknown";
        }
        return code(item.line() == 0 ? item.sourceFile() : item.sourceFile() + ":" + item.line());
    }

    private static String edges(List<EdgeOutcome> edges) {
        return table(List.of("Source", "Target", "Mode", "Status", "References"),
                edges.stream().map(edge -> List.of(
                        text(edge.source()),
                        text(edge.target()),
                        edge.mode().name(),
                        words(edge.verdict().name()),
                        String.valueOf(edge.evidence().size()))).toList());
    }

    private static String components(List<ComponentSummary> components) {
        return table(List.of("Component", "Status", "Classes", "Packages"),
                components.stream().map(component -> List.of(
                        text(component.id()),
                        words(component.status().name()),
                        String.valueOf(component.classCount()),
                        component.packages().stream().map(MarkdownReport::code).collect(Collectors.joining(", "))))
                        .toList());
    }

    private static String table(List<String> header, List<List<String>> rows) {
        List<String> lines = new ArrayList<>();
        lines.add(row(header));
        lines.add("|" + "---|".repeat(header.size()));
        rows.forEach(cells -> lines.add(row(cells)));
        return String.join("\n", lines);
    }

    private static String row(List<String> cells) {
        return "| " + String.join(" | ", cells) + " |";
    }

    /** NO_STATIC_EVIDENCE as "no static evidence". */
    private static String words(String constant) {
        return constant.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** Text on one line that cannot end a paragraph or a table cell. */
    private static String text(String value) {
        return value.replace("\r", " ").replace("\n", " ").replace("|", "\\|");
    }

    /** A code span whose fence is longer than any run of backticks inside. */
    private static String code(String value) {
        String fence = "`";
        while (value.contains(fence)) {
            fence = fence + "`";
        }
        String padded = fence.length() == 1 ? value : " " + value + " ";
        return fence + text(padded) + fence;
    }
}
