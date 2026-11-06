package io.github.stigjohnny.docs.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
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
            "security/README.md", "security/reviews", ".github/workflows", "tools/docs/src/test/java",
            "tools/docs/vendor", "tools/docs/publication-scan-exceptions.json");

    private final Path root;
    private final Path reports;

    private Fixture(Path root, Path reports) {
        this.root = root;
        this.reports = reports;
    }

    /** Copies the inputs into {@code dir}/repo and creates an empty {@code dir}/reports. */
    public static Fixture copy(Path dir) {
        Path root = dir.resolve("repo");
        Path reports = dir.resolve("reports");
        try {
            Files.createDirectories(reports);
            for (String input : INPUTS) {
                Path source = Repository.file(input);
                if (Files.isDirectory(source)) {
                    try (Stream<Path> walk = Files.walk(source)) {
                        for (Path file : walk.filter(Files::isRegularFile).toList()) {
                            copy(file, root.resolve(Repository.root().relativize(file).toString()));
                        }
                    }
                } else if (Files.isRegularFile(source)) {
                    copy(source, root.resolve(input));
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

    /** Passing reports for the evidence the repository's capabilities name today. */
    public Fixture passingEvidence() {
        return report("io.github.stigjohnny.docs.generate.BrainGeneratorTest", 7, 0, 0, 0)
                .report("io.github.stigjohnny.docs.check.BrainCheckTest", 19, 0, 0, 0);
    }

    private static void copy(Path from, Path to) throws IOException {
        Files.createDirectories(Objects.requireNonNull(to.getParent()));
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }
}
