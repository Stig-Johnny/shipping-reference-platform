package io.github.stigjohnny.shipping.database;

import static io.github.stigjohnny.shipping.database.ReviewedPostgres.Engine.GITHUB_UBUNTU_24_04;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.shipping.database.ReviewedPostgres.ContainerFacts;
import io.github.stigjohnny.shipping.database.ReviewedPostgres.EngineReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The harness fails closed: each condition it checks refuses on its own, without an engine. The smoke test,
 * {@link PostgresHarnessIntegrationTest}, shows that the conditions hold on the reviewed runner.
 */
class ReviewedPostgresTest {

    private static final String REFERENCE =
            "postgres@sha256:bd5dfa494a5a259704365813dbfa5fa6b3e69be7348d43b547f540c730478583";

    @TempDir
    Path home;

    @TempDir
    Path dockerConfig;

    @Test
    void theHarnessNamesTheReviewedImageByItsIndexDigestAlone() {
        assertThat(ReviewedPostgres.reference()).isEqualTo(REFERENCE);
    }

    @Test
    void theEnvironmentThatTheRunnerPreparesHasNoProblem() throws IOException {
        assertThat(problems(prepared())).isEmpty();
    }

    @Test
    void withoutAReviewedEngineNamedNothingStarts() throws IOException {
        Map<String, String> unnamed = prepared();
        unnamed.remove(ReviewedPostgres.ENGINE_VARIABLE);
        Map<String, String> unreviewed = prepared();
        unreviewed.put(ReviewedPostgres.ENGINE_VARIABLE, "colima-demotest");

        assertThat(problems(unnamed)).singleElement().asString().contains("names no reviewed container engine (unset)");
        assertThat(problems(unreviewed)).singleElement().asString().contains("('colima-demotest')");
    }

    @Test
    void ryukAndTheStartupChecksStayOffAndNoOtherTestcontainersSettingIsAccepted() throws IOException {
        Map<String, String> environment = prepared();
        environment.remove("TESTCONTAINERS_RYUK_DISABLED");
        environment.put("TESTCONTAINERS_CHECKS_DISABLE", "false");
        environment.put("TESTCONTAINERS_HOST_OVERRIDE", "example.org");

        assertThat(problems(environment)).containsExactly("TESTCONTAINERS_CHECKS_DISABLE is 'false', not true",
                "TESTCONTAINERS_RYUK_DISABLED is unset, not true",
                "TESTCONTAINERS_HOST_OVERRIDE is set; no Testcontainers setting but TESTCONTAINERS_CHECKS_DISABLE and "
                        + "TESTCONTAINERS_RYUK_DISABLED may be");
    }

    @Test
    void anotherEndpointOrAnyRegistryCredentialIsRefused() throws IOException {
        Map<String, String> environment = prepared();
        environment.put("DOCKER_HOST", "tcp://example.org:2375");
        environment.put("DOCKER_AUTH_CONFIG", "{}");
        Files.writeString(dockerConfig.resolve("config.json"), "{\"credsStore\":\"desktop\"}");

        assertThat(problems(environment)).hasSize(3).anySatisfy(problem -> assertThat(problem)
                .isEqualTo("DOCKER_HOST is 'tcp://example.org:2375', not unix:///var/run/docker.sock"))
                .anySatisfy(problem -> assertThat(problem).startsWith("DOCKER_AUTH_CONFIG is set"))
                .anySatisfy(problem -> assertThat(problem).startsWith("DOCKER_CONFIG must name a directory"));
    }

    @Test
    void theDockerConfigurationHoldsOnlyAnEmptyConfigJson() throws IOException {
        assertThat(ReviewedPostgres.dockerConfigProblem(null)).hasValueSatisfying(problem ->
                assertThat(problem).endsWith("it is not set"));
        Files.writeString(dockerConfig.resolve("config.json"), "{}\n");
        assertThat(ReviewedPostgres.dockerConfigProblem(dockerConfig.toString())).isEmpty();
        Files.writeString(dockerConfig.resolve("daemon.json"), "{}");
        assertThat(ReviewedPostgres.dockerConfigProblem(dockerConfig.toString())).isPresent();
    }

    @Test
    void aTestcontainersPropertiesFileOrAJnaLoadingSettingIsRefused() throws IOException {
        Files.writeString(home.resolve(".testcontainers.properties"), "docker.client.strategy=x\n");
        Files.writeString(dockerConfig.resolve("config.json"), "{}");

        assertThat(ReviewedPostgres.environmentProblems(prepared(), Set.of("jna.nosys"), true, home)).containsExactly(
                "a testcontainers.properties is on the test classpath",
                "~/.testcontainers.properties exists; Testcontainers would read it",
                "the system property jna.nosys is set");
    }

