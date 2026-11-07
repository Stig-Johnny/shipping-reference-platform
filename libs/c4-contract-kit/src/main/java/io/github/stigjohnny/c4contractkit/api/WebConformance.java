package io.github.stigjohnny.c4contractkit.api;

import io.github.stigjohnny.c4contractkit.model.InvalidModelException;
import io.github.stigjohnny.c4contractkit.model.WebModel;
import io.github.stigjohnny.c4contractkit.model.WebModelReader;
import io.github.stigjohnny.c4contractkit.report.WebJsonReport;
import io.github.stigjohnny.c4contractkit.report.WebMarkdownReport;
import io.github.stigjohnny.c4contractkit.rules.WebComparison;
import io.github.stigjohnny.c4contractkit.rules.WebFinding;
import io.github.stigjohnny.c4contractkit.rules.WebRules;
import io.github.stigjohnny.c4contractkit.sources.SourceReader;
import io.github.stigjohnny.c4contractkit.sources.SourceSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checks the TypeScript sources of a web app against the web container of a C4 model written in Structurizr DSL, in
 * both directions: an import between two components that the model does not declare fails, and so does a REQUIRED
 * relationship without any import. The sources are read as data with a restricted import grammar; nothing in them is
 * executed, compiled or installed, and whatever the grammar does not cover fails the check instead of being skipped.
 */
public final class WebConformance {

    private WebConformance() {
    }

    /**
     * Parses the model in restricted mode, reads the web root it names and compares the two.
     *
     * @param model the Structurizr DSL file, for example {@code architecture/workspace.dsl}
     * @param repositoryRoot the directory that the model's {@code web.root} is relative to
     * @return what the comparison found; {@link WebReport#passed()} tells whether the sources conform
     * @throws ModelException listing every model problem, when the model is invalid or uses a rejected construct
     * @throws IllegalArgumentException if the web root is not a directory, or is or holds a symbolic link
     * @throws UncheckedIOException if a file under the web root cannot be read
     */
    public static WebReport check(Path model, Path repositoryRoot) {
        String modelPath = Conformance.displayPath(model);
        WebModel web;
        try {
            web = WebModelReader.read(model, modelPath);
        } catch (InvalidModelException e) {
            throw new ModelException(modelPath, e.problems());
        }
        SourceSet sources = SourceReader.read(repositoryRoot.resolve(web.root()), web.root());
        return WebResultMapping.toReport(modelPath, WebRules.compare(web, sources));
    }

    /**
     * Writes {@code report.json} and {@code report.md} to {@code reportDirectory}, then fails if the report did not pass.
     * The reports are written in every case, so a failing build always leaves its evidence behind. Both files are
     * UTF-8 with LF line ends and contain no timestamp: identical reports give identical bytes.
     *
     * @param report a report from {@link #check}
     * @param reportDirectory where the reports go, for example {@code build/reports/c4-contract-web}; created if
     *     missing, and earlier reports in it are replaced
     * @throws AssertionError if the report has violations; the message lists them
     * @throws UncheckedIOException if the reports cannot be written
     */
    public static void assertConforms(WebReport report, Path reportDirectory) {
        WebComparison comparison = WebResultMapping.toComparison(report);
        Path markdown = reportDirectory.resolve("report.md");
        try {
            Files.createDirectories(reportDirectory);
            Files.write(reportDirectory.resolve("report.json"),
                    WebJsonReport.render(report.schema(), report.modelPath(), comparison)
                            .getBytes(StandardCharsets.UTF_8));
            Files.write(markdown, WebMarkdownReport.render(report.schema(), report.modelPath(), comparison)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("The web import reports could not be written to " + reportDirectory, e);
        }
        if (!comparison.passed()) {
            int count = comparison.findings().size();
            StringBuilder message = new StringBuilder("The web sources do not conform to the C4 model ")
                    .append(report.modelPath()).append(": ").append(count)
                    .append(count == 1 ? " violation" : " violations").append(". Reports: ").append(markdown)
                    .append(" and report.json.");
            for (WebFinding finding : comparison.findings()) {
                message.append("\n  - ").append(finding.type().name()).append(' ')
                        .append(String.join(", ", finding.identifiers())).append(": ").append(finding.message());
            }
            throw new AssertionError(message.toString());
        }
    }
}
