package io.github.stigjohnny.c4contractkit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The assertion writes report.json and report.md first, and only then fails a test on a violation. */
class ConformanceAssertionsTest {

    private static final Report PASSING = new Report(Report.SCHEMA, "workspace.dsl",
            List.of(new ComponentResult("orders", "Orders", ComponentStatus.BUILT, List.of("fx.orders"), 1)),
            List.of(), List.of());

    private static final Report FAILING = new Report(Report.SCHEMA, "workspace.dsl",
            List.of(new ComponentResult("orders", "Orders", ComponentStatus.BUILT, List.of("fx.orders"), 0)),
            List.of(),
            List.of(new Violation(ViolationType.BUILT_COMPONENT_EMPTY, List.of("orders"),
                            "orders is built, but its packages contain no class.", List.of()),
                    new Violation(ViolationType.UNMAPPED_CLASS, List.of("fx.Stray"), "fx.Stray is unmapped.",
                            List.of())));

    @Test
    void aPassingReportIsWrittenAndTheAssertionPasses(@TempDir Path dir) throws IOException {
        Path reports = dir.resolve("build/reports/c4-contract");

        ConformanceAssertions.assertConforms(PASSING, reports);

        assertThat(Files.readString(reports.resolve("report.json"), StandardCharsets.UTF_8))
                .startsWith("{\n  \"schema\": \"c4-contract-kit/report/v1\",\n");
        assertThat(Files.readString(reports.resolve("report.md"), StandardCharsets.UTF_8))
                .startsWith("# C4 contract report\n\n**Verdict: PASSED**");
    }

    @Test
    void aFailingReportIsWrittenBeforeTheAssertionFails(@TempDir Path dir) throws IOException {
        Path reports = dir.resolve("reports");

        assertThatThrownBy(() -> ConformanceAssertions.assertConforms(FAILING, reports))
                .isInstanceOf(AssertionError.class)
                .hasMessage("The code does not conform to the C4 model workspace.dsl: 2 violations. Reports: "
                        + reports.resolve("report.md") + " and report.json.\n"
                        + "  - BUILT_COMPONENT_EMPTY orders: orders is built, but its packages contain no class.\n"
                        + "  - UNMAPPED_CLASS fx.Stray: fx.Stray is unmapped.");

        assertThat(Files.readString(reports.resolve("report.json"), StandardCharsets.UTF_8))
                .contains("\"passed\": false");
        assertThat(Files.readString(reports.resolve("report.md"), StandardCharsets.UTF_8))
                .startsWith("# C4 contract report\n\n**Verdict: FAILED** with 2 violations.");
    }

    @Test
    void oneViolationIsCountedInTheSingular(@TempDir Path dir) {
        Report report = new Report(Report.SCHEMA, "m.dsl", List.of(), List.of(), List.of(FAILING.violations().get(1)));

        assertThatThrownBy(() -> ConformanceAssertions.assertConforms(report, dir))
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("The code does not conform to the C4 model m.dsl: 1 violation. Reports: ");
    }

    @Test
    void earlierReportsAreReplaced(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("report.json"), "stale", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("report.md"), "stale", StandardCharsets.UTF_8);

        ConformanceAssertions.assertConforms(PASSING, dir);

        assertThat(Files.readString(dir.resolve("report.json"), StandardCharsets.UTF_8)).doesNotContain("stale");
        assertThat(Files.readString(dir.resolve("report.md"), StandardCharsets.UTF_8)).doesNotContain("stale");
    }

    @Test
    void aReportDirectoryThatCannotBeWrittenIsAnError(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("occupied"), "a file, not a directory", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ConformanceAssertions.assertConforms(PASSING, file))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessage("The C4 contract reports could not be written to " + file);
    }

    @Test
    void everyRecordKeepsItsOwnCopyOfTheListsItIsGiven() {
        Evidence evidence = new Evidence("fx.orders.Order", "invoice", "fx.billing.Invoice", "", "field type",
                "Order.java", 0);
        List<String> packages = new ArrayList<>(List.of("fx.orders"));
        List<Evidence> edgeEvidence = new ArrayList<>(List.of(evidence));
        List<String> identifiers = new ArrayList<>(List.of("orders", "billing"));
        List<Evidence> violationEvidence = new ArrayList<>(List.of(evidence));
        ComponentResult component = new ComponentResult("orders", "Orders", ComponentStatus.BUILT, packages, 1);
        EdgeResult edge = new EdgeResult("orders", "billing", Mode.PERMITTED, EdgeStatus.CONVERGENCE, edgeEvidence);
        Violation violation = new Violation(ViolationType.DIVERGENCE, identifiers, "orders depends on billing.",
                violationEvidence);
        List<ComponentResult> components = new ArrayList<>(List.of(component));
        List<EdgeResult> edges = new ArrayList<>(List.of(edge));
        List<Violation> violations = new ArrayList<>(List.of(violation));
        Report report = new Report(Report.SCHEMA, "workspace.dsl", components, edges, violations);

        Stream.of(packages, edgeEvidence, identifiers, violationEvidence, components, edges, violations)
                .forEach(List::clear);

        assertSoftly(softly -> {
            softly.assertThat(component.packages()).as("packages").containsExactly("fx.orders");
            softly.assertThat(edge.evidence()).as("edge evidence").containsExactly(evidence);
            softly.assertThat(violation.identifiers()).as("identifiers").containsExactly("orders", "billing");
            softly.assertThat(violation.evidence()).as("violation evidence").containsExactly(evidence);
            softly.assertThat(report.components()).as("components").containsExactly(component);
            softly.assertThat(report.edges()).as("edges").containsExactly(edge);
            softly.assertThat(report.violations()).as("violations").containsExactly(violation);
        });
    }

    @Test
    void aReportIsImmutableAndPassesOnlyWithoutViolations() {
        assertThat(PASSING.passed()).isTrue();
        assertThat(FAILING.passed()).isFalse();
        assertThatThrownBy(() -> FAILING.violations().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> FAILING.components().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> FAILING.edges().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> FAILING.components().get(0).packages().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> FAILING.violations().get(0).identifiers().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> FAILING.violations().get(0).evidence().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new EdgeResult("a", "b", Mode.REQUIRED, EdgeStatus.ABSENCE, List.of()).evidence()
                .clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
