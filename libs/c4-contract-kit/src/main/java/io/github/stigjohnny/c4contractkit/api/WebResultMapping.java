package io.github.stigjohnny.c4contractkit.api;

import io.github.stigjohnny.c4contractkit.rules.EdgeMode;
import io.github.stigjohnny.c4contractkit.rules.EdgeVerdict;
import io.github.stigjohnny.c4contractkit.rules.ImportEvidenceItem;
import io.github.stigjohnny.c4contractkit.rules.WebComparison;
import io.github.stigjohnny.c4contractkit.rules.WebComponentSummary;
import io.github.stigjohnny.c4contractkit.rules.WebEdgeOutcome;
import io.github.stigjohnny.c4contractkit.rules.WebFinding;
import io.github.stigjohnny.c4contractkit.rules.WebFindingType;
import java.util.List;

/** Turns the internal web comparison into the public result records, and back for the report writers. */
final class WebResultMapping {

    private WebResultMapping() {
    }

    static WebReport toReport(String modelPath, WebComparison comparison) {
        return new WebReport(WebReport.SCHEMA, modelPath, comparison.sourceRoot(),
                comparison.components().stream().map(component -> new WebComponentResult(component.id(),
                        component.name(), status(component.status()), component.paths(), component.entry(),
                        component.packages(), component.fileCount())).toList(),
                comparison.edges().stream().map(edge -> new WebEdgeResult(edge.source(), edge.target(),
                        mode(edge.mode()), status(edge.verdict()), evidence(edge.evidence()))).toList(),
                comparison.findings().stream().map(finding -> new WebViolation(
                        WebViolationType.valueOf(finding.type().name()), finding.identifiers(), finding.message(),
                        evidence(finding.evidence()))).toList());
    }

    /** The way back, for the report writers, which see only the internal comparison. */
    static WebComparison toComparison(WebReport report) {
        return new WebComparison(report.sourceRoot(),
                report.components().stream().map(component -> new WebComponentSummary(component.id(),
                        component.name(), status(component.status()), component.paths(), component.entry(),
                        component.packages(), component.fileCount())).toList(),
                report.edges().stream().map(edge -> new WebEdgeOutcome(edge.source(), edge.target(),
                        EdgeMode.valueOf(edge.mode().name()), EdgeVerdict.valueOf(edge.status().name()),
                        items(edge.evidence()))).toList(),
                report.violations().stream().map(violation -> new WebFinding(
                        WebFindingType.valueOf(violation.type().name()), violation.identifiers(), violation.message(),
                        items(violation.evidence()))).toList());
    }

    private static List<ImportEvidence> evidence(List<ImportEvidenceItem> items) {
        return items.stream().map(item -> new ImportEvidence(item.file(), item.line(), item.specifier(), item.kind(),
                item.target())).toList();
    }

    private static List<ImportEvidenceItem> items(List<ImportEvidence> evidence) {
        return evidence.stream().map(item -> new ImportEvidenceItem(item.file(), item.line(), item.specifier(),
                item.kind(), item.target())).toList();
    }

    private static ComponentStatus status(io.github.stigjohnny.c4contractkit.model.ComponentStatus status) {
        return ComponentStatus.valueOf(status.name());
    }

    private static io.github.stigjohnny.c4contractkit.model.ComponentStatus status(ComponentStatus status) {
        return io.github.stigjohnny.c4contractkit.model.ComponentStatus.valueOf(status.name());
    }

    private static Mode mode(EdgeMode mode) {
        return Mode.valueOf(mode.name());
    }

    private static EdgeStatus status(EdgeVerdict verdict) {
        return EdgeStatus.valueOf(verdict.name());
    }
}
