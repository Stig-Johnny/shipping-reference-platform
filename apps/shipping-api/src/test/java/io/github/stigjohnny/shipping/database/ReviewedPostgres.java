package io.github.stigjohnny.shipping.database;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Version;
import com.github.dockerjava.api.model.VersionComponent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts the reviewed PostgreSQL for a test, and only on a container engine that a review approves for these tests.
 *
 * <p>It fails closed, in three steps. Before it connects to an engine, it checks the environment the runner prepared:
 * the engine it names, that engine's Unix socket, empty registry credentials, Ryuk and the startup checks off, and no
 * Testcontainers configuration file. Before any container exists, it checks the engine's own report (the exact Docker
 * Engine version, a runc the review accepts, the operating system and platform) and that the image is the reviewed
 * digest, already staged, so that nothing is pulled here. After the start, it checks how the container was made: from
 * that image, not privileged, with no host binds or mounts, on the default bridge, and every published port on
 * 127.0.0.1. Any problem stops the test with every problem found; nothing is loosened to make a database start.
 *
 * <p>The conditions are those of the reviews in security/reviews/: the GitHub-hosted runner's Docker Engine 28.0.4,
 * the PostgreSQL 18.6 image, Testcontainers 2.0.5 and its internals. The runner's half (the loopback-only daemon, the
 * staging, the image-set check and the cleanup) is tools/ci/container-engine.sh.
 */
public final class ReviewedPostgres {

    /** The reviewed image index, by its digest alone, never a tag (container-image--postgres--18.6-trixie.md). */
    private static final DockerImageName IMAGE =
            DockerImageName.parse("postgres@sha256:bd5dfa494a5a259704365813dbfa5fa6b3e69be7348d43b547f540c730478583");

    /** Where the runner names the reviewed engine it prepared. */
    static final String ENGINE_VARIABLE = "SRP_CONTAINER_ENGINE";

    /** The two Testcontainers settings the reviews require; any other one is refused. */
    static final Map<String, String> TESTCONTAINERS_SETTINGS =
            Map.of("TESTCONTAINERS_RYUK_DISABLED", "true", "TESTCONTAINERS_CHECKS_DISABLE", "true");

    /** Settings that would add registry credentials, TLS or another endpoint; refused when set at all. */
    static final List<String> REFUSED_DOCKER_SETTINGS =
            List.of("DOCKER_AUTH_CONFIG", "DOCKER_CERT_PATH", "DOCKER_TLS_VERIFY", "DOCKER_CONTEXT");

    /** JNA's loading settings: the internals review allows only the native stub of the pinned jar. */
    static final List<String> REFUSED_JNA_PROPERTIES =
            List.of("jna.boot.library.path", "jna.library.path", "jna.nosys", "jna.nounpack");

    /** The oldest runc the runner review accepts: 1.3.3 has the container-escape fixes, and 1.2.x is unsupported. */
    private static final List<Integer> RUNC_FLOOR = List.of(1, 3, 3);
    private static final Pattern RELEASE = Pattern.compile("v?(\\d+)\\.(\\d+)\\.(\\d+)([-+.~].*)?");
    private static final String LOOPBACK = "127.0.0.1";
    private static final Set<String> DEFAULT_NETWORKS = Set.of("bridge", "default");

    private ReviewedPostgres() {
    }

    /** A container engine that a review approves for these tests, by the name the runner passes. */
    enum Engine {
        /** GitHub-hosted ubuntu-24.04, x64: runner-image--github-ubuntu-24.04-docker-engine--28.0.4.md. */
        GITHUB_UBUNTU_24_04("github-ubuntu-24.04", "unix:///var/run/docker.sock", "28.0.4", "Ubuntu 24.04", "amd64");

        final String id;
        final String dockerHost;
        final String serverVersion;
        final String operatingSystem;
        final String architecture;

        Engine(String id, String dockerHost, String serverVersion, String operatingSystem, String architecture) {
            this.id = id;
            this.dockerHost = dockerHost;
            this.serverVersion = serverVersion;
            this.operatingSystem = operatingSystem;
            this.architecture = architecture;
        }

        static Optional<Engine> named(@Nullable String id) {
            return Stream.of(values()).filter(engine -> engine.id.equals(id)).findFirst();
        }
    }

