package io.github.stigjohnny.c4contractkit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.stigjohnny.c4contractkit.testing.RepositoryFiles;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The repository's web app conforms to the web container of architecture/workspace.dsl: the web import check, run by
 * {@code ./gradlew check} on every pull request. It writes its reports to
 * libs/c4-contract-kit/build/reports/c4-contract-web/ and fails with every violation, so a pull request whose web
 * sources and diagram disagree fails the {@code java} check.
 */
class WebArchitectureConformanceTest {

    private static final Path REPOSITORY = RepositoryFiles.root();

    @Test
    void theWebAppConformsToTheModel() {
        WebReport report = WebConformance.check(REPOSITORY.resolve("architecture/workspace.dsl"), REPOSITORY);

        WebConformance.assertConforms(report, RepositoryFiles.library().resolve("build/reports/c4-contract-web"));
        assertThat(report.sourceRoot()).isEqualTo("apps/shipping-web/src");
        assertThat(report.testRoots()).isEmpty();
        assertThat(report.publicRoots()).containsExactly("apps/shipping-web/public");
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
