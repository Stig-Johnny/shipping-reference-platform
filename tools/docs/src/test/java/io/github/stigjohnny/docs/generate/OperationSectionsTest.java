package io.github.stigjohnny.docs.generate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.markdown.MarkdownRenderer;
import io.github.stigjohnny.docs.markdown.MarkdownTable;
import io.github.stigjohnny.docs.repo.ApiContract;
import io.github.stigjohnny.docs.repo.Capabilities;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.Fixture;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The module documents' HTTP operation sections, generated from the contract and the capabilities that name each
 * operation and problem type (ADR 0025): the generator owns the level-2 section that starts with HTTP operations, as the
 * site's parser finds it, up to the next heading of level 1 or 2; it composes the documents in memory, the check
 * validates the composed documents, and only then are they written. checkGenerated fails on any difference; DocsToolTest
 * runs the two commands. Every copy of the repository starts with its sections generated (Fixture).
 */
class OperationSectionsTest {

    private static final String SHIPMENTS = "docs/modules/shipments.md";
    private static final String QUOTES = "docs/modules/quotes.md";
    private static final String QUOTES_HEADING = "## HTTP operations (`contracts/shipping.openapi.yaml`, tag `quotes`)";

    @TempDir
    Path dir;

    private Fixture fixture;

    @BeforeEach
    void copyTheRepository() {
        fixture = Fixture.copy(dir.resolve("repo")).passingEvidence();
    }

    @Test
    void theQuotesSectionIsGeneratedFromTheContractAndTheCapabilities() {
        assertThat(OperationSections.section(sources(), "quotes")).isEqualTo("""
                ## HTTP operations (`contracts/shipping.openapi.yaml`, tag `quotes`)

                Generated from `contracts/shipping.openapi.yaml` and `docs/capabilities/capabilities.yaml`. Edit those \
                sources, then
                run `./gradlew :tools:docs:generate`; do not edit this section.

                The last column lists problem types whose registry entry explicitly names this module. "none" means \
                none in that
                subset; common errors still apply. The API pages show each operation's declared responses, and the \
                API overview
                documents common errors.

                An operation or problem is marked planned when every capability naming it is planned. An unmarked \
                entry may still be
                partly built. Query parameters are shown by name without values; request headers shown here are \
                required. Response
                headers common to every declared response are omitted.

                | operationId | Method and path | Success | Module-specific problems |
                |---|---|---|---|
                | `createQuote` | `POST /api/v1/quotes` | `201` `Quote`, header `Location` | `422 no-offer-available` \
                with `reason` |
                | `getQuote` | `GET /api/v1/quotes/{quoteId}` | `200` `Quote` | `404 quote-not-found` |
                """);
    }

    @Test
    void theShipmentsRowsShowParametersHeadersAndPlannedMarks() {
        assertThat(OperationSections.section(sources(), "shipments").lines().filter(l -> l.startsWith("| `")).toList())
                .containsExactly("| `bookShipment` | `POST /api/v1/shipments` (header `Idempotency-Key`) | `201` "
                        + "`Shipment`, header `Location`; `200` `Shipment`, headers `Location`, `Idempotency-Replayed` | "
                        + "`404 quote-not-found`, `404 offer-not-found`, `409 quote-already-booked`, "
                        + "`422 quote-expired`, `422 recipient-does-not-match-quote`, `422 idempotency-key-reused` |",
                        "| `listShipments` | `GET /api/v1/shipments?limit&cursor&status` | `200` `ShipmentPage` | none |",
                        "| `getShipment` | `GET /api/v1/shipments/{shipmentId}` | `200` `Shipment` | "
                                + "`404 shipment-not-found` |",
                        "| `cancelShipment` (planned) | `POST /api/v1/shipments/{shipmentId}/cancellation` | `200` "
                                + "`Shipment` | `404 shipment-not-found` |",
                        "| `getShipmentLabel` | `GET /api/v1/shipments/{shipmentId}/label` | `200` `image/svg+xml`, "
                                + "headers `Content-Disposition`, `X-Content-Type-Options`, `Content-Security-Policy` | "
                                + "`404 shipment-not-found`, `409 shipment-cancelled` (planned) |");
        assertThat(OperationSections.section(sources(), "tenants")).contains("| `getCurrentMembership` | "
                + "`GET /api/v1/me` | `200` `Membership` | `401 unauthenticated`, `403 no-tenant-membership` |");
    }

    @Test
    void eachModuleWithOperationsHasItsSectionAndComposingAgainChangesNothing() {
        Map<String, String> composed = OperationSections.compose(sources());

        assertThat(composed).containsOnlyKeys("docs/modules/quotes.md", SHIPMENTS, "docs/modules/tenants.md");
        composed.forEach((path, text) -> assertThat(text).as(path).isEqualTo(fixture.read(path)));
        assertThat(OperationSections.compare(composed, fixture.root())).isEmpty();
    }

