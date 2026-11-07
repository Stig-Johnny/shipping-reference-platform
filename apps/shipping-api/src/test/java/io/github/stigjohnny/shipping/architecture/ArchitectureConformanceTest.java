package io.github.stigjohnny.shipping.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.stigjohnny.c4contractkit.api.ComponentResult;
import io.github.stigjohnny.c4contractkit.api.ComponentStatus;
import io.github.stigjohnny.c4contractkit.api.Conformance;
import io.github.stigjohnny.c4contractkit.api.ConformanceAssertions;
import io.github.stigjohnny.c4contractkit.api.EdgeResult;
import io.github.stigjohnny.c4contractkit.api.EdgeStatus;
import io.github.stigjohnny.c4contractkit.api.Evidence;
import io.github.stigjohnny.c4contractkit.api.Mode;
import io.github.stigjohnny.c4contractkit.api.Report;
import io.github.stigjohnny.c4contractkit.api.Violation;
import io.github.stigjohnny.c4contractkit.api.ViolationType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The API's compiled classes against architecture/workspace.dsl, in both directions, with c4-contract-kit (ADR 0002):
 * a dependency between two modules that the model does not declare fails, and so does a REQUIRED relationship between
 * built modules that no class backs with a direct static reference. Every main class-output directory is checked, and
 * the reports are written to build/reports/c4-contract/ before the assertion.
 */
class ArchitectureConformanceTest {

    private static final Path MODEL = Path.of("../../architecture/workspace.dsl");
    private static final Path REPORTS = Path.of("build/reports/c4-contract");
    private static final String QUOTES = "io.github.stigjohnny.shipping.quotes.";
    private static final String TENANTS_API = "io.github.stigjohnny.shipping.tenants.api.";

    @Test
    void theApiConformsToTheModel() {
        Report report = Conformance.check(MODEL, ClassDirectories.main());

        ConformanceAssertions.assertConforms(report, REPORTS);
        assertThat(report.components()).extracting(ComponentResult::id, ComponentResult::status).containsExactly(
                tuple("audit", ComponentStatus.PLANNED), tuple("orders", ComponentStatus.PLANNED),
                tuple("quotes", ComponentStatus.BUILT), tuple("returns", ComponentStatus.PLANNED),
                tuple("shipments", ComponentStatus.PLANNED), tuple("tenants", ComponentStatus.BUILT),
                tuple("tracking", ComponentStatus.PLANNED));
        assertThat(report.components()).allSatisfy(component -> assertThat(component.classCount() > 0)
                .as(component.id()).isEqualTo(component.status() == ComponentStatus.BUILT));
        // The one relationship between built modules is evaluated; every other one touches a planned module.
        assertThat(report.edges()).extracting(EdgeResult::source, EdgeResult::target, EdgeResult::mode,
                EdgeResult::status).containsExactly(
                tuple("audit", "quotes", Mode.PERMITTED, EdgeStatus.NOT_EVALUATED),
                tuple("audit", "shipments", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED),
                tuple("audit", "tenants", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED),
                tuple("orders", "quotes", Mode.PERMITTED, EdgeStatus.NOT_EVALUATED),
                tuple("orders", "shipments", Mode.PERMITTED, EdgeStatus.NOT_EVALUATED),
                tuple("quotes", "tenants", Mode.REQUIRED, EdgeStatus.CONVERGENCE),
                tuple("returns", "shipments", Mode.PERMITTED, EdgeStatus.NOT_EVALUATED),
                tuple("shipments", "quotes", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED),
                tuple("shipments", "tenants", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED),
                tuple("tracking", "shipments", Mode.PERMITTED, EdgeStatus.NOT_EVALUATED));
        List<Evidence> quotesToTenants = report.edges().get(5).evidence();
        assertThat(quotesToTenants).allSatisfy(evidence -> {
            assertThat(evidence.originClass()).startsWith(QUOTES);
            assertThat(evidence.targetClass()).startsWith(TENANTS_API);
        });
        assertThat(quotesToTenants).extracting(Evidence::targetClass)
                .contains(TENANTS_API + "CurrentTenant", TENANTS_API + "TenantId");
    }

    @Test
    void withoutTheQuotesToTenantsRelationshipTheApiDivergesFromTheModel(@TempDir Path dir) throws IOException {
        // The check is a real one: drop quotes -> tenants (and its step in the dynamic view) and the code diverges.
        String model = Files.readString(MODEL, StandardCharsets.UTF_8);
        String relationship = """
                        quotes -> tenants "Resolves the caller's tenant (tenants.api: CurrentTenant, TenantId)" "Java" \
                "Required" {
                            properties {
                                "contract.mode" "REQUIRED"
                            }
                        }
                """;
        String step = "            quotes -> tenants \"Resolves the caller's tenant\"\n";
        assertThat(model).contains(relationship, step);
        Path weakened = Files.writeString(dir.resolve("workspace.dsl"), model.replace(relationship, "")
                .replace(step, ""), StandardCharsets.UTF_8);

        Report report = Conformance.check(weakened, ClassDirectories.main());

        assertThat(report.violations()).extracting(Violation::type, Violation::identifiers)
                .containsExactly(tuple(ViolationType.DIVERGENCE, List.of("quotes", "tenants")));
        assertThat(report.edges()).filteredOn(edge -> edge.source().equals("quotes"))
                .extracting(EdgeResult::target, EdgeResult::mode, EdgeResult::status)
                .containsExactly(tuple("tenants", Mode.UNDECLARED, EdgeStatus.DIVERGENCE));
        assertThatThrownBy(() -> ConformanceAssertions.assertConforms(report, dir.resolve("reports")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("DIVERGENCE quotes, tenants: quotes depends on tenants");
    }
}
