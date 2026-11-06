package io.github.stigjohnny.docs.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.generate.Brains;
import io.github.stigjohnny.docs.generate.GeneratedFiles;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.Fixture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * ADR 0011's negative fixtures for the generated brain and docs: each changes one thing in a copy of the repository's
 * documentation and expects the check to fail and say why. Acceptance test of control.brain-check.
 */
class BrainCheckTest {

    private static final String CAPABILITIES = "docs/capabilities/capabilities.yaml";
    private static final String ADR_0010 = "docs/adr/0010-documentation-as-code-with-a-generated-brain.md";
    private static final String ADR_0011 = "docs/adr/0011-java-documentation-tool-and-publication.md";

    @TempDir
    Path dir;

    private Fixture fixture;

    @BeforeEach
    void copyTheRepository() {
        fixture = Fixture.copy(dir).passingEvidence();
    }

    @Test
    void theUnchangedRepositoryPasses() {
        Map<String, String> files = GeneratedFiles.generate(sources());

        assertThat(files).isNotEmpty();
        GeneratedFiles.write(files, fixture.root());
        assertThat(GeneratedFiles.compare(GeneratedFiles.generate(sources()), fixture.root())).isEmpty();
    }

    @Test
    void aShippedCapabilityWithoutEvidenceFails() {
        fixture.edit(CAPABILITIES, text -> text.replaceFirst("(id: quotes.synthetic-tariff\n(?:    .*\n){2})"
                + "    status: planned", "$1    status: shipped"));

        assertFails().hasMessageContaining("quotes.synthetic-tariff: claims shipped without evidence");
    }

    @Test
    void evidenceThatDoesNotExistFails() {
        fixture.edit(CAPABILITIES, text -> text.replace("evidence: [BrainGeneratorTest, BrainCheckTest]",
                "evidence: [BrainGeneratorTest, BrainCheckTest, NoSuchTest]")
                .replace("acceptanceTests: [BrainGeneratorTest, BrainCheckTest]",
                        "acceptanceTests: [BrainGeneratorTest, BrainCheckTest, NoSuchTest]"));

        assertFails().hasMessageContaining("evidence NoSuchTest does not exist in the repository");
    }

    @Test
    void evidenceThatFailedInThisBuildFails() {
        fixture.report("io.github.stigjohnny.docs.check.BrainCheckTest", 19, 1, 0, 0);

        assertFails().hasMessageContaining("evidence BrainCheckTest did not pass in this build");
    }

    @Test
    void aSkippedTestIsNotEvidence() {
        fixture.report("io.github.stigjohnny.docs.check.BrainCheckTest", 19, 0, 0, 1);

        assertFails().hasMessageContaining("evidence BrainCheckTest did not pass in this build");
    }

    @Test
    void evidenceWithoutATestReportFails() {
        Fixture bare = Fixture.copy(dir.resolve("bare"))
                .report("io.github.stigjohnny.docs.generate.BrainGeneratorTest", 7, 0, 0, 0);

        assertThatThrownBy(() -> GeneratedFiles.generate(Sources.read(bare.root(), List.of(bare.reports()))))
                .isInstanceOf(DocsException.class)
                .hasMessageContaining("evidence BrainCheckTest has no test report in this build");
    }

    @Test
    void aPlannedCapabilityThatNamesEvidenceFails() {
        fixture.edit(CAPABILITIES, text -> text.replaceFirst("(id: quotes.synthetic-tariff\n(?:    .*\n){4})"
                + "    evidence: none yet", "$1    evidence: [SyntheticTariffTest]"));

        assertFails().hasMessageContaining("quotes.synthetic-tariff: a planned capability names no evidence");
    }

    @Test
    void aShippedControlWhoseCheckIsNoWorkflowJobFails() {
        fixture.edit(CAPABILITIES, text -> text.replace("where: tools/docs\n    check: java",
                "where: tools/docs\n    check: docs-brain"));

        assertFails().hasMessageContaining("check 'docs-brain' is not a job of any workflow");
    }

    @Test
    void aBrokenLinkFails() {
        fixture.edit("docs/gaps.md", text -> text + "\nSee [the missing page](missing.md).\n");

        assertFails().hasMessageContaining("docs/gaps.md: link to 'missing.md'")
                .hasMessageContaining("docs/missing.md does not exist");
    }

    @Test
    void aBrokenAnchorFails() {
        fixture.edit("docs/gaps.md", text -> text + "\nSee [the levels](risk-levels.md#no-such-section).\n");

        assertFails().hasMessageContaining("no heading of docs/risk-levels.md has the anchor #no-such-section");
    }

    @Test
    void aStaleModuleStatusFails() {
        fixture.edit("docs/modules/quotes.md", text -> text.replace("moduleStatus: planned", "moduleStatus: built"));

        assertFails().hasMessageContaining("docs/modules/quotes.md: moduleStatus 'built' is stale");
    }

    @Test
    void anAdrWithoutARequiredSectionFails() {
        fixture.edit(ADR_0011, text -> text.replace("### Confirmation", "### How it is checked"));

        assertFails().hasMessageContaining(ADR_0011 + ": the required section 'Confirmation' is missing");
    }

    @Test
    void aGapInTheAdrNumbersFails() {
        fixture.move(ADR_0011, "docs/adr/0012-java-documentation-tool-and-publication.md");

        assertFails().hasMessageContaining("ADR numbers run from 0001 without gaps; expected 0011");
    }

    @Test
    void aSupersededAdrMustNameItsSuccessor() {
        fixture.edit(ADR_0010, text -> text.replace("supersededBy: \"0011\"\n", ""));

        assertFails().hasMessageContaining(ADR_0010 + ": a superseded ADR names its successor in supersededBy");
    }

