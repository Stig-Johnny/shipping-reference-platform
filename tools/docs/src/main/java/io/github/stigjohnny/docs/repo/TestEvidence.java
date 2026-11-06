package io.github.stigjohnny.docs.repo;

import io.github.stigjohnny.docs.DocsException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
 * here yet, so they never count as passing.
 */
public final class TestEvidence {

    /** A JUnit XML suite's counts. */
    public record Result(String suite, int tests, int skipped, int failures, int errors) {

        public boolean passed() {
            return tests > 0 && skipped == 0 && failures == 0 && errors == 0;
        }
    }

    private final Set<String> sources;
    private final Map<String, Result> results;

    public TestEvidence(Set<String> sources, Map<String, Result> results) {
        this.sources = Set.copyOf(sources);
        this.results = Map.copyOf(results);
    }

    /** Reads the test sources of the repository and the JUnit XML reports in the given directories. */
    public static TestEvidence read(Path repo, List<Path> reportDirs) {
        Set<String> sources = new TreeSet<>();
        try (Stream<Path> files = Files.walk(repo)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> !skipped(repo.relativize(p).toString().replace('\\', '/')))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/test/java/"))
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .forEach(p -> sources.add(p.getFileName().toString().replace(".java", "")));
        } catch (IOException e) {
            throw new DocsException("the repository's test sources cannot be listed", e);
        }
        Map<String, Result> results = new HashMap<>();
        for (Path dir : reportDirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.list(dir)) {
                for (Path report : files.filter(p -> p.getFileName().toString().startsWith("TEST-")
                        && p.getFileName().toString().endsWith(".xml")).sorted().toList()) {
                    Result result = parse(report);
                    String simpleName = result.suite().substring(result.suite().lastIndexOf('.') + 1);
                    Result previous = results.put(simpleName, result);
                    if (previous != null && !previous.suite().equals(result.suite())) {
                        throw new DocsException("two test classes are called " + simpleName + " (" + previous.suite()
                                + ", " + result.suite() + "); evidence names must be unique");
                    }
                }
            } catch (IOException e) {
                throw new DocsException("the test reports cannot be listed", e);
            }
        }
        return new TestEvidence(sources, results);
    }

    public boolean exists(String test) {
        return sources.contains(test);
    }

    public Optional<Result> result(String test) {
        return Optional.ofNullable(results.get(test));
    }

    /** Exists in the repository and passed in this build. */
    public boolean passed(String test) {
        return exists(test) && result(test).map(Result::passed).orElse(false);
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
