package io.github.stigjohnny.docs.check;

import static io.github.stigjohnny.docs.testing.Demos.GATE;
import static io.github.stigjohnny.docs.testing.Demos.VISUAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DemoRecord;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.Decks;
import io.github.stigjohnny.docs.testing.Demos;
import io.github.stigjohnny.docs.testing.Fixture;
import io.github.stigjohnny.docs.testing.Repository;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The demonstrations' negative fixtures (docs/demos/how-to.md): each changes one thing in a demonstration and expects
 * the check to fail and say why. The demonstrations are read against one copy of the repository, whose tests and
 * test reports they name; the check never runs a command they show. Then the milestone deck's, against the same copy,
 * whose capabilities a deck counts: each changes one thing in the sample deck.
 */
class DemoCheckTest {

    @TempDir
    static Path dir;

    private static Sources sources;

    @BeforeAll
    static void readTheSources() {
        Fixture fixture = Fixture.copy(dir.resolve("copy")).passingEvidence().withoutDemos()
                .write("libs/one/src/test/java/io/example/one/TwinTest.java", "class TwinTest {}\n")
                .write("libs/two/src/test/java/io/example/two/TwinTest.java", "class TwinTest {}\n");
        sources = Sources.read(fixture.root(), List.of(fixture.reports()));
    }

    @Test
    void theRepositoryWithItsHowToPasses() {
        Consistency.check(sources);
    }

    @ParameterizedTest
    @ValueSource(strings = {"gate", "visual", "docs", "build", "feature"})
    void aDemonstrationOfEveryKindPasses(String kind) {
        assertThat(problems(Demos.path(kind), Demos.text(kind))).isEmpty();
    }

    @Test
    void theRepositoryCheckRunsTheDemonstrationsCheck(@TempDir Path copy) {
        Fixture fixture = Fixture.copy(copy).passingEvidence().withoutDemos().write("docs/demos/42-a-gate.md",
                GATE.replace("kind: gate", "kind: video"));

        assertThatThrownBy(() -> Consistency.check(Sources.read(fixture.root(), List.of(fixture.reports()))))
                .isInstanceOf(DocsException.class)
                .hasMessageContaining("docs/demos/42-a-gate.md: kind 'video' is not one of visual, gate, build, docs "
                        + "and feature");
    }

    @ParameterizedTest
    @ValueSource(strings = {"docs/demos/42-gate.png", "docs/demos/clip.mp4", "docs/demos/42/notes.txt"})
    void aFileUnderDocsDemosThatIsNotMarkdownFailsBecauseTheRepositoryIsPublicToo(String file, @TempDir Path copy) {
        Fixture fixture = Fixture.copy(copy).passingEvidence().withoutDemos().write(file, "not text\n");

        assertThatThrownBy(() -> Consistency.check(Sources.read(fixture.root(), List.of(fixture.reports()))))
                .isInstanceOf(DocsException.class).hasMessageContaining(file + ": docs/demos/ holds demonstrations "
                        + "as Markdown only; media waits for the reviewed media gate");
    }

    @Test
    void theTemplateOfTheHowToFailsUntilItIsFilledIn() {
        String howTo = Repository.read("docs/demos/how-to.md");
        int start = howTo.indexOf("````markdown\n");
        assertThat(start).as("the how-to shows the template in a ````markdown fence").isNotNegative();
        String template = howTo.substring(start + "````markdown\n".length(), howTo.indexOf("\n````", start) + 1);

        assertThat(problems("docs/demos/42-template.md", template)).contains(
                "docs/demos/42-template.md: pr must be the pull request's number, a positive integer without quotes",
                "docs/demos/42-template.md: YYYY-MM-DD is left from the template",
                "docs/demos/42-template.md: title holds '<observable outcome>', an unfilled template placeholder",
                "docs/demos/42-template.md: What changed holds '<Before and after, in plain words.>', an unfilled "
                        + "template placeholder",
                "docs/demos/42-template.md: Evidence holds '<verbatim output excerpt>', an unfilled template "
                        + "placeholder");
    }

    static Stream<Arguments> malformedMetadata() {
        return Stream.of(
                Arguments.of("pr: 42", "pr: \"42\"", "pr must be the pull request's number, a positive integer "
                        + "without quotes"),
                Arguments.of("pr: 42", "pr: 0", "pr must be the pull request's number, a positive integer without "
                        + "quotes"),
                Arguments.of("pr: 42", "pr: -42", "pr must be the pull request's number, a positive integer without "
                        + "quotes"),
                Arguments.of("pr: 42", "pr: 42.0", "pr must be the pull request's number, a positive integer without "
                        + "quotes"),
                Arguments.of("pr: 42", "pr: true", "pr must be the pull request's number, a positive integer without "
                        + "quotes"),
                Arguments.of("pr: 42\n", "", "frontmatter missing key 'pr'"),
                Arguments.of("kind: gate", "kind: video", "kind 'video' is not one of visual, gate, build, docs and "
                        + "feature"),
                Arguments.of("kind: gate\n", "", "frontmatter missing key 'kind'"),
                Arguments.of("recorded: 2026-11-07", "recorded: 2026-13-01", "recorded '2026-13-01' is not an ISO date "
                        + "(YYYY-MM-DD)"),
                Arguments.of("recorded: 2026-11-07", "recorded: 7 November 2026", "recorded '7 November 2026' is not "
                        + "an ISO date (YYYY-MM-DD)"),
                Arguments.of("updated: 2026-11-07", "updated: 2026-11-06", "recorded 2026-11-07 is later than "
                        + "updated 2026-11-06"),
                Arguments.of("tests: [BrainCheckTest]", "tests: BrainCheckTest", "tests is a list of test class "
                        + "names, such as [BrainCheckTest]"),
                Arguments.of("tests: [BrainCheckTest]", "tests: [brainCheck]", "tests names 'brainCheck', which is "
                        + "not a test class name (it ends in Test)"),
                Arguments.of("tests: [BrainCheckTest]", "tests: [BrainCheckTest, BrainCheckTest]", "tests names "
                        + "BrainCheckTest twice"),
                Arguments.of("tests: [BrainCheckTest]", "tests: [NoSuchTest]", "tests names NoSuchTest, which does "
                        + "not exist in the repository"),
                Arguments.of("tests: [BrainCheckTest]", "tests: [TwinTest]", "tests names TwinTest, which is "
                        + "ambiguous: more than one test class is called TwinTest"),
                Arguments.of("status: accepted", "status: accepted\nmerged: 2026-11-07", "frontmatter key 'merged' "
                        + "is not one of title, description, status, updated, pr, kind, recorded and tests; a "
                        + "revision or a merge is never entered by hand"),
                Arguments.of("status: accepted", "status: accepted\nrevision: 44ef312", "frontmatter key 'revision' "
                        + "is not one of title, description, status, updated, pr, kind, recorded and tests; a "
                        + "revision or a merge is never entered by hand"));
    }

