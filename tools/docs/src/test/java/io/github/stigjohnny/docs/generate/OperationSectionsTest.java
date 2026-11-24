package io.github.stigjohnny.docs.generate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
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
 * runs the two commands.
 */
class OperationSectionsTest {

    private static final String SHIPMENTS = "docs/modules/shipments.md";
    private static final String QUOTES = "docs/modules/quotes.md";
    private static final String QUOTES_HEADING = "## HTTP operations (`contracts/shipping.openapi.yaml`, tag `quotes`)";

    @TempDir
    Path dir;

    private Fixture fixture;

    /**
     * Copies the repository and generates its sections, so that a committed section that differs, which checkGenerated
     * reports, fails none of these tests and cannot block {@code ./gradlew :tools:docs:generate}, which runs them first.
     */
    @BeforeEach
    void copyTheRepository() {
        fixture = Fixture.copy(dir.resolve("repo")).passingEvidence();
        OperationSections.write(OperationSections.compose(sources()), fixture.root());
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

    /** The line, counted from 1, on which a text contains {@code part}. */
    private static int lineOf(String text, String part) {
        return (int) text.substring(0, text.indexOf(part)).chars().filter(c -> c == '\n').count() + 1;
    }

    private Sources sources() {
        return Sources.read(fixture.root(), List.of(fixture.reports()));
    }
}
