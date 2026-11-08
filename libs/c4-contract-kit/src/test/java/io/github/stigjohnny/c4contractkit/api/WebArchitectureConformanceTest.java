package io.github.stigjohnny.c4contractkit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.stigjohnny.c4contractkit.testing.RepositoryFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Partial lint of the repository's detected web imports against the web container of architecture/workspace.dsl,
 * run by {@code ./gradlew check} on every pull request. It writes its reports to
 * libs/c4-contract-kit/build/reports/c4-contract-web/. Detected disagreements and reported refusals fail the
 * {@code java} check; silent misses remain, including loaders after spread syntax (ADR 0013).
 */
class WebArchitectureConformanceTest {

    private static final Path REPOSITORY = RepositoryFiles.root();

    @Test
    void theWebAppConformsToTheModel() {
        WebReport report = WebConformance.check(REPOSITORY.resolve("architecture/workspace.dsl"), REPOSITORY);

        WebConformance.assertConforms(report, RepositoryFiles.library().resolve("build/reports/c4-contract-web"));
        assertThat(report.sourceRoot()).isEqualTo("apps/shipping-web/src");
        assertThat(report.testRoots()).containsExactly("apps/shipping-web/e2e");
        assertThat(report.publicRoots()).containsExactly("apps/shipping-web/public");
    }

    @Test
    void anUnapprovedImportInTheRepositorysE2eRootFailsTheGate(@TempDir Path repository) throws IOException {
        Path app = repository.resolve("apps/shipping-web");
        Files.createDirectories(app.resolve("e2e"));
        Files.writeString(app.resolve("package.json"), "{\"engines\":{\"node\":\"26.13.0\"}}\n");
        Files.writeString(app.resolve("e2e/unapproved.spec.ts"), "import 'node:fs';\n");
        // Use the repository's unchanged model, with no runtime approval in the synthetic repository.
        WebReport report = WebConformance.check(REPOSITORY.resolve("architecture/workspace.dsl"), repository);

        assertThat(report.violations()).extracting(WebViolation::type)
                .containsExactly(WebViolationType.UNAPPROVED_IMPORT);
        assertThat(report.externalImports()).containsExactly(new WebExternalImport(
                "apps/shipping-web/e2e/unapproved.spec.ts", 1, "node:fs", "node:fs", "26.13.0",
                "web.testBuiltins", "", ExternalImportStatus.UNAPPROVED));
        assertThatThrownBy(() -> WebConformance.assertConforms(report, repository.resolve("reports")))
                .isInstanceOf(AssertionError.class).hasMessageContaining("UNAPPROVED_IMPORT")
                .hasMessageContaining("apps/shipping-web/e2e/unapproved.spec.ts");
    }

    @Test
    void everyPartIsPlannedWithoutCodeSoNoEdgeIsEvaluatedYet() {
        WebReport report = WebConformance.check(REPOSITORY.resolve("architecture/workspace.dsl"), REPOSITORY);

        assertThat(report.components()).extracting(WebComponentResult::id, WebComponentResult::status,
                WebComponentResult::fileCount).containsExactly(
                tuple("api", ComponentStatus.PLANNED, 0),
                tuple("app", ComponentStatus.PLANNED, 0),
                tuple("auth", ComponentStatus.PLANNED, 0),
                tuple("quote-feature", ComponentStatus.PLANNED, 0),
                tuple("shipment-feature", ComponentStatus.PLANNED, 0),
                tuple("ui", ComponentStatus.PLANNED, 0));
        // No part declares an npm package, so an import of any package in browser code fails as undeclared.
        assertThat(report.components()).allSatisfy(component -> assertThat(component.packages()).isEmpty());
        assertThat(report.externalImports()).isEmpty();
        assertThat(report.components()).filteredOn(component -> component.entry().isPresent())
                .extracting(WebComponentResult::id, component -> component.entry().orElseThrow()).containsExactly(
                        tuple("quote-feature", "features/quotes/index.ts"),
                        tuple("shipment-feature", "features/shipments/index.ts"));
        assertThat(report.edges()).extracting(WebEdgeResult::source, WebEdgeResult::target, WebEdgeResult::mode,
                WebEdgeResult::status).containsExactly(
                tuple("app", "api", Mode.PERMITTED, EdgeStatus.NOT_EVALUATED),
                tuple("app", "auth", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED),
                tuple("app", "quote-feature", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED),
                tuple("app", "shipment-feature", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED),
                tuple("app", "ui", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED),
                tuple("auth", "api", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED),
                tuple("auth", "ui", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED),
                tuple("quote-feature", "api", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED),
                tuple("quote-feature", "shipment-feature", Mode.PERMITTED, EdgeStatus.NOT_EVALUATED),
                tuple("quote-feature", "ui", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED),
                tuple("shipment-feature", "api", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED),
                tuple("shipment-feature", "auth", Mode.PERMITTED, EdgeStatus.NOT_EVALUATED),
                tuple("shipment-feature", "ui", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED));
        assertThat(report.violations()).isEmpty();
    }
}