    @ParameterizedTest
    @MethodSource("malformedMetadata")
    void malformedMetadataFails(String from, String to, String problem) {
        assertThat(problems("docs/demos/42-gate.md", replace(GATE, from, to)))
                .containsExactly("docs/demos/42-gate.md: " + problem);
    }

    @ParameterizedTest
    @ValueSource(strings = {"docs/demos/gate.md", "docs/demos/042-gate.md", "docs/demos/42_gate.md",
        "docs/demos/42-Gate.md", "docs/demos/42-.md", "docs/demos/42.md", "docs/demos/old/42-gate.md"})
    void aDemonstrationIsNamedAfterItsPullRequestAndASlug(String path) {
        assertThat(problems(path, GATE)).containsExactly(path + ": a demonstration is named "
                + "docs/demos/<pr>-<slug>.md: its pull request's number, a hyphen and a kebab-case slug");
    }

    @Test
    void theFileNameAndThePullRequestAgree() {
        assertThat(problems("docs/demos/41-gate.md", GATE))
                .containsExactly("docs/demos/41-gate.md: the file name starts with 41, but pr is 42");
    }

    @Test
    void twoDemonstrationsOfOnePullRequestFailBecauseTheyWouldShareASheet() {
        List<String> problems = DemoCheck.problems(sources, List.of(DocFile.parse("docs/demos/42-a.md", GATE),
                DocFile.parse("docs/demos/42-b.md", GATE)));

        assertThat(problems).containsExactly("docs/demos/42-b.md: pull request #42 already has a demonstration, "
                + "docs/demos/42-a.md; each has one sheet, X-42");
    }

    static Stream<Arguments> missingSections() {
        return Stream.of(
                Arguments.of("visual", "See it", "a visual demonstration needs the section 'See it'"),
                Arguments.of("docs", "See it", "a docs demonstration needs the section 'See it'"),
                Arguments.of("gate", "Try it", "a gate demonstration needs the section 'Try it'"),
                Arguments.of("build", "Try it", "a build demonstration needs the section 'Try it'"),
                Arguments.of("feature", "Try it", "a feature demonstration needs the section 'See it' or 'Try it'"),
                Arguments.of("gate", "What changed", "a gate demonstration needs the section 'What changed'"),
                Arguments.of("visual", "Evidence", "a visual demonstration needs the section 'Evidence'"),
                Arguments.of("build", "Limits", "a build demonstration needs the section 'Limits'"));
    }

    @ParameterizedTest
    @MethodSource("missingSections")
    void eachKindNeedsItsSections(String kind, String section, String problem) {
        assertThat(problems(Demos.path(kind), withoutSection(Demos.text(kind), section)))
                .containsExactly(Demos.path(kind) + ": " + problem);
    }

    @Test
    void aSectionThatIsUnknownRepeatedOutOfOrderOrEmptyFails() {
        assertThat(problems("docs/demos/42-gate.md", GATE.replace("## Limits", "## Background\n\nMore.\n\n## Limits")))
                .containsExactly("docs/demos/42-gate.md: the section 'Background' is not one of What changed, See it, "
                        + "Try it, Evidence and Limits");
        assertThat(problems("docs/demos/42-gate.md", GATE + "\n## Limits\n\nAgain.\n"))
                .containsExactly("docs/demos/42-gate.md: the section 'Limits' appears twice");
        assertThat(problems("docs/demos/43-visual.md", VISUAL.replace("## See it", "## Swap").replace("## What changed",
                "## See it").replace("## Swap", "## What changed")))
                .containsExactly("docs/demos/43-visual.md: the section 'What changed' comes after 'See it'; the order "
                        + "is What changed, See it, Try it, Evidence, Limits");
        assertThat(problems("docs/demos/42-gate.md", replace(GATE, "It covers the declared dependencies on the "
                + "sanctioned path only.\n", "### Notes\n")))
                .containsExactly("docs/demos/42-gate.md: the section 'Limits' is empty");
    }

    @Test
    void textBeforeTheFirstSectionOrATitleHeadingFails() {
        assertThat(problems("docs/demos/42-gate.md", GATE.replace("## What changed", "An introduction.\n\n## What "
                + "changed")))
                .containsExactly("docs/demos/42-gate.md: text stands before the first section; everything is under "
                        + "What changed, See it, Try it, Evidence or Limits");
        assertThat(problems("docs/demos/42-gate.md", GATE.replace("## What changed", "# A title\n\n## What changed")))
                .containsExactly("docs/demos/42-gate.md: a level-1 heading; a demonstration's title is its frontmatter "
                        + "title");
    }