    /** What an engine reports about itself, with each component's version and commit. */
    record EngineReport(String dockerHost, String serverVersion, String apiVersion, String architecture,
            String operatingSystem, String kernelVersion, Map<String, String> versions, Map<String, String> commits) {

        static EngineReport of(String dockerHost, Version version, Info info) {
            Map<String, String> versions = new TreeMap<>();
            Map<String, String> commits = new TreeMap<>();
            List<VersionComponent> components = version.getComponents();
            for (VersionComponent component : components == null ? List.<VersionComponent>of() : components) {
                Map<String, String> details = component.getDetails();
                versions.put(text(component.getName()), text(component.getVersion()));
                commits.put(text(component.getName()), details == null ? "" : text(details.get("GitCommit")));
            }
            return new EngineReport(dockerHost, text(info.getServerVersion()), text(version.getApiVersion()),
                    text(version.getArch()), text(info.getOperatingSystem()), text(info.getKernelVersion()),
                    Map.copyOf(versions), Map.copyOf(commits));
        }

        /** One line for the test report: the versions and commits the runner review asks to be recorded. */
        String describe(Engine engine) {
            String components = versions.keySet().stream()
                    .map(name -> name + " " + versions.get(name) + " (commit " + commits.getOrDefault(name, "") + ")")
                    .collect(Collectors.joining(", "));
            return "Container engine " + engine.id + " at " + dockerHost + ": ServerVersion " + serverVersion
                    + ", ApiVersion " + apiVersion + ", " + components + ", KernelVersion " + kernelVersion
                    + ", OperatingSystem " + operatingSystem + ", " + architecture;
        }
    }

    /** How a container was made, as far as the reviews' conditions go. */
    record ContainerFacts(String image, String imageId, boolean privileged, int binds, int mounts, String networkMode,
            Map<String, List<String>> publishedHostIps) {

        static ContainerFacts of(InspectContainerResponse container) {
            HostConfig host = container.getHostConfig();
            Map<String, List<String>> published = new TreeMap<>();
            Map<ExposedPort, Ports.Binding[]> bindings = container.getNetworkSettings().getPorts().getBindings();
            for (Map.Entry<ExposedPort, Ports.Binding[]> entry : bindings.entrySet()) {
                Ports.Binding[] each = entry.getValue();
                published.put(entry.getKey().toString(), each == null ? List.of()
                        : Stream.of(each).map(binding -> binding == null ? "" : text(binding.getHostIp())).toList());
            }
            return new ContainerFacts(text(container.getConfig().getImage()), text(container.getImageId()),
                    Boolean.TRUE.equals(host.getPrivileged()), host.getBinds() == null ? 0 : host.getBinds().length,
                    host.getMounts() == null ? 0 : host.getMounts().size(), text(host.getNetworkMode()),
                    Map.copyOf(published));
        }
    }

    /** The reviewed image as the tests name it: postgres, by its index digest. */
    static String reference() {
        return IMAGE.asCanonicalNameString();
    }

    /**
     * A running test database, as far as a test may reach it: where to connect, as whom, and how to stop it. The
     * container itself stays inside this class, so that no test can change or restart it after the checks.
     */
    public static final class Database implements AutoCloseable {

        private final PostgreSQLContainer container;

        private Database(PostgreSQLContainer container) {
            this.container = container;
        }

        public String jdbcUrl() {
            return container.getJdbcUrl();
        }

        public String username() {
            return container.getUsername();
        }

        public String password() {
            return container.getPassword();
        }

        /** Stops the container, which Testcontainers removes with its anonymous volume. */
        @Override
        public void close() {
            container.stop();
        }
    }

