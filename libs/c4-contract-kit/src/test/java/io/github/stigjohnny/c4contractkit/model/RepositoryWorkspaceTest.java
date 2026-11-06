package io.github.stigjohnny.c4contractkit.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.c4contractkit.api.Conformance;
import io.github.stigjohnny.c4contractkit.api.EdgeStatus;
import io.github.stigjohnny.c4contractkit.api.Report;
import io.github.stigjohnny.c4contractkit.testing.RepositoryFiles;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The repository's real model, architecture/workspace.dsl, parses and validates. Every component is still planned. */
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
        assertThat(model.components()).allSatisfy(component -> {
            assertThat(component.status()).isEqualTo(ComponentStatus.PLANNED);
            assertThat(component.packages()).containsExactly("io.github.stigjohnny.shipping." + component.id());
        });
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
    void theRepositoryModelPassesWithoutCodeBecauseEveryComponentIsPlanned() {
        Report report = Conformance.check(RepositoryFiles.root().resolve("architecture/workspace.dsl"), List.of());

        assertThat(report.passed()).isTrue();
        assertThat(report.modelPath()).isEqualTo("../../architecture/workspace.dsl");
        assertThat(report.components()).hasSize(7).allSatisfy(component -> {
            assertThat(component.status()).isEqualTo(io.github.stigjohnny.c4contractkit.api.ComponentStatus.PLANNED);
            assertThat(component.classCount()).isZero();
        });
        assertThat(report.edges()).hasSize(10).allSatisfy(edge -> {
            assertThat(edge.status()).isEqualTo(EdgeStatus.NOT_EVALUATED);
            assertThat(edge.evidence()).isEmpty();
        });
    }
}
