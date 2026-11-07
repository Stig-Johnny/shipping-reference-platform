package io.github.stigjohnny.docs.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.testing.Repository;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The repository's own model, read the way the generator reads it. */
class WorkspacesTest {

    private static final ParsedWorkspace WORKSPACE =
            Workspaces.read(Repository.file("architecture/workspace.dsl"), "architecture/workspace.dsl");

    @Test
    void theFourViewsAreReadInZoomOrder() {
        assertThat(WORKSPACE.model().views()).extracting(C4View::key)
                .containsExactly("SystemContext", "Containers", "ApiComponents", "QuoteBookLabel");
        assertThat(WORKSPACE.model().views()).extracting(C4View::kind).containsExactly(
                C4View.Kind.SYSTEM_CONTEXT, C4View.Kind.CONTAINER, C4View.Kind.COMPONENT,
                C4View.Kind.DYNAMIC);
        assertThat(WORKSPACE.exports()).extracting(RawMermaid::viewKey)
                .containsExactly("SystemContext", "Containers", "ApiComponents", "QuoteBookLabel");
    }

    @Test
    void theSevenModulesCarryTheirContractProperties() {
        List<ModelElement> modules = WORKSPACE.model().modules();

        assertThat(modules).extracting(m -> m.contractId().orElseThrow() + ": " + m.contractStatus().orElseThrow()
                + (m.planned() ? ", tagged Planned" : "")).containsExactly("quotes: built",
                "shipments: built", "tenants: built", "audit: planned, tagged Planned",
                "orders: planned, tagged Planned", "tracking: planned, tagged Planned", "returns: planned, tagged Planned");
        assertThat(modules).allSatisfy(m -> assertThat(m.contractPackages())
                .containsExactly("io.github.stigjohnny.shipping." + m.contractId().get()));
    }

    @Test
    void theWebAppAndTheDatabaseAreTaggedPlannedAndTheApiIsNot() {
        // Their planned label comes from the model, not from every capability being planned.
        assertThat(WORKSPACE.model().elements()).filteredOn(e -> e.type().equals("Container"))
                .extracting(e -> e.name() + (e.planned() ? ": planned" : ""))
                .containsExactlyInAnyOrder("Web app: planned", "API", "Database: planned");
    }

    @Test
    void relationshipModesAreRead() {
        C4Model model = WORKSPACE.model();
        ModelElement audit = model.module("audit").orElseThrow();

        assertThat(model.outgoing(audit.id()))
                .filteredOn(r -> model.element(r.destinationId()).contractId().isPresent())
                .extracting(r -> model.element(r.destinationId()).contractId().orElseThrow() + ":"
                        + r.mode().orElseThrow())
                .containsExactlyInAnyOrder("shipments:REQUIRED", "tenants:REQUIRED", "quotes:PERMITTED");
    }

    @Test
    void theDynamicViewKeepsItsStepsInOrder() {
        C4View dynamic = WORKSPACE.model().view("QuoteBookLabel");

        assertThat(dynamic.steps()).extracting(C4View.Step::order)
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15");
        assertThat(dynamic.steps().get(2).description()).isEqualTo("POST /api/v1/quotes (createQuote)");
    }

    @Test
    void theRawExportIsTheExportersFlowchart() {
        assertThat(WORKSPACE.exports()).hasSize(4).allSatisfy(export ->
                assertThat(export.definition()).startsWith("graph LR"));
    }
}