    /**
     * Starts the reviewed PostgreSQL with a random password of its own and returns its {@link Database}; the caller
     * closes it, with try-with-resources. It throws if any condition does not hold: before any container exists when
     * the environment, the engine or the staged image fails its check, and after stopping the container again when the
     * started container fails its own. Its published ports are therefore checked after the start; the runner's
     * loopback-only daemon binds them to 127.0.0.1 from the moment the container is created.
     */
    public static Database start() {
        Set<String> jna = REFUSED_JNA_PROPERTIES.stream().filter(name -> System.getProperty(name) != null)
                .collect(Collectors.toUnmodifiableSet());
        refuseUnless(environmentProblems(System.getenv(), jna,
                ReviewedPostgres.class.getResource("/testcontainers.properties") != null,
                Path.of(Objects.requireNonNull(System.getProperty("user.home"), "user.home"))));
        Engine engine = Engine.named(System.getenv(ENGINE_VARIABLE)).orElseThrow();

        DockerClient client = DockerClientFactory.instance().client();
        EngineReport report = EngineReport.of(DockerClientFactory.instance().getTransportConfig().getDockerHost()
                .toString(), client.versionCmd().exec(), client.infoCmd().exec());
        System.out.println(report.describe(engine));
        refuseUnless(engineProblems(engine, report));

        InspectImageResponse image;
        try {
            image = client.inspectImageCmd(reference()).exec();
        } catch (NotFoundException e) {
            IllegalStateException refusal = refusal(List.of("the reviewed image " + reference()
                    + " is not staged: the runner pulls it by digest before the tests, and the tests pull nothing"));
            refusal.initCause(e);
            throw refusal;
        }
        List<String> digests = image.getRepoDigests();
        refuseUnless(imageProblems(engine, digests == null ? List.of() : digests, text(image.getOs()),
                text(image.getArch())));

        PostgreSQLContainer postgres = new PostgreSQLContainer(IMAGE).withPassword(password())
                .withImagePullPolicy(name -> false);
        postgres.start();
        List<String> problems = containerProblems(ContainerFacts.of(postgres.getContainerInfo()), text(image.getId()));
        if (!problems.isEmpty()) {
            postgres.stop();
            throw refusal(problems);
        }
        return new Database(postgres);
    }

    /** What is wrong with the environment the runner prepared, before any engine is contacted. */
    static List<String> environmentProblems(Map<String, String> environment, Set<String> jnaProperties,
            boolean classpathProperties, Path home) {
        List<String> problems = new ArrayList<>();
        String named = environment.get(ENGINE_VARIABLE);
        Optional<Engine> engine = Engine.named(named);
        if (engine.isEmpty()) {
            problems.add(ENGINE_VARIABLE + " names no reviewed container engine (" + shown(named) + "). Database "
                    + "tests run only where a runner prepared one under its review: :apps:shipping-api:databaseTest "
                    + "in CI's java check and the pages workflow's test job. ./gradlew check needs no engine");
        }
        engine.filter(e -> !e.dockerHost.equals(environment.get("DOCKER_HOST"))).ifPresent(e -> problems.add(
                "DOCKER_HOST is " + shown(environment.get("DOCKER_HOST")) + ", not " + e.dockerHost));
        new TreeMap<>(TESTCONTAINERS_SETTINGS).forEach((name, value) -> {
            if (!value.equals(environment.get(name))) {
                problems.add(name + " is " + shown(environment.get(name)) + ", not " + value);
            }
        });
        environment.keySet().stream().filter(name -> name.startsWith("TESTCONTAINERS_"))
                .filter(name -> !TESTCONTAINERS_SETTINGS.containsKey(name)).sorted()
                .forEach(name -> problems.add(name + " is set; no Testcontainers setting but "
                        + String.join(" and ", new TreeMap<>(TESTCONTAINERS_SETTINGS).keySet()) + " may be"));
        REFUSED_DOCKER_SETTINGS.stream().filter(environment::containsKey)
                .forEach(name -> problems.add(name + " is set; the tests use no credentials, TLS or context"));
        dockerConfigProblem(environment.get("DOCKER_CONFIG")).ifPresent(problems::add);
        if (classpathProperties) {
            problems.add("a testcontainers.properties is on the test classpath");
        }
        if (Files.exists(home.resolve(".testcontainers.properties"))) {
            problems.add("~/.testcontainers.properties exists; Testcontainers would read it");
        }
        jnaProperties.stream().sorted().forEach(name -> problems.add("the system property " + name + " is set"));
        return List.copyOf(problems);
    }

    /** DOCKER_CONFIG must name a directory that holds only config.json, and that file only {}. */
    static Optional<String> dockerConfigProblem(@Nullable String directory) {
        String wanted = "DOCKER_CONFIG must name a directory that holds only config.json with {} (no credentials, "
                + "no credential helper)";
        if (directory == null || directory.isBlank()) {
            return Optional.of(wanted + "; it is not set");
        }
        Path config = Path.of(directory);
        try (Stream<Path> files = Files.list(config)) {
            List<String> names = files.map(file -> file.getFileName().toString()).sorted().toList();
            boolean empty = names.equals(List.of("config.json"))
                    && Files.readString(config.resolve("config.json")).strip().equals("{}");
            return empty ? Optional.empty() : Optional.of(wanted);
        } catch (IOException e) {
            return Optional.of(wanted + "; it cannot be read (" + e.getClass().getSimpleName() + ")");
        }
    }

