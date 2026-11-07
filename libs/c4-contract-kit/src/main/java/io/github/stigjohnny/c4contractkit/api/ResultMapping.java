package io.github.stigjohnny.c4contractkit.api;

import io.github.stigjohnny.c4contractkit.rules.Comparison;
import io.github.stigjohnny.c4contractkit.rules.ComponentSummary;
import io.github.stigjohnny.c4contractkit.rules.EdgeMode;
import io.github.stigjohnny.c4contractkit.rules.EdgeOutcome;
import io.github.stigjohnny.c4contractkit.rules.EdgeVerdict;
import io.github.stigjohnny.c4contractkit.rules.EvidenceItem;
import io.github.stigjohnny.c4contractkit.rules.Finding;
import io.github.stigjohnny.c4contractkit.rules.FindingType;
import java.util.List;

/** Turns the internal comparison into the public result records, and back for the report writers. */
final class ResultMapping {

    private ResultMapping() {
    }

    static Report toReport(String modelPath, Comparison comparison) {
        return new Report(Report.SCHEMA, modelPath,
                comparison.components().stream().map(ResultMapping::component).toList(),
                comparison.edges().stream().map(ResultMapping::edge).toList(),
                comparison.findings().stream().map(ResultMapping::violation).toList());
    }

    /** The way back, for the report writers, which see only the internal comparison. */
    static Comparison toComparison(Report report) {
        return new Comparison(
                report.components().stream().map(component -> new ComponentSummary(component.id(), component.name(),
                        status(component.status()), component.packages(), component.classCount())).toList(),
                report.edges().stream().map(edge -> new EdgeOutcome(edge.source(), edge.target(), mode(edge.mode()),
                        verdict(edge.status()), items(edge.evidence()))).toList(),
                report.violations().stream().map(violation -> new Finding(type(violation.type()),
                        violation.identifiers(), violation.message(), items(violation.evidence()))).toList());
    }

    private static List<EvidenceItem> items(List<Evidence> evidence) {
        return evidence.stream().map(item -> new EvidenceItem(item.originClass(), item.originMember(),
                item.targetClass(), item.targetMember(), item.kind(), item.sourceFile(), item.line())).toList();
    }

    private static io.github.stigjohnny.c4contractkit.model.ComponentStatus status(ComponentStatus status) {
        return switch (status) {
            case BUILT -> io.github.stigjohnny.c4contractkit.model.ComponentStatus.BUILT;
            case PLANNED -> io.github.stigjohnny.c4contractkit.model.ComponentStatus.PLANNED;
        };
    }

    private static EdgeMode mode(Mode mode) {
        return switch (mode) {
            case REQUIRED -> EdgeMode.REQUIRED;
            case PERMITTED -> EdgeMode.PERMITTED;
            case UNDECLARED -> EdgeMode.UNDECLARED;
        };
    }

    private static EdgeVerdict verdict(EdgeStatus status) {
        return switch (status) {
            case CONVERGENCE -> EdgeVerdict.CONVERGENCE;
            case DIVERGENCE -> EdgeVerdict.DIVERGENCE;
            case ABSENCE -> EdgeVerdict.ABSENCE;
            case NO_STATIC_EVIDENCE -> EdgeVerdict.NO_STATIC_EVIDENCE;
            case NOT_EVALUATED -> EdgeVerdict.NOT_EVALUATED;
        };
    }

    private static FindingType type(ViolationType type) {
        return switch (type) {
            case DIVERGENCE -> FindingType.DIVERGENCE;
            case ABSENCE -> FindingType.ABSENCE;
            case UNMAPPED_CLASS -> FindingType.UNMAPPED_CLASS;
            case ISOLATION_BREACH -> FindingType.ISOLATION_BREACH;
            case PLANNED_COMPONENT_HAS_CODE -> FindingType.PLANNED_COMPONENT_HAS_CODE;
            case BUILT_COMPONENT_EMPTY -> FindingType.BUILT_COMPONENT_EMPTY;
        };
    }

    private static ComponentResult component(ComponentSummary component) {
        return new ComponentResult(component.id(), component.name(), status(component.status()), component.packages(),
                component.classCount());
    }

    private static EdgeResult edge(EdgeOutcome edge) {
        return new EdgeResult(edge.source(), edge.target(), mode(edge.mode()), status(edge.verdict()),
                evidence(edge.evidence()));
    }

    private static Violation violation(Finding finding) {
        return new Violation(type(finding.type()), finding.identifiers(), finding.message(),
                evidence(finding.evidence()));
    }

    private static List<Evidence> evidence(List<EvidenceItem> items) {
        return items.stream().map(item -> new Evidence(item.originClass(), item.originMember(), item.targetClass(),
                item.targetMember(), item.kind(), item.sourceFile(), item.line())).toList();
    }

    private static ComponentStatus status(io.github.stigjohnny.c4contractkit.model.ComponentStatus status) {
        return switch (status) {
            case BUILT -> ComponentStatus.BUILT;
            case PLANNED -> ComponentStatus.PLANNED;
        };
    }

    private static Mode mode(EdgeMode mode) {
        return switch (mode) {
            case REQUIRED -> Mode.REQUIRED;
            case PERMITTED -> Mode.PERMITTED;
            case UNDECLARED -> Mode.UNDECLARED;
        };
    }

    private static EdgeStatus status(EdgeVerdict verdict) {
        return switch (verdict) {
            case CONVERGENCE -> EdgeStatus.CONVERGENCE;
            case DIVERGENCE -> EdgeStatus.DIVERGENCE;
            case ABSENCE -> EdgeStatus.ABSENCE;
            case NO_STATIC_EVIDENCE -> EdgeStatus.NO_STATIC_EVIDENCE;
            case NOT_EVALUATED -> EdgeStatus.NOT_EVALUATED;
        };
    }

    private static ViolationType type(FindingType type) {
        return switch (type) {
            case DIVERGENCE -> ViolationType.DIVERGENCE;
            case ABSENCE -> ViolationType.ABSENCE;
            case UNMAPPED_CLASS -> ViolationType.UNMAPPED_CLASS;
            case ISOLATION_BREACH -> ViolationType.ISOLATION_BREACH;
            case PLANNED_COMPONENT_HAS_CODE -> ViolationType.PLANNED_COMPONENT_HAS_CODE;
            case BUILT_COMPONENT_EMPTY -> ViolationType.BUILT_COMPONENT_EMPTY;
        };
    }
}
