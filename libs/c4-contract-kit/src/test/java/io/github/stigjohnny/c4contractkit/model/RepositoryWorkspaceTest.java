package io.github.stigjohnny.c4contractkit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.stigjohnny.c4contractkit.api.ComponentResult;
import io.github.stigjohnny.c4contractkit.api.Conformance;
import io.github.stigjohnny.c4contractkit.api.EdgeResult;
import io.github.stigjohnny.c4contractkit.api.EdgeStatus;
import io.github.stigjohnny.c4contractkit.api.Mode;
import io.github.stigjohnny.c4contractkit.api.Report;
import io.github.stigjohnny.c4contractkit.api.Violation;
import io.github.stigjohnny.c4contractkit.api.ViolationType;
import io.github.stigjohnny.c4contractkit.testing.RepositoryFiles;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The repository's real model, architecture/workspace.dsl, parses and validates. Two components are built, quotes and
 * tenants, and the other five are planned; ArchitectureConformanceTest in :apps:shipping-api checks the model against
 * the API's classes.
 */
class RepositoryWorkspaceTest {

    @Test
    void theRepositoryModelParsesAndValidates() throws Exception {
        ContractModel model = ContractModelReader.read(
                RepositoryFiles.root().resolve("architecture/workspace.dsl"), "architecture/workspace.dsl");

        assertThat(model.root()).isEqualTo("io.github.stigjohnny.shipping");
        assertThat(model.isolated()).containsExactly(
                "io.github.stigjohnny.shipping.CorrelationIdFilter",
                "io.github.stigjohnny.shipping.FallbackProblemHandler",
                "io.github.stigjohnny.shipping.ShippingApplication");
        assertThat(model.components()).extracting(ContractComponent::id)
                .containsExactly("audit", "orders", "quotes", "returns", "shipments", "tenants", "tracking");
        assertThat(model.components()).extracting(ContractComponent::id, ContractComponent::status).containsExactly(
                tuple("audit", ComponentStatus.PLANNED), tuple("orders", ComponentStatus.PLANNED),
                tuple("quotes", ComponentStatus.BUILT), tuple("returns", ComponentStatus.PLANNED),
                tuple("shipments", ComponentStatus.PLANNED), tuple("tenants", ComponentStatus.BUILT),
                tuple("tracking", ComponentStatus.PLANNED));
        assertThat(model.components()).allSatisfy(component -> assertThat(component.packages())
                .containsExactly("io.github.stigjohnny.shipping." + component.id()));
        assertThat(model.relationships()).containsExactly(
                new ContractRelationship("audit", "quotes", RelationshipMode.PERMITTED),
                new ContractRelationship("audit", "shipments", RelationshipMode.REQUIRED),
                new ContractRelationship("audit", "tenants", RelationshipMode.REQUIRED),
                new ContractRelationship("orders", "quotes", RelationshipMode.PERMITTED),
                new ContractRelationship("orders", "shipments", RelationshipMode.PERMITTED),
                new ContractRelationship("quotes", "tenants", RelationshipMode.REQUIRED),
                new ContractRelationship("returns", "shipments", RelationshipMode.PERMITTED),
                new ContractRelationship("shipments", "quotes", RelationshipMode.REQUIRED),
                new ContractRelationship("shipments", "tenants", RelationshipMode.REQUIRED),
                new ContractRelationship("tracking", "shipments", RelationshipMode.PERMITTED));
    }

    @Test
    void withoutClassesTheBuiltComponentsAreEmptyAndTheirRequiredEdgeIsAbsent() {
        // The model now asks for code: a check without classes fails for quotes and tenants, and only for them.
        Report report = Conformance.check(RepositoryFiles.root().resolve("architecture/workspace.dsl"), List.of());

        assertThat(report.passed()).isFalse();
        assertThat(report.modelPath()).isEqualTo("../../architecture/workspace.dsl");
        assertThat(report.violations()).extracting(Violation::type, Violation::identifiers).containsExactly(
                tuple(ViolationType.ABSENCE, List.of("quotes", "tenants")),
                tuple(ViolationType.BUILT_COMPONENT_EMPTY, List.of("quotes")),
                tuple(ViolationType.BUILT_COMPONENT_EMPTY, List.of("tenants")));
        assertThat(report.components()).hasSize(7).allSatisfy(component ->
                assertThat(component.classCount()).as(component.id()).isZero());
        assertThat(report.components()).filteredOn(component -> component.status()
                == io.github.stigjohnny.c4contractkit.api.ComponentStatus.BUILT)
                .extracting(ComponentResult::id).containsExactly("quotes", "tenants");
        assertThat(report.edges()).extracting(EdgeResult::source, EdgeResult::target, EdgeResult::mode,
                EdgeResult::status).containsExactly(
                tuple("audit", "quotes", Mode.PERMITTED, EdgeStatus.NOT_EVALUATED),
                tuple("audit", "shipments", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED),
                tuple("audit", "tenants", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED),
                tuple("orders", "quotes", Mode.PERMITTED, EdgeStatus.NOT_EVALUATED),
                tuple("orders", "shipments", Mode.PERMITTED, EdgeStatus.NOT_EVALUATED),
                tuple("quotes", "tenants", Mode.REQUIRED, EdgeStatus.ABSENCE),
                tuple("returns", "shipments", Mode.PERMITTED, EdgeStatus.NOT_EVALUATED),
                tuple("shipments", "quotes", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED),
                tuple("shipments", "tenants", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED),
                tuple("tracking", "shipments", Mode.PERMITTED, EdgeStatus.NOT_EVALUATED));
        assertThat(report.edges()).allSatisfy(edge -> assertThat(edge.evidence()).isEmpty());
    }
}
