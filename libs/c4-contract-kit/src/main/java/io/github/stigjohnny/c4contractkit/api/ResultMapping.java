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

/** Turns the internal comparison into the public result records. */
final class ResultMapping {

    private ResultMapping() {
    }

    static Report toReport(String modelPath, Comparison comparison) {
        return new Report(Report.SCHEMA, modelPath,
                comparison.components().stream().map(ResultMapping::component).toList(),
                comparison.edges().stream().map(ResultMapping::edge).toList(),
                comparison.findings().stream().map(ResultMapping::violation).toList());
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
