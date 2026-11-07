package io.github.stigjohnny.c4contractkit.api;

import io.github.stigjohnny.c4contractkit.report.JsonReport;
import io.github.stigjohnny.c4contractkit.report.MarkdownReport;
import io.github.stigjohnny.c4contractkit.rules.Comparison;
import io.github.stigjohnny.c4contractkit.rules.Finding;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Turns a {@link Report} into a test result, after writing it down. */
public final class ConformanceAssertions {

    private ConformanceAssertions() {
    }

    /**
     * Writes {@code report.json} and {@code report.md} to {@code reportDirectory}, then fails if the report did not pass.
     * The reports are written in every case, so a failing build always leaves its evidence behind. Both files are
     * UTF-8 with LF line ends and contain no timestamp: identical reports give identical bytes.
     *
     * @param report a report from {@link Conformance#check}
     * @param reportDirectory where the reports go, for example {@code build/reports/c4-contract}; created if missing,
     *     and earlier reports in it are replaced
     * @throws AssertionError if the report has violations; the message lists them
     * @throws UncheckedIOException if the reports cannot be written
     */
    public static void assertConforms(Report report, Path reportDirectory) {
        Comparison comparison = ResultMapping.toComparison(report);
        Path markdown = reportDirectory.resolve("report.md");
        try {
            Files.createDirectories(reportDirectory);
            write(reportDirectory.resolve("report.json"),
                    JsonReport.render(report.schema(), report.modelPath(), comparison));
            write(markdown, MarkdownReport.render(report.schema(), report.modelPath(), comparison));
        } catch (IOException e) {
            throw new UncheckedIOException("The C4 contract reports could not be written to " + reportDirectory, e);
        }
        if (!comparison.passed()) {
            throw new AssertionError(failure(report.modelPath(), comparison, markdown));
        }
    }

    /** Writes UTF-8; an unpaired surrogate, which only a hand-made report can hold, becomes a question mark. */
    private static void write(Path file, String content) throws IOException {
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String failure(String modelPath, Comparison comparison, Path markdown) {
        int count = comparison.findings().size();
        StringBuilder message = new StringBuilder("The code does not conform to the C4 model ").append(modelPath)
                .append(": ").append(count).append(count == 1 ? " violation" : " violations").append(". Reports: ")
                .append(markdown).append(" and report.json.");
        for (Finding finding : comparison.findings()) {
            message.append("\n  - ").append(finding.type().name()).append(' ')
                    .append(String.join(", ", finding.identifiers())).append(": ").append(finding.message());
        }
        return message.toString();
    }
}
