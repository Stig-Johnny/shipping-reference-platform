package io.github.stigjohnny.docs;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.testing.Repository;
import io.github.stigjohnny.docs.yaml.SafeYaml;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The engine is unavailable before checkout and outside the two reviewed database-test windows. */
class ContainerEngineWorkflowTest {
    @Test
    void everyJobStartsWithAnUnconditionalGuardBeforeAnyAction() throws IOException {
        try (var files = Files.list(Repository.file(".github/workflows"))) {
            for (var file : files.toList()) {
                YMap workflow = SafeYaml.yaml(Files.readString(file), file.toString()).asMap("workflow");
                for (var job : workflow.map("jobs").entries().values()) {
                    YMap first = steps(job.asMap("job")).getFirst();
                    assertThat(first.string("id")).isEqualTo("engine_off");
                    assertThat(first.has("if")).isFalse();
                    assertGuard(first);
                }
            }
        }
    }

    @Test
    void onlyTheDatabaseTaskRunsBetweenEnginePreparationAndShutdown() {
        for (String path : List.of(".github/workflows/java.yml", ".github/workflows/pages.yml")) {
            YMap workflow = SafeYaml.yaml(Repository.read(path), path).asMap("workflow");
            List<YMap> steps = steps(workflow.map("jobs").map(path.endsWith("java.yml") ? "java" : "test"));
            int prepare = index(steps, "prepare");
            int closed = index(steps, "engine_closed");
            assertThat(closed).isEqualTo(prepare + 4);
            assertThat(steps.get(prepare).string("run")).contains("systemctl unmask", "container-engine.sh prepare");
            assertThat(steps.get(prepare + 1).string("run")).endsWith("container-engine.sh stage");
            assertThat(steps.get(prepare + 2).string("run"))
                    .isEqualTo("./gradlew --no-daemon --console=plain :apps:shipping-api:databaseTest");
            assertThat(steps.get(prepare + 3).string("run")).endsWith("container-engine.sh verify");
            YMap guard = steps.get(closed);
            assertGuard(guard);
            assertThat(guard.string("if")).isEqualTo("${{ always() }}");
            for (YMap later : steps.subList(closed + 1, steps.size())) {
                // Failure handlers and uploads must not proceed if shutdown failed.
                assertThat(later.string("if")).contains("steps.engine_closed.outcome == 'success'");
            }
        }
    }

    @Test
    void javaRunsTheExecutableGuardRegressionAndOrdinaryJobsNeverEnableTheEngine() {
        for (String path : List.of(".github/workflows/java.yml", ".github/workflows/pages.yml",
                ".github/workflows/preflight.yml")) {
            YMap workflow = SafeYaml.yaml(Repository.read(path), path).asMap("workflow");
            for (var entry : workflow.map("jobs").entries().entrySet()) {
                if (path.endsWith("java.yml") && entry.getKey().equals("java")) {
                    assertThat(steps(entry.getValue().asMap("job"))).anySatisfy(step ->
                            assertThat(step.optionalString("run").orElse(""))
                                    .contains("bash tools/ci/engine-guard-test.sh"));
                } else if (!(path.endsWith("pages.yml") && entry.getKey().equals("test"))) {
                    assertThat(steps(entry.getValue().asMap("job"))).allSatisfy(step ->
                            assertThat(step.optionalString("run").orElse(""))
                                    .doesNotContain("systemctl unmask", "container-engine.sh prepare", ":apps:shipping-api:databaseTest"));
                }
            }
        }
    }

    private static void assertGuard(YMap step) {
        assertThat(step.has("uses")).isFalse();
        assertThat(step.has("continue-on-error")).isFalse();
        assertThat(step.string("run")).contains("# engine-guard: begin", "set -euo pipefail",
                "systemctl stop", "systemctl mask --runtime", "rm -f /var/run/", "nc -z -U -w 2",
                "exit 1", "# engine-guard: end");
    }

    private static List<YMap> steps(YMap job) {
        return job.list("steps").stream().map(step -> step.asMap("step")).toList();
    }

    private static int index(List<YMap> steps, String id) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).optionalString("id").orElse("").equals(id)) {
                return i;
            }
        }
        throw new AssertionError("missing step " + id);
    }
}
