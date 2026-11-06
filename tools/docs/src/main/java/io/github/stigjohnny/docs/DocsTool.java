package io.github.stigjohnny.docs;

import io.github.stigjohnny.docs.generate.GeneratedFiles;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.site.SiteBuilder;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;

/**
 * The command line of {@code :tools:docs}, which the Gradle tasks run.
 *
 * <pre>
 * DocsTool generate        --repo DIR [--test-results DIR]...   write docs/architecture/, BRAIN.md, docs/brain/
 * DocsTool check-generated --repo DIR [--test-results DIR]...   fail on any difference from what is committed
 * DocsTool site            --repo DIR [--test-results DIR]... --out DIR [--revision TEXT]   build the public site
 * </pre>
 *
 * <p>Every command runs under a timeout, and any exception or {@link Error}, {@link StackOverflowError} included, fails
 * it: exit code 1 for a documentation problem, 2 for anything unexpected.
 */
public final class DocsTool {

    static final Duration TIMEOUT = Duration.ofMinutes(10);

    private DocsTool() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err, TIMEOUT));
    }

    /** Runs a command on its own thread and returns the exit code. */
    static int run(String[] args, PrintStream out, PrintStream err, Duration timeout) {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "docs-tool");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<?> task = executor.submit(() -> {
                execute(Arguments.parse(args), out);
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
            err.println("docs: failed with " + cause.getClass().getName() + ": " + cause.getMessage());
            cause.printStackTrace(err);
            return 2;
        } finally {
            executor.shutdownNow();
        }
    }

    private static void execute(Arguments arguments, PrintStream out) {
        Path repo = arguments.repo();
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
            default -> throw new DocsException("unknown command '" + arguments.command()
                    + "' (generate, check-generated, site)");
        }
    }

    /** The parsed command line. */
    record Arguments(String command, Path repo, List<Path> testResults, @Nullable Path out, String revision,
            @Nullable Path summary) {

        static Arguments parse(String[] args) {
            if (args.length == 0) {
                throw new DocsException("usage: DocsTool <command> --repo DIR [--test-results DIR]... [--out DIR] "
                        + "[--revision TEXT] [--summary FILE]");
            }
            Path repo = null;
            Path out = null;
            Path summary = null;
            String revision = "";
            List<Path> testResults = new ArrayList<>();
            for (int i = 1; i < args.length; i++) {
                String option = args[i];
                if (i + 1 >= args.length) {
                    throw new DocsException("option " + option + " needs a value");
                }
                String value = args[++i];
                switch (option) {
                    case "--repo" -> repo = Path.of(value);
                    case "--test-results" -> testResults.add(Path.of(value));
                    case "--out" -> out = Path.of(value);
                    case "--revision" -> revision = value;
                    case "--summary" -> summary = Path.of(value);
                    default -> throw new DocsException("unknown option " + option);
                }
            }
            if (repo == null) {
                throw new DocsException("--repo is required");
            }
            return new Arguments(args[0], repo.toAbsolutePath().normalize(), List.copyOf(testResults), out, revision,
                    summary);
        }
    }
}
