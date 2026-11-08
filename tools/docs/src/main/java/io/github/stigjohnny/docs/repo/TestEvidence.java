package io.github.stigjohnny.docs.repo;

import io.github.stigjohnny.docs.DocsException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Which tests exist in the repository and which passed in this build. A test is evidence only when both hold: its
 * source file exists under a {@code src/test/java} directory, and the build's JUnit XML report shows it ran at least
 * one test with no failure, error or skip. Test names that are not Java classes (TypeScript files) have no reports
 * here yet, so they never count as passing. A simple name that more than one test class carries, among the sources or
 * the reports, is ambiguous and never evidence: it would not say which class passed. Modules may share such a name as
 * long as no evidence uses it. A suite that more than one report covers, as when two producers each run part of one
 * class, counts as its worst report: it passes only when every report of it passed, whichever producer is read first.
 * The reader also records which report directories it read, each as the Gradle task that writes it, and which
 * TypeScript and JavaScript test files exist, so that a page can say a test was not read.
 */
public final class TestEvidence {

    /** A JUnit XML suite's counts. */
    public record Result(String suite, int tests, int skipped, int failures, int errors) {

        public boolean passed() {
            return tests > 0 && skipped == 0 && failures == 0 && errors == 0;
        }

        /** How far the report is from passing: a failure or error, then a skip, then no test run, then none. */
        int shortfall() {
            if (failures > 0 || errors > 0) {
                return 3;
            }
            if (skipped > 0) {
                return 2;
            }
            return tests == 0 ? 1 : 0;
        }
    }

    /**
     * A directory of reports that this build was given: the Gradle task that writes it, such as
     * {@code :apps:shipping-api:test}, or what it is when it is not one, and how many reports it held.
     */
    public record Producer(String task, int reports) {
    }

    /** A test file in another language than Java, by its name: a TypeScript or JavaScript test or spec. */
    private static final Pattern OTHER_TEST = Pattern.compile(".+\\.(?:test|spec)\\.(?:[cm]?[jt]s|[jt]sx)");

    private final Set<String> sources;
    private final Map<String, Result> results;
    private final Set<String> ambiguous;
    private final Set<String> otherTests;
    private final List<Producer> producers;

    public TestEvidence(Set<String> sources, Map<String, Result> results) {
        this(sources, results, Set.of());
    }

    public TestEvidence(Set<String> sources, Map<String, Result> results, Set<String> ambiguous) {
        this(sources, results, ambiguous, Set.of(), List.of());
    }

    private TestEvidence(Set<String> sources, Map<String, Result> results, Set<String> ambiguous,
            Set<String> otherTests, List<Producer> producers) {
        this.sources = Set.copyOf(sources);
        this.results = Map.copyOf(results);
        this.ambiguous = Set.copyOf(ambiguous);
        this.otherTests = Set.copyOf(otherTests);
        this.producers = List.copyOf(producers);
    }

