package io.github.stigjohnny.docs;

import io.github.stigjohnny.docs.generate.GeneratedFiles;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.repo.TestEvidence;
import io.github.stigjohnny.docs.scan.Finding;
import io.github.stigjohnny.docs.scan.PublicationScanner;
import io.github.stigjohnny.docs.scan.ScanExceptions;
import io.github.stigjohnny.docs.scan.ScanResult;
import io.github.stigjohnny.docs.site.SiteBuilder;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The command line of {@code :tools:docs}, which the Gradle tasks run.
 *
 * <pre>
 * DocsTool generate        --repo DIR [--test-results DIR]...   write docs/architecture/, BRAIN.md, docs/brain/
 * DocsTool check-generated --repo DIR [--test-results DIR]...   fail on any difference from what is committed
 * DocsTool site            --repo DIR [--test-results DIR]... --out DIR [--revision TEXT]   build the public site
 * DocsTool scan            --repo DIR --site DIR --summary FILE   scan the built site before it is published
 * </pre>
 *
 * <p>{@code --required-results DIR}, for generate, check-generated and site, reads DIR as test results too and fails
 * the command unless they show a passing run: at least one report, every suite with a test run and none failed,
 * errored or skipped. CI passes the database tests' reports so.
 *
 * <p>The scan reads {@code PUBLIC_SCAN_DICTIONARY} and {@code GITHUB_SHA} from the environment, and appends its summary
 * to {@code GITHUB_STEP_SUMMARY} when that is set.
 *
 * <p>Every command runs under a timeout, and any exception or {@link Error}, {@link StackOverflowError} included, fails
 * it: exit code 1 for a documentation problem, 2 for anything unexpected.
 */
public final class DocsTool {

    static final Duration TIMEOUT = Duration.ofMinutes(10);