    @Test
    void everyBlockIsCheckedTheSectionHeadingsAndTheTextBeforeThemIncluded() {
        assertThat(problems("docs/demos/42-gate.md", replace(GATE, "## Limits", "## <b>Limits</b>")))
                .containsExactly("docs/demos/42-gate.md: the heading 'Limits' holds '<b>': raw HTML or an unfilled "
                        + "template placeholder; a demonstration is text only");
        assertThat(problems("docs/demos/42-gate.md", replace(GATE, "## Limits",
                "## [Limits](https://example.com/shot.png)")))
                .containsExactly("docs/demos/42-gate.md: the heading 'Limits' links the media file "
                        + "'https://example.com/shot.png'; a demonstration is text only until the reviewed media gate "
                        + "exists");
        assertThat(problems("docs/demos/42-gate.md", replace(GATE, "## What changed",
                "<img src=\"shot.png\" alt=\"\">\n\n## What changed")))
                .containsExactly("docs/demos/42-gate.md: text stands before the first section; everything is under "
                        + "What changed, See it, Try it, Evidence or Limits",
                        "docs/demos/42-gate.md: the text before the first section holds '<img src=\"shot.png\" "
                                + "alt=\"\">': raw HTML or an unfilled template placeholder; a demonstration is text "
                                + "only");
        assertThat(problems("docs/demos/42-gate.md", replace(GATE, "## What changed", "![](shot.png)\n\n## What "
                + "changed")))
                .containsExactly("docs/demos/42-gate.md: text stands before the first section; everything is under "
                        + "What changed, See it, Try it, Evidence or Limits",
                        "docs/demos/42-gate.md: the text before the first section shows the image 'shot.png'; a "
                                + "demonstration is text only until the reviewed media gate exists");
    }

    @Test
    void aSectionNamedInsideAFenceIsNotASection() {
        String gate = GATE.replace("## Dependency allowlist: clean", "## Limits");

        assertThat(problems("docs/demos/42-gate.md", gate)).isEmpty();
        assertThat(problems("docs/demos/42-gate.md", gate.substring(0, gate.lastIndexOf("## Limits\n"))))
                .containsExactly("docs/demos/42-gate.md: a gate demonstration needs the section 'Limits'");
    }

    @Test
    void theEvidenceSaysItIsRecordedBeforeAnyRecordedOutput() {
        String marker = "Recorded output \u2014 not rerun by this site build.\n";

        assertThat(problems("docs/demos/42-gate.md", GATE.replace(marker, "")))
                .containsExactly("docs/demos/42-gate.md: Evidence lacks the line 'Recorded output \u2014 not rerun by "
                        + "this site build.'");
        assertThat(problems("docs/demos/42-gate.md", GATE.replace(marker, "Recorded output - not rerun by this site "
                + "build.\n")))
                .containsExactly("docs/demos/42-gate.md: Evidence lacks the line 'Recorded output \u2014 not rerun by "
                        + "this site build.'");
        assertThat(problems("docs/demos/42-gate.md", GATE.replace(marker, "").replace("Reviewed in pull request #42.",
                marker)))
                .containsExactly("docs/demos/42-gate.md: Evidence shows recorded output before the line 'Recorded "
                        + "output \u2014 not rerun by this site build.'");
    }