    /** Reads the test sources of the repository and the JUnit XML reports in the given directories. */
    public static TestEvidence read(Path repo, List<Path> reportDirs) {
        Set<String> sources = new TreeSet<>();
        Set<String> ambiguous = new TreeSet<>();
        Set<String> otherTests = new TreeSet<>();
        try (Stream<Path> files = Files.walk(repo)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(p -> !skipped(repo.relativize(p).toString().replace('\\', '/'))).toList()) {
                String name = file.getFileName().toString();
                if (file.toString().replace('\\', '/').contains("/src/test/java/") && name.endsWith(".java")) {
                    String simpleName = name.replace(".java", "");
                    if (!sources.add(simpleName)) {
                        ambiguous.add(simpleName);
                    }
                } else if (OTHER_TEST.matcher(name).matches()) {
                    otherTests.add(name);
                }
            }
        } catch (IOException e) {
            throw new DocsException("the repository's test sources cannot be listed", e);
        }
        Map<String, Result> results = new HashMap<>();
        List<Producer> producers = new ArrayList<>();
        for (Path dir : reportDirs) {
            if (!Files.isDirectory(dir)) {
                producers.add(new Producer(task(repo, dir), 0));
                continue;
            }
            try (Stream<Path> files = Files.list(dir)) {
                List<Path> reports = files.filter(p -> p.getFileName().toString().startsWith("TEST-")
                        && p.getFileName().toString().endsWith(".xml")).sorted().toList();
                producers.add(new Producer(task(repo, dir), reports.size()));
                for (Path report : reports) {
                    Result result = parse(report);
                    String simpleName = result.suite().substring(result.suite().lastIndexOf('.') + 1);
                    Result previous = results.putIfAbsent(simpleName, result);
                    if (previous != null && !previous.suite().equals(result.suite())) {
                        // Two modules may name a test class alike; the name is only unusable as evidence.
                        ambiguous.add(simpleName);
                    } else if (previous != null && result.shortfall() > previous.shortfall()) {
                        // The same suite again, from another producer: its worst report counts, so a passing report
                        // never hides one that failed, was skipped or ran nothing.
                        results.put(simpleName, result);
                    }
                }
            } catch (IOException e) {
                throw new DocsException("the test reports cannot be listed", e);
            }
        }
        return new TestEvidence(sources, results, ambiguous, otherTests, producers);
    }

    /**
     * The Gradle task that writes a report directory, {@code <project>/build/test-results/<task>}, as a task path; a
     * directory outside the repository or of another layout is named by what it is, never by its path.
     */
    private static String task(Path repo, Path dir) {
        Path root = repo.toAbsolutePath().normalize();
        Path absolute = dir.toAbsolutePath().normalize();
        if (!absolute.startsWith(root) || absolute.equals(root)) {
            return "a report directory outside the repository";
        }
        Path relative = root.relativize(absolute);
        int names = relative.getNameCount();
        if (names < 3 || !relative.getName(names - 3).toString().equals("build")
                || !relative.getName(names - 2).toString().equals("test-results")) {
            return "a report directory of another layout";
        }
        StringBuilder task = new StringBuilder();
        for (int i = 0; i < names - 3; i++) {
            task.append(':').append(relative.getName(i));
        }
        return task.append(':').append(relative.getName(names - 1)).toString();
    }

    /** The report directories this build read, in the order it was given them. */
    public List<Producer> producers() {
        return producers;
    }

    /** Whether the repository has a TypeScript or JavaScript test file of this name, which no report here covers. */
    public boolean otherTestExists(String name) {
        return otherTests.contains(name);
    }

    /**
     * What is wrong with reports that must show a passing run, such as the database tests' when CI asks for them: the
     * directory holds at least one JUnit XML report, and every suite in it ran at least one test, none of which failed,
     * errored or was skipped. Evidence that is required cannot be absent, so a missing or empty directory is a problem
     * too. {@code name} is how a problem names the directory.
     */
    public static List<String> requiredProblems(Path dir, String name) {
        if (!Files.isDirectory(dir)) {
            return List.of(name + ": no test results; the tests that must pass did not run in this build");
        }
        List<Path> reports;
        try (Stream<Path> files = Files.list(dir)) {
            reports = files.filter(p -> p.getFileName().toString().startsWith("TEST-")
                    && p.getFileName().toString().endsWith(".xml")).sorted().toList();
        } catch (IOException e) {
            throw new DocsException("the test reports cannot be listed", e);
        }
        if (reports.isEmpty()) {
            return List.of(name + ": no test report; the tests that must pass did not run in this build");
        }
        List<String> problems = new ArrayList<>();
        for (Path report : reports) {
            Result result = parse(report);
            if (!result.passed()) {
                problems.add(name + ": " + result.suite() + " ran " + result.tests() + " tests with "
                        + result.failures() + " failed, " + result.errors() + " errored and " + result.skipped()
                        + " skipped; it must run at least one and pass them all");
            }
        }
        return List.copyOf(problems);
    }

    /** Whether more than one test class, among the sources or the reports, has this simple name. */
    public boolean ambiguous(String test) {
        return ambiguous.contains(test);
    }

    public boolean exists(String test) {
        return sources.contains(test);
    }

    public Optional<Result> result(String test) {
        return Optional.ofNullable(results.get(test));
    }

    /** Exists in the repository, names one test class only, and passed in this build. */
    public boolean passed(String test) {
        return exists(test) && !ambiguous(test) && result(test).map(Result::passed).orElse(false);
    }

    private static boolean skipped(String relative) {
        return relative.startsWith(".git/") || relative.startsWith(".gradle/") || relative.contains("/build/")
                || relative.startsWith("build/") || relative.contains("/node_modules/");
    }

    private static Result parse(Path report) {
        try (InputStream in = Files.newInputStream(report)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Element suite = factory.newDocumentBuilder().parse(in).getDocumentElement();
            if (!"testsuite".equals(suite.getTagName())) {
                throw new DocsException("test report " + report.getFileName() + " is not a JUnit test suite");
            }
            return new Result(suite.getAttribute("name"), count(suite, "tests"), count(suite, "skipped"),
                    count(suite, "failures"), count(suite, "errors"));
        } catch (IOException | ParserConfigurationException | SAXException | NumberFormatException e) {
            throw new DocsException("test report " + report.getFileName() + " cannot be read: " + e.getMessage(), e);
        }
    }

    private static int count(Element suite, String attribute) {
        String value = suite.getAttribute(attribute);
        return value.isEmpty() ? 0 : Integer.parseInt(value);
    }
}
