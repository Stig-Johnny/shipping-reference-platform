package io.github.stigjohnny.c4contractkit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.stigjohnny.c4contractkit.testing.ClassRoots;
import io.github.stigjohnny.c4contractkit.testing.RepositoryFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The library's own packages conform to its self-model, libs/c4-contract-kit/architecture/self.dsl, checked with the
 * library itself: every allowed edge is used, and no other edge exists.
 */
class SelfConformanceTest {

    private static final Path SELF_MODEL = RepositoryFiles.library().resolve("architecture/self.dsl");
    private static final Path LIBRARY_CLASSES = ClassRoots.of(Conformance.class.getName());

    @Test
    void theLibraryConformsToItsSelfModel() {
        Report report = Conformance.check(SELF_MODEL, List.of(LIBRARY_CLASSES));

        ConformanceAssertions.assertConforms(report, RepositoryFiles.library().resolve("build/reports/c4-contract-self"));
        assertThat(report.components()).extracting(ComponentResult::id)
                .containsExactly("api", "bytecode", "model", "report", "rules", "sources");
        assertThat(report.components()).allSatisfy(component -> {
            assertThat(component.status()).isEqualTo(ComponentStatus.BUILT);
            assertThat(component.classCount()).isPositive();
        });
        assertThat(report.edges()).extracting(EdgeResult::source, EdgeResult::target, EdgeResult::mode,
                EdgeResult::status).containsExactly(
                tuple("api", "bytecode", Mode.REQUIRED, EdgeStatus.CONVERGENCE),
                tuple("api", "model", Mode.REQUIRED, EdgeStatus.CONVERGENCE),
                tuple("api", "report", Mode.REQUIRED, EdgeStatus.CONVERGENCE),
                tuple("api", "rules", Mode.REQUIRED, EdgeStatus.CONVERGENCE),
                tuple("report", "model", Mode.REQUIRED, EdgeStatus.CONVERGENCE),
                tuple("report", "rules", Mode.REQUIRED, EdgeStatus.CONVERGENCE),
                tuple("rules", "bytecode", Mode.REQUIRED, EdgeStatus.CONVERGENCE),
                tuple("rules", "model", Mode.REQUIRED, EdgeStatus.CONVERGENCE));
    }

    @Test
    void aSelfModelWithoutAnAllowedEdgeFailsWithADivergence(@TempDir Path dir) throws IOException {
        // The self-check is a real check: drop report -> model from the self-model and the library diverges from it.
        String weakened = Files.readString(SELF_MODEL, StandardCharsets.UTF_8).replace("""
                        reportPackage -> modelPackage "Writes component statuses from" "Java" "Required" {
                            properties {
                                "contract.mode" "REQUIRED"
                            }
                        }
                """, "");
        Path model = Files.writeString(dir.resolve("self.dsl"), weakened, StandardCharsets.UTF_8);

        Report report = Conformance.check(model, List.of(LIBRARY_CLASSES));

        assertThat(report.violations()).extracting(Violation::type, Violation::identifiers)
                .containsExactly(tuple(ViolationType.DIVERGENCE, List.of("report", "model")));
        assertThatThrownBy(() -> ConformanceAssertions.assertConforms(report, dir.resolve("reports")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("DIVERGENCE report, model: report depends on model");
    }
}
