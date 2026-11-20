package io.github.stigjohnny.docs.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.generate.GeneratedFiles;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.Fixture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The inline references' negative fixtures (ADR 0023): each writes one reference into a copy of the repository and
 * expects the check to name the file, the line and the rule. A bare test name must name a test class; a planned one
 * carries the planned marker and is registered as planned; records, generated files and code blocks are not read.
 */
class ReferencesTest {

    private static final String NOTES = "tools/notes/README.md";

    @TempDir
    Path dir;

    @Test
    void theRepositoryPasses() {
        assertThat(problems(Fixture.copy(dir).passingEvidence())).isEmpty();
    }

    @Test
    void aBareNameOfNoTestClassFails() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\nSee `RenamedAwayTest`.\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `RenamedAwayTest` names no test class in the "
                + "repository; if it is planned, write planned:test:RenamedAwayTest (ADR 0023)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"BrainCheckTest", "PolicyCheckTest", "LoopbackPortCheckTest", "ProviderIdentityTest"})
    void aBareNameOfAnExistingTestClassPasses(String test) {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\n`" + test + "` exists.\n");

        assertThat(problems(fixture)).isEmpty();
    }

    @Test
    void aBareNameOfARegisteredPlannedTestFailsBecauseItReadsAsCurrent() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\n`CrossTenantAccessTest`\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `CrossTenantAccessTest` names no test class in the "
                + "repository; if it is planned, write planned:test:CrossTenantAccessTest (ADR 0023)");
    }

    @Test
    void aClassIsKnownByItsDeclaredNameNotByItsFileName() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write("tools/notes/src/test/java/io/example/OriginalTest.java", "class ReplacementTest {\n}\n")
                .write("tools/notes/EmptyTest.java", "")
                .write("tools/notes/src/test/java/io/example/CommentedTest.java", """
                        // class InCommentTest
                        /* record InBlockTest() */
                        class CommentedTest {
                            String text = "class InStringTest";
                        }
                        """)
                .write(NOTES, "# Notes\n\n`ReplacementTest` `OriginalTest` `EmptyTest` `CommentedTest` `InCommentTest` "
                        + "`InBlockTest` `InStringTest`\n");

        assertThat(problems(fixture)).extracting(problem -> problem.substring(0, problem.indexOf(" names")))
                .containsExactly(NOTES + ":3: `EmptyTest`", NOTES + ":3: `InBlockTest`", NOTES + ":3: `InCommentTest`",
                        NOTES + ":3: `InStringTest`", NOTES + ":3: `OriginalTest`");
    }

    @Test
    void testClassesAreReadFromTestSourcesAndStandaloneTestFilesOnly() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write("tools/notes/src/test/java/io/example/Helpers.java", "class ExtraTest {\n}\n")
                .write("tools/notes/src/main/java/io/example/Main.java", "class MainOnlyTest {\n}\n")
                .write(NOTES, "# Notes\n\n`ExtraTest` `MainOnlyTest`\n");

        assertThat(problems(fixture)).extracting(problem -> problem.substring(0, problem.indexOf(" names")))
                .containsExactly(NOTES + ":3: `MainOnlyTest`");
    }

    @Test
    void theFixtureCopiesTheJavaSourcesTheCheckReads() throws IOException {
        Path repository = dir.resolve("repository");
        List<String> read = List.of("x/src/test/java/io/example/Helpers.java",
                "y/src/providerIdentityTest/java/io/example/Other.java", "z/StandaloneTest.java");
        for (String file : read) {
            write(repository.resolve(file), "class X {\n}\n");
        }
        write(repository.resolve("w/src/main/java/io/example/Main.java"), "class Y {\n}\n");

        Fixture fixture = Fixture.copy(dir.resolve("copy"), repository);

        assertThat(read).allSatisfy(file -> assertThat(fixture.root().resolve(file)).exists());
        assertThat(fixture.root().resolve("w/src/main/java/io/example/Main.java")).doesNotExist();
    }

    @Test
    void aShippedEntryRegistersNoPlannedTest() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .edit("docs/capabilities/capabilities.yaml", text -> text + """

                          - id: control.shipped-only
                            title: A shipped control.
                            where: tools/docs
                            check: java
                            status: shipped
                            target: v0.1
                            evidence: [ShippedOnlyTest]
                            evidenceLabel: none
                            acceptanceTests: [ShippedOnlyTest]
                            negativeFixture: none.
                            docs: docs/gaps.md
                        """)
                .write(NOTES, "# Notes\n\n`planned:test:ShippedOnlyTest`\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `planned:test:ShippedOnlyTest`: ShippedOnlyTest is "
                + "not registered as planned; list it in the acceptanceTests of a capability or control that is not "
                + "shipped, or in an ADR's plannedEnforcement (ADR 0023)");
    }

    @Test
    void frontmatterWithWindowsLineEndingsIsNotReadAndLinesStayPhysical() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES,
                "---\r\ntitle: \"The `GhostMetaTest`\"\r\n---\r\n\r\nSee `RenamedAwayTest`.\r\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":5: `RenamedAwayTest` names no test class in the "
                + "repository; if it is planned, write planned:test:RenamedAwayTest (ADR 0023)");
    }

    @Test
    void aPlannedMarkerOnATestThatExistsFails() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write(NOTES, "# Notes\n\n`planned:test:BrainCheckTest`\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `planned:test:BrainCheckTest`: BrainCheckTest "
                + "exists, so name it without planned: (ADR 0023)");
    }

    @Test
    void aPlannedMarkerOnAnUnregisteredTestFails() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write(NOTES, "# Notes\n\n`planned:test:NobodyPlannedTest`\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `planned:test:NobodyPlannedTest`: "
                + "NobodyPlannedTest is not registered as planned; list it in the acceptanceTests of a capability or "
                + "control that is not shipped, or in an ADR's plannedEnforcement (ADR 0023)");
    }

    @Test
    void aPlannedMarkerOnARegisteredTestPasses() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write(NOTES, "# Notes\n\n`planned:test:CrossTenantAccessTest`\n");

        assertThat(problems(fixture)).isEmpty();
    }

    @Test
    void anAdrsPlannedEnforcementRegistersAPlannedTest() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .edit("docs/adr/0022-agent-entry-point-and-documentation-lookup.md", text -> text.replace(
                        "plannedEnforcement:\n", "plannedEnforcement:\n  - \"java: FutureOnlyTest\"\n"))
                .write(NOTES, "# Notes\n\n`planned:test:FutureOnlyTest`\n");

        assertThat(problems(fixture)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"planned:test:", "planned:test:lowercaseTest", "planned:test:BrainCheckTest extra",
        "planned:tests:CrossTenantAccessTest", "planned:path:policy/rulesets.json", "absent:test:GhostTest",
        "illustrative:test:SomeTest", "planned:"})
    void aMalformedOrNotYetDefinedMarkerFails(String marker) {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\n`" + marker + "`\n");

        assertThat(problems(fixture)).singleElement().asString().startsWith(NOTES + ":3: `" + marker + "` ")
                .endsWith("(ADR 0023)");
    }

    @Test
    void anIllustrativeMarkerWithAPlaceholderPasses() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write(NOTES, "# Notes\n\n`illustrative:test:<TestClass>`\n");

        assertThat(problems(fixture)).isEmpty();
    }

    @Test
    void codeBlocksAreNotRead() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write(NOTES, "# Notes\n\n```text\n`GhostTest`\n```\n\n    `IndentedGhostTest`\n");

        assertThat(problems(fixture)).isEmpty();
    }

    @Test
    void tablesDoubleBackticksAndSeveralSpansOnALineAreRead() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES,
                "# Notes\n\n| Test |\n|---|\n| `TableGhostTest` |\n\n``DoubleGhostTest`` and `SecondGhostTest`.\n");

        assertThat(problems(fixture)).extracting(problem -> problem.substring(0, problem.indexOf(" names")))
                .containsExactly(NOTES + ":5: `TableGhostTest`", NOTES + ":7: `DoubleGhostTest`",
                        NOTES + ":7: `SecondGhostTest`");
    }

    @ParameterizedTest
    @ValueSource(strings = {"docs/demos/how-to.md", "libs/c4-contract-kit/README.md", "apps/shipping-web/README.md",
        "README.md", "AGENTS.md", "docs/adr/0022-agent-entry-point-and-documentation-lookup.md"})
    void aCurrentDocumentAnywhereIsReadWithTheLinesOfItsFile(String path) {
        Fixture fixture = Fixture.copy(dir).passingEvidence();
        int line = appendLine(fixture, path, "See `GhostTest`.");

        assertThat(problems(fixture)).containsExactly(path + ":" + line + ": `GhostTest` names no test class in the "
                + "repository; if it is planned, write planned:test:GhostTest (ADR 0023)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"docs/contributions/history.md", "docs/demos/142-claude-md-pairs-are-checked.md",
        "security/reviews/maven--pgjdbc--42.7.14.md", "security/verifications/2026-11-06-toolchain.md",
        "docs/adr/0010-documentation-as-code-with-a-generated-brain.md", "BRAIN.md", "docs/brain/quotes.md",
        "docs/architecture/containers.md", "build/notes.md", "apps/shipping-web/node_modules/pkg/README.md"})
    void recordsSupersededDecisionsGeneratedFilesAndSkippedDirectoriesAreNotRead(String path) {
        Fixture fixture = Fixture.copy(dir).passingEvidence();
        appendLine(fixture, path, "See `GhostTest`.");

        assertThat(problems(fixture)).isEmpty();
    }

    @Test
    void generationStopsOnAnUnknownTestName() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\nSee `RenamedAwayTest`.\n");

        assertThatThrownBy(() -> GeneratedFiles.generate(Sources.read(fixture.root(), List.of(fixture.reports()))))
                .isInstanceOf(DocsException.class)
                .hasMessageContaining(NOTES + ":3: `RenamedAwayTest` names no test class");
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static List<String> problems(Fixture fixture) {
        Problems problems = new Problems();
        References.check(Sources.read(fixture.root(), List.of(fixture.reports())), problems);
        return problems.list();
    }

    /** Appends a paragraph and returns its line number, so that a message can be checked against the whole file. */
    private static int appendLine(Fixture fixture, String path, String line) {
        String text = fixture.root().resolve(path).toFile().exists() ? fixture.read(path) : "";
        String base = text.isEmpty() || text.endsWith("\n") ? text : text + "\n";
        fixture.write(path, base + "\n" + line + "\n");
        return (int) base.chars().filter(c -> c == '\n').count() + 2;
    }
}
