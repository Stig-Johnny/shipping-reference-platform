package io.github.stigjohnny.docs.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.check.AgentInstructions.Candidate;
import io.github.stigjohnny.docs.generate.GeneratedFiles;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.Fixture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The instruction files' negative fixtures (ADR 0022): each changes one AGENTS.md or CLAUDE.md in a copy of the
 * repository and expects the documentation check to fail and say which file and why. Discovery is tested on its own,
 * on a small tree, so that a fault in it cannot hide behind fixtures that it also copies.
 */
class AgentInstructionsTest {

    private static final String WRAPPER_RULE = "must hold exactly '@AGENTS.md' and a line break, so that it adds no "
            + "rules of its own (ADR 0022)";

    @TempDir
    Path dir;

    @Test
    void theRepositoryWithItsFivePairsPasses() {
        Fixture fixture = Fixture.copy(dir).passingEvidence();

        assertThat(fixture.read("apps/shipping-web/CLAUDE.md")).isEqualTo("@AGENTS.md\n");
        assertThat(problems(fixture)).isEmpty();
    }

    @Test
    void rulesWithoutAWrapperFail() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().delete("apps/shipping-web/CLAUDE.md");

        assertFails(fixture).hasMessageContaining("apps/shipping-web/AGENTS.md has no CLAUDE.md beside it: add "
                + "apps/shipping-web/CLAUDE.md holding exactly '@AGENTS.md' and a line break, so that Claude Code "
                + "reads these rules wherever it loads CLAUDE.md files (ADR 0022)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"@AGENTS.md\nRun the tests first.\n", "@AGENTS.md", "@AGENTS.md\r\n", "﻿@AGENTS.md\n",
        "@./AGENTS.md\n", "@AGENTS.md\n@AGENTS.md\n", "", "\n@AGENTS.md\n", "@agents.md\n"})
    void aWrapperThatHoldsAnythingElseFails(String text) {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write("apps/shipping-api/CLAUDE.md", text);

        assertFails(fixture).hasMessageContaining("apps/shipping-api/CLAUDE.md " + WRAPPER_RULE);
    }

    @Test
    void aWrapperWithoutRulesFails() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write("tools/docs/CLAUDE.md", "@AGENTS.md\n");

        assertFails(fixture).hasMessageContaining("tools/docs/CLAUDE.md has no AGENTS.md beside it: the rules live in "
                + "an AGENTS.md, which the CLAUDE.md imports (ADR 0022)");
    }

    @Test
    void newRulesWithoutAWrapperFail() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write("tools/docs/AGENTS.md", "# Rules\n");

        assertFails(fixture).hasMessageContaining("tools/docs/AGENTS.md has no CLAUDE.md beside it");
    }

    @Test
    void aNewPairPasses() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write("tools/docs/AGENTS.md", "# Rules\n")
                .write("tools/docs/CLAUDE.md", "@AGENTS.md\n");

        assertThat(problems(fixture)).isEmpty();
    }

    @Test
    void theRootNeedsItsRules() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().delete("AGENTS.md").delete("CLAUDE.md");

        assertThat(problems(fixture)).containsExactly("AGENTS.md is missing: the repository root holds the rules that "
                + "every agent reads first (ADR 0022)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"tools/docs/Claude.md", "tools/docs/agents.md", "tools/docs/AGENTS.MD"})
    void aFileNamedLikeAnInstructionFileInAnotherCaseFails(String path) {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(path, "@AGENTS.md\n");

        assertFails(fixture).hasMessageContaining(path + " is named like an instruction file in another letter case; "
                + "name it AGENTS.md or CLAUDE.md (ADR 0022)");
    }

    @Test
    void aLinkedWrapperFails() throws IOException {
        Fixture fixture = Fixture.copy(dir).passingEvidence().delete("apps/shipping-web/CLAUDE.md");
        Files.createSymbolicLink(fixture.root().resolve("apps/shipping-web/CLAUDE.md"), Path.of("../../CLAUDE.md"));

        assertFails(fixture).hasMessageContaining("apps/shipping-web/CLAUDE.md is a link or not a regular file; an "
                + "instruction file is a regular file (ADR 0022)");
    }

    @ParameterizedTest
    @ValueSource(strings = {".claude/CLAUDE.md", ".claude/AGENTS.md", "apps/shipping-api/.claude/CLAUDE.md"})
    void anInstructionFileUnderDotClaudeFails(String path) {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(path, "@AGENTS.md\n");

        assertFails(fixture).hasMessageContaining(path + " is under .claude/; the rules live in AGENTS.md files "
                + "(ADR 0022)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"build/CLAUDE.md", ".gradle/AGENTS.md", ".git/CLAUDE.md", "node_modules/pkg/AGENTS.md",
        "apps/shipping-web/node_modules/pkg/CLAUDE.md", "tools/docs/build/AGENTS.md",
        ".claude/worktrees/one/CLAUDE.md"})
    void malformedInstructionFilesInSkippedDirectoriesAreNotRead(String path) {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(path, "Rules of my own.\n");

        assertThat(problems(fixture)).isEmpty();
    }

    @Test
    void aDirectoryNamedBuildInsideASourceTreeIsAPackageAndIsRead() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write("apps/shipping-api/src/main/java/io/example/build/CLAUDE.md", "@AGENTS.md\n");

        assertFails(fixture).hasMessageContaining("apps/shipping-api/src/main/java/io/example/build/CLAUDE.md has no "
                + "AGENTS.md beside it");
    }

    @Test
    void discoveryFindsEveryCandidateOutsideSkippedDirectoriesWithoutFollowingLinks() throws IOException {
        Path tree = dir.resolve("tree");
        for (String file : List.of("AGENTS.md", "CLAUDE.md", "a/claude.md", "b/Agents.MD", "c/notes.md",
                "build/CLAUDE.md", "x/node_modules/y/AGENTS.md", ".claude/CLAUDE.md", ".claude/worktrees/w/AGENTS.md",
                "linked/real/AGENTS.md")) {
            write(tree.resolve(file), "@AGENTS.md\n");
        }
        Files.createDirectories(tree.resolve("d/AGENTS.md"));
        Files.createSymbolicLink(tree.resolve("c/CLAUDE.md"), Path.of("../CLAUDE.md"));
        Files.createSymbolicLink(tree.resolve("followed"), Path.of("linked/real"));

        assertThat(AgentInstructions.candidates(tree)).containsExactly(
                new Candidate(".claude/CLAUDE.md", true),
                new Candidate("AGENTS.md", true),
                new Candidate("CLAUDE.md", true),
                new Candidate("a/claude.md", true),
                new Candidate("b/Agents.MD", true),
                new Candidate("c/CLAUDE.md", false),
                new Candidate("d/AGENTS.md", false),
                new Candidate("linked/real/AGENTS.md", true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "@AGENTS.md\nMore rules.\n"})
    @DisabledOnOs(OS.WINDOWS)
    void aDirectoryWhoseNameHoldsABackslashIsNotTakenForANestedOne(String wrapper) {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write("tools/a/b/AGENTS.md", "# Rules\n")
                .write("tools/a/b/CLAUDE.md", "@AGENTS.md\n").write("tools/a\\b/AGENTS.md", "# Rules\n");
        if (!wrapper.isEmpty()) {
            fixture.write("tools/a\\b/CLAUDE.md", wrapper);
        }

        assertThat(problems(fixture)).containsExactly(wrapper.isEmpty()
                ? "tools/a\\b/AGENTS.md has no CLAUDE.md beside it: add tools/a\\b/CLAUDE.md holding exactly "
                        + "'@AGENTS.md' and a line break, so that Claude Code reads these rules wherever it loads "
                        + "CLAUDE.md files (ADR 0022)"
                : "tools/a\\b/CLAUDE.md " + WRAPPER_RULE);
    }

    @Test
    void theFixtureCopiesNoInstructionFileFromADirectoryTheCheckSkips() throws IOException {
        Path repository = dir.resolve("repository");
        List<String> skipped = List.of(".claude/worktrees/w/CLAUDE.md", "build/CLAUDE.md", ".gradle/AGENTS.md",
                ".git/CLAUDE.md", "x/node_modules/y/AGENTS.md");
        for (String file : List.of("AGENTS.md", "CLAUDE.md", "a/AGENTS.md", "a/CLAUDE.md")) {
            write(repository.resolve(file), "@AGENTS.md\n");
        }
        for (String file : skipped) {
            write(repository.resolve(file), "Rules of my own.\n");
        }

        Fixture fixture = Fixture.copy(dir.resolve("copy"), repository);

        assertThat(fixture.root().resolve("a/CLAUDE.md")).exists();
        assertThat(skipped).allSatisfy(file -> assertThat(fixture.root().resolve(file)).doesNotExist());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aWrapperThatCannotBeReadFailsTheCheck() throws IOException {
        Fixture fixture = Fixture.copy(dir).passingEvidence();
        Path wrapper = fixture.root().resolve("apps/shipping-web/CLAUDE.md");
        Files.setPosixFilePermissions(wrapper, Set.of());
        try {
            assumeFalse(Files.isReadable(wrapper), "the file stays readable, as for a superuser");

            assertThatThrownBy(() -> problems(fixture)).isInstanceOf(DocsException.class)
                    .hasMessageContaining("apps/shipping-web/CLAUDE.md cannot be read");
        } finally {
            Files.setPosixFilePermissions(wrapper, PosixFilePermissions.fromString("rw-r--r--"));
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aDirectoryThatCannotBeListedFailsTheCheck() throws IOException {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write("tools/locked/AGENTS.md", "# Rules\n");
        Path locked = fixture.root().resolve("tools/locked");
        Files.setPosixFilePermissions(locked, Set.of());
        try {
            assumeFalse(Files.isReadable(locked), "the directory stays readable, as for a superuser");

            assertThatThrownBy(() -> problems(fixture)).isInstanceOf(DocsException.class)
                    .hasMessageContaining("the instruction files cannot be listed");
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    void everyViolationIsReportedInPathOrder() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().delete("apps/shipping-web/CLAUDE.md")
                .write("apps/shipping-api/CLAUDE.md", "@AGENTS.md\nAnd one more rule.\n");

        assertThat(problems(fixture)).containsExactly(
                "apps/shipping-api/CLAUDE.md " + WRAPPER_RULE,
                "apps/shipping-web/AGENTS.md has no CLAUDE.md beside it: add apps/shipping-web/CLAUDE.md holding "
                        + "exactly '@AGENTS.md' and a line break, so that Claude Code reads these rules wherever it "
                        + "loads CLAUDE.md files (ADR 0022)");
    }

    @Test
    void generationStopsOnAViolation() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().delete("security/CLAUDE.md");

        assertThatThrownBy(() -> GeneratedFiles.generate(Sources.read(fixture.root(), List.of(fixture.reports()))))
                .isInstanceOf(DocsException.class)
                .hasMessageContaining("security/AGENTS.md has no CLAUDE.md beside it");
    }

    private static List<String> problems(Fixture fixture) {
        Problems problems = new Problems();
        AgentInstructions.check(fixture.root(), problems);
        return problems.list();
    }

    private static AbstractThrowableAssert<?, ? extends Throwable> assertFails(Fixture fixture) {
        return assertThatThrownBy(() -> Consistency.check(Sources.read(fixture.root(), List.of(fixture.reports()))))
                .isInstanceOf(DocsException.class);
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