    @Test
    void composingReplacesTheSectionAndKeepsEveryOtherByte() {
        String original = fixture.read(SHIPMENTS);
        String edited = original.replace("| `200` `ShipmentPage` | none |", "| `200` `Shipments` | none |")
                .replace("## Events\n", "## Events\n\nA paragraph that the generator keeps.\n");
        fixture.write(SHIPMENTS, edited);

        String composed = OperationSections.compose(sources()).getOrDefault(SHIPMENTS, "");

        assertThat(composed).isEqualTo(original.replace("## Events\n", "## Events\n\nA paragraph that the generator "
                + "keeps.\n"));
        assertThat(OperationSections.compare(Map.of(SHIPMENTS, composed), fixture.root())).containsExactly(SHIPMENTS
                + ": its section HTTP operations differs from what the contract and the capabilities generate, first "
                + "at line " + lineOf(edited, "| `200` `Shipments` | none |") + "; run ./gradlew :tools:docs:generate "
                + "(ADR 0025)");
    }

    @Test
    void aHeadingInAFenceOrAQuoteAndALevelThreeHeadingDoNotEndOrStartTheSection() {
        String original = fixture.read(QUOTES);
        fixture.write(QUOTES, original.replace(QUOTES_HEADING, "```text\n## HTTP operations\n```\n\n> ## HTTP operations "
                + "\n\n" + QUOTES_HEADING + "\n\n### Notes inside the section\n\nThey belong to the generator."));

        String composed = OperationSections.compose(sources()).getOrDefault(QUOTES, "");

        assertThat(composed).contains("```text\n## HTTP operations\n```\n\n> ## HTTP operations \n\n" + QUOTES_HEADING)
                .doesNotContain("### Notes inside the section");
        assertThat(composed.split(Pattern.quote(QUOTES_HEADING), -1)).hasSize(2);
    }

    @Test
    void aModuleDocumentWithoutItsSectionOrWithTwoFails() {
        String original = fixture.read(QUOTES);
        fixture.write(QUOTES, original.replace(QUOTES_HEADING, "## Operations"));

        assertThatThrownBy(() -> OperationSections.compose(sources())).isInstanceOf(DocsException.class)
                .hasMessageContaining(QUOTES + " has 0 sections that start with the heading HTTP operations; the "
                        + "contract tags operations with quotes, so it has one (ADR 0025)");

        fixture.write(QUOTES, original.replace("## Events\n", QUOTES_HEADING + "\n\n## Events\n"));
        assertThatThrownBy(() -> OperationSections.compose(sources())).isInstanceOf(DocsException.class)
                .hasMessageContaining(QUOTES + " has 2 sections that start with the heading HTTP operations");
    }

    @Test
    void aSectionInTheDocumentOfAModuleWithoutOperationsFails() {
        String audit = "docs/modules/audit.md";
        fixture.write(audit, fixture.read(audit) + "\n## HTTP operations\n\nNone yet.\n");

        assertThatThrownBy(() -> OperationSections.compose(sources())).isInstanceOf(DocsException.class)
                .hasMessageContaining(audit + " has a section HTTP operations, but the contract tags no operation with "
                        + "audit; remove the section (ADR 0025)");
    }

    @Test
    void aMissingModuleDocumentFails() {
        fixture.delete(QUOTES);

        assertThatThrownBy(() -> OperationSections.compose(sources())).isInstanceOf(DocsException.class)
                .hasMessageContaining("the contract tags operations with quotes, and docs/modules/quotes.md does not "
                        + "exist (ADR 0025)");
    }

    @Test
    void aTagThatTagsNoOperationNeedsNoSection() {
        // A declared tag, audit, that no operation carries.
        String tenants = "tags:\n  - name: tenants\n";
        edit(ApiContract.PATH, tenants, tenants.replace("tags:\n", "tags:\n  - name: audit\n"));

        assertThat(OperationSections.compose(sources())).containsOnlyKeys(QUOTES, SHIPMENTS, "docs/modules/tenants.md");
    }

    @Test
    void theChecksReadTheComposedDocumentsSoALinkToAHeadingOfTheReplacedSectionFails() {
        fixture.write(QUOTES, fixture.read(QUOTES).replace(QUOTES_HEADING, QUOTES_HEADING
                + "\n\n### Hand-written notes\n\nThe generator removes them."));
        fixture.write(SHIPMENTS, fixture.read(SHIPMENTS).replace("## Events\n", "## Events\n\nSee [the notes]"
                + "(quotes.md#hand-written-notes).\n"));
        // The documents as the repository holds them pass the check.
        assertThat(GeneratedFiles.generate(sources())).isNotEmpty();

        Sources composed = sources().withTexts(OperationSections.compose(sources()));

        assertThatThrownBy(() -> GeneratedFiles.generate(composed)).isInstanceOf(DocsException.class)
                .hasMessageContaining("no heading of docs/modules/quotes.md has the anchor #hand-written-notes");
    }

