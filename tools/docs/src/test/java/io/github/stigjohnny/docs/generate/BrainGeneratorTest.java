package io.github.stigjohnny.docs.generate;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.markdown.LinkDecision;
import io.github.stigjohnny.docs.markdown.MarkdownRenderer;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.RepositoryLinks;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.Fixture;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The generated brain and view pages: deterministic, ordered, within budget, marked as generated, and linked only to
 * files that exist. Acceptance test of control.brain-check.
 */
class BrainGeneratorTest {

    @TempDir
    Path dir;

    private Fixture fixture;

    @BeforeEach
    void copyTheRepository() {
        fixture = Fixture.copy(dir).passingEvidence();
    }

    @Test
    void twoGenerationsAreByteIdentical() {
        Map<String, String> first = GeneratedFiles.generate(sources());
        Map<String, String> second = GeneratedFiles.generate(sources());

        assertThat(second).isEqualTo(first);
        assertThat(List.copyOf(first.keySet())).isSorted();
    }

    @Test
    void theExpectedFilesAreGenerated() {
        assertThat(GeneratedFiles.generate(sources()).keySet()).containsExactly("BRAIN.md",
                "docs/architecture/api-components.md", "docs/architecture/api-components.mmd",
                "docs/architecture/containers.md", "docs/architecture/containers.mmd",
                "docs/architecture/quote-book-label.md", "docs/architecture/quote-book-label.mmd",
                "docs/architecture/system-context.md", "docs/architecture/system-context.mmd",
                "docs/brain/audit.md", "docs/brain/orders.md", "docs/brain/quotes.md", "docs/brain/returns.md",
                "docs/brain/shipments.md", "docs/brain/tenants.md", "docs/brain/tracking.md");
    }

    @Test
    void everyFileStartsWithItsFrontmatterOrHeaderAndSaysItIsGenerated() {
        GeneratedFiles.generate(sources()).forEach((path, text) -> {
            assertThat(text).as(path).contains("Generated—do not hand-edit");
            if (path.endsWith(".md")) {
                DocFile doc = DocFile.parse(path, text);
                assertThat(doc.title()).as(path).isNotBlank();
                assertThat(doc.description()).as(path).isNotBlank();
                assertThat(doc.status()).as(path).isEqualTo("accepted");
                assertThat(doc.updated()).as(path).matches("\\d{4}-\\d{2}-\\d{2}");
                assertThat(doc.body()).as(path).startsWith("\n> Generated—do not hand-edit");
            } else {
                assertThat(text).as(path).startsWith("%% Generated—do not hand-edit. Source: "
                        + "architecture/workspace.dsl, view ");
            }
        });
    }

    @Test
    void theBrainsStayWithinTheirBudgets() {
        GeneratedFiles.generate(sources()).forEach((path, text) -> {
            int bytes = text.getBytes(StandardCharsets.UTF_8).length;
            if (path.equals("BRAIN.md")) {
                assertThat(bytes).as(path).isLessThanOrEqualTo(12 * 1024);
            } else if (path.startsWith("docs/brain/")) {
                assertThat(bytes).as(path).isLessThanOrEqualTo(8 * 1024);
            }
        });
    }

    @Test
    void theViewPagesCarryTheValidatedDefinitionAndItsTextEquivalent() {
        Map<String, String> files = GeneratedFiles.generate(sources());
        String page = file(files, "docs/architecture/api-components.md");
        String mmd = file(files, "docs/architecture/api-components.mmd");
        String definition = mmd.substring(mmd.indexOf('\n') + 1);
        assertThat(definition).startsWith("flowchart LR\n");
        assertThat(page).contains("```mermaid\n" + definition + "```")
                .contains("## Elements", "## Relationships", "## What this view cannot show")
                .contains("| 8 | Quotes | Component | Spring Modulith module | planned |")
                .contains("| Quotes | Tenants | Resolves the caller's tenant (tenants.api: CurrentTenant, TenantId) "
                        + "| Java | thick: REQUIRED dependency |");
    }

    @Test
    void theLegendsSayWhatTheImplementedCheckerCoversAndThatItLeavesOutPlannedModules() {
        Map<String, String> files = GeneratedFiles.generate(sources());
        String components = file(files, "docs/architecture/api-components.md");

        assertThat(components).contains("- A thick line is a REQUIRED dependency between modules. Where both modules "
                + "are built, the checker in libs/c4-contract-kit fails when the compiled classes have no static "
                + "reference for it.\n", "- A dotted line labelled PERMITTED is a dependency between modules that the "
                + "code may have; the checker requires no reference for it and reports one without any as \"no static "
                + "evidence\".\n", "- The checker does not evaluate a line that touches a planned module until both "
                + "modules are built.\n");
        assertThat(file(files, "docs/architecture/quote-book-label.md")).contains("Where both modules are built, "
                + "the checker in libs/c4-contract-kit fails", "The checker does not evaluate a line that touches a "
                + "planned module");
        // A view without a line between two modules says nothing about the checker.
        assertThat(file(files, "docs/architecture/system-context.md")).doesNotContain("checker");
        files.forEach((path, text) -> assertThat(text).as(path).doesNotContain("It is not implemented yet",
                "The planned conformance check", "the planned check"));
    }

    @Test
    void aPlannedCapabilityIsNeverShownAsShipped() {
        Map<String, String> files = GeneratedFiles.generate(sources());

        assertThat(file(files, "docs/brain/quotes.md")).contains("| `quotes.synthetic-tariff` | planned |")
                .doesNotContain("| shipped |");
        Capability planned = sources().capabilities().controls().stream()
                .filter(c -> c.status() == Capability.Status.PLANNED).findFirst().orElseThrow();
        assertThat(file(files, "BRAIN.md")).contains("| `control.brain-check` | shipped | `java` | "
                + "BrainGeneratorTest, BrainCheckTest |")
                .contains("| `" + planned.id() + "` | planned | `" + planned.check() + "` | none yet |");
    }

    @Test
    void everyLinkInTheGeneratedFilesResolves() {
        Map<String, String> files = GeneratedFiles.generate(sources());
        GeneratedFiles.write(files, fixture.root());

        files.forEach((path, text) -> {
            if (path.endsWith(".md")) {
                String body = DocFile.parse(path, text).body();
                MarkdownRenderer.render(body, path, destination -> switch (RepositoryLinks.classify(fixture.root(),
                        path, destination)) {
                    case RepositoryLinks.RepoFile ignored -> new LinkDecision.Keep();
                    case RepositoryLinks.External ignored -> new LinkDecision.Keep();
                    default -> new LinkDecision.Reject("does not resolve");
                }, false);
            }
        });
    }

    private static String file(Map<String, String> files, String path) {
        String text = files.get(path);
        assertThat(text).as(path).isNotNull();
        return text == null ? "" : text;
    }

    private Sources sources() {
        return Sources.read(fixture.root(), List.of(fixture.reports()));
    }
}
