package io.github.stigjohnny.shipping.architecture;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The provider index that build-logic's ProviderIndexTask writes for this project: every artifact on its
 * compileClasspath, runtimeClasspath, testCompileClasspath and testRuntimeClasspath, with the classes it provides, read
 * from its JAR's entries. A class is attributed to an artifact by its exact name, never by its package.
 */
final class ProviderIndex {

    /** Set for the test task by apps/shipping-api/build.gradle.kts: the index file. */
    static final String PROPERTY = "shipping.providerIndex";
    static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    /** One artifact: a Maven component ({@code maven}), a project of this build ({@code project}) or neither. */
    record Provider(String kind, String coordinate, String version, String file, @Nullable String sha256,
                    Set<String> configurations, Set<String> classes) {

        String label() {
            return kind + " " + coordinate + (version.isEmpty() ? "" : ":" + version);
        }
    }

    /** A class that more than one artifact provides on one configuration. */
    record Duplicate(String configuration, String className, List<String> providers) {
    }

    private final List<Provider> providers;
    private final List<Duplicate> duplicates;
    private final Map<String, Map<String, List<Provider>>> byConfiguration = new HashMap<>();

    ProviderIndex(List<Provider> providers, List<Duplicate> duplicates) {
        this.providers = List.copyOf(providers);
        this.duplicates = List.copyOf(duplicates);
        for (Provider provider : providers) {
            for (String configuration : provider.configurations()) {
                Map<String, List<Provider>> classes = byConfiguration.computeIfAbsent(configuration,
                        key -> new HashMap<>());
                provider.classes().forEach(name -> classes.computeIfAbsent(name, key -> new ArrayList<>())
                        .add(provider));
            }
        }
    }

    /** The index the build wrote for this test run. */
    static ProviderIndex current() {
        String file = System.getProperty(PROPERTY, "");
        if (file.isBlank()) {
            throw new IllegalStateException("The system property " + PROPERTY + " is not set: run the tests with "
                    + "Gradle.");
        }
        return read(Path.of(file));
    }

    static ProviderIndex read(Path file) {
        JsonNode index = JSON.readTree(file);
        if (!"provider-index/1".equals(index.path("schema").asString(""))) {
            throw new IllegalStateException(file + " is not a provider index of schema provider-index/1.");
        }
        List<Provider> providers = new ArrayList<>();
        for (JsonNode provider : index.get("providers").values()) {
            JsonNode sha256 = provider.get("sha256");
            providers.add(new Provider(provider.get("kind").stringValue(), provider.get("coordinate").stringValue(),
                    provider.get("version").stringValue(), provider.get("file").stringValue(),
                    sha256.isNull() ? null : sha256.stringValue(), strings(provider.get("configurations")),
                    strings(provider.get("classes"))));
        }
        List<Duplicate> duplicates = new ArrayList<>();
        for (JsonNode duplicate : index.get("duplicates").values()) {
            duplicates.add(new Duplicate(duplicate.get("configuration").stringValue(),
                    duplicate.get("class").stringValue(),
                    strings(duplicate.get("providers")).stream().sorted().toList()));
        }
        return new ProviderIndex(providers, duplicates);
    }

    List<Provider> providers() {
        return providers;
    }

    /** Classes that two artifacts provide on one configuration: an ambiguity, wherever it is. */
    List<Duplicate> duplicates() {
        return duplicates;
    }

    /** The artifacts that provide the class on the configuration: exactly one, or none, or more (ambiguous). */
    List<Provider> providers(String configuration, String className) {
        return List.copyOf(byConfiguration.getOrDefault(configuration, Map.of()).getOrDefault(className, List.of()));
    }

    /** The configurations on which some artifact provides the class, sorted. */
    List<String> configurationsProviding(String className) {
        return byConfiguration.entrySet().stream().filter(entry -> entry.getValue().containsKey(className))
                .map(Map.Entry::getKey).sorted().toList();
    }

    /** A copy with one artifact changed, for the tests that show what an index the build could write must fail. */
    ProviderIndex changing(String coordinate, UnaryOperator<Provider> change) {
        return new ProviderIndex(providers.stream().map(provider -> provider.coordinate().equals(coordinate)
                ? change.apply(provider) : provider).toList(), duplicates);
    }

    /** A copy with one more artifact. */
    ProviderIndex adding(Provider provider) {
        return new ProviderIndex(Stream.concat(providers.stream(), Stream.of(provider)).toList(), duplicates);
    }

    private static Set<String> strings(JsonNode array) {
        return array.values().stream().map(JsonNode::stringValue).collect(Collectors.toUnmodifiableSet());
    }
}
