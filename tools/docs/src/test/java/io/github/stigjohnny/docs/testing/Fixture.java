package io.github.stigjohnny.docs.testing;

import io.github.stigjohnny.docs.repo.Capabilities;
import io.github.stigjohnny.docs.repo.Capability;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * A copy of the repository's documentation inputs in a temporary directory, for negative fixtures: a test changes
 * one thing and expects the check to fail. Test reports are written as JUnit XML, the way Gradle writes them.
 */
public final class Fixture {

    /** Everything the docs tool reads, and the generated outputs it compares. */
    private static final List<String> INPUTS = List.of("README.md", "AGENTS.md", "BRAIN.md", "LICENSE", "NOTICE",
            "SECURITY.md", "THIRD-PARTY-NOTICES.md", "docs",
            "libs/c4-contract-kit/AGENTS.md", "apps/shipping-api/AGENTS.md", "apps/shipping-web/AGENTS.md",
            "security/AGENTS.md",
            "architecture/workspace.dsl", "contracts", "policy/risk.json", "security/allowlist.json",
            "security/README.md", "security/reviews", ".github/workflows", "tools/docs/vendor",
            "tools/docs/publication-scan-exceptions.json");

    private final Path root;
    private final Path reports;

    private Fixture(Path root, Path reports) {
        this.root = root;
        this.reports = reports;
    }

    /** Copies the inputs into {@code dir}/repo and creates an empty {@code dir}/reports. */
    public static Fixture copy(Path dir) {
        return copy(dir, Repository.root());
    }

    /** Copies the inputs of the repository at {@code repository} into {@code dir}/repo. */
    public static Fixture copy(Path dir, Path repository) {
        Path root = dir.resolve("repo");
        Path reports = dir.resolve("reports");
        try {
            Files.createDirectories(reports);
            for (String input : INPUTS) {
                Path source = repository.resolve(input);
                if (Files.isDirectory(source)) {
                    try (Stream<Path> walk = Files.walk(source)) {
                        for (Path file : walk.filter(Files::isRegularFile).toList()) {
                            copyFile(file, root.resolve(repository.relativize(file).toString()));
                        }
                    }
                } else if (Files.isRegularFile(source)) {
                    copyFile(source, root.resolve(input));
                }
            }
            // Every test source tree, wherever it is, so that a test named as evidence exists in the copy too.
            for (Path tree : testTrees(repository)) {
                try (Stream<Path> walk = Files.walk(tree)) {
                    for (Path file : walk.filter(f -> Files.isRegularFile(f) && f.toString().endsWith(".java")).toList()) {
                        copyFile(file, root.resolve(repository.relativize(file).toString()));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Fixture(root, reports);
    }

    public Path root() {
        return root;
    }

    public Path reports() {
        return reports;
    }

    public String read(String path) {
        try {
            return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Fixture write(String path, String text) {
        try {
            Path file = root.resolve(path);
            Files.createDirectories(Objects.requireNonNull(file.getParent()));
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    public Fixture edit(String path, UnaryOperator<String> change) {
        String before = read(path);
        String after = change.apply(before);
        if (after.equals(before)) {
            throw new IllegalStateException("the edit of " + path + " changed nothing");
        }
        return write(path, after);
    }

    public Fixture delete(String path) {
        try {
            Files.delete(root.resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /**
     * Removes the repository's own demonstrations and milestone decks, keeping the how-to, so that a test's sample
     * demonstrations (pull requests 42 to 46) and sample deck stand alone and never meet a real one with the same
     * number or path.
     */
    public Fixture withoutDemos() {
        try (Stream<Path> files = Files.list(root.resolve("docs/demos"))) {
            for (Path file : files.filter(f -> !f.getFileName().toString().equals("how-to.md")).toList()) {
                Files.delete(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    public Fixture move(String from, String to) {
        try {
            Files.move(root.resolve(from), root.resolve(to));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /** Writes a JUnit XML report for a test class, as Gradle's test task does. */
    public Fixture report(String className, int tests, int failures, int errors, int skipped) {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="%s" tests="%d" skipped="%d" failures="%d" errors="%d" timestamp="2026-11-06T00:00:00" \
                hostname="fixture" time="0.1">
                  <properties/>
                </testsuite>
                """.formatted(className, tests, skipped, failures, errors);
        try {
            Files.writeString(reports.resolve("TEST-" + className + ".xml"), xml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /**
     * Passing reports for every test that the copied capabilities.yaml names as evidence, under the test class's own
     * package, as the build would write them. A named test with no source in the copy gets no report.
     */
    public Fixture passingEvidence() {
        Path capabilities = root.resolve(Capabilities.PATH);
        if (!Files.isRegularFile(capabilities)) {
            return this;
        }
        Map<String, String> classes = testClasses();
        for (Capability capability : Capabilities.parse(read(Capabilities.PATH)).all()) {
            for (String test : capability.evidence()) {
                String className = classes.get(test);
                if (className != null) {
                    report(className, 1, 0, 0, 0);
                }
            }
        }
        return this;
    }

    /** The test classes in the copy, by simple name; the first in path order wins if two share a name. */
    private Map<String, String> testClasses() {
        Map<String, String> classes = new TreeMap<>();
        for (Path tree : testTrees(root)) {
            try (Stream<Path> walk = Files.walk(tree)) {
                for (Path file : walk.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                    String relative = tree.relativize(file).toString().replace('\\', '/');
                    String className = relative.substring(0, relative.length() - ".java".length()).replace('/', '.');
                    classes.putIfAbsent(file.getFileName().toString().replace(".java", ""), className);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return classes;
    }

    /** Every src/test/java directory of a repository, outside build output and tool caches, sorted. */
    private static List<Path> testTrees(Path repository) {
        try (Stream<Path> walk = Files.walk(repository, 6)) {
            return walk.filter(Files::isDirectory).filter(dir -> {
                String relative = repository.relativize(dir).toString().replace('\\', '/');
                return relative.endsWith("src/test/java") && List.of(relative.split("/", -1)).stream()
                        .noneMatch(List.of("build", ".gradle", ".git", "node_modules")::contains);
            }).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copyFile(Path from, Path to) throws IOException {
        Files.createDirectories(Objects.requireNonNull(to.getParent()));
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }
}
