package io.github.stigjohnny.c4contractkit.api;

import io.github.stigjohnny.c4contractkit.model.InvalidModelException;
import io.github.stigjohnny.c4contractkit.model.WebModel;
import io.github.stigjohnny.c4contractkit.model.WebModelReader;
import io.github.stigjohnny.c4contractkit.report.WebJsonReport;
import io.github.stigjohnny.c4contractkit.report.WebMarkdownReport;
import io.github.stigjohnny.c4contractkit.rules.WebComparison;
import io.github.stigjohnny.c4contractkit.rules.WebFinding;
import io.github.stigjohnny.c4contractkit.rules.WebRules;
import io.github.stigjohnny.c4contractkit.rules.WebInputs;
import io.github.stigjohnny.c4contractkit.sources.Allowlist;
import io.github.stigjohnny.c4contractkit.sources.AppConfiguration;
import io.github.stigjohnny.c4contractkit.sources.RootKind;
import io.github.stigjohnny.c4contractkit.sources.SourceReader;
import io.github.stigjohnny.c4contractkit.sources.SourceSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Partial static check of detected TypeScript imports against the web container of a C4 model written in Structurizr
 * DSL, in both directions: a detected import without a declared component relationship fails, as does a REQUIRED
 * relationship without a detected import. Every detected package and Node.js built-in import must be declared in the model and
 * approved in the dependency allowlist. Sources, tests, public files and configuration are read as data; nothing is
 * executed, compiled or installed. Reported reader refusals fail the check, but imports and unsupported constructs
 * can be missed silently, including loaders after spread syntax. A passing report is not a complete import graph
 * or proof of runtime safety (ADR 0015). Repository integration in WebArchitectureConformanceTest checks real imports in all
 * six built web parts. Compiler-graph boundary enforcement and browser verification remain outstanding.
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
     * @throws IllegalArgumentException if a root is not a directory, or a symbolic link is on its path or under it
     * @throws UncheckedIOException if a file cannot be read
     */
    public static WebReport check(Path model, Path repositoryRoot) {
        String modelPath = Conformance.displayPath(model);
        WebModel web;
        try {
            web = WebModelReader.read(model, modelPath);
        } catch (InvalidModelException e) {
            throw new ModelException(modelPath, e.problems());
        }
        List<String> roots = new ArrayList<>(List.of(web.root()));
        roots.addAll(web.testRoots());
        roots.addAll(web.publicRoots());
        roots.forEach(root -> noLinkOnThePath(repositoryRoot, root));
        SourceSet sources = SourceReader.read(repositoryRoot.resolve(web.root()), web.root());
        List<SourceSet> testRoots = web.testRoots().stream()
                .map(root -> SourceReader.read(repositoryRoot.resolve(root), root, RootKind.TEST)).toList();
        List<SourceSet> publicRoots = web.publicRoots().stream()
                .map(root -> SourceReader.read(repositoryRoot.resolve(root), root, RootKind.PUBLIC)).toList();
        AppConfiguration configuration = AppConfiguration.read(
                web.app().isEmpty() ? repositoryRoot : repositoryRoot.resolve(web.app()), web.app());
        Allowlist allowlist = Allowlist.read(repositoryRoot.resolve(Allowlist.PATH));
        return WebResultMapping.toReport(modelPath, WebRules.compare(web,
                new WebInputs(sources, testRoots, publicRoots, configuration, allowlist)));
    }

    /**
     * No directory on the way from the repository root to a root may be a symbolic link: the reader would follow it out
     * of the repository, and the link checks below the root would not see it.
     */
    private static void noLinkOnThePath(Path repositoryRoot, String root) {
        Path path = repositoryRoot;
        for (String name : root.split("/", -1)) {
            path = path.resolve(name);
            if (Files.isSymbolicLink(path)) {
                throw new IllegalArgumentException("The web root " + root + " lies under a symbolic link, "
                        + repositoryRoot.relativize(path).toString().replace('\\', '/')
                        + ": the check reads only regular files and directories");
            }
        }
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