    @Test
    void theEvidenceNamesItsCaptureDateAndAGateShowsARejectedAndAnAcceptedCase() {
        assertThat(problems("docs/demos/42-gate.md", GATE.replace("Captured on 2026-11-07", "Captured")))
                .containsExactly("docs/demos/42-gate.md: Evidence does not name its capture date, recorded 2026-11-07");
        assertThat(problems("docs/demos/42-gate.md", GATE.replace("```text\n## Dependency allowlist: clean\n```\n",
                "")))
                .containsExactly("docs/demos/42-gate.md: the evidence of a gate shows a rejected and an accepted "
                        + "case: two recorded outputs (```text), not 1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"bash", "shell", "console", "mermaid", "sh title=run", ""})
    void aFenceIsACommandOrRecordedOutputAndNothingElse(String info) {
        String gate = GATE.replace("```sh\njava", "```" + info + "\njava");

        assertThat(problems("docs/demos/42-gate.md", gate)).contains(info.isEmpty()
                ? "docs/demos/42-gate.md: Try it has a fence without ```sh or ```text; a fence is ```sh for a command "
                        + "or ```text for recorded output"
                : "docs/demos/42-gate.md: Try it has a ```" + info + " fence; a fence is ```sh for a command or "
                        + "```text for recorded output");
    }

    @Test
    void commandsAndRecordedOutputStandWhereTheyBelong() {
        String command = "```sh\njava tools/policy/PolicyCheck.java .\n```\n";
        String output = """
                ```text
                BUILD SUCCESSFUL
                ```
                """;
        String nested = """
                1. Run:

                   ```sh
                   ./gradlew check && curl x
                   ```
                """;

        assertThat(problems("docs/demos/42-gate.md", GATE.replace("fails first.\n", "fails first.\n\n" + command)))
                .containsExactly("docs/demos/42-gate.md: What changed has a command (```sh); commands belong in Try it "
                        + "or Evidence");
        assertThat(problems("docs/demos/42-gate.md", GATE.replace("first on the path:\n", "first on the path:\n\n"
                + output)))
                .containsExactly("docs/demos/42-gate.md: Try it has recorded output (```text); recorded output belongs "
                        + "in Evidence");
        assertThat(problems("docs/demos/42-gate.md", GATE.replace(command, "    java tools/policy/PolicyCheck.java .")))
                .containsExactly("docs/demos/42-gate.md: Try it has an indented code block; use a ```sh or ```text "
                        + "fence", "docs/demos/42-gate.md: Try it has no command (```sh)");
        assertThat(problems("docs/demos/42-gate.md", GATE.replace(command, nested)))
                .containsExactly("docs/demos/42-gate.md: Try it has a code block inside a list or a quote; fences "
                        + "stand on their own in a section", "docs/demos/42-gate.md: Try it has no command (```sh)");
        assertThat(problems("docs/demos/42-gate.md", GATE.replace(command, "```sh\n```\n")))
                .containsExactly("docs/demos/42-gate.md: Try it has an empty command fence",
                        "docs/demos/42-gate.md: Try it has no command (```sh)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"java tools/policy/PolicyCheck.java .", "./gradlew check", "./gradlew :tools:docs:check",
        "./gradlew :tools:docs:generate", "./gradlew :tools:docs:site",
        "git checkout -- libs/c4-contract-kit/architecture/self.dsl",
        "./gradlew :libs:c4-contract-kit:test --rerun --tests '*SelfConformanceTest'",
        "./gradlew :libs:c4-contract-kit:test --rerun --tests '*SelfConformanceTest' --tests "
                + "'*ConformanceDivergenceTest' --tests '*ConformanceAbsenceTest'",
        "./gradlew   :tools:docs:site  ", "./gradlew :libs:c4-contract-kit:test --rerun \\\n    --tests "
                + "'*ConformanceAbsenceTest'"})
    void anApprovedCommandPassesWhole(String command) {
        assertThat(problems("docs/demos/42-gate.md", withCommand(command))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"./gradlew check --init-script evil.gradle", "./gradlew checkstyle", "./gradlew check -q",
        "./gradlew :tools:docs:site --offline", "./gradlew :libs:c4-contract-kit:pitest", "./gradlew build",
        "./gradlew tasks", "git checkout -- .", "git checkout -- libs/c4-contract-kit/architecture/self.dsl README.md",
        "git status", "git push", "java tools/policy/PolicyCheck.java . extra", "java tools/policy/PolicyCheck.java",
        "java -jar x.jar", "PATH=bin ./gradlew check", "sudo ./gradlew check", "./gradlew :libs:c4-contract-kit:test",
        "./gradlew :libs:c4-contract-kit:test --rerun --tests '*Self*'",
        "./gradlew :libs:c4-contract-kit:test --rerun --tests '*SelfConformanceTest' --init-script x.gradle",
        "./gradlew :libs:c4-contract-kit:test --tests '*SelfConformanceTest' --rerun",
        "./gradlew :libs:c4-contract-kit:test --rerun --tests \"*SelfConformanceTest\"",
        "./gradlew :tools:docs:test --rerun --tests '*SelfConformanceTest'",
        "./gradlew :libs:c4-contract-kit:test --rerun --tests '*ShopFixture'",
        "./gradlew\u00A0check", "\uFF0E/gradlew check", "./gradlew check\u200B"})
    void aCommandThatIsNotApprovedWholeFails(String command) {
        assertThat(problems("docs/demos/42-gate.md", withCommand(command))).singleElement().asString()
                .startsWith("docs/demos/42-gate.md: Try it: '")
                .endsWith("' is not an approved command; a demonstration shows only the commands "
                        + "docs/demos/how-to.md lists, whole and with exactly their arguments");
    }

    @Test
    void aLineContinuationJoinsTheLinesAsTheShellDoesWithoutAddingASpace() {
        // A backslash and the newline after it are removed, and nothing is put in their place: the shell reads
        // "./gradlew\" and "check" on the next line as ./gradlewcheck.
        assertThat(problems("docs/demos/42-gate.md", withCommand("./gradlew\\\ncheck")))
                .containsExactly("docs/demos/42-gate.md: Try it: './gradlewcheck' is not an approved command; a "
                        + "demonstration shows only the commands docs/demos/how-to.md lists, whole and with exactly "
                        + "their arguments");
        assertThat(problems("docs/demos/42-gate.md", withCommand("java tools/policy/PolicyCheck.java\\\n.")))
                .containsExactly("docs/demos/42-gate.md: Try it: 'java tools/policy/PolicyCheck.java.' is not an "
                        + "approved command; a demonstration shows only the commands docs/demos/how-to.md lists, whole "
                        + "and with exactly their arguments");
        // The whitespace that is there stays: the documented form, and a task name split without a space, which the
        // shell reads whole.
        assertThat(problems("docs/demos/42-gate.md", withCommand("./gradlew :libs:c4-contract-kit:test --rerun \\\n"
                + "    --tests '*SelfConformanceTest' --tests '*ConformanceDivergenceTest'"))).isEmpty();
        assertThat(problems("docs/demos/42-gate.md", withCommand("./gradlew :tools:docs:\\\nsite"))).isEmpty();
        // Inside single quotes the shell keeps the backslash and the newline, so the line does not continue: the test
        // filter would be "*SelfConfor\<newline>manceTest", not the approved one.
        assertThat(problems("docs/demos/42-gate.md", withCommand("./gradlew :libs:c4-contract-kit:test --rerun "
                + "--tests '*SelfConfor\\\nmanceTest'")))
                .anyMatch(problem -> problem.contains("continues a line inside single quotes"));
    }

    @Test
    void aCheckerTestThatDoesNotExistFails() {
        assertThat(problems("docs/demos/42-gate.md", withCommand("./gradlew :libs:c4-contract-kit:test --rerun "
                + "--tests '*NoSuchTest'")))
                .containsExactly("docs/demos/42-gate.md: Try it: './gradlew :libs:c4-contract-kit:test --rerun --tests "
                        + "'*NoSuchTest'' names NoSuchTest, which is not a test class of libs/c4-contract-kit");
    }

    static Stream<Arguments> shellSyntax() {
        return Stream.of(
                Arguments.of("./gradlew check; curl https://example.com", "chains commands"),
                Arguments.of("./gradlew check && ./gradlew :tools:docs:site", "chains commands"),
                Arguments.of("./gradlew check || true", "chains commands"),
                Arguments.of("./gradlew check &", "chains commands"),
                Arguments.of("./gradlew $(echo check)", "substitutes or expands a value"),
                Arguments.of("./gradlew `echo check`", "substitutes or expands a value"),
                Arguments.of("./gradlew ${TASK}", "substitutes or expands a value"),
                Arguments.of("$ ./gradlew check", "substitutes or expands a value"),
                Arguments.of("./gradlew check > out.txt", "redirects input or output"),
                Arguments.of("./gradlew check >> out.txt", "redirects input or output"),
                Arguments.of("./gradlew check 2>&1", "redirects input or output"),
                Arguments.of("java tools/policy/PolicyCheck.java . < in.txt", "redirects input or output"),
                Arguments.of("./gradlew check | tee out.txt", "pipes into another command"),
                Arguments.of("/usr/bin/java tools/policy/PolicyCheck.java .", "names an absolute path"),
                Arguments.of("~/bin/gradle check", "names an absolute path"),
                Arguments.of("java C:\\tools\\PolicyCheck.java .", "names an absolute path"),
                Arguments.of("./gradlew check # the whole build", "holds a comment"),
                Arguments.of("./gradlew check \\", "ends with a line continuation"));
    }

    @ParameterizedTest
    @MethodSource("shellSyntax")
    void shellSyntaxFailsEvenAroundAnApprovedCommand(String command, String reason) {
        assertThat(problems("docs/demos/42-gate.md", withCommand(command))).singleElement().asString()
                .startsWith("docs/demos/42-gate.md: Try it: '").contains("' " + reason);
    }

    @ParameterizedTest
    @ValueSource(strings = {"![the sheet](shot.png)", "<img src=\"shot.png\" alt=\"the sheet\">",
        "<iframe src=\"https://example.com/demo\"></iframe>", "<video src=\"clip.mp4\" controls></video>",
        "A <b>bold</b> claim.", "[the recording](clip.mp4)", "[a capture](https://example.com/demo.gif)",
        "[a session](session.cast)", "[a screenshot](https://example.com/shot.PNG#top)",
        "[a capture](https://example.com/shot%2epng)", "[a capture](https://example.com/shot.%70ng)",
        "[a capture](https://example.com/shot%2EpN%67)", "[a capture](https://example.com/shot%2epng?size=large)",
        "[a capture](https://example.com/shot%2epng#top)", "[a clip](clip%2Emp4)"})
    void aDemonstrationIsTextOnly(String markdown) {
        List<String> problems = problems("docs/demos/42-gate.md", GATE.replace("Now the check\nfails first.\n",
                "Now the check\nfails first. " + markdown + "\n"));

        assertThat(problems).singleElement().asString().startsWith("docs/demos/42-gate.md: What changed ")
                .contains("a demonstration is text only");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/shot%zz.png", "https://example.com/shot%2",
        "https://example.com/a%C3%28b", "https://example.com/shot%\uFF12e.png", "page%.md"})
    void aLinkWhosePercentEncodingIsMalformedFails(String destination) {
        assertThat(problems("docs/demos/42-gate.md", GATE.replace("fails first.\n", "fails first. See [the page]("
                + destination + ").\n")))
                .containsExactly("docs/demos/42-gate.md: What changed links '" + destination + "', whose "
                        + "percent-encoding is malformed");
    }

    @Test
    void aDateOrAPlaceholderLeftFromTheTemplateFails() {
        assertThat(problems("docs/demos/42-gate.md", GATE.replace("Reviewed in pull request #42.", "Reviewed on "
                + "YYYY-MM-DD.")))
                .containsExactly("docs/demos/42-gate.md: YYYY-MM-DD is left from the template");
        assertThat(problems("docs/demos/42-gate.md", GATE.replace("## Dependency allowlist: clean",
                "<verbatim output excerpt>")))
                .containsExactly("docs/demos/42-gate.md: Evidence holds '<verbatim output excerpt>', an unfilled "
                        + "template placeholder");
        assertThat(problems("docs/demos/42-gate.md", GATE.replace("Reviewed in pull request #42.",
                "<Capture date, environment, procedure and exit status.>")))
                .containsExactly("docs/demos/42-gate.md: Evidence holds '<Capture date, environment, procedure and "
                        + "exit status.>', an unfilled template placeholder");
        // CommonMark reads this one as an HTML tag; either way it is left from the template.
        assertThat(problems("docs/demos/42-gate.md", GATE.replace("Reviewed in pull request #42.",
                "<Review reference and separately identified current-build tests.>")))
                .containsExactly("docs/demos/42-gate.md: Evidence holds '<Review reference and separately identified "
                        + "current-build tests.>': raw HTML or an unfilled template placeholder; a demonstration is "
                        + "text only");
        assertThat(problems("docs/demos/42-gate.md", GATE.replace("Reviewed in pull request #42.", "<why it "
                + "matters>")))
                .containsExactly("docs/demos/42-gate.md: Evidence holds '<why it matters>': raw HTML or an unfilled "
                        + "template placeholder; a demonstration is text only");
        assertThat(problems("docs/demos/42-gate.md", GATE.replace("description: \"A library without a review can no "
                + "longer reach the build.\"", "description: \"<why it matters>\"")))
                .containsExactly("docs/demos/42-gate.md: description holds '<why it matters>', an unfilled template "
                        + "placeholder");
    }

    @Test
    void outputMayShowAngleBracketsThatAreNotAPlaceholder() {
        String gate = GATE.replace("## Dependency allowlist: clean", "Authorization: Bearer <token>\nMap<String, "
                + "String> entries");

        assertThat(problems("docs/demos/42-gate.md", gate)).isEmpty();
    }

    @Test
    void theApprovedCommandsAreTheOnesTheReadmeDocumentsAndTheHowToLists() {
        String readme = Repository.read("README.md").replaceAll("\\\\\n\\s*", "").replaceAll("[ \\t]+", " ");
        String howTo = Repository.read("docs/demos/how-to.md");

        for (String command : DemoCheck.COMMANDS) {
            assertThat(readme).as(command).contains(command);
            assertThat(howTo).as(command).contains("`" + command + "`");
        }
        assertThat(readme.lines().map(String::strip).filter(line -> DemoCheck.CHECKER_TESTS.matcher(line).matches()))
                .as("the checker's documented test run").hasSize(1);
        assertThat(howTo).contains("`./gradlew :libs:c4-contract-kit:test --rerun --tests '*<TestClass>'`");
        assertThat(DemoCheck.COMMANDS).noneMatch(command -> command.contains("pitest"));
    }

    // Milestone decks: docs/demos/deck-<slug>.md, checked by their own rules; a deck is not a demonstration.

    private static final String DECK = Decks.PATH + ": ";
    private static final String SLIDE_3 = "slide 3 'Én endring porten avviser'";

    @Test
    void aDeckIsValidBesideTheDemonstrationsAndIsNoDemonstration() {
        assertThat(problems(Decks.PATH, Decks.SAMPLE)).isEmpty();
        assertThat(DemoRecord.isDemo(Decks.PATH)).isFalse();
        assertThat(DemoRecord.isDemo("docs/demos/42-gate.md")).isTrue();
    }

    @Test
    void theRepositorysOwnDeckPasses() {
        assertThat(problems(Decks.PATH, Repository.read(Decks.PATH))).isEmpty();
    }

    @Test
    void theRepositoryCheckRunsTheDeckCheck(@TempDir Path copy) {
        Fixture fixture = Fixture.copy(copy).passingEvidence().withoutDemos().write(Decks.PATH,
                Decks.with("lang: nb", "lang: en"));

        assertThatThrownBy(() -> Consistency.check(Sources.read(fixture.root(), List.of(fixture.reports()))))
                .isInstanceOf(DocsException.class).hasMessageContaining(DECK + "lang 'en' is not nb; a deck is "
                        + "written in Norwegian Bokmål (nb), the language of its slide controls");
    }

    @Test
    void aDemonstrationWithTheKindDeckIsToldWhereDecksStand() {
        assertThat(problems("docs/demos/42-deck.md", GATE.replace("kind: gate", "kind: deck"))).containsExactly(
                "docs/demos/42-deck.md: kind 'deck' is not one of visual, gate, build, docs and feature; a deck is "
                        + "named docs/demos/deck-<slug>.md");
    }

    static Stream<Arguments> invalidDeckMetadata() {
        String keys = "is not one of title, description, status, updated, kind, lang, milestone, mainSlides and "
                + "appendixSlides; a deck names no pull request, and no revision is entered by hand";
        return Stream.of(
                Arguments.of("kind: deck", "kind: gate", "kind 'gate' is not deck; a file named "
                        + "docs/demos/deck-<slug>.md is a deck"),
                Arguments.of("kind: deck\n", "", "frontmatter missing key 'kind'"),
                Arguments.of("lang: nb", "lang: en", "lang 'en' is not nb; a deck is written in Norwegian Bokmål (nb), "
                        + "the language of its slide controls"),
                Arguments.of("lang: nb\n", "", "frontmatter missing key 'lang'"),
                Arguments.of("milestone: 2026-11-10", "milestone: 10 November", "milestone '10 November' is not an "
                        + "ISO date (YYYY-MM-DD)"),
                Arguments.of("milestone: 2026-11-10", "milestone: 2026-02-30", "milestone '2026-02-30' is not an ISO "
                        + "date (YYYY-MM-DD)"),
                Arguments.of("milestone: 2026-11-10\n", "", "frontmatter missing key 'milestone'"),
                Arguments.of("mainSlides: 3", "mainSlides: \"3\"", "mainSlides must be the number of main slides, a "
                        + "whole number without quotes"),
                Arguments.of("mainSlides: 3", "mainSlides: 3.0", "mainSlides must be the number of main slides, a "
                        + "whole number without quotes"),
                Arguments.of("mainSlides: 3\n", "", "frontmatter missing key 'mainSlides'"),
                Arguments.of("appendixSlides: 1", "appendixSlides: -1", "appendixSlides must be the number of appendix "
                        + "slides, a whole number without quotes"),
                Arguments.of("appendixSlides: 1\n", "", "frontmatter missing key 'appendixSlides'"),
                Arguments.of("status: accepted", "status: accepted\npr: 48", "frontmatter key 'pr' " + keys),
                Arguments.of("status: accepted", "status: accepted\nrevision: 44ef312", "frontmatter key 'revision' "
                        + keys));
    }

    @ParameterizedTest
    @MethodSource("invalidDeckMetadata")
    void invalidDeckMetadataFails(String from, String to, String problem) {
        assertThat(problems(Decks.PATH, Decks.with(from, to))).containsExactly(DECK + problem);
    }

    @ParameterizedTest
    @ValueSource(strings = {"docs/demos/deck-.md", "docs/demos/deck-Milestone.md", "docs/demos/deck-milestone-.md",
        "docs/demos/deck--milestone.md", "docs/demos/deck-milestone_2.md"})
    void aDeckIsNamedDeckAndAKebabCaseSlug(String path) {
        assertThat(problems(path, Decks.SAMPLE)).containsExactly(path + ": a deck is named "
                + "docs/demos/deck-<slug>.md: deck-, then a kebab-case slug");
    }

    @Test
    void theDeclaredCountsAreTheSlidesAndNotesAreNeverCounted() {
        assertThat(problems(Decks.PATH, Decks.with("mainSlides: 3", "mainSlides: 4")))
                .containsExactly(DECK + "mainSlides is 4, but the deck has 3 main slides");
        // Three notes headings would make six, if they were counted.
        assertThat(problems(Decks.PATH, Decks.with("mainSlides: 3", "mainSlides: 6")))
                .containsExactly(DECK + "mainSlides is 6, but the deck has 3 main slides");
        assertThat(problems(Decks.PATH, Decks.with("appendixSlides: 1", "appendixSlides: 0")))
                .containsExactly(DECK + "appendixSlides is 0, but the deck has 1 appendix slide (a title that starts "
                        + "with 'Vedlegg:')");
        assertThat(problems(Decks.PATH, Decks.with("## Vedlegg: verktøy", "## Verktøy")))
                .containsExactly(DECK + "appendixSlides is 1, but the deck has 0 appendix slides (a title that starts "
                        + "with 'Vedlegg:')", DECK + "mainSlides is 3, but the deck has 4 main slides");
    }

    @Test
    void theAppendixComesLastAndADeckHasTwoMainSlidesAtLeast() {
        String moved = Decks.with("## Status i dag", "## Vedlegg: status i dag");

        assertThat(problems(Decks.PATH, moved.replace("mainSlides: 3", "mainSlides: 2")
                .replace("appendixSlides: 1", "appendixSlides: 2"))).containsExactly(DECK + "the main slide 'Én "
                        + "endring porten avviser' comes after an appendix slide; the appendix comes last");
        String one = Decks.SAMPLE.substring(0, Decks.SAMPLE.indexOf("## Status i dag")).replace("mainSlides: 3",
                "mainSlides: 1").replace("appendixSlides: 1", "appendixSlides: 0");
        assertThat(problems(Decks.PATH, one)).containsExactly(DECK + "a deck has at least two main slides; slide 02 "
                + "carries the repository's ledger");
    }

    @Test
    void aDeckHasOneTitleItsOwnAndNothingBeforeItsFirstSlide() {
        assertThat(problems(Decks.PATH, Decks.with("# Milepæl: et eksempel\n", "")))
                .containsExactly(DECK + "the deck has no level-1 heading; it starts with # and its title");
        assertThat(problems(Decks.PATH, Decks.with("# Milepæl: et eksempel\n", "# Milepæl\n")))
                .containsExactly(DECK + "the level-1 heading 'Milepæl' is not the title 'Milepæl: et eksempel'");
        assertThat(problems(Decks.PATH, Decks.with("# Milepæl: et eksempel\n", "# Milepæl: et eksempel\n\nIntro.\n")))
                .containsExactly(DECK + "text stands before the first slide; everything stands under a slide's "
                        + "level-2 heading");
        assertThat(problems(Decks.PATH, Decks.with("Java 25 og strenge bygg.", "Java 25.\n\n# Slutt")))
                .containsExactly(DECK + "a second level-1 heading, 'Slutt'; a deck has one title");
    }

    @Test
    void aSlideHasATitleAndContentAndNoHeadingButItsNotesHeading() {
        assertThat(problems(Decks.PATH, Decks.with("Java 25 og strenge bygg.\n", "")))
                .containsExactly(DECK + "slide 4 'Vedlegg: verktøy' is empty");
        assertThat(problems(Decks.PATH, Decks.with("## Status i dag", "##")))
                .containsExactly(DECK + "slide 2 has no title");
        assertThat(problems(Decks.PATH, Decks.with("Sjekkeren kjører i hvert bygg.", "### Bygget\n\nSjekkeren.")))
                .containsExactly(DECK + "slide 1 'Hva som er bygget' has the heading 'Bygget'; below its title a slide "
                        + "has only ### Notater, which starts its notes");
        // A mistyped notes heading would put the notes on the projected slide.
        assertThat(problems(Decks.PATH, Decks.with("### Notater\n\n- Tallene", "### Notes\n\n- Tallene")))
                .containsExactly(DECK + "slide 2 'Status i dag' has the heading 'Notes'; below its title a slide has "
                        + "only ### Notater, which starts its notes");
        assertThat(problems(Decks.PATH, Decks.with("- Tallene kommer fra kildene\n", "")))
                .containsExactly(DECK + "the notes of slide 2 'Status i dag' are empty");
        assertThat(problems(Decks.PATH, Decks.with("- Tallene kommer fra kildene\n", """
                - Tallene

                ### Notater

                - Igjen
                """)))
                .containsExactly(DECK + "the notes of slide 2 'Status i dag' have the heading 'Notater'; notes have no "
                        + "headings");
    }

    @Test
    void eachSlideHasAnAnchorOfItsOwnAndInPageLinksNameASlide() {
        assertThat(problems(Decks.PATH, Decks.with("## Status i dag", "## Hva som er bygget")))
                .containsExactly(DECK + "slide 2 'Hva som er bygget' has the anchor #hva-som-er-bygget, which slide 1 "
                        + "also has; each slide needs a title of its own");
        assertThat(problems(Decks.PATH, Decks.with("## Status i dag", "## Milepæl: et eksempel")))
                .containsExactly(DECK + "slide 2 'Milepæl: et eksempel' has the anchor #milepæl-et-eksempel, which the "
                        + "deck's title has; each slide needs a title of its own");
        assertThat(problems(Decks.PATH, Decks.with("## Status i dag", "## Notater")))
                .containsExactly(DECK + "slide 2 'Notater' has the anchor #notater, which the notes headings have; each "
                        + "slide needs a title of its own");
        assertThat(problems(Decks.PATH, Decks.with("Java 25 og strenge bygg.", "Se [status](#status-i-dag).")))
                .isEmpty();
        assertThat(problems(Decks.PATH, Decks.with("Java 25 og strenge bygg.", "Se [notatene](#notater).")))
                .containsExactly(DECK + "slide 4 'Vedlegg: verktøy' links #notater, which is no slide of this deck");
        assertThat(problems(Decks.PATH, Decks.with("- Tallene kommer fra kildene", "- Se [status](#status)")))
                .containsExactly(DECK + "the notes of slide 2 'Status i dag' link #status, which is no slide of this "
                        + "deck");
    }

    @ParameterizedTest
    @ValueSource(strings = {"![skjermbilde](bilde.png)", "<img src=\"bilde.png\" alt=\"skjermbilde\">",
        "<script>alert(1)</script>", "<iframe src=\"https://example.com/\"></iframe>", "En <b>fet</b> påstand.",
        "[opptaket](opptak.mp4)", "[en skjerm](https://example.com/skjerm.gif)"})
    void aDeckIsTextOnlyWithoutActiveContent(String markdown) {
        List<String> problems = problems(Decks.PATH, Decks.with("Sjekkeren kjører i hvert bygg.",
                "Sjekkeren kjører i hvert bygg. " + markdown));

        assertThat(problems).singleElement().asString().startsWith(DECK + "slide 1 'Hva som er bygget' ")
                .contains("a deck is text only");
    }

    @Test
    void aDeckShowsRecordedOutputInTextFencesOnly() {
        assertThat(problems(Decks.PATH, Decks.with("Java 25 og strenge bygg.", "```sh\n./gradlew check\n```")))
                .containsExactly(DECK + "slide 4 'Vedlegg: verktøy' has a ```sh fence; a deck shows recorded output in "
                        + "```text fences and nothing else");
        assertThat(problems(Decks.PATH, Decks.with("Java 25 og strenge bygg.", "```\nJava 25\n```")))
                .containsExactly(DECK + "slide 4 'Vedlegg: verktøy' has a fence without ```text; a deck shows recorded "
                        + "output in ```text fences and nothing else");
        assertThat(problems(Decks.PATH, Decks.with("Java 25 og strenge bygg.", "Java 25.\n\n    java -version")))
                .containsExactly(DECK + "slide 4 'Vedlegg: verktøy' has an indented code block; recorded output goes "
                        + "in a ```text fence");
        assertThat(problems(Decks.PATH, Decks.with("- Modellen styrer strukturen", "- Modellen:\n\n  ```text\n  x\n"
                + "  ```")))
                .containsExactly(DECK + "slide 1 'Hva som er bygget' has a code block inside a list or a quote; fences "
                        + "stand on their own");
    }

    @ParameterizedTest
    @ValueSource(strings = {"TODO", "Tekst kommer (TBD).", "todo: tall", "<sett inn tall>", "<...>", "Dato YYYY-MM-DD."})
    void aPlaceholderFails(String text) {
        List<String> problems = problems(Decks.PATH, Decks.with("Java 25 og strenge bygg.", "Java 25 og strenge bygg. "
                + text));

        assertThat(problems).singleElement().asString().startsWith(DECK).containsAnyOf("placeholder",
                "YYYY-MM-DD is left from a template");
    }

    @Test
    void aPlaceholderInTheTitleTheDescriptionOrTheNotesFails() {
        assertThat(problems(Decks.PATH, Decks.with("title: \"Milepæl: et eksempel\"", "title: \"<tittel>\"")
                .replace("# Milepæl: et eksempel", "# <tittel>"))).contains(DECK + "title holds '<tittel>', an "
                        + "unfilled placeholder");
        assertThat(problems(Decks.PATH, Decks.with("- Si først hva som ikke er bygget", "- TBD")))
                .containsExactly(DECK + "the notes of slide 1 'Hva som er bygget' hold 'TBD', a placeholder");
    }

    @Test
    void recordedOutputCarriesItsLabelAndItsRecordInTheNotes() {
        assertThat(problems(Decks.PATH, Decks.with("Recorded output — not rerun by this site build. Full record",
                "Full record"))).containsExactly(DECK + SLIDE_3 + " shows recorded output (```text) without the line "
                        + "'Recorded output — not rerun by this site build.'");
        assertThat(problems(Decks.PATH, Decks.with("Recorded 2026-11-07T14:56:19Z on", "Recorded on")))
                .containsExactly(DECK + "the notes of " + SLIDE_3 + " do not name the date its output was recorded "
                        + "(YYYY-MM-DD)");
        assertThat(problems(Decks.PATH, Decks.with("on revision 44ef312", "on the branch")))
                .containsExactly(DECK + "the notes of " + SLIDE_3 + " do not name the revision it was recorded on "
                        + "(revision and seven to forty hexadecimal digits)");
        assertThat(problems(Decks.PATH, Decks.with("exited 1, and 0 before and after", "failed")))
                .containsExactly(DECK + "the notes of " + SLIDE_3 + " do not state its exit status (exited and a "
                        + "number)");
        String command = "`./gradlew :libs:c4-contract-kit:test --rerun --tests '*SelfConformanceTest'`";
        for (String other : List.of("the checker's test", "`./gradlew :libs:c4-contract-kit:test`",
                "`./gradlew :libs:c4-contract-kit:test --rerun --tests '*NoSuchTest'`")) {
            assertThat(problems(Decks.PATH, Decks.with(command, other))).as(other)
                    .containsExactly(DECK + "the notes of " + SLIDE_3 + " do not name the command that recorded it, an "
                            + "approved command of docs/demos/how-to.md in a code span");
        }
        assertThat(problems(Decks.PATH, Decks.SAMPLE.substring(0, Decks.SAMPLE.indexOf("### Notater\n\n- Recorded"))
                + "## Vedlegg: verktøy\n\nJava 25 og strenge bygg.\n"))
                .containsExactly(DECK + SLIDE_3 + " shows recorded output, but has no notes that record its date, "
                        + "revision, command and exit status");
    }

    @Test
    void aCountOfCapabilitiesOnASlideIsTheSourcesCount() {
        List<Capability> product = sources.capabilities().capabilities();
        long planned = product.stream().filter(c -> c.status() == Capability.Status.PLANNED).count();
        String slide = "Kontrollene er bygget; produktet er planlagt.";

        assertThat(problems(Decks.PATH, Decks.with(slide, "Planlagt: " + planned + " av " + product.size()
                + " funksjoner."))).isEmpty();
        assertThat(problems(Decks.PATH, Decks.with(slide, "Planlagt: " + (planned - 1) + " av\n" + product.size()
                + " funksjoner."))).containsExactly(DECK + "slide 2 'Status i dag' says '" + (planned - 1) + " av "
                        + product.size() + " funksjoner', but docs/capabilities/capabilities.yaml has " + planned + " of "
                        + product.size() + " product capabilities planned");
        assertThat(problems(Decks.PATH, Decks.with("| Sjekkeren | Bygget og testet |", "| Produktet | "
                + planned + " av " + (product.size() + 1) + " funksjoner |"))).containsExactly(DECK + "slide 2 'Status "
                        + "i dag' says '" + planned + " av " + (product.size() + 1) + " funksjoner', but "
                        + "docs/capabilities/capabilities.yaml has " + planned + " of " + product.size() + " product "
                        + "capabilities planned");
    }

    @Test
    void aDecksNotesPageIsNoOtherDecksPage() {
        List<String> problems = DemoCheck.problems(sources, List.of(DocFile.parse("docs/demos/deck-x.md",
                Decks.SAMPLE), DocFile.parse("docs/demos/deck-x-notes.md", Decks.SAMPLE)));

        assertThat(problems).containsExactly("docs/demos/deck-x.md: its notes page, demos/deck-x-notes.html, is also "
                + "the page of docs/demos/deck-x-notes.md");
    }

    private static List<String> problems(String path, String text) {
        return DemoCheck.problems(sources, List.of(DocFile.parse(path, text)));
    }

    /** The demonstration without one of its sections, heading and text. */
    private static String withoutSection(String text, String section) {
        int start = text.indexOf("## " + section + "\n");
        assertThat(start).as(section).isNotNegative();
        int end = text.indexOf("\n## ", start + 1);
        return text.substring(0, start) + (end < 0 ? "" : text.substring(end + 1));
    }

    /** The gate demonstration with another command in its Try it fence. */
    private static String withCommand(String command) {
        return GATE.replace("```sh\njava tools/policy/PolicyCheck.java .\n```", "```sh\n" + command + "\n```");
    }

    private static String replace(String text, String from, String to) {
        assertThat(text).contains(from);
        return text.replace(from, to);
    }
}