    @Test
    void theReviewedEngineReportPasses() {
        assertThat(ReviewedPostgres.engineProblems(GITHUB_UBUNTU_24_04, report("28.0.4", "1.3.3", "Ubuntu 24.04.5 LTS")))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"28.0.5", "28.0.3", "29.5.2", "28.0.4-rc.1", ""})
    void anyOtherEngineVersionIsRefusedNotLoosened(String version) {
        assertThat(ReviewedPostgres.engineProblems(GITHUB_UBUNTU_24_04, report(version, "1.3.3", "Ubuntu 24.04.5 LTS")))
                .singleElement().asString().startsWith("Docker Engine is '" + version + "'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.3.3", "1.3.4", "v1.3.3", "1.4.0", "1.4.0-rc.3", "1.10.0", "2.0.0"})
    void runcFrom133OnIsAccepted(String version) {
        assertThat(ReviewedPostgres.runcAccepted(version)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.2.5", "1.2.8", "1.3.2", "1.3.0-rc.1", "1.1.15", "0.9.9", "", "1.3", "unknown"})
    void runcBefore133OrUnreadableIsRefused(String version) {
        assertThat(ReviewedPostgres.runcAccepted(version)).isFalse();
        assertThat(ReviewedPostgres.engineProblems(GITHUB_UBUNTU_24_04, report("28.0.4", version, "Ubuntu 24.04.5 LTS")))
                .singleElement().asString().startsWith("runc is");
    }

    @Test
    void anotherOperatingSystemEndpointOrPlatformIsRefused() {
        EngineReport elsewhere = new EngineReport("unix:///run/user/1000/docker.sock", "28.0.4", "1.48", "arm64",
                "Ubuntu 22.04.5 LTS", "6.8.0", Map.of("Engine", "28.0.4", "containerd", "1.7.27", "runc", "1.3.3"),
                Map.of());

        assertThat(ReviewedPostgres.engineProblems(GITHUB_UBUNTU_24_04, elsewhere)).hasSize(3);
        assertThat(ReviewedPostgres.engineProblems(GITHUB_UBUNTU_24_04, report("28.0.4", "1.3.3", "Ubuntu 24.04")))
                .isEmpty();
    }

    @Test
    void theStagedImageMustBeTheReviewedDigestForThePlatform() {
        assertThat(ReviewedPostgres.imageProblems(GITHUB_UBUNTU_24_04, List.of(REFERENCE), "linux", "amd64")).isEmpty();
        assertThat(ReviewedPostgres.imageProblems(GITHUB_UBUNTU_24_04, List.of(), "linux", "arm64")).hasSize(2);
    }

    @Test
    void aContainerFromTheStagedImageOnLoopbackOnlyPasses() {
        assertThat(ReviewedPostgres.containerProblems(container(false, 0, "bridge", "127.0.0.1"), "sha256:staged"))
                .isEmpty();
    }

    @Test
    void aPrivilegedContainerHostBindsAnotherNetworkOrAPortBeyondLoopbackIsRefused() {
        assertThat(ReviewedPostgres.containerProblems(container(true, 1, "host", "0.0.0.0"), "sha256:staged"))
                .containsExactly("the container is privileged", "the container has host binds or mounts",
                        "the container's network mode is 'host', not the bridge",
                        "port 5432/tcp is published on '0.0.0.0', not 127.0.0.1");
        assertThat(ReviewedPostgres.containerProblems(container(false, 0, "bridge", "127.0.0.1"), "sha256:other"))
                .singleElement().asString().startsWith("the container was made from");
        assertThat(ReviewedPostgres.containerProblems(new ContainerFacts(REFERENCE, "sha256:staged", false, 0, 0,
                "bridge", Map.of()), "sha256:staged")).containsExactly("the container publishes no port");
    }

    private Map<String, String> prepared() throws IOException {
        if (!Files.exists(dockerConfig.resolve("config.json"))) {
            Files.writeString(dockerConfig.resolve("config.json"), "{}");
        }
        Map<String, String> environment = new HashMap<>();
        environment.put(ReviewedPostgres.ENGINE_VARIABLE, "github-ubuntu-24.04");
        environment.put("DOCKER_HOST", "unix:///var/run/docker.sock");
        environment.put("DOCKER_CONFIG", dockerConfig.toString());
        environment.put("TESTCONTAINERS_RYUK_DISABLED", "true");
        environment.put("TESTCONTAINERS_CHECKS_DISABLE", "true");
        environment.put("HOME", home.toString());
        return environment;
    }

    private List<String> problems(Map<String, String> environment) {
        return ReviewedPostgres.environmentProblems(environment, Set.of(), false, home);
    }

    private static EngineReport report(String engine, String runc, String operatingSystem) {
        return new EngineReport("unix:///var/run/docker.sock", engine, "1.48", "amd64", operatingSystem,
                "6.8.0-1017-azure", Map.of("Engine", engine, "containerd", "1.7.27", "runc", runc), Map.of());
    }

    private static ContainerFacts container(boolean privileged, int binds, String network, String hostIp) {
        return new ContainerFacts(REFERENCE, "sha256:staged", privileged, binds, 0, network,
                Map.of("5432/tcp", List.of(hostIp)));
    }
}
