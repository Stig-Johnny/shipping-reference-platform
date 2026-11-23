package io.github.stigjohnny.docs.check;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.Fixture;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The behavioural diagrams' negative fixtures (ADR 0024). A sequence or state diagram in current prose is compared with
 * the code by no test, so the paragraph right before its fence, in the same container, starts with the label
 * {@code **Unchecked design.**}; a label that no such diagram follows fails, and so does a Mermaid fence whose info
 * string or first line hides its type. Records and generated files are not read.
 */
class BehaviouralDiagramsTest {

    private static final String DOC = "docs/notes.md";
    private static final String LABEL = "**Unchecked design.** No test compares this diagram with the code.\n";
    private static final String SEQUENCE = "```mermaid\nsequenceDiagram\n    a->>b: hi\n```\n";
    private static final String STATE = "```mermaid\nstateDiagram-v2\n    [*] --> A\n```\n";
    private static final String FLOWCHART = "```mermaid\nflowchart LR\n    a --> b\n```\n";

    @TempDir
    Path dir;

    @Test
    void theRepositoryPasses() {
        assertThat(repository(Fixture.copy(dir).passingEvidence())).isEmpty();
    }

    @Test
    void anUnlabelledSequenceDiagramFails() {
        assertThat(problems("# Notes\n\n" + SEQUENCE)).containsExactly(DOC + ":3: no test compares this sequence "
                + "diagram with the code; write a paragraph that starts with **Unchecked design.** right before it "
                + "(ADR 0024)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"stateDiagram", "stateDiagram-v2"})
    void anUnlabelledStateDiagramFails(String type) {
        assertThat(problems("# Notes\n\nText.\n\n```mermaid\n" + type + "\n    [*] --> A\n```\n")).containsExactly(DOC
                + ":5: no test compares this state diagram with the code; write a paragraph that starts with "
                + "**Unchecked design.** right before it (ADR 0024)");
    }

    @Test
    void aLabelledSequenceOrStateDiagramPasses() {
        assertThat(problems("# Notes\n\n" + LABEL + "\n" + SEQUENCE + "\n" + LABEL + STATE)).isEmpty();
    }

    @Test
    void theLabelMustStandRightBeforeItsDiagram() {
        assertThat(problems("# Notes\n\n" + LABEL + "\nAnother paragraph.\n\n" + SEQUENCE)).containsExactly(
                DOC + ":3: **Unchecked design.** labels the sequence or state diagram right after it, and none follows "
                        + "(ADR 0024)",
                DOC + ":7: no test compares this sequence diagram with the code; write a paragraph that starts with "
                        + "**Unchecked design.** right before it (ADR 0024)");
        assertThat(problems("# Notes\n\n" + LABEL + "\n## Steps\n\n" + SEQUENCE)).hasSize(2);
        assertThat(problems("# Notes\n\n" + SEQUENCE + "\n" + LABEL)).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"**Unchecked design**.", "*Unchecked design.*", "Unchecked design.", "**unchecked design.**",
            "Note: **Unchecked design.**", "**Unchecked design. Planned.**", "**Unchecked `design`.**",
            "**[Unchecked design.](#notes)**"})
    void onlyAStrongLeadWithExactlyTheseWordsIsTheLabel(String lead) {
        assertThat(problems("# Notes\n\n" + lead + " No test compares it.\n" + SEQUENCE)).containsExactly(DOC + ":4: no "
                + "test compares this sequence diagram with the code; write a paragraph that starts with **Unchecked "
                + "design.** right before it (ADR 0024)");
    }

    @Test
    void theLabelMayEndItsLeadThereOrUseUnderscores() {
        assertThat(problems("# Notes\n\n**Unchecked design.**\n" + SEQUENCE + "\n__Unchecked design.__ Text.\n"
                + STATE)).isEmpty();
    }

    @Test
    void aLabelThatNoSequenceOrStateDiagramFollowsFails() {
        String stray = ": **Unchecked design.** labels the sequence or state diagram right after it, and none follows "
                + "(ADR 0024)";

        assertThat(problems("# Notes\n\n" + LABEL)).containsExactly(DOC + ":3" + stray);
        assertThat(problems("# Notes\n\n" + LABEL + FLOWCHART)).containsExactly(DOC + ":3" + stray);
        assertThat(problems("# Notes\n\n" + LABEL + "```text\nsequenceDiagram\n```\n")).containsExactly(DOC + ":3"
                + stray);
        assertThat(problems("# Notes\n\n" + LABEL + "\n    sequenceDiagram\n")).containsExactly(DOC + ":3" + stray);
    }

    @ParameterizedTest
    @ValueSource(strings = {"flowchart LR", "flowchart", "graph TD", "graph", "flowchart BT"})
    void aFlowchartNeedsNoLabel(String header) {
        assertThat(problems("# Notes\n\n```mermaid\n" + header + "\n    a --> b\n```\n")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mermaid", "MERMAID", "mermaid title"})
    void aMermaidFenceIsWrittenAsMermaidAlone(String info) {
        List<String> problems = problems("# Notes\n\n" + LABEL + "```" + info + "\nsequenceDiagram\n```\n");

        assertThat(problems).containsExactly(DOC + ":4: write the info string of a Mermaid fence as mermaid alone, not '"
                + info + "' (ADR 0024)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"%% a note", "---", "%%{init: {}}%%", "journey", "classDiagram", "sequenceDiagram %% note",
            "stateDiagram-v3", "flowchart lr", "flowchart LR;"})
    void aFirstLineThatNamesNoKnownTypeFails(String first) {
        assertThat(problems("# Notes\n\n```mermaid\n" + first + "\nsequenceDiagram\n```\n")).containsExactly(DOC + ":3: the first line of a Mermaid diagram names its type, one of "
                + "flowchart, graph, sequenceDiagram, stateDiagram and stateDiagram-v2, and '" + first + "' is none "
                + "(ADR 0024)");
    }

    @Test
    void anEmptyMermaidFenceFails() {
        assertThat(problems("# Notes\n\n```mermaid\n\n```\n")).containsExactly(DOC + ":3: the Mermaid fence is empty, "
                + "so it names no type (ADR 0024)");
    }

    @Test
    void linesAfterTheTypeAreFree() {
        assertThat(problems("# Notes\n\n" + LABEL + "```mermaid\n\n  sequenceDiagram\n    %% a comment\n    a->>b: hi\n"
                + "```\n")).isEmpty();
    }

    @Test
    void fencesInListsAndQuotesAreReadWithTheirOwnSiblings() {
        String unlabelled = "no test compares this sequence diagram with the code; write a paragraph that starts with "
                + "**Unchecked design.** right before it (ADR 0024)";

        assertThat(problems("# Notes\n\n- Item\n\n  ```mermaid\n  sequenceDiagram\n  ```\n")).containsExactly(DOC
                + ":5: " + unlabelled);
        assertThat(problems("# Notes\n\n> Quote\n>\n> ```mermaid\n> stateDiagram\n> ```\n")).containsExactly(DOC
                + ":5: no test compares this state diagram with the code; write a paragraph that starts with "
                + "**Unchecked design.** right before it (ADR 0024)");
        assertThat(problems("""
                # Notes

                - **Unchecked design.** Text.

                  ```mermaid
                  sequenceDiagram
                  ```

                > **Unchecked design.** Text.
                >
                > ```mermaid
                > stateDiagram-v2
                > ```
                """)).isEmpty();
        assertThat(problems("# Notes\n\n" + LABEL + "\n- Item\n\n  ```mermaid\n  sequenceDiagram\n  ```\n"))
                .containsExactly(DOC + ":3: **Unchecked design.** labels the sequence or state diagram right after "
                        + "it, and none follows (ADR 0024)", DOC + ":7: " + unlabelled);
    }

    @Test
    void tildeFencesAreMermaidFencesToo() {
        assertThat(problems("# Notes\n\n~~~mermaid\nsequenceDiagram\n~~~\n")).hasSize(1);
        assertThat(problems("# Notes\n\n" + LABEL + "~~~mermaid\nsequenceDiagram\n~~~\n")).isEmpty();
    }

    @Test
    void lineNumbersCountTheFrontmatter() {
        Problems problems = new Problems();

        BehaviouralDiagrams.check(DOC, "# Notes\n\n" + SEQUENCE, 5, problems);

        assertThat(problems.list()).singleElement().asString().startsWith(DOC + ":8: no test compares");
    }

    @ParameterizedTest
    @ValueSource(strings = {"README.md", "docs/demos/how-to.md",
        "docs/adr/0013-scenario-views-are-checked-against-recorded-interactions.md"})
    void currentProseAnywhereIsReadWithTheLinesOfItsFile(String path) {
        Fixture fixture = Fixture.copy(dir).passingEvidence();
        int line = appendFence(fixture, path);

        assertThat(repository(fixture)).containsExactly(path + ":" + line + ": no test compares this sequence diagram "
                + "with the code; write a paragraph that starts with **Unchecked design.** right before it (ADR 0024)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"docs/contributions/history.md", "docs/demos/142-claude-md-pairs-are-checked.md",
        "docs/adr/0010-documentation-as-code-with-a-generated-brain.md", "docs/architecture/containers.md",
        "build/notes.md"})
    void recordsSupersededDecisionsGeneratedFilesAndSkippedDirectoriesAreNotRead(String path) {
        Fixture fixture = Fixture.copy(dir).passingEvidence();
        appendFence(fixture, path);
        String text = fixture.root().resolve(path).toFile().exists() ? fixture.read(path) : "";
        fixture.write(path, text + "\n" + LABEL);

        assertThat(repository(fixture)).isEmpty();
    }

    @Test
    void theHandWrittenDiagramsOfTheRepositoryCarryTheLabel() {
        Fixture fixture = Fixture.copy(dir).passingEvidence();
        for (String path : List.of("docs/flows/quote-book-label.md", "docs/modules/shipments.md")) {
            fixture.write(path, fixture.read(path).replace("**Unchecked design.**", "**Design.**"));
        }

        assertThat(repository(fixture)).hasSize(2).allMatch(problem -> problem.contains("no test compares this"));
    }

    /** Appends an unlabelled sequence diagram to a file and returns the line its fence starts on. */
    private static int appendFence(Fixture fixture, String path) {
        String text = fixture.root().resolve(path).toFile().exists() ? fixture.read(path) : "";
        String base = text.isEmpty() || text.endsWith("\n") ? text : text + "\n";
        fixture.write(path, base + "\n" + SEQUENCE);
        return (int) base.chars().filter(c -> c == '\n').count() + 2;
    }

    /** The problems that the check finds in one document of current prose, without frontmatter. */
    private static List<String> problems(String markdown) {
        Problems problems = new Problems();
        BehaviouralDiagrams.check(DOC, markdown, 0, problems);
        return problems.list();
    }

    /** The behavioural-diagram problems in a copy of the repository, as the documentation check finds them. */
    private static List<String> repository(Fixture fixture) {
        Problems problems = new Problems();
        References.check(Sources.read(fixture.root(), List.of(fixture.reports())), problems);
        return problems.list().stream().filter(problem -> problem.endsWith("(ADR 0024)")).toList();
    }
}
