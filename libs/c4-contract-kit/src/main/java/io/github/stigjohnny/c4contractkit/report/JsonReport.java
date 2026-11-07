package io.github.stigjohnny.c4contractkit.report;

import static io.github.stigjohnny.c4contractkit.report.Json.array;
import static io.github.stigjohnny.c4contractkit.report.Json.member;
import static io.github.stigjohnny.c4contractkit.report.Json.object;
import static io.github.stigjohnny.c4contractkit.report.Json.strings;

import io.github.stigjohnny.c4contractkit.rules.Comparison;
import io.github.stigjohnny.c4contractkit.rules.ComponentSummary;
import io.github.stigjohnny.c4contractkit.rules.EdgeOutcome;
import io.github.stigjohnny.c4contractkit.rules.EvidenceItem;
import io.github.stigjohnny.c4contractkit.rules.Finding;
import java.util.List;

/**
 * report.json: the comparison as data, in the order of {@link Comparison}, with the field names of the public result
 * records. No timestamp and no absolute path, so identical inputs give identical bytes.
 */
public final class JsonReport {

    private JsonReport() {
    }

    public static String render(String schema, String modelPath, Comparison comparison) {
        return object(
                member("schema", schema),
                member("modelPath", modelPath),
                member("passed", comparison.passed()),
                member("components", array(comparison.components().stream().map(JsonReport::component).toList())),
                member("edges", array(comparison.edges().stream().map(JsonReport::edge).toList())),
                member("violations", array(comparison.findings().stream().map(JsonReport::violation).toList())))
                .print();
    }

    private static Json component(ComponentSummary component) {
        return object(
                member("id", component.id()),
                member("name", component.name()),
                member("status", component.status().name()),
                member("packages", strings(component.packages())),
                member("classCount", component.classCount()));
    }

    private static Json edge(EdgeOutcome edge) {
        return object(
                member("source", edge.source()),
                member("target", edge.target()),
                member("mode", edge.mode().name()),
                member("status", edge.verdict().name()),
                member("evidence", evidence(edge.evidence())));
    }

    private static Json violation(Finding finding) {
        return object(
                member("type", finding.type().name()),
                member("identifiers", strings(finding.identifiers())),
                member("message", finding.message()),
                member("evidence", evidence(finding.evidence())));
    }

    private static Json evidence(List<EvidenceItem> items) {
        return array(items.stream().map(item -> object(
                member("originClass", item.originClass()),
                member("originMember", item.originMember()),
                member("targetClass", item.targetClass()),
                member("targetMember", item.targetMember()),
                member("kind", item.kind()),
                member("sourceFile", item.sourceFile()),
                member("line", item.line()))).toList());
    }
}