    /** What is wrong with the engine, by its own report, before any container exists. */
    static List<String> engineProblems(Engine engine, EngineReport report) {
        List<String> problems = new ArrayList<>();
        if (!engine.dockerHost.equals(report.dockerHost())) {
            problems.add("Testcontainers connected to " + report.dockerHost() + ", not " + engine.dockerHost);
        }
        String component = report.versions().getOrDefault("Engine", "");
        if (!engine.serverVersion.equals(report.serverVersion()) || !engine.serverVersion.equals(component)) {
            problems.add("Docker Engine is " + shown(report.serverVersion()) + " (component " + shown(component)
                    + "), not the reviewed " + engine.serverVersion + ": another version needs its own review first");
        }
        String runc = report.versions().getOrDefault("runc", "");
        if (!runcAccepted(runc)) {
            problems.add("runc is " + shown(runc) + "; the review accepts 1.3.3 or later and rejects 1.2.x");
        }
        if (report.versions().getOrDefault("containerd", "").isBlank()) {
            problems.add("the engine reports no containerd version, which the review asks to record");
        }
        String system = report.operatingSystem();
        if (!system.equals(engine.operatingSystem) && !system.startsWith(engine.operatingSystem + ".")
                && !system.startsWith(engine.operatingSystem + " ")) {
            problems.add("the engine runs on " + shown(system) + ", not " + engine.operatingSystem);
        }
        if (!engine.architecture.equals(report.architecture())) {
            problems.add("the engine is " + shown(report.architecture()) + ", not " + engine.architecture);
        }
        return List.copyOf(problems);
    }

    /**
     * Whether runc is 1.3.3 or later, by its release numbers: a later release passes with any suffix (1.4.0-rc.3), the
     * floor only without one (1.3.3-rc.1 is a prerelease of it). Anything unreadable is refused.
     */
    static boolean runcAccepted(String version) {
        Matcher release = RELEASE.matcher(version);
        if (!release.matches()) {
            return false;
        }
        for (int i = 0; i < RUNC_FLOOR.size(); i++) {
            int compared = Integer.compare(Integer.parseInt(release.group(i + 1)), RUNC_FLOOR.get(i));
            if (compared != 0) {
                return compared > 0;
            }
        }
        // The floor itself, exactly: a prerelease of it (1.3.3-rc.1) or anything else after its numbers is below it.
        return release.group(4) == null;
    }

    /** What is wrong with the staged image: it must be the reviewed index digest, for the engine's platform. */
    static List<String> imageProblems(Engine engine, List<String> repoDigests, String os, String architecture) {
        List<String> problems = new ArrayList<>();
        if (!repoDigests.contains(reference())) {
            problems.add("the staged image's RepoDigests " + repoDigests + " do not contain " + reference());
        }
        if (!"linux".equals(os) || !engine.architecture.equals(architecture)) {
            problems.add("the staged image is " + shown(os) + "/" + shown(architecture) + ", not linux/"
                    + engine.architecture);
        }
        return List.copyOf(problems);
    }

    /** What is wrong with how the container was made, after its start. */
    static List<String> containerProblems(ContainerFacts container, String imageId) {
        List<String> problems = new ArrayList<>();
        if (!reference().equals(container.image()) || !imageId.equals(container.imageId())) {
            problems.add("the container was made from " + shown(container.image()) + ", not the staged "
                    + reference());
        }
        if (container.privileged()) {
            problems.add("the container is privileged");
        }
        if (container.binds() > 0 || container.mounts() > 0) {
            problems.add("the container has host binds or mounts");
        }
        if (!DEFAULT_NETWORKS.contains(container.networkMode())) {
            problems.add("the container's network mode is " + shown(container.networkMode()) + ", not the bridge");
        }
        if (container.publishedHostIps().values().stream().allMatch(List::isEmpty)) {
            problems.add("the container publishes no port");
        }
        container.publishedHostIps().forEach((port, hostIps) -> hostIps.stream().filter(ip -> !LOOPBACK.equals(ip))
                .forEach(ip -> problems.add("port " + port + " is published on " + shown(ip) + ", not " + LOOPBACK)));
        return List.copyOf(problems);
    }

    private static void refuseUnless(List<String> problems) {
        if (!problems.isEmpty()) {
            throw refusal(problems);
        }
    }

    private static IllegalStateException refusal(List<String> problems) {
        return new IllegalStateException("Refusing to start PostgreSQL for the tests:\n- " + String.join("\n- ", problems));
    }

    private static String password() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String text(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static String shown(@Nullable String value) {
        return value == null ? "unset" : "'" + value + "'";
    }
}
