package io.github.stigjohnny.c4contractkit.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.c4contractkit.testing.RepositoryFiles;
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
}
