package io.github.stigjohnny.docs.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.testing.Fixture;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A test counts as evidence only when it exists in the repository and its report in this build passed. */
class TestEvidenceTest {

    @TempDir
    Path dir;

    @Test
    void onlyAnExistingTestWithAPassingReportCounts() {
        Fixture fixture = Fixture.copy(dir)
                .report("io.github.stigjohnny.docs.check.BrainCheckTest", 3, 0, 0, 0)
                .report("io.github.stigjohnny.docs.generate.BrainGeneratorTest", 2, 1, 0, 0)
                .report("io.github.stigjohnny.docs.yaml.SafeYamlTest", 4, 0, 0, 1)
                .report("io.github.stigjohnny.docs.markdown.MarkdownRendererTest", 0, 0, 0, 0)
                .report("io.github.stigjohnny.example.NotInTheRepositoryTest", 5, 0, 0, 0);

        TestEvidence evidence = TestEvidence.read(fixture.root(), List.of(fixture.reports()));

        assertThat(evidence.passed("BrainCheckTest")).isTrue();
        assertThat(evidence.passed("BrainGeneratorTest")).as("a failure").isFalse();
        assertThat(evidence.passed("SafeYamlTest")).as("a skipped test").isFalse();
        assertThat(evidence.passed("MarkdownRendererTest")).as("no test ran").isFalse();
        assertThat(evidence.passed("NotInTheRepositoryTest")).as("a report without a source").isFalse();
        assertThat(evidence.passed("MermaidValidatorTest")).as("a source without a report").isFalse();
        assertThat(evidence.exists("MermaidValidatorTest")).isTrue();
    }

    @Test
    void twoTestClassesWithOneNameAreAmbiguousAndNeverPassAsEvidence() {
        Fixture fixture = Fixture.copy(dir)
                .report("io.github.stigjohnny.docs.check.BrainCheckTest", 3, 0, 0, 0)
                .report("io.github.stigjohnny.other.BrainCheckTest", 3, 0, 0, 0)
                .report("io.github.stigjohnny.docs.generate.BrainGeneratorTest", 3, 0, 0, 0);

        TestEvidence evidence = TestEvidence.read(fixture.root(), List.of(fixture.reports()));

        assertThat(evidence.ambiguous("BrainCheckTest")).isTrue();
        assertThat(evidence.passed("BrainCheckTest")).isFalse();
        assertThat(evidence.ambiguous("BrainGeneratorTest")).isFalse();
        assertThat(evidence.passed("BrainGeneratorTest")).isTrue();
    }

    @Test
    void twoTestSourcesWithOneNameAreAmbiguousEvenWhenOnlyOneOfThemRan() {
        Fixture fixture = Fixture.copy(dir)
                .write("libs/other/src/test/java/io/github/stigjohnny/other/BrainCheckTest.java",
                        "package io.github.stigjohnny.other;\n\nclass BrainCheckTest {}\n")
                .report("io.github.stigjohnny.docs.check.BrainCheckTest", 3, 0, 0, 0)
                .report("io.github.stigjohnny.docs.generate.BrainGeneratorTest", 3, 0, 0, 0);

        TestEvidence evidence = TestEvidence.read(fixture.root(), List.of(fixture.reports()));

        assertThat(evidence.ambiguous("BrainCheckTest")).isTrue();
        assertThat(evidence.passed("BrainCheckTest")).isFalse();
        assertThat(evidence.passed("BrainGeneratorTest")).isTrue();
    }

    @Test
    void requiredResultsPassOnlyWhenEverySuiteRanAtLeastOneTestAndNoneFailedErroredOrWasSkipped() {
        Fixture passing = Fixture.copy(dir.resolve("passing"))
                .report("io.github.stigjohnny.shipping.database.PostgresHarnessIntegrationTest", 1, 0, 0, 0);
        assertThat(TestEvidence.requiredProblems(passing.reports(), "database")).isEmpty();

        for (int[] counts : new int[][] {{1, 1, 0, 0}, {1, 0, 1, 0}, {1, 0, 0, 1}, {0, 0, 0, 0}}) {
            Fixture fixture = Fixture.copy(dir.resolve("case-" + counts[0] + counts[1] + counts[2] + counts[3]))
                    .report("io.github.stigjohnny.shipping.database.PostgresHarnessIntegrationTest", 1, 0, 0, 0)
                    .report("io.github.stigjohnny.shipping.database.OtherIntegrationTest", counts[0], counts[1],
                            counts[2], counts[3]);
            assertThat(TestEvidence.requiredProblems(fixture.reports(), "database")).singleElement().asString()
                    .startsWith("database: io.github.stigjohnny.shipping.database.OtherIntegrationTest ran "
                            + counts[0] + " tests with " + counts[1] + " failed, " + counts[2] + " errored and "
                            + counts[3] + " skipped");
        }
    }

    @Test
    void requiredResultsThatAreMissingOrEmptyAreAProblemNotAPass() {
        Fixture empty = Fixture.copy(dir);

        assertThat(TestEvidence.requiredProblems(empty.reports(), "database")).containsExactly(
                "database: no test report; the tests that must pass did not run in this build");
        assertThat(TestEvidence.requiredProblems(dir.resolve("absent"), "database")).containsExactly(
                "database: no test results; the tests that must pass did not run in this build");
    }

    @Test
    void aReportWithADoctypeIsRefused() {
        Fixture fixture = Fixture.copy(dir).write("../reports/TEST-x.Evil.xml",
                "<?xml version=\"1.0\"?><!DOCTYPE t [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><testsuite name=\"&e;\" "
                        + "tests=\"1\"/>");

        assertThatThrownBy(() -> TestEvidence.read(fixture.root(), List.of(fixture.reports())))
                .isInstanceOf(DocsException.class).hasMessageContaining("cannot be read");
    }
}
