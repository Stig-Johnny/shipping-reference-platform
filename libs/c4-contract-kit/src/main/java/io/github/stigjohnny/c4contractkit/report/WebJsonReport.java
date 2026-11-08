package io.github.stigjohnny.c4contractkit.report;

import static io.github.stigjohnny.c4contractkit.report.Json.array;
import static io.github.stigjohnny.c4contractkit.report.Json.member;
import static io.github.stigjohnny.c4contractkit.report.Json.object;
import static io.github.stigjohnny.c4contractkit.report.Json.strings;

import io.github.stigjohnny.c4contractkit.rules.ExternalImportItem;
import io.github.stigjohnny.c4contractkit.rules.ImportEvidenceItem;
import io.github.stigjohnny.c4contractkit.rules.WebComparison;
import io.github.stigjohnny.c4contractkit.rules.WebComponentSummary;
import io.github.stigjohnny.c4contractkit.rules.WebEdgeOutcome;
import io.github.stigjohnny.c4contractkit.rules.WebFinding;
import java.util.List;

/**
 * report.json of the web import check: the comparison as data, in the order of {@link WebComparison}, with the field
 * names of the public result records. No timestamp and no absolute path, so identical inputs give identical bytes.
 */
public final class WebJsonReport {

    private WebJsonReport() {
    }

    public static String render(String schema, String modelPath, WebComparison comparison) {
        return object(
                member("schema", schema),
                member("modelPath", modelPath),
                member("sourceRoot", comparison.sourceRoot()),
                member("testRoots", strings(comparison.testRoots())),
                member("publicRoots", strings(comparison.publicRoots())),
                member("passed", comparison.passed()),
                member("components", array(comparison.components().stream().map(WebJsonReport::component).toList())),
                member("edges", array(comparison.edges().stream().map(WebJsonReport::edge).toList())),
                member("externalImports", array(comparison.externalImports().stream()
                        .map(WebJsonReport::externalImport).toList())),
                member("violations", array(comparison.findings().stream().map(WebJsonReport::violation).toList())))
                .print();
    }

    private static Json component(WebComponentSummary component) {
        return object(
                member("id", component.id()),
                member("name", component.name()),
                member("status", component.status().name()),
                member("paths", strings(component.paths())),
                member("entry", component.entry().orElse("")),
                member("packages", strings(component.packages())),
                member("fileCount", component.fileCount()));
    }

    private static Json edge(WebEdgeOutcome edge) {
        return object(
                member("source", edge.source()),
                member("target", edge.target()),
                member("mode", edge.mode().name()),
                member("status", edge.verdict().name()),
                member("evidence", evidence(edge.evidence())));
    }

    private static Json externalImport(ExternalImportItem item) {
        return object(
                member("file", item.file()),
                member("line", item.line()),
                member("specifier", item.specifier()),
                member("target", item.target()),
                member("version", item.version()),
                member("rule", item.rule()),
                member("allowlistEntry", item.allowlistEntry()),
                member("status", item.verdict().name()));
    }

    private static Json violation(WebFinding finding) {
        return object(
                member("type", finding.type().name()),
                member("identifiers", strings(finding.identifiers())),
                member("message", finding.message()),
                member("evidence", evidence(finding.evidence())));
    }

    private static Json evidence(List<ImportEvidenceItem> items) {
        return array(items.stream().map(item -> object(
                member("file", item.file()),
                member("line", item.line()),
                member("specifier", item.specifier()),
                member("kind", item.kind()),
                member("target", item.target()))).toList());
    }
}