    @Test
    void aSuccessorThatDoesNotExistFails() {
        fixture.edit(ADR_0010, text -> text.replace("supersededBy: \"0011\"", "supersededBy: \"0042\""));

        assertFails().hasMessageContaining(ADR_0010 + ": supersededBy names ADR 0042, which does not exist");
    }

    @Test
    void anAdrThatIsNotSupersededNamesNoSuccessor() {
        fixture.edit(ADR_0011, text -> text.replace("status: accepted", "status: accepted\nsupersededBy: \"0010\""));

        assertFails().hasMessageContaining(ADR_0011 + ": only a superseded ADR has supersededBy");
    }

    @Test
    void anEnforcedByTestThatDoesNotExistFails() {
        fixture.edit("docs/adr/0001-record-architecture-decisions.md",
                text -> text.replace("enforcedBy:", "enforcedBy:\n  - \"java: NoSuchEnforcementTest\"\nformerEnforcedBy:"));

        assertFails().hasMessageContaining("enforcedBy names NoSuchEnforcementTest, which does not exist");
    }

    @Test
    void aMentionOfAMissingAdrFails() {
        fixture.edit("docs/risk-levels.md", text -> text + "\nAs ADR 0042 decided.\n");

        assertFails().hasMessageContaining("docs/risk-levels.md: mentions ADR 0042, which does not exist");
    }

    @Test
    void missingOrInvalidFrontmatterFails() {
        fixture.edit("docs/risk-levels.md", text -> text.replace("status: accepted", "status: approved")
                .replace("updated: ", "updated: 6 November "));

        assertFails().hasMessageContaining("docs/risk-levels.md: status 'approved' is not one of")
                .hasMessageContaining("docs/risk-levels.md: updated '6 November");
    }

    @Test
    void anEditedGeneratedFileFails() {
        GeneratedFiles.write(GeneratedFiles.generate(sources()), fixture.root());
        fixture.edit(Sources.BRAIN, text -> text.replace("## Modules", "## Modules (edited by hand)"));

        assertThat(GeneratedFiles.compare(GeneratedFiles.generate(sources()), fixture.root()))
                .singleElement().asString().startsWith("BRAIN.md differs from what the sources generate");
    }

    @Test
    void aStaleGeneratedFileFails() {
        GeneratedFiles.write(GeneratedFiles.generate(sources()), fixture.root());
        fixture.write("docs/brain/billing.md", "---\ntitle: x\n---\n");

        assertThat(GeneratedFiles.compare(GeneratedFiles.generate(sources()), fixture.root()))
                .containsExactly("docs/brain/billing.md is not generated by anything; remove it");
    }

    @ParameterizedTest
    @ValueSource(strings = {"docs/architecture/notes.md", "docs/architecture/extra.mmd", "docs/architecture/view.png",
        "docs/architecture/nested/containers.md", "docs/architecture/.hidden", "docs/brain/extra.md"})
    void aFileThatNothingGeneratesInAGeneratedDirectoryFailsWhateverItsKind(String path) {
        GeneratedFiles.write(GeneratedFiles.generate(sources()), fixture.root());
        fixture.write(path, "hand-written\n");

        assertThat(GeneratedFiles.compare(GeneratedFiles.generate(sources()), fixture.root()))
                .containsExactly(path + " is not generated by anything; remove it");
    }

    @Test
    void aLinkInAGeneratedDirectoryFailsWhereverItPoints() throws IOException {
        GeneratedFiles.write(GeneratedFiles.generate(sources()), fixture.root());
        Path architecture = fixture.root().resolve("docs/architecture");
        Files.createSymbolicLink(architecture.resolve("linked-file.md"), Path.of("../gaps.md"));
        Files.createSymbolicLink(architecture.resolve("linked-directory"), Path.of("../modules"));
        Files.createSymbolicLink(architecture.resolve("dangling.md"), Path.of("no-such-file.md"));

        assertThat(GeneratedFiles.compare(GeneratedFiles.generate(sources()), fixture.root())).containsExactly(
                "docs/architecture/dangling.md is a link; a generated directory holds generated files only",
                "docs/architecture/linked-directory is a link; a generated directory holds generated files only",
                "docs/architecture/linked-file.md is a link; a generated directory holds generated files only");
    }

    @Test
    void anEditedDiagramDefinitionFails() {
        GeneratedFiles.write(GeneratedFiles.generate(sources()), fixture.root());
        fixture.edit("docs/architecture/containers.mmd", text -> text + "    extra --> node\n");

        assertThat(GeneratedFiles.compare(GeneratedFiles.generate(sources()), fixture.root())).singleElement()
                .asString().startsWith("docs/architecture/containers.mmd differs from what the sources generate");
    }

    @Test
    void anExceededBudgetFails() {
        String paragraph = "Padding words for the budget. ".repeat(450);
        fixture.edit("README.md", text -> text.replaceFirst("^# ([^\n]+)\n\n", "# $1\n\n" + paragraph));

        assertFails().hasMessageContaining("BRAIN.md is ").hasMessageContaining("its budget is 12288 bytes");
        assertThatThrownBy(() -> Brains.generate(sources())).isInstanceOf(DocsException.class);
    }

    private Sources sources() {
        return Sources.read(fixture.root(), List.of(fixture.reports()));
    }

    private AbstractThrowableAssert<?, ? extends Throwable> assertFails() {
        return assertThatThrownBy(() -> GeneratedFiles.generate(sources())).isInstanceOf(DocsException.class);
    }
}