    private DocsTool() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err, TIMEOUT, System::getenv));
    }

    /** Runs a command line; {@code environment} gives the environment variables the scan reads. */
    static int run(String[] args, PrintStream out, PrintStream err, Duration timeout,
            Function<String, @Nullable String> environment) {
        boolean sanitized = args.length > 0 && "scan".equals(args[0]);
        return guarded(() -> execute(Arguments.parse(args), out, environment), err, timeout, sanitized);
    }

    /** One command. */
    @FunctionalInterface
    interface Command {
        void run() throws Exception;
    }

    /** Runs a command on its own thread and returns the exit code. */
    static int guarded(Command command, PrintStream err, Duration timeout) {
        return guarded(command, err, timeout, false);
    }

    /**
     * Runs a command; a sanitized run (the scan) prints only the kind of an unexpected failure, never its message or
     * stack trace, which could name a path or a value of the site.
     */
    static int guarded(Command command, PrintStream err, Duration timeout, boolean sanitized) {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "docs-tool");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<?> task = executor.submit(() -> {
                command.run();
                return null;
            });
            task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return 0;
        } catch (TimeoutException e) {
            err.println("docs: timed out after " + timeout.toSeconds() + " seconds");
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println("docs: interrupted");
            return 2;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof DocsException problem) {
                err.println("docs: " + problem.getMessage());
                return 1;
            }
            if (sanitized) {
                err.println("docs: failed unexpectedly with " + cause.getClass().getSimpleName()
                        + "; the details are not printed, because they could name a path or a value of the site");
                return 2;
            }
            err.println("docs: failed with " + cause.getClass().getName() + ": " + cause.getMessage());
            cause.printStackTrace(err);
            return 2;
        } finally {
            // Stop the command and wait briefly for it, so that it prints and writes nothing after this returns.
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void execute(Arguments arguments, PrintStream out, Function<String, @Nullable String> environment) {
        Path repo = arguments.repo();
        requirePassed(arguments);
        switch (arguments.command()) {
            case "generate" -> {
                Map<String, String> files = GeneratedFiles.generate(Sources.read(repo, arguments.testResults()));
                GeneratedFiles.write(files, repo);
                out.println("docs: wrote " + files.size() + " generated files");
            }
            case "check-generated" -> {
                Map<String, String> files = GeneratedFiles.generate(Sources.read(repo, arguments.testResults()));
                List<String> problems = GeneratedFiles.compare(files, repo);
                if (!problems.isEmpty()) {
                    throw new DocsException("the committed generated files are not what the sources generate; run "
                            + "./gradlew :tools:docs:generate and commit the result:\n  - "
                            + String.join("\n  - ", problems));
                }
                out.println("docs: the " + files.size() + " generated files are up to date");
            }
            case "site" -> {
                Path directory = arguments.out();
                if (directory == null) {
                    throw new DocsException("site needs --out DIR");
                }
                int files = SiteBuilder.build(Sources.read(repo, arguments.testResults()), directory,
                        arguments.revision());
                out.println("docs: wrote the site, " + files + " files");
            }
            case "scan" -> scan(arguments, out, environment);
            default -> throw new DocsException("unknown command '" + arguments.command()
                    + "' (generate, check-generated, site, scan)");
        }
    }

    /**
     * Fails unless every --required-results directory shows a passing run (TestEvidence#requiredProblems): CI passes
     * the database tests' reports so, because the documentation it builds must show their evidence from this build.
     */
    private static void requirePassed(Arguments arguments) {
        List<String> problems = new ArrayList<>();
        for (Path dir : arguments.requiredResults()) {
            Path absolute = dir.toAbsolutePath().normalize();
            String name = absolute.startsWith(arguments.repo())
                    ? arguments.repo().relativize(absolute).toString().replace('\\', '/') : "--required-results "
                            + absolute.getFileName();
            problems.addAll(TestEvidence.requiredProblems(absolute, name));
        }
        if (!problems.isEmpty()) {
            throw new DocsException("the test results that must pass in this build do not:\n  - "
                    + String.join("\n  - ", problems));
        }
    }

    /**
     * Scans the built site. The dictionary and the revision come from the environment when the scan runs, so that the
     * build never stores them; the output names rules and files only.
     */
    private static void scan(Arguments arguments, PrintStream out, Function<String, @Nullable String> environment) {
        Path site = arguments.site();
        Path summary = arguments.summary();
        if (site == null || summary == null) {
            throw new DocsException("scan needs --site DIR and --summary FILE");
        }
        ScanExceptions exceptions = ScanExceptions.parse(Sources.text(arguments.repo(), PublicationScanner.EXCEPTIONS),
                PublicationScanner.EXCEPTIONS);
        String revision = Objects.requireNonNullElse(environment.apply("GITHUB_SHA"), "");
        ScanResult result = PublicationScanner.scan(site, environment.apply(PublicationScanner.DICTIONARY_VARIABLE),
                exceptions, revision);
        String text = result.summary();
        try {
            Path parent = summary.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(summary, text, StandardCharsets.UTF_8);
            String stepSummary = environment.apply("GITHUB_STEP_SUMMARY");
            if (stepSummary != null && !stepSummary.isBlank()) {
                Files.writeString(Path.of(stepSummary), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            throw new DocsException("the scan summary cannot be written (" + e.getClass().getSimpleName() + ")", e);
        }
        for (Finding finding : result.findings()) {
            out.println("scan: " + finding.rule() + (finding.file().isEmpty() ? "" : " " + finding.file()));
        }
        if (!result.passed()) {
            int count = result.findings().size();
            throw new DocsException("the publication scan failed with " + count + (count == 1 ? " finding" : " findings")
                    + ", listed above by rule and file; the site must not be published");
        }
        out.println("scan: passed, " + result.files() + " files, site digest sha256:" + result.siteDigest());
    }

    /** The parsed command line. */
    record Arguments(String command, Path repo, List<Path> testResults, List<Path> requiredResults, @Nullable Path out,
            String revision, @Nullable Path summary, @Nullable Path site) {

        static Arguments parse(String[] args) {
            if (args.length == 0) {
                throw new DocsException("usage: DocsTool <command> --repo DIR [--test-results DIR]... "
                        + "[--required-results DIR]... [--out DIR] [--revision TEXT] [--summary FILE]");
            }
            Path repo = null;
            Path out = null;
            Path summary = null;
            Path site = null;
            String revision = "";
            List<Path> testResults = new ArrayList<>();
            List<Path> requiredResults = new ArrayList<>();
            for (int i = 1; i < args.length; i++) {
                String option = args[i];
                if (i + 1 >= args.length) {
                    throw new DocsException("option " + option + " needs a value");
                }
                String value = args[++i];
                switch (option) {
                    case "--repo" -> repo = Path.of(value);
                    case "--test-results" -> testResults.add(Path.of(value));
                    case "--required-results" -> {
                        // Evidence like any other test results, which must also show a passing run.
                        testResults.add(Path.of(value));
                        requiredResults.add(Path.of(value));
                    }
                    case "--out" -> out = Path.of(value);
                    case "--revision" -> revision = value;
                    case "--summary" -> summary = Path.of(value);
                    case "--site" -> site = Path.of(value);
                    default -> throw new DocsException("unknown option " + option);
                }
            }
            if (repo == null) {
                throw new DocsException("--repo is required");
            }
            return new Arguments(args[0], repo.toAbsolutePath().normalize(), List.copyOf(testResults),
                    List.copyOf(requiredResults), out, revision, summary, site);
        }
    }
}