    @Test
    void aPipeOrABacktickInAValueStaysInItsCellAndALineBreakFails() {
        edit(ApiContract.PATH, "      operationId: getQuote\n", "      operationId: get|Quote\n");
        edit(Capabilities.PATH, "    operations: [getQuote]\n", "    operations: [get|Quote]\n");
        edit(ApiContract.PATH, "      name: Idempotency-Key\n", "      name: Idempotency`Key\n");

        Map<String, String> composed = OperationSections.compose(sources());

        // As the site's parser reads the tables: four cells in every row, each showing its value.
        MarkdownTable quotes = table(composed, QUOTES);
        assertThat(quotes.rows()).allSatisfy(row -> assertThat(row.cells()).hasSize(4));
        assertThat(quotes.rows().get(2).texts()).containsExactly("get|Quote", "GET /api/v1/quotes/{quoteId}",
                "200 Quote", "404 quote-not-found");
        assertThat(table(composed, SHIPMENTS).rows().get(1).texts().get(1))
                .isEqualTo("POST /api/v1/shipments (header Idempotency`Key)");

        edit(ApiContract.PATH, "      name: Idempotency`Key\n", "      name: \"Idempotency\\nKey\"\n");
        assertThatThrownBy(() -> OperationSections.compose(sources())).isInstanceOf(DocsException.class)
                .hasMessageContaining("contracts/shipping.openapi.yaml bookShipment: the value 'Idempotency\\nKey' is "
                        + "blank or holds a line break, so no table row can show it (ADR 0025)");
    }

    @Test
    void theBlankLinesBeforeTheNextHeadingBelongToTheSectionAndThoseBeforeItsHeadingDoNot() {
        String original = fixture.read(QUOTES);
        edit(QUOTES, "\n" + QUOTES_HEADING, "\n\n" + QUOTES_HEADING);
        edit(QUOTES, "\n\n## Events\n", "\n\n  \n\n## Events\n");

        assertThat(OperationSections.compose(sources()).getOrDefault(QUOTES, "")).isEqualTo(original.replace(
                "\n" + QUOTES_HEADING, "\n\n" + QUOTES_HEADING));
    }

    @Test
    void aReferenceToAReferenceFailsAndALoneCarriageReturnFails() {
        String definition = "    QuoteIdPath:\n";
        edit(ApiContract.PATH, definition, "    QuoteIdAlias:\n      $ref: '#/components/parameters/QuoteIdPath'\n"
                + definition);
        edit(ApiContract.PATH, "        - $ref: '#/components/parameters/QuoteIdPath'\n",
                "        - $ref: '#/components/parameters/QuoteIdAlias'\n");
        assertThatThrownBy(() -> OperationSections.compose(sources())).isInstanceOf(DocsException.class)
                .hasMessageContaining("contracts/shipping.openapi.yaml: #/components/parameters/QuoteIdAlias is a "
                        + "reference itself; reference the component that defines it (ADR 0025)");

        Fixture other = Fixture.copy(dir.resolve("other")).passingEvidence();
        other.edit(QUOTES, text -> text.replace("## Events\n", "## Events\n\rA line that a carriage return ends.\n"));
        assertThatThrownBy(() -> OperationSections.compose(Sources.read(other.root(), List.of(other.reports()))))
                .isInstanceOf(DocsException.class)
                .hasMessageContaining(QUOTES + " holds a carriage return without a line feed (ADR 0025)");
    }

    @Test
    void aCopyOfARepositoryWhoseSectionNamesAMissingPathPassesTheCheck() {
        // A committed section edited by hand to name a path that does not exist, which the check refuses in prose.
        edit(QUOTES, "| `200` `Quote` | `404 quote-not-found` |", "| `200` `docs/missing.md` | `404 quote-not-found` |");
        assertThatThrownBy(() -> GeneratedFiles.generate(sources())).hasMessageContaining("docs/missing.md");

        Fixture copy = Fixture.copy(dir.resolve("copy"), fixture.root()).passingEvidence();

        assertThat(copy.read(QUOTES)).doesNotContain("docs/missing.md");
        assertThat(GeneratedFiles.generate(Sources.read(copy.root(), List.of(copy.reports())))).isNotEmpty();
    }

    /** Replaces one passage of a file of the copy, which must occur once. */
    private void edit(String path, String passage, String replacement) {
        fixture.edit(path, text -> {
            assertThat(text.split(Pattern.quote(passage), -1)).as(passage).hasSize(2);
            return text.replace(passage, replacement);
        });
    }

    /** The operation table of a composed document, as the site's parser reads it. */
    private static MarkdownTable table(Map<String, String> composed, String path) {
        return MarkdownRenderer.tables(DocFile.parse(path, composed.getOrDefault(path, "")).body(), path).stream()
                .filter(t -> t.rows().getFirst().texts().getFirst().equals("operationId")).findFirst().orElseThrow();
    }

    /** The line, counted from 1, on which a text contains {@code part}. */
    private static int lineOf(String text, String part) {
        return (int) text.substring(0, text.indexOf(part)).chars().filter(c -> c == '\n').count() + 1;
    }

    private Sources sources() {
        return Sources.read(fixture.root(), List.of(fixture.reports()));
    }
}
